package tr.kapisma.game;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import tr.kapisma.Msg;
import tr.kapisma.arena.*;
import tr.kapisma.arena.MatchRoster.*;
import tr.kapisma.lobby.Lobby;
import tr.kapisma.origin.OriginService;
import tr.kapisma.team.TeamService;
import tr.kapisma.voice.VoiceBridge;

/** Opt-in integration candidate; one isolated world, no global datapack rules or permanent economy. */
public final class NativeArenaGame extends Game {
    public enum Mode {
        CARAVAN("KERVAN_BASKINI","Kervan Baskını"),
        CLUES("YASAYAN_HARITA","Yaşayan Harita"),
        BUILD("INSA_ET_SAVUN","İnşa Et, Savun"),
        AUCTION("KIT_IHALESI","Kit İhalesi"),
        FLIGHT("ALCAK_IRTIFA","Alçak İrtifa"),
        RADIO("KIRIK_TELSIZ","Kırık Telsiz");
        public final String id,title;
        Mode(String id,String title){this.id=id;this.title=title;}
    }
    public static Optional<Mode> mode(String id){return Arrays.stream(Mode.values()).filter(m->m.id.equals(id)).findFirst();}
    public static boolean enabled(){return Boolean.getBoolean("kapisma.experimentalArena");}
    public static boolean isNew(String id){return RotatingOutpostGame.ID.equals(id)||mode(id).isPresent();}
    private final Mode mode;
    private final UUID match=UUID.randomUUID();
    private final Set<UUID> entered=new HashSet<>(),offline=new HashSet<>();
    private final Map<UUID,Integer> revision=new HashMap<>();
    private final Map<UUID,Position> countdownAnchors=new HashMap<>();
    private final List<BlockPos> pinned=new ArrayList<>();
    private final ArrayDeque<Work> changes=new ArrayDeque<>();
    private record Work(BlockPos pos,Block block){}
    private GameWorld arena;
    private MatchRoster roster;
    private CaravanHostAdapter caravan2;
    private CaravanRules caravan;private ClueRaceRules clues;private BuildAssaultRules build;
    private SiegeEconomyRules siege;
    private final Set<UUID> siegeKitIssued=new HashSet<>();
    private final EnumMap<Side,Integer> siegeCoreLastHit=new EnumMap<>(Side.class);
    private boolean siegeOpening;
    private AuctionRules auction;private FlightRaceRules flight;private RadioPuzzleRules radio;
    private List<FlightRaceRules.Gate> gates=List.of();
    private FlightBoostRules boosts;
    private final Map<UUID,net.minecraft.world.entity.projectile.FireworkRocketEntity> flyingRockets=new HashMap<>();
    private final Set<UUID> touchingSpeed=new HashSet<>(),touchingLift=new HashSet<>();
    private List<ClueRaceRules.Clue> redRoute=List.of(),blueRoute=List.of();
    private int y,xRadius,zRadius,cursor,prepTicks,countdown=100,stage=1,endTicks,shownAward;
    private boolean closed,closing,paused,resetBuilding;
    private BlockPos caravanMarker;
    private String outcome="";
    public NativeArenaGame(GameManager mgr,Mode mode){super(mgr);this.mode=Objects.requireNonNull(mode);}
    @Override public String id(){return mode.id;}
    @Override public String title(){return mode.title;}
    @Override public boolean isClosed(){return closed;}
    public boolean cleanupPending(){return closing&&!closed;}
    private void thread(){if(!server.isSameThread())throw new IllegalStateException("server thread");}
    private boolean owns(ServerPlayer p){return arena!=null&&p.level()==arena.level&&contains(p.getUUID())&&entered.contains(p.getUUID());}
    private boolean active(ServerPlayer p){return owns(p)&&!offline.contains(p.getUUID())&&!p.isSpectator()&&p.isAlive();}
    private boolean eligible(ServerPlayer p){return phase==Phase.RUNNING&&!closing&&!resetBuilding&&active(p);}
    private boolean safeLobby(ServerPlayer p){
        if(!Lobby.inLobby(p)||p.gameMode.getGameModeForPlayer()!=GameType.ADVENTURE||
            p.containerMenu!=p.inventoryMenu||!p.containerMenu.getCarried().isEmpty())return false;
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            ItemStack s=p.getInventory().getItem(i);
            if(!s.isEmpty()&&(i!=4||!ItemStack.matches(s,Lobby.menuItem())))return false;
        }
        for(int i=0;i<p.getEnderChestInventory().getContainerSize();i++)
            if(!p.getEnderChestInventory().getItem(i).isEmpty())return false;
        return p.totalExperience==0&&p.experienceLevel==0&&p.experienceProgress==0;
    }
    @Override public String prepare(List<ServerPlayer> participants){
        thread();
        if(!enabled())return "Deneysel arena devre dışı.";
        if(arena!=null||closed||participants.size()<2||participants.size()>8)return "2–8 oyuncu gerekli.";
        if(mode==Mode.RADIO&&(!Boolean.getBoolean("kapisma.experimentalAnalogVoice")||!tr.kapisma.mixin.AnalogVoiceMixinGate.supported()||!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat")))return "Telsiz için deneysel Analog ve ses köprüsü gerekli.";
        Set<UUID> unique=new HashSet<>();
        for(var p:participants)if(!safeLobby(p)||!unique.add(p.getUUID()))return "Temiz lobi envanteri gerekli.";
        try{
            xRadius=mode==Mode.FLIGHT?76:36;zRadius=mode==Mode.FLIGHT?24:mode==Mode.CLUES?28:24;
            arena=GameWorld.open(server,rnd.nextLong(),0,0,new GameWorld.Options(true,false,false,true));
            y=arena.level.getMaxBuildHeight()-(mode==Mode.FLIGHT?70:20);
            // Keep the authoritative playable bounds and horse/hook limits unchanged.
            arena.setBorder(mode==Mode.CARAVAN&&Boolean.getBoolean("kapisma.experimentalCaravan2")?96:xRadius+8);
            for(int cx=Math.floorDiv(-xRadius,16);cx<=Math.floorDiv(xRadius,16);cx++)
                for(int cz=Math.floorDiv(-zRadius,16);cz<=Math.floorDiv(zRadius,16);cz++){
                    var p=new BlockPos(cx*16,y,cz*16);arena.force(p.getX(),p.getZ(),true);pinned.add(p);
                }
            var order=new ArrayList<>(participants);Collections.shuffle(order,rnd);
            List<UUID> a=new ArrayList<>(),b=new ArrayList<>();
            List<ServerPlayer> ar=new ArrayList<>(),br=new ArrayList<>();
            for(int i=0;i<order.size();i++){
                var p=order.get(i);players.add(p.getUUID());names.put(p.getUUID(),p.getGameProfile().getName());
                (i%2==0?a:b).add(p.getUUID());(i%2==0?ar:br).add(p);
            }
            roster=new MatchRoster(match,a,b);
            teams=mode==Mode.AUCTION||mode==Mode.FLIGHT
                ?TeamService.assign(server,order,false,true,rnd,mgr.teamPrefs)
                :tr.kapisma.team.ArenaTeamFactory.balanced(server,ar,br);
            switch(mode){
                case CARAVAN -> {if(Boolean.getBoolean("kapisma.experimentalCaravan2"))caravan2=new CaravanHostAdapter(arena.level,roster,y,xRadius,zRadius,mgr.settings.rules.getOrDefault(mode.id,Map.of()).getOrDefault("manualClimb",0d)>=.5);else caravan=new CaravanRules(roster,new Position(-23.5,y+1,.5));}
                case CLUES -> {redRoute=route(Side.RED);blueRoute=route(Side.BLUE);clues=new ClueRaceRules(roster,redRoute,blueRoute);}
                case BUILD -> {
                    if(Boolean.getBoolean("kapisma.experimentalSiegeEconomy"))
                        siege=new SiegeEconomyRules(roster,SiegeShop.PRODUCTS.stream().map(SiegeShop.Product::rule).toList(),
                            new SiegeEconomyRules.Zone(-32,-3,0,5,-20,20),new SiegeEconomyRules.Zone(3,32,0,5,-20,20),1200);
                    else build=new BuildAssaultRules(roster);
                }
                case AUCTION -> auction=new AuctionRules(match,players,rnd);
                case FLIGHT -> {
                    gates=List.of(new FlightRaceRules.Gate(-30,y+38,0,4,4),new FlightRaceRules.Gate(0,y+31,12,4,4),
                        new FlightRaceRules.Gate(28,y+24,-8,4,4),new FlightRaceRules.Gate(52,y+17,0,4,4));
                    flight=new FlightRaceRules(match,players,gates);
                    boosts=new FlightBoostRules(match,players,List.of(new FlightBoostRules.Pad("speed",FlightBoostRules.PadKind.SPEED,60),new FlightBoostRules.Pad("lift",FlightBoostRules.PadKind.LIFT,60)));
                }
                case RADIO -> radio=new RadioPuzzleRules(roster,new java.security.SecureRandom());
            }
            return null;
        }catch(RuntimeException ex){tr.kapisma.Kapisma.LOG.error("Arena prepare failed",ex);stop("Arena hazırlığı başarısız.");return "Arena açılamadı.";}
    }
    private List<ClueRaceRules.Clue> route(Side side){
        int sign=side==Side.RED?-1:1;
        return List.of(new ClueRaceRules.Clue("Taş sütun",new Position(-24.5,y+1,sign*6+.5)),
            new ClueRaceRules.Clue("Bakır kemer",new Position(-8.5,y+1,sign*20+.5)),
            new ClueRaceRules.Clue("Altın işaret",new Position(10.5,y+1,sign*8+.5)),
            new ClueRaceRules.Clue("Zümrüt kule",new Position(25.5,y+1,sign*20+.5)));
    }
    private static Block clueBlock(int n){return switch(n){case 0->Blocks.STONE_BRICKS;case 1->Blocks.COPPER_BLOCK;case 2->Blocks.GOLD_BLOCK;default->Blocks.EMERALD_BLOCK;};}
    private Block layout(int x,int dy,int z){
        if(caravan2!=null)return switch(CaravanTerrainLayout.renderCell(x,dy,z)){
            case AIR->Blocks.AIR;case GRASS->Blocks.GRASS_BLOCK;case ROAD->Blocks.COARSE_DIRT;
            case STONE->Blocks.STONE;case DIRT->Blocks.DIRT;case ROCK->Blocks.STONE;
            case BASE->Blocks.SMOOTH_STONE;case TUNNEL_ROOF->Blocks.STONE_BRICKS;
            case ESCORT_MARK->Blocks.PURPLE_CONCRETE;case EXTRACTION_MARK->Blocks.ORANGE_CONCRETE;
            case FINISH_MARK->Blocks.GOLD_BLOCK;case SAFE_MARK->Blocks.GREEN_CONCRETE;case RISKY_MARK->Blocks.RED_CONCRETE;
            // Full cubes only: material accents preserve the established 48-block geometry.
            case ESCORT_POST->dy==2?Blocks.STRIPPED_SPRUCE_LOG:Blocks.SPRUCE_LOG;
            case ESCORT_BEAM->Math.abs(x+29)==3?Blocks.DARK_OAK_PLANKS:Blocks.SPRUCE_PLANKS;
            case ESCORT_CANOPY->Math.abs(x+29)==2?Blocks.PURPLE_TERRACOTTA:Blocks.PURPLE_WOOL;
            case ATTACK_POST->dy==2?Blocks.CHISELED_STONE_BRICKS:Blocks.STONE_BRICKS;
            case ATTACK_BEAM->Math.abs(x)==3?Blocks.SMOOTH_STONE:Blocks.DARK_OAK_PLANKS;
            case ATTACK_CANOPY->Math.abs(x)==2?Blocks.ORANGE_TERRACOTTA:Blocks.ORANGE_WOOL;
            case TUNNEL_PORTAL->Blocks.STONE_BRICKS;case TUNNEL_KEYSTONE->Blocks.CHISELED_STONE_BRICKS;
            case ESCORT_SCREEN->Blocks.SPRUCE_PLANKS;case ATTACK_SCREEN->Blocks.STONE_BRICKS;
            case SCENIC_TRUNK->Blocks.SPRUCE_LOG;case SCENIC_LEAVES->Blocks.SPRUCE_LEAVES;};
        if(mode==Mode.FLIGHT){
            if(dy==0)return Blocks.SMOOTH_STONE;
            if(dy==46&&x>=-58&&x<=-49&&Math.abs(z)<=6)return x>=-57&&x<=-55&&z>=4?Blocks.LIME_CONCRETE:Blocks.SMOOTH_STONE;
            // Optional side-route speed ring, separated from the mandatory gates.
            if(x==-42&&Math.abs(z-10)<=3&&Math.abs(dy-42)<=3&&(Math.abs(z-10)==3||Math.abs(dy-42)==3))return Blocks.CYAN_CONCRETE;
            for(int i=0;i<gates.size();i++){
                var g=gates.get(i);double yy=y+dy;
                if(x==(int)g.x()&&Math.abs(z-g.z())<=5&&Math.abs(yy-g.y())<=5&&
                    (Math.abs(z-g.z())==5||Math.abs(yy-g.y())==5))
                    return new Block[]{Blocks.YELLOW_CONCRETE,Blocks.ORANGE_CONCRETE,Blocks.LIME_CONCRETE,Blocks.BLUE_CONCRETE}[i];
                if(dy==(int)(g.y()-y+3)&&x>g.x()&&x<=g.x()+4&&Math.abs(z-g.z())<=2&&i<3)return Blocks.SMOOTH_STONE;
            }
            return Blocks.AIR;
        }
        if(siege!=null){
            if(dy==0)return Blocks.SMOOTH_STONE;
            if(x==0&&dy<=10)return Blocks.BEDROCK;
            if(dy==1&&Math.abs(x)==24&&z==0)return Blocks.TARGET;
            return Blocks.AIR;
        }
        if(dy==0){
            if(mode==Mode.CARAVAN&&Math.abs(z)<=2&&Math.abs(x)<=24)return Blocks.GRAY_CONCRETE;
            if(mode==Mode.BUILD&&Math.abs(x)<=10&&Math.abs(z)<=6)return Blocks.POLISHED_ANDESITE;
            return Blocks.SMOOTH_STONE;
        }
        if(mode==Mode.CARAVAN&&dy<=2&&Math.abs(Math.abs(z)-7)==0&&(Math.abs(x+14)<=1||Math.abs(x)<=1||Math.abs(x-14)<=1))return Blocks.STONE_BRICKS;
        if(mode==Mode.CLUES&&dy<=4)for(var side:Side.values()){
            var path=side==Side.RED?redRoute:blueRoute;for(int i=0;i<path.size();i++){
                var target=path.get(i).target();
                if(x==(int)Math.floor(target.x())&&z==(int)Math.floor(target.z()))return clueBlock(i);
            }
        }
        if(mode==Mode.BUILD&&dy==1&&x==0&&z==0)return Blocks.TARGET;
        if(mode==Mode.AUCTION&&dy<=3&&Math.abs(Math.abs(x)-10)<=1&&Math.abs(Math.abs(z)-10)<=1)return Blocks.STONE_BRICKS;
        if(mode==Mode.RADIO&&x==0&&dy<=5&&Math.abs(z)<=zRadius)return Blocks.STONE_BRICKS;
        return Blocks.AIR;
    }
    private void prepareSlice(){
        if(++prepTicks>4800){stop("Arena hazırlama zaman aşımı.");return;}
        for(var p:pinned)if(!arena.loaded(p.getX(),p.getZ()))return;
        int renderX=caravan2!=null?CaravanTerrainLayout.SCENIC_X:xRadius;
        int renderZ=caravan2!=null?CaravanTerrainLayout.SCENIC_Z:zRadius;
        int w=renderX*2+1,d=renderZ*2+1;
        int height=mode==Mode.FLIGHT?48:caravan2!=null?CaravanTerrainLayout.HEIGHT:siege!=null?11:8,total=w*d*height;
        long deadline=System.nanoTime()+3_000_000L;
        for(int n=0;n<512&&cursor<total&&System.nanoTime()<deadline;n++,cursor++){
            int x=cursor%w-renderX,z=(cursor/w)%d-renderZ,dy=cursor/(w*d);
            Block b=layout(x,dy,z);BlockPos pos=new BlockPos(x,y+dy,z);
            if(caravan2!=null&&b==Blocks.SPRUCE_LEAVES){
                var desired=b.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true);
                var current=arena.level.getBlockState(pos);
                if(!current.is(b)||!current.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT))arena.level.setBlock(pos,desired,2);
            }else if(!arena.level.getBlockState(pos).is(b))arena.level.setBlock(pos,b.defaultBlockState(),2);
        }
        if(cursor!=total)return;
        if(caravan2!=null&&Boolean.getBoolean("kapisma.arenaProof"))tr.kapisma.Kapisma.LOG.info("CARAVAN_PREP scans={} ticks={} pinned={}",total,prepTicks,pinned.size());
        for(var p:onlinePlayers())if(!safeLobby(p)){stop("Lobi envanteri değişti.");return;}
        var batch=onlinePlayers();
        try{ArenaRecovery.rememberAll(server,batch,match);}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
        // All returns are durable before any player inventory or dimension is changed.
        for(var p:batch)entered.add(p.getUUID());
        for(var p:batch)enterPrepared(p);
        phase=Phase.COUNTDOWN;
    }
    private Position pos(ServerPlayer p){return new Position(p.getX(),p.getY(),p.getZ());}
    private Presence observation(ServerPlayer p,boolean includeSpectators){
        return new Presence(p.getUUID(),match,owns(p),p.isAlive()&&!offline.contains(p.getUUID())&&(includeSpectators||!p.isSpectator()),pos(p));
    }
    private List<Presence> observations(boolean includeSpectators){
        var list=new ArrayList<Presence>();for(var p:onlinePlayers())if(owns(p))list.add(observation(p,includeSpectators));return list;
    }
    private void move(ServerPlayer p,double x,double h,double z,float yaw){
        if(flight!=null)invalidateFlight(p.getUUID());
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;p.teleportTo(arena.level,x,h,z,yaw,0);
        p.setRespawnPosition(arena.level.dimension(),BlockPos.containing(x,h,z),yaw,true,false);
        if(phase==Phase.PREP||phase==Phase.COUNTDOWN)countdownAnchors.put(p.getUUID(),new Position(x,h,z));
    }
    private void spawn(ServerPlayer p){
        Side side=roster.side(p.getUUID());int sign=side==Side.RED?-1:1;
        int i=players.indexOf(p.getUUID());
        switch(mode){
            case CARAVAN -> {
                if(caravan2==null)move(p,-28.5,y+1,side==caravan.escort()?-8.5:8.5,-90);
                else{var b=caravan2.spawnPoint(p.getUUID());move(p,b.x(),b.y(),b.z(),-90);}
            }
            case CLUES -> move(p,-30.5,y+1,sign*4+.5,-90);
            case BUILD -> {
                if(siege!=null)move(p,sign*28+.5,y+1,(i%4)*2-4+.5,side==Side.RED?-90:90);
                else move(p,build.state().phase()==BuildAssaultRules.Phase.BUILD&&side==build.defender()?-5.5:.5,y+1,
                    build.state().phase()==BuildAssaultRules.Phase.BUILD&&side==build.defender()?5.5:side==build.defender()?-10.5:12.5,0);
            }
            case AUCTION -> {double a=i*2*Math.PI/players.size();move(p,Math.cos(a)*18+.5,y+1,Math.sin(a)*18+.5,(float)(i*360.0/players.size()+90));}
            case FLIGHT -> {
                int progress=flight.progress(p.getUUID());
                if(progress<=0)move(p,-53.5,y+47,i-3.5,-90);
                else{var g=gates.get(Math.min(progress,3)-1);move(p,g.x()+2.5,g.y()+4,g.z()+.5,-90);}
            }
            case RADIO -> move(p,sign*16+.5,y+1,(i%4)*2+.5,side==Side.RED?-90:90);
        }
    }
    private void enter(ServerPlayer p){
        if(!safeLobby(p))throw new IllegalStateException("unsafe lobby");
        try{ArenaRecovery.remember(server,p,match);}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
        entered.add(p.getUUID());enterPrepared(p);
    }
    private void enterPrepared(ServerPlayer p){
        OriginService.reset(server,p);Lobby.resetPlayer(p);p.setGameMode(GameType.ADVENTURE);
        spawn(p);syncStage(p);
    }
    private void set(ServerPlayer p,int slot,Item item,int count){p.getInventory().setItem(slot,new ItemStack(item,count));}
    private static AdventureModePredicate blockPredicate(Block... blocks){
        return new AdventureModePredicate(List.of(net.minecraft.advancements.critereon.BlockPredicate.Builder.block().of(blocks).build()),true);
    }
    private void planks(ServerPlayer p,int slot,int count){
        if(count<=0){p.getInventory().setItem(slot,ItemStack.EMPTY);return;}
        var stack=new ItemStack(Items.OAK_PLANKS,count);
        stack.set(DataComponents.CAN_PLACE_ON,blockPredicate(Blocks.POLISHED_ANDESITE,Blocks.OAK_PLANKS));
        p.getInventory().setItem(slot,stack);
    }
    private void armor(ServerPlayer p){
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
    }
    private void combatKit(ServerPlayer p){
        armor(p);set(p,0,Items.IRON_SWORD,1);set(p,1,Items.BOW,1);set(p,2,Items.ARROW,16);set(p,3,Items.COOKED_BEEF,16);
        if(caravan2!=null){set(p,6,Items.SHIELD,1);p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);}
        else p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));
    }
    private void hint(ServerPlayer p){
        if(!active(p))return;
        if(clues!=null)clues.hint(p.getUUID()).ifPresent(c->{
            var item=new ItemStack(Items.COMPASS);
            item.set(DataComponents.CUSTOM_NAME,Component.literal(c.label()));
            item.set(DataComponents.LODESTONE_TRACKER,new LodestoneTracker(Optional.of(GlobalPos.of(arena.level.dimension(),BlockPos.containing(c.target().x(),c.target().y(),c.target().z()))),false));
            p.getInventory().setItem(0,item);
            Msg.tell(p,"Sıradaki hedef: "+c.label());
        });
        if(radio!=null)radio.hint(p.getUUID()).ifPresent(h->Msg.tell(p,"Tur "+h.round()+" · "+h.role()+" · Gizli parça: "+h.fragment()));
    }
    /** Analog ships its default radio switched off. Give every participant an actual working radio. */
    @SuppressWarnings("unchecked")
    private static ItemStack readyRadio(){
        var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("analog:radio"));
        var component=(net.minecraft.core.component.DataComponentType<Object>)net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.get(net.minecraft.resources.ResourceLocation.parse("minecraft:radio"));
        if(item==Items.AIR||component==null||component.codec()==null)throw new IllegalStateException("unsupported Analog radio");
        var tag=new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("enabled",true);tag.putBoolean("transmit",true);tag.putBoolean("receive",true);tag.putInt("channel",7);
        var stack=new ItemStack(item);stack.set(component,component.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,tag).getOrThrow());return stack;
    }
    private void syncStage(ServerPlayer p){
        if(!owns(p)||Objects.equals(revision.get(p.getUUID()),stage))return;
        revision.put(p.getUUID(),stage);p.setGameMode(GameType.ADVENTURE);
        p.getInventory().clearContent();p.containerMenu.setCarried(ItemStack.EMPTY);p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);p.getFoodData().setSaturation(20);
        switch(mode){
            case CARAVAN -> {
                if(caravan2!=null&&caravan2.waiting(p.getUUID())){p.setGameMode(GameType.SPECTATOR);spawn(p);}
                else{combatKit(p);spawn(p);if(caravan2!=null){caravan2.preparePlayer(p);caravan2.brief(p);}}
            }
            case CLUES -> hint(p);
            case RADIO -> {p.getInventory().setItem(0,readyRadio());hint(p);}
            case BUILD -> {
                spawn(p);
                if(siege!=null){if(siege.state().phase()==SiegeEconomyRules.Phase.BATTLE)issueSiegeKit(p);}
                else if(build.state().phase()==BuildAssaultRules.Phase.BUILD){
                    if(roster.side(p.getUUID())==build.defender()){
                        int supply=build.supply(p.getUUID());planks(p,0,Math.min(64,supply));planks(p,1,Math.max(0,supply-64));
                    }else p.setGameMode(GameType.SPECTATOR);
                }else{combatKit(p);if(roster.side(p.getUUID())!=build.defender()){var axe=new ItemStack(Items.IRON_AXE);axe.set(DataComponents.CAN_BREAK,blockPredicate(Blocks.OAK_PLANKS,Blocks.TARGET));p.getInventory().setItem(4,axe);}}
            }
            case AUCTION -> {set(p,0,Items.WOODEN_SWORD,1);set(p,3,Items.COOKED_BEEF,16);p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.LEATHER_CHESTPLATE));}
            case FLIGHT -> {var elytra=new ItemStack(Items.ELYTRA);elytra.set(DataComponents.UNBREAKABLE,new Unbreakable(true));p.setItemSlot(EquipmentSlot.CHEST,elytra);
                var rockets=flightRocket();p.getInventory().setItem(0,rockets);}
        }
    }
    private static ItemStack flightRocket(){
        var stack=new ItemStack(Items.FIREWORK_ROCKET);stack.set(DataComponents.FIREWORKS,new Fireworks(1,List.of()));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Sınırsız roket · 10 sn"));return stack;
    }
    private void invalidateFlight(UUID id){
        flight.invalidate(id);if(boosts!=null)boosts.invalidate(id);
        var rocket=flyingRockets.remove(id);if(rocket!=null)rocket.discard();
        touchingSpeed.remove(id);touchingLift.remove(id);
    }
    public boolean controlsFlightRocket(ServerPlayer p){return mode==Mode.FLIGHT&&arenaLevel(p.level());}
    /** Null means ordinary vanilla outside the flight arena; actual rocket entity, no item consumption. */
    public net.minecraft.world.InteractionResultHolder<ItemStack> useFlightRocket(ServerPlayer p,InteractionHand hand){
        thread();ItemStack stack=p.getItemInHand(hand);
        if(!controlsFlightRocket(p))return null;
        if(!eligible(p)||!p.isFallFlying()||!stack.is(Items.FIREWORK_ROCKET)||boosts==null)return net.minecraft.world.InteractionResultHolder.fail(stack);
        UUID id=p.getUUID();
        boolean success=boosts.rocket(id,match,()->{
            var rocket=new net.minecraft.world.entity.projectile.FireworkRocketEntity(arena.level,flightRocket(),p);
            if(!arena.level.addFreshEntity(rocket))return false;
            var old=flyingRockets.put(id,rocket);if(old!=null)old.discard();return true;
        });
        if(Boolean.getBoolean("kapisma.arenaProof"))tr.kapisma.Kapisma.LOG.info("FLIGHT_ROCKET {} {} remaining={} count={}",p.getScoreboardName(),success,boosts.remaining(id),stack.getCount());
        if(success)p.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(Items.FIREWORK_ROCKET));
        return success?net.minecraft.world.InteractionResultHolder.consume(stack):net.minecraft.world.InteractionResultHolder.fail(stack);
    }
    private void flightPads(ServerPlayer p){
        UUID id=p.getUUID();boolean speed=p.isFallFlying()&&Math.abs(p.getX()+42)<=2&&Math.abs(p.getY()-(y+42))<2.8&&Math.abs(p.getZ()-10)<2.8;
        boolean lift=p.onGround()&&p.getY()>=y+46.9&&p.getY()<=y+47.2&&p.getX()>=-57&&p.getX()<-54&&p.getZ()>=4&&p.getZ()<7;
        if(!speed)touchingSpeed.remove(id);else if(touchingSpeed.add(id))boosts.pad(id,match,"speed",()->{
            Vec3 before=p.getDeltaMovement(),v=before.add(p.getLookAngle().scale(.65));p.setDeltaMovement(v.length()>3?v.normalize().scale(3):v);p.hurtMarked=true;
            if(Boolean.getBoolean("kapisma.arenaProof"))tr.kapisma.Kapisma.LOG.info("FLIGHT_PAD {} speed before={} after={}",p.getScoreboardName(),before,p.getDeltaMovement());return true;
        });
        if(!lift)touchingLift.remove(id);else if(touchingLift.add(id))boosts.pad(id,match,"lift",()->{
            Vec3 v=p.getDeltaMovement();p.setDeltaMovement(v.x,1.05,v.z);p.hurtMarked=true;
            if(Boolean.getBoolean("kapisma.arenaProof"))tr.kapisma.Kapisma.LOG.info("FLIGHT_PAD {} lift before={} after={}",p.getScoreboardName(),v,p.getDeltaMovement());return true;
        });
    }
    private void enforce(ServerPlayer p){
        if(!owns(p))return;
        if(Math.abs(p.getX())>xRadius||Math.abs(p.getZ())>zRadius||p.getY()<y+.5||p.getY()>arena.level.getMaxBuildHeight()-1||
            (mode==Mode.FLIGHT&&p.onGround()&&p.getY()<y+2))spawn(p);
        if(mode==Mode.RADIO&&((roster.side(p.getUUID())==Side.RED&&p.getX()>-.5)||(roster.side(p.getUUID())==Side.BLUE&&p.getX()<1.5)||p.getY()>y+5.5))spawn(p);
        if(siege!=null&&(siege.state().phase()==SiegeEconomyRules.Phase.BUILD||siegeOpening)&&
            ((roster.side(p.getUUID())==Side.RED?p.getX()>-.5:p.getX()<1.5)||p.getY()>y+9))spawn(p);
        if(build!=null&&build.state().phase()==BuildAssaultRules.Phase.BUILD&&roster.side(p.getUUID())==build.defender()&&
            (Math.abs(p.getX())>10.5||Math.abs(p.getZ())>6.5||p.getY()>y+8))spawn(p);
    }
    private void announce(String msg){for(var p:onlinePlayers())if(owns(p))Msg.tell(p,msg);}
    private void bar(String msg){for(var p:onlinePlayers())if(owns(p))Msg.actionbar(p,Component.literal(msg));}
    private void end(String message){if(caravan2!=null)caravan2.close();if(boosts!=null){boosts.end();for(var rocket:flyingRockets.values())rocket.discard();flyingRockets.clear();}outcome=message;phase=Phase.ENDED;for(var p:onlinePlayers())if(owns(p)){p.setInvulnerable(true);Msg.title(p,message,title(),5,80,15);}}
    private String winner(Side side){return side==null?"Berabere":side==Side.RED?"Kırmızı kazandı":"Mavi kazandı";}
    private void newStage(){stage++;for(var p:onlinePlayers())if(owns(p))syncStage(p);}
    private void markCaravan(){
        var pos=caravan.position();var next=BlockPos.containing(pos.x(),y,pos.z());
        if(Objects.equals(next,caravanMarker))return;
        if(caravanMarker!=null)arena.level.setBlock(caravanMarker,Blocks.GRAY_CONCRETE.defaultBlockState(),2);
        caravanMarker=next;arena.level.setBlock(next,Blocks.GOLD_BLOCK.defaultBlockState(),2);
    }
    private List<ServerPlayer> caravanOnline(){return onlinePlayers().stream().filter(p->owns(p)&&!offline.contains(p.getUUID())).toList();}
    private List<Presence> caravanObservations(){return caravanOnline().stream().map(p->observation(p,false)).toList();}
    private boolean caravanRedResultShown,caravanBlueResultShown;
    private void caravanResults(CaravanMatchRules.State state){
        if(state.red()!=null&&!caravanRedResultShown){caravanRedResultShown=true;announce(CaravanMatchRules.resultLine(Side.RED,state.red()));announce("Roller değişiyor.");}
        if(state.blue()!=null&&!caravanBlueResultShown){
            caravanBlueResultShown=true;announce(CaravanMatchRules.resultLine(Side.BLUE,state.blue()));
            announce("Maç sonucu: "+winner(state.winner())+" · Ölçüt: "+CaravanMatchRules.reason(state.red(),state.blue()));
        }
    }
    private void tickCaravan2(){
        var connected=caravanOnline();var before=caravan2.state();
        var after=caravan2.tick(ticks,caravanObservations(),connected);
        boolean both=caravan2.bothConnected(connected);
        paused=!both||after.phase()!=CaravanMatchRules.Phase.LEG;
        caravanResults(after);
        if(after.phase()==CaravanMatchRules.Phase.ENDED){end(winner(after.winner()));return;}
        if(before.leg()!=after.leg()){newStage();caravan2.newLeg(connected);}
        for(var p:connected){
            if(caravan2.waiting(p.getUUID())){
                p.setCamera(p);
                if(!p.isSpectator())p.setGameMode(GameType.SPECTATOR);
                // Each slot has its own anchor. Outer slots lie outside the base-center
                // sphere even when standing exactly at their assigned spawn.
                var anchor=caravan2.spawnPoint(p.getUUID());
                if(pos(p).distanceSquared(anchor)>9)spawn(p);
            }else caravan2.ensureHorse(p);
        }
        for(var id:caravan2.readyRespawns()){
            var p=player(id);if(p==null||!owns(p)||offline.contains(id))continue;
            // Complete the world/kit operation before acknowledging the timer.
            p.setGameMode(GameType.ADVENTURE);p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);p.getFoodData().setSaturation(20);
            p.getInventory().clearContent();p.containerMenu.setCarried(ItemStack.EMPTY);combatKit(p);
            var b=caravan2.spawnPoint(id);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
            p.teleportTo(arena.level,b.x(),b.y(),b.z(),-90,0);
            caravan2.preparePlayer(p);
            if(!caravan2.acknowledge(id))throw new IllegalStateException("respawn acknowledgement lost");
            revision.put(id,stage);VoiceBridge.refresh(server);
        }
        if(ticks%20==0){
            long cargo=after.crates().stream().filter(c->c.status()==CaravanCargoRules.Status.ON_WAGON).count();
            long stolen=after.crates().stream().filter(c->c.status()==CaravanCargoRules.Status.STOLEN).count();
            for(var p:connected){
                String hud=!both?"Maç durakladı · Rakip takım bekleniyor":
                    after.phase()==CaravanMatchRules.Phase.BREAK?"Rol değişimi · "+((after.breakLeft()+19)/20)+" sn":
                    caravan2.waiting(p.getUUID())?"Yeniden doğma: "+((caravan2.respawnRemaining(p.getUUID())+19)/20)+" sn":
                    after.crates().stream().anyMatch(c->p.getUUID().equals(c.carrier()))?
                        "Sandık taşıyorsun · "+(roster.side(p.getUUID())==after.escort()?"Kervana dön":"Üssüne dön")+" · "+after.ticksLeft()/20+" sn":
                    p.getUUID().equals(after.route().driver())&&after.route().waitingAtJunction()?
                        "Yol ayrımı · A: Güvenli / D: Riskli · W: İlerle":
                    "Tur "+after.leg()+" · Yol "+Math.round(after.route().distance())+"/"+Math.round(after.route().length())+" · Yük "+cargo+" · Çalınan "+stolen+" · "+after.ticksLeft()/20+" sn";
                Msg.actionbar(p,Component.literal(hud));
            }
        }
    }
    private void tickRules(){
        if(caravan2!=null){tickCaravan2();return;}
        var obs=observations(build!=null&&build.state().phase()==BuildAssaultRules.Phase.BUILD||mode==Mode.CARAVAN&&caravan.state().phase()==CaravanRules.Phase.BREAK);
        paused=switch(mode){case AUCTION -> !allAliveOnline();case FLIGHT -> false;default -> !roster.bothSides(roster.active(obs));};
        switch(mode){
            case CARAVAN -> {
                var before=caravan.state();var after=caravan.tick(obs);
                if(after.phase()==CaravanRules.Phase.ENDED){end(winner(after.winner()));return;}
                if(before.leg()!=after.leg())newStage();
                if(after.phase()==CaravanRules.Phase.BREAK){paused=true;if(before.phase()!=after.phase())announce("Roller değişiyor.");}
                markCaravan();if(ticks%20==0)bar("Kervan "+after.distance()+"/48 · Yük "+after.cargo()+"/20 · "+after.ticksLeft()/20+" sn");
            }
            case CLUES -> {
                var before=clues.state();var after=clues.tick(obs);
                if(after.ended()){end(winner(after.winner()));return;}
                if(before.red()!=after.red()||before.blue()!=after.blue())
                    for(var p:onlinePlayers())if(active(p)&&((roster.side(p.getUUID())==Side.RED&&before.red()!=after.red())||(roster.side(p.getUUID())==Side.BLUE&&before.blue()!=after.blue())))hint(p);
                if(ticks%20==0)bar("Kırmızı "+after.red()+"/4 · Mavi "+after.blue()+"/4 · "+after.ticksLeft()/20+" sn");
            }
            case BUILD -> {
                if(siege!=null){tickSiege(obs);return;}
                if(resetBuilding){drainChanges();return;}
                var before=build.state();var after=build.tick(obs);buildTransition(before,after);
                if(ticks%20==0)bar("Tur "+after.leg()+" · "+after.phase()+" · "+after.ticksLeft()/20+" sn");
            }
            case AUCTION -> {
                var observations=new ArrayList<AuctionRules.Observation>();
                for(var p:onlinePlayers())if(owns(p)&&!offline.contains(p.getUUID()))observations.add(new AuctionRules.Observation(p.getUUID(),match,true,true));
                var before=auction.state();var after=auction.tick(observations);
                after.lastAward().ifPresent(a->{if(a.round()>shownAward){shownAward=a.round();deliver(a);}});
                if(before.phase()!=after.phase()&&after.phase()==AuctionRules.Phase.COMBAT)for(var p:onlinePlayers())if(owns(p))spawn(p);
                if(after.phase()==AuctionRules.Phase.ENDED){end(after.winner()==null?"Berabere":names.get(after.winner())+" kazandı");return;}
                if(ticks%20==0)bar(after.phase()+" · "+after.ticksLeft()/20+" sn");
            }
            case FLIGHT -> {
                var list=new ArrayList<FlightRaceRules.Sample>();var boostObs=new ArrayList<FlightBoostRules.Observation>();
                for(var p:onlinePlayers())if(active(p)){list.add(new FlightRaceRules.Sample(p.getUUID(),match,pos(p),true,p.isFallFlying(),false));boostObs.add(new FlightBoostRules.Observation(p.getUUID(),match,true,true,p.isFallFlying()));}
                boosts.tick(true,boostObs);
                var result=flight.tick(list);
                if(result.ended()){end(result.winners().isEmpty()?"Süre doldu":String.join(", ",result.winners().stream().map(names::get).toList())+" kazandı");return;}
                for(var p:onlinePlayers())if(active(p)){flightPads(p);if(ticks%10==0){int left=boosts.remaining(p.getUUID());Msg.actionbar(p,Component.literal("Geçit "+flight.progress(p.getUUID())+"/4 · "+(1800-result.elapsedTicks())/20+" sn · Roket "+(left==0?"Hazır":((left+19)/20)+" sn")));}}
            }
            case RADIO -> {
                var before=radio.state();var after=radio.tick(obs);
                if(after.phase()!=RadioPuzzleRules.Phase.ACTIVE){end(after.phase()==RadioPuzzleRules.Phase.WON?"Görev başarılı":"Görev başarısız");return;}
                if(before.round()!=after.round())for(var p:onlinePlayers())hint(p);
                if(ticks%20==0)bar("Tur "+after.round()+"/3 · Hak "+after.attempts()+" · "+after.ticksLeft()/20+" sn");
            }
        }
    }
    private boolean allAliveOnline(){for(var id:players)if(!eliminated.contains(id)){var p=player(id);if(p==null||!active(p))return false;}return true;}
    private void deliver(AuctionRules.Award award){
        UUID id=award.winner();if(id==null){announce("Teklif yok.");return;}
        var p=player(id);if(p==null||!active(p))throw new IllegalStateException("award receiver missing");
        switch(award.round()){
            case 1->{set(p,0,Items.IRON_SWORD,1);p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));}
            case 2->{set(p,1,Items.BOW,1);set(p,2,Items.ARROW,24);}
            case 3->armor(p);
            default->throw new IllegalStateException("bad award");
        }
        Msg.tell(p,"Kalan jetonun: "+auction.balance(id).orElseThrow());announce("İhaleyi kazanan: "+names.get(id));
    }
    private void buildTransition(BuildAssaultRules.State before,BuildAssaultRules.State after){
        if(after.phase()==BuildAssaultRules.Phase.ENDED){end(winner(after.winner()));return;}
        if(before.leg()!=after.leg()){
            resetBuilding=true;build.invalidateReadiness();
            for(int x=-10;x<=10;x++)for(int z=-6;z<=6;z++)for(int h=0;h<=6;h++)
                changes.add(new Work(new BlockPos(x,y+1+h,z),x==0&&z==0&&h==0?Blocks.TARGET:Blocks.AIR));
        }else if(before.phase()!=after.phase())newStage();
    }
    private void drainChanges(){
        long until=System.nanoTime()+3_000_000L;
        for(int n=0;n<192&&!changes.isEmpty()&&System.nanoTime()<until;n++){
            var w=changes.removeFirst();arena.level.setBlock(w.pos(),w.block().defaultBlockState(),2);
        }
        if(changes.isEmpty()){resetBuilding=false;newStage();}
    }
    @Override public void tick(){
        thread();if(closed)return;
        if(closing){if(server.getTickCount()%20==0)closeAttempt();return;}
        try{
            if(phase==Phase.PREP){prepareSlice();return;}
            if(phase==Phase.ENDED){if(++endTicks>=100)stop(outcome);return;}
            for(var p:onlinePlayers()){
                if(offline.contains(p.getUUID()))continue;
                if(!entered.contains(p.getUUID())){if(!safeLobby(p)){stop("Güvensiz lobi envanteri.");return;}enter(p);continue;}
                if(!owns(p)){stop("Beklenmeyen boyut.");return;}
                if(!eliminated.contains(p.getUUID()))enforce(p);
                if(phase==Phase.COUNTDOWN&&!p.isSpectator()){
                    var anchor=countdownAnchors.get(p.getUUID());if(anchor==null||pos(p).distanceSquared(anchor)>.04)spawn(p);
                }
            }
            if(phase==Phase.COUNTDOWN){
                if(onlinePlayers().stream().filter(p->owns(p)&&!offline.contains(p.getUUID())).count()==players.size()&&--countdown==0){
                    phase=Phase.RUNNING;paused=false;if(caravan2!=null)caravan2.newLeg(caravanOnline());VoiceBridge.refresh(server);
                }
                return;
            }
            ticks++;tickRules();
        }catch(RuntimeException ex){tr.kapisma.Kapisma.LOG.error("Arena tick failed",ex);stop("Arena hatası.");}
    }
    /** Read-only console QA status; no private clue, offer or balance is exposed. */
    public String proofState(){
        thread();
        String state=switch(mode){
            case CARAVAN->String.valueOf(caravan2!=null?caravan2.state():caravan==null?null:caravan.state());
            case CLUES->String.valueOf(clues==null?null:clues.state());
            case BUILD->siege==null?String.valueOf(build==null?null:build.state()):siege.state()+" credits="+players.stream().map(id->names.get(id)+":"+siege.credits(id).orElseThrow()).toList()+" opening="+siegeOpening;
            case AUCTION->String.valueOf(auction==null?null:auction.state());
            case RADIO->String.valueOf(radio==null?null:radio.state());
            case FLIGHT->flight==null?"null":flight.state()+" gates="+players.stream().map(id->names.get(id)+":"+flight.progress(id)).toList()+" cooldowns="+players.stream().map(id->names.get(id)+":"+boosts.remaining(id)).toList();
        };
        return id()+" "+phase+" "+state+" paused="+paused+" reset="+resetBuilding;
    }
    public boolean command(ServerPlayer p,String action,int value){
        thread();if(caravan2!=null&&action.equals("caravan_help"))return !closed&&phase==Phase.RUNNING&&owns(p)&&contains(p.getUUID())&&caravan2.help(p);
        if(!eligible(p)||paused)return false;
        if(caravan2!=null&&action.startsWith("caravan_"))return caravan2.command(p,action,caravanObservations());
        if(siege!=null&&action.equals("balance")){Msg.tell(p,"Takım kredisi: "+siege.credits(p.getUUID()).orElseThrow());return true;}
        if(action.equals("hint")&&(clues!=null||radio!=null)){hint(p);return true;}
        if(action.equals("find")&&clues!=null){var s=clues.state();return clues.request(p.getUUID(),match,roster.side(p.getUUID())==Side.RED?s.red():s.blue());}
        if(action.equals("code")&&radio!=null)return radio.submit(p.getUUID(),match,radio.state().round(),value);
        if(action.equals("bid")&&auction!=null)return auction.bid(p.getUUID(),match,auction.state().round(),value);
        if(action.equals("balance")&&auction!=null){Msg.tell(p,"Jeton: "+auction.balance(p.getUUID()).orElseThrow()+" · Teklif: "+auction.offer(p.getUUID()).orElseThrow());return true;}
        return false;
    }
    public boolean arenaLevel(Level level){return arena!=null&&level==arena.level;}
    /** No voluntary equipment transfer in these closed-economy arenas. */
    public boolean locksEquipment(ServerPlayer p){return !closed&&owns(p)&&(mode==Mode.AUCTION||mode==Mode.BUILD||mode==Mode.FLIGHT||caravan2!=null);}

    private boolean reachable(ServerPlayer p,BlockPos pos,net.minecraft.core.Direction face){
        Vec3 eye=p.getEyePosition(),end=eye.add(p.getViewVector(1).scale(5.5));
        var hit=arena.level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos)&&(face==null||hit.getDirection()==face);
    }
    /** All ordinary block use in this isolated arena is denied; only this transaction places planks. */
    public InteractionResult useBlock(ServerPlayer p,InteractionHand hand,BlockHitResult hit){
        thread();if(!arenaLevel(p.level()))return InteractionResult.PASS;
        // Vanilla first tries use-on-block when the crosshair is over a nearby solid face.
        // Route only the tagged hook to the same authoritative raycast; do not trust hit coordinates.
        if(caravan2!=null&&CaravanHookAdapter.isHook(p.getItemInHand(hand))){
            var result=caravanUseHook(p,hand);
            return result==null?InteractionResult.FAIL:result.getResult();
        }
        if(siege!=null)return siegePlace(p,hand,hit);
        if(build==null||!eligible(p)||paused||!p.getItemInHand(hand).is(Items.OAK_PLANKS)||!reachable(p,hit.getBlockPos(),hit.getDirection()))return InteractionResult.FAIL;
        BlockPos at=hit.getBlockPos().relative(hit.getDirection());
        if(!arena.level.getBlockState(at).isAir()||!arena.level.getBlockState(hit.getBlockPos()).isFaceSturdy(arena.level,hit.getBlockPos(),hit.getDirection())||
            !arena.level.getEntitiesOfClass(LivingEntity.class,new AABB(at)).isEmpty())return InteractionResult.FAIL;
        var cell=new BuildAssaultRules.Cell(at.getX(),at.getY()-y-1,at.getZ());
        if(!build.place(observation(p,false),cell,()->arena.level.setBlock(at,Blocks.OAK_PLANKS.defaultBlockState(),3)))return InteractionResult.FAIL;
        p.getItemInHand(hand).shrink(1);return InteractionResult.SUCCESS;
    }
    /** Actual server block-break event; manual transaction suppresses drops and duplicate vanilla destruction. */
    public InteractionResult breakBlock(ServerPlayer p,InteractionHand hand,BlockPos at){
        thread();if(!arenaLevel(p.level()))return InteractionResult.PASS;
        if(siege!=null)return siegeBreak(p,hand,at);
        if(build==null||!eligible(p)||paused||!p.getItemInHand(hand).is(Items.IRON_AXE)||!reachable(p,at,null))return InteractionResult.FAIL;
        var cell=new BuildAssaultRules.Cell(at.getX(),at.getY()-y-1,at.getZ());
        var before=build.state();
        boolean core=arena.level.getBlockState(at).is(Blocks.TARGET);
        boolean ok=core?build.capture(observation(p,false),cell,()->arena.level.setBlock(at,Blocks.AIR.defaultBlockState(),3))
            :arena.level.getBlockState(at).is(Blocks.OAK_PLANKS)&&build.breakWall(observation(p,false),cell,()->arena.level.setBlock(at,Blocks.AIR.defaultBlockState(),3));
        if(ok)buildTransition(before,build.state());
        return ok?InteractionResult.SUCCESS:InteractionResult.FAIL;
    }
    /** Experimental stage-one shop; no fluid, redstone or explosive world effects yet. */
    public boolean siegeShopReady(ServerPlayer p){return server.isSameThread()&&siege!=null&&tr.kapisma.Kapisma.GM.current==this&&eligible(p)&&!paused&&!siegeOpening&&siege.state().ready()&&siege.state().phase()!=SiegeEconomyRules.Phase.ENDED;}
    int siegeBalance(ServerPlayer p){return siege.credits(p.getUUID()).orElseThrow();}
    int siegeStock(ServerPlayer p,int id){var product=SiegeShop.product(id);return product==null?0:siege.stock(p.getUUID(),product.id());}
    int siegeSelectedKit(ServerPlayer p){return siege.kit(p.getUUID()).orElse(SiegeEconomyRules.Kit.FIGHTER).ordinal()+1;}
    public boolean siegeCommand(ServerPlayer p,String action,int product,int count){
        thread();if(!siegeShopReady(p))return false;
        var actor=observation(p,false);
        if(action.equals("shop")){
            if(p.containerMenu!=p.inventoryMenu||!p.inventoryMenu.getCarried().isEmpty())return false;
            return new SiegeShopGui(this,p).open();
        }
        if(action.equals("catalog")){
            Msg.tell(p,"Takım kredisi: "+siege.credits(p.getUUID()).orElseThrow());
            for(int i=0;i<SiegeShop.PRODUCTS.size();i++){var item=SiegeShop.PRODUCTS.get(i);Msg.tell(p,(i+1)+" · "+item.id()+" · "+item.price()+" kredi");}
            Msg.tell(p,"/arena al <ürün> <adet> · /arena sat <ürün> <adet> · /arena kit <1-5>");return true;
        }
        if(action.equals("kit")){
            if(product<1||product>5)return false;
            var kit=SiegeEconomyRules.Kit.values()[product-1];
            if(!siege.chooseKit(actor,kit))return false;
            Msg.tell(p,"Kit: "+kit);return true;
        }
        var item=SiegeShop.product(product);if(item==null)return false;
        boolean success=action.equals("buy")?siege.buy(actor,item.id(),count,()->SiegeShop.transact(p,item,count,true)):
            action.equals("sell")&&siege.sell(actor,item.id(),count,()->SiegeShop.transact(p,item,count,false));
        if(success)Msg.tell(p,"Takım kredisi: "+siege.credits(p.getUUID()).orElseThrow());
        return success;
    }
    private void issueSiegeKit(ServerPlayer p){
        UUID id=p.getUUID();if(siegeKitIssued.contains(id))return;
        var kit=siege.kit(id).orElse(SiegeEconomyRules.Kit.FIGHTER);
        var tool=new ItemStack(Items.IRON_PICKAXE);tool.set(DataComponents.UNBREAKABLE,new Unbreakable(true));
        var blocks=new ArrayList<Block>();for(var product:SiegeShop.PRODUCTS)blocks.add(product.block());blocks.add(Blocks.TARGET);
        tool.set(DataComponents.CAN_BREAK,blockPredicate(blocks.toArray(Block[]::new)));
        var gear=new ArrayList<ItemStack>();gear.add(tool);
        switch(kit){
            case SIEGE -> {gear.add(new ItemStack(Items.IRON_AXE));gear.add(new ItemStack(Items.SHIELD));}
            case FIGHTER -> {gear.add(new ItemStack(Items.IRON_SWORD));gear.add(new ItemStack(Items.SHIELD));}
            case ARCHER -> {gear.add(new ItemStack(Items.BOW));gear.add(new ItemStack(Items.ARROW,32));}
            case INFILTRATOR -> {gear.add(new ItemStack(Items.STONE_SWORD));gear.add(new ItemStack(Items.CROSSBOW));gear.add(new ItemStack(Items.ARROW,12));}
            case SUPPORT -> {gear.add(new ItemStack(Items.STONE_SWORD));gear.add(new ItemStack(Items.COOKED_BEEF,16));gear.add(new ItemStack(Items.SHIELD));}
        }
        if(eu.pb4.sgui.api.GuiHelpers.getCurrentGui(p) instanceof SiegeShopGui gui)gui.close();
        if(!SiegeShop.prepareKitSlots(p,gear.size()))throw new IllegalStateException("siege inventory cannot preserve stock");
        var free=new ArrayList<Integer>();for(int i=0;i<36;i++)if(p.getInventory().getItem(i).isEmpty())free.add(i);
        if(free.size()<gear.size())throw new IllegalStateException("siege kit slots");
        for(int i=0;i<gear.size();i++)p.getInventory().setItem(free.get(i),gear.get(i));
        p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(kit==SiegeEconomyRules.Kit.FIGHTER?Items.IRON_CHESTPLATE:Items.LEATHER_CHESTPLATE));
        p.getInventory().setChanged();p.containerMenu.broadcastChanges();siegeKitIssued.add(id);
    }
    private void tickSiege(List<Presence> obs){
        if(siegeOpening){
            long until=System.nanoTime()+3_000_000L;
            for(int n=0;n<192&&!changes.isEmpty()&&System.nanoTime()<until;n++){
                var w=changes.removeFirst();arena.level.setBlock(w.pos(),w.block().defaultBlockState(),2);
            }
            if(changes.isEmpty()){siegeOpening=false;for(var p:onlinePlayers())if(active(p))issueSiegeKit(p);announce("Karşılıklı kuşatma başladı.");}
            return;
        }
        var before=siege.state();var after=siege.tick(obs);
        if(after.phase()==SiegeEconomyRules.Phase.ENDED){end(winner(after.winner()));return;}
        if(before.phase()!=after.phase()){
            siegeOpening=true;for(int z=-zRadius;z<=zRadius;z++)for(int h=1;h<=10;h++)changes.add(new Work(new BlockPos(0,y+h,z),Blocks.AIR));
        }
        if(ticks%20==0)for(var p:onlinePlayers())if(active(p))Msg.actionbar(p,Component.literal("Kırmızı "+after.redPoints()+"/500 · Mavi "+after.bluePoints()+"/500 · Kredi "+siege.credits(p.getUUID()).orElseThrow()+(after.phase()==SiegeEconomyRules.Phase.BUILD?" · İnşa "+after.buildTicks()/20+" sn":"")));
    }
    private InteractionResult siegePlace(ServerPlayer p,InteractionHand hand,BlockHitResult hit){
        if(!eligible(p)||paused||siegeOpening||!reachable(p,hit.getBlockPos(),hit.getDirection()))return InteractionResult.FAIL;
        var stack=p.getItemInHand(hand);var item=SiegeShop.fromStack(stack);if(item==null)return InteractionResult.FAIL;
        BlockPos at=hit.getBlockPos().relative(hit.getDirection());
        if(!arena.level.getBlockState(at).isAir()||!arena.level.getBlockState(hit.getBlockPos()).isFaceSturdy(arena.level,hit.getBlockPos(),hit.getDirection())||
            !arena.level.getEntitiesOfClass(LivingEntity.class,new AABB(at)).isEmpty())return InteractionResult.FAIL;
        var cell=new SiegeEconomyRules.Cell(at.getX(),at.getY()-y-1,at.getZ());
        if(!siege.place(observation(p,false),item.id(),cell,()->arena.level.setBlock(at,item.block().defaultBlockState(),3)))return InteractionResult.FAIL;
        stack.shrink(1);return InteractionResult.SUCCESS;
    }
    private InteractionResult siegeBreak(ServerPlayer p,InteractionHand hand,BlockPos at){
        if(!eligible(p)||paused||siegeOpening||siege.state().phase()!=SiegeEconomyRules.Phase.BATTLE||!p.getItemInHand(hand).is(Items.IRON_PICKAXE)||!reachable(p,at,null))return InteractionResult.FAIL;
        if(at.getY()==y+1&&at.getZ()==0&&Math.abs(at.getX())==24&&arena.level.getBlockState(at).is(Blocks.TARGET)){
            Side victim=at.getX()<0?Side.RED:Side.BLUE;
            // Provisional core-hit policy, isolated opt-in only; team-shared 20-tick interval.
            if(ticks-siegeCoreLastHit.getOrDefault(victim,-1000)<20||!siege.queueDamage(observation(p,false),victim,25))return InteractionResult.FAIL;
            siegeCoreLastHit.put(victim,ticks);return InteractionResult.SUCCESS;
        }
        var cell=new SiegeEconomyRules.Cell(at.getX(),at.getY()-y-1,at.getZ());
        return siege.destroy(observation(p,false),cell,()->arena.level.setBlock(at,Blocks.AIR.defaultBlockState(),3))?InteractionResult.SUCCESS:InteractionResult.FAIL;
    }
    private boolean combat(){return switch(mode){case CARAVAN->caravan2!=null?caravan2.state().phase()==CaravanMatchRules.Phase.LEG:caravan.state().phase()==CaravanRules.Phase.LEG;
        case BUILD->siege!=null?!siegeOpening&&siege.state().phase()==SiegeEconomyRules.Phase.BATTLE:build.state().phase()==BuildAssaultRules.Phase.ASSAULT;case AUCTION->auction.state().phase()==AuctionRules.Phase.COMBAT;default->false;};}
    @Override public boolean allowDamage(LivingEntity e,DamageSource src,float amount){
        if(caravan2!=null&&arenaLevel(e.level())&&caravan2.protects(e))return false;
        if(!(e instanceof ServerPlayer p))return true;
        boolean target=arenaLevel(p.level());
        if(contains(p.getUUID())&&(!eligible(p)||paused))return false;
        if(src.getEntity() instanceof ServerPlayer a&&(target||arenaLevel(a.level())))
            return target&&eligible(p)&&eligible(a)&&combat()&&!paused&&!a.getUUID().equals(p.getUUID())&&
                (mode==Mode.AUCTION||roster.side(a.getUUID())!=roster.side(p.getUUID()));
        return !target||eligible(p)&&!paused&&combat();
    }
    @Override public boolean allowDeath(ServerPlayer p,DamageSource src){
        if(caravan2!=null&&phase==Phase.RUNNING&&owns(p)&&!offline.contains(p.getUUID())){
            if(caravan2.death(p)){p.setHealth(p.getMaxHealth());p.stopRiding();p.getInventory().clearContent();p.containerMenu.setCarried(ItemStack.EMPTY);p.setGameMode(GameType.SPECTATOR);spawn(p);VoiceBridge.refresh(server);}
            return false;
        }
        if(siege!=null&&phase==Phase.RUNNING&&owns(p)&&!offline.contains(p.getUUID())){p.setHealth(p.getMaxHealth());spawn(p);return false;}
        if(mode!=Mode.AUCTION||phase!=Phase.RUNNING||closing||!owns(p)||offline.contains(p.getUUID())||auction.state().phase()!=AuctionRules.Phase.COMBAT)return true;
        // ALLOW_DEATH runs after lethal damage: isAlive() is already false here.
        if(eliminated.contains(p.getUUID())){p.setHealth(p.getMaxHealth());return false;}
        if(!auction.death(p.getUUID(),match))return false;
        eliminated.add(p.getUUID());p.setHealth(p.getMaxHealth());p.setGameMode(GameType.SPECTATOR);p.getInventory().clearContent();p.containerMenu.setCarried(ItemStack.EMPTY);VoiceBridge.refresh(server);return false;
    }
    @Override public void onRespawn(ServerPlayer p){if(owns(p)&&!eliminated.contains(p.getUUID())){spawn(p);if(mode==Mode.CARAVAN||mode==Mode.BUILD&&siege==null){revision.remove(p.getUUID());syncStage(p);}}}
    public void onServerTeleport(UUID player){if(flight!=null&&contains(player))invalidateFlight(player);
        if(caravan2!=null&&phase==Phase.RUNNING){var p=player(player);if(p!=null&&owns(p)&&!caravan2.waiting(player))caravan2.externalTeleport(p);}}
    public InteractionResult caravanUseEntity(ServerPlayer p,net.minecraft.world.entity.Entity target){
        thread();if(caravan2==null||!arenaLevel(p.level()))return InteractionResult.PASS;
        try{return caravan2.useEntity(p,target,caravanObservations(),eligible(p)&&!paused);}
        catch(RuntimeException ex){tr.kapisma.Kapisma.LOG.error("Caravan interaction failed",ex);stop("Kervan etkileşim hatası.");return InteractionResult.FAIL;}
    }
    public net.minecraft.world.InteractionResultHolder<ItemStack> caravanUseHook(ServerPlayer p,InteractionHand hand){
        thread();var stack=p.getItemInHand(hand);
        if(caravan2==null||!arenaLevel(p.level())||!CaravanHookAdapter.isHook(stack))return null;
        boolean ok=eligible(p)&&!paused&&caravan2.useHook(p,ticks,caravanObservations(),true);
        return ok?net.minecraft.world.InteractionResultHolder.consume(stack):net.minecraft.world.InteractionResultHolder.fail(stack);
    }
    public void caravanInput(ServerPlayer p,float forward,float sideways,boolean shift){if(caravan2!=null&&owns(p)&&phase==Phase.RUNNING)caravan2.input(p,forward,sideways,shift,()->caravanObservations(),eligible(p)&&!paused);}
    public boolean caravanProtects(net.minecraft.world.entity.Entity target){return caravan2!=null&&arenaLevel(target.level())&&caravan2.protects(target);}
    String caravanQaHook(UUID id){if(!Boolean.getBoolean("kapisma.arenaProof")||caravan2==null)throw new IllegalStateException("QA only");return " hookRemaining="+caravan2.hookRemaining(id)+" hookAttached="+caravan2.hookAttached(id)+caravan2.qaDrive()+caravan2.qaMap(id)+" horseHome="+caravan2.qaHorseHome(id);}
    CaravanMatchRules.State caravanQaState(){
        if(!Boolean.getBoolean("kapisma.arenaProof")||caravan2==null)throw new IllegalStateException("QA only");
        return caravan2.state();
    }
    @Override public void onDisconnect(ServerPlayer p){
        if(!contains(p.getUUID()))return;offline.add(p.getUUID());if(caravan2!=null&&owns(p))caravan2.disconnect(p);if(flight!=null)invalidateFlight(p.getUUID());if(build!=null)build.invalidateReadiness();if(siege!=null)siege.invalidateReadiness();
        if(clues!=null)clues.discardPending();if(radio!=null)radio.discardPending();if(auction!=null)auction.discardPending();
        if(phase==Phase.RUNNING&&mode!=Mode.FLIGHT)paused=true;
    }
    @Override public void onJoin(ServerPlayer p){
        if(!contains(p.getUUID())||closed)return;offline.remove(p.getUUID());
        if(phase==Phase.PREP||closing)return;
        if(phase==Phase.ENDED){if(owns(p))p.setInvulnerable(true);return;}
        if(!entered.contains(p.getUUID())){if(!safeLobby(p)){stop("Güvensiz lobi envanteri.");return;}enter(p);return;}
        if(!owns(p)){stop("Arena dışı yeniden bağlantı.");return;}
        if(eliminated.contains(p.getUUID())||caravan2!=null&&caravan2.waiting(p.getUUID()))p.setGameMode(GameType.SPECTATOR);
        else{boolean sameStage=Objects.equals(revision.get(p.getUUID()),stage);syncStage(p);if(caravan2!=null&&sameStage)caravan2.brief(p);if(siege!=null&&siege.state().phase()==SiegeEconomyRules.Phase.BATTLE&&!siegeOpening)issueSiegeKit(p);if(clues!=null||radio!=null)hint(p);if(flight!=null)invalidateFlight(p.getUUID());}
    }
    @Override public boolean blocksPortals(Level level){return arenaLevel(level);}
    @Override public boolean cheatGuard(Level level){return phase==Phase.RUNNING&&arenaLevel(level);}
    @Override public void stop(String reason){thread();if(closed)return;if(boosts!=null){boosts.end();for(var rocket:flyingRockets.values())rocket.discard();flyingRockets.clear();}closing=true;phase=Phase.ENDED;closeAttempt();}
    private void closeAttempt(){
        try{
            if(caravan2!=null)caravan2.close();
            for(var p:onlinePlayers())if(entered.contains(p.getUUID())&&!offline.contains(p.getUUID())){
                if(eu.pb4.sgui.api.GuiHelpers.getCurrentGui(p) instanceof SiegeShopGui gui)gui.close();
                if(!ArenaRecovery.returnPlayer(server,p,match))return;
                entered.remove(p.getUUID());
            }
            if(arena!=null&&!arena.level.players().isEmpty())return;
            if(!teams.isEmpty())TeamService.clear(server);
            if(arena!=null){arena.delete();arena=null;}
            changes.clear();pinned.clear();closed=true;VoiceBridge.refresh(server);
        }catch(java.io.IOException|RuntimeException ex){if(server.getTickCount()%100==0)tr.kapisma.Kapisma.LOG.error("Arena cleanup deferred",ex);}
    }
}