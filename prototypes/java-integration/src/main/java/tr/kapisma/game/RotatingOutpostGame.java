package tr.kapisma.game;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import tr.kapisma.Msg;
import tr.kapisma.arena.MatchRoster;
import tr.kapisma.arena.MatchRoster.*;
import tr.kapisma.arena.OutpostRules;
import tr.kapisma.lobby.Lobby;
import tr.kapisma.origin.OriginService;
import tr.kapisma.team.TeamService;
import tr.kapisma.voice.VoiceBridge;

/** Staged native adapter. Do not register until the integration acceptance gates pass. */
public final class RotatingOutpostGame extends Game {
    public static final String ID="DONEN_KARAKOL";
    private static final int X=36,Z=12,HEIGHT=5,MAX_PREP_TICKS=2400;
    private final UUID match=UUID.randomUUID();
    private final List<BlockPos> pinned=new ArrayList<>();
    private final Set<UUID> entered=new HashSet<>(),offline=new HashSet<>();
    private GameWorld arena;
    private MatchRoster roster;
    private OutpostRules rules;
    private int floorY,cursor,countdown=100,prepTicks,endTicks,warningSeconds;
    private boolean closed,paused,closing;
    private static final int[] POINTS={-24,0,24};

    public RotatingOutpostGame(GameManager mgr){super(mgr);}
    @Override public String id(){return ID;}
    @Override public String title(){return "Dönen Karakol";}
    @Override public boolean isClosed(){return closed;}
    public boolean cleanupPending(){return closing&&!closed;}
    public String proofState(){requireThread();return ID+" "+phase+" "+(rules==null?"null":rules.snapshot())+" paused="+paused;}
    private void requireThread(){if(!server.isSameThread())throw new IllegalStateException("server thread required");}
    private boolean owns(ServerPlayer p){return arena!=null && p.level()==arena.level && contains(p.getUUID());}
    /** Existing lobby reset clears inventory and ender chest; refuse arbitrary player-owned items. */
    private boolean safeLobby(ServerPlayer p){
        if(!Lobby.inLobby(p)||p.gameMode.getGameModeForPlayer()!=GameType.ADVENTURE||
            !p.containerMenu.getCarried().isEmpty()||p.containerMenu!=p.inventoryMenu)return false;
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            ItemStack it=p.getInventory().getItem(i);
            if(!it.isEmpty()&&(i!=4||!ItemStack.matches(it,Lobby.menuItem())))return false;
        }
        for(int i=0;i<p.getEnderChestInventory().getContainerSize();i++)
            if(!p.getEnderChestInventory().getItem(i).isEmpty())return false;
        return p.totalExperience==0&&p.experienceLevel==0&&p.experienceProgress==0;
    }
    @Override public String prepare(List<ServerPlayer> participants){
        requireThread();
        if(closed||arena!=null||participants.size()<2||participants.size()>8)return "2–8 oyuncu gerekli.";
        var ids=new HashSet<UUID>();
        for(var p:participants)if(!ids.add(p.getUUID())||!safeLobby(p))
            return "Oyuncular temiz lobi envanterinde ve maceracı modunda olmalı.";
        int rotation=(int)st.num(ID,"rotationSeconds"),goal=(int)st.num(ID,"scoreGoal");
        warningSeconds=5;
        if(rotation<10||rotation>120||goal<5||goal>200)return "Karakol ayarları geçersiz.";
        try {
            // A separate Fantasy dimension, never the overworld or lobby.
            arena=GameWorld.open(server,rnd.nextLong(),0,0,new GameWorld.Options(true,false,false,true));
            floorY=arena.level.getMaxBuildHeight()-16;
            arena.setBorder(40);
            for(int cx=-3;cx<=2;cx++)for(int cz=-1;cz<=0;cz++){
                BlockPos pos=new BlockPos(cx*16,floorY,cz*16);
                arena.force(pos.getX(),pos.getZ(),true);pinned.add(pos);
            }
            var shuffled=new ArrayList<>(participants);Collections.shuffle(shuffled,rnd);
            var red=new ArrayList<ServerPlayer>();var blue=new ArrayList<ServerPlayer>();
            for(int i=0;i<shuffled.size();i++)(i%2==0?red:blue).add(shuffled.get(i));
            teams=tr.kapisma.team.ArenaTeamFactory.balanced(server,red,blue);
            // Existing scoreboard namespace, fixed side index.
            for(var p:participants){players.add(p.getUUID());names.put(p.getUUID(),p.getGameProfile().getName());}
            roster=new MatchRoster(match,List.copyOf(teams.get(0).members),List.copyOf(teams.get(1).members));
            rules=new OutpostRules(roster,Arrays.stream(POINTS).mapToObj(x->new Position(x+.5,floorY+1,.5)).toList(),rotation,warningSeconds,goal);
            phase=Phase.PREP;
            return null;
        }catch(RuntimeException ex){stop("Arena hazırlığı başarısız.");return "Arena hazırlığı başarısız: "+ex.getClass().getSimpleName();}
    }
    private void buildSlice(){
        if(++prepTicks>MAX_PREP_TICKS){stop("Arena hazırlama zaman aşımı.");return;}
        for(var p:pinned)if(!arena.loaded(p.getX(),p.getZ()))return;
        int width=2*X+1,depth=2*Z+1,total=width*depth*(HEIGHT+1);
        long until=System.nanoTime()+3_000_000L;
        for(int n=0;n<192&&cursor<total&&System.nanoTime()<until;n++,cursor++){
            int x=cursor%width-X,z=(cursor/width)%depth-Z,dy=cursor/(width*depth);
            var block=Blocks.AIR;
            if(dy==0){
                block=Blocks.SMOOTH_STONE;
                for(int i=0;i<POINTS.length;i++)
                    if(Math.abs(x-POINTS[i])<=4&&Math.abs(z)<=4)block=i==0?Blocks.GOLD_BLOCK:Blocks.GRAY_CONCRETE;
                if(Math.abs(x)<=2&&z<=-10)block=Blocks.RED_CONCRETE;
                if(Math.abs(x)<=2&&z>=10)block=Blocks.BLUE_CONCRETE;
            }else if(dy<=2&&Math.abs(Math.abs(x)-16)<=1&&Math.abs(z)==7)block=Blocks.STONE_BRICKS;
            arena.level.setBlock(new BlockPos(x,floorY+dy,z),block.defaultBlockState(),2);
        }
        if(cursor!=total)return;
        // Validate all online players before destructive lobby reset.
        for(var p:onlinePlayers())if(!safeLobby(p)){stop("Lobi envanteri değişti; maç iptal.");return;}
        var batch=onlinePlayers();
        try{ArenaRecovery.rememberAll(server,batch,match);}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
        // All returns are durable before any player inventory or dimension is changed.
        for(var p:batch)entered.add(p.getUUID());
        for(var p:batch)enterPrepared(p);
        phase=Phase.COUNTDOWN;
    }
    private void spawn(ServerPlayer p){
        var side=roster.side(p.getUUID());
        double z=side==Side.RED?-10.5:11.5;
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        p.teleportTo(arena.level,.5,floorY+1,z,side==Side.RED?0:180,0);
        p.setRespawnPosition(arena.level.dimension(),new BlockPos(0,floorY+1,(int)Math.floor(z)),0,true,false);
    }
    private void kit(ServerPlayer p){
        p.getInventory().clearContent();
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
        p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));
        p.getInventory().setItem(0,new ItemStack(Items.IRON_SWORD));
        p.getInventory().setItem(1,new ItemStack(Items.BOW));
        p.getInventory().setItem(2,new ItemStack(Items.COOKED_BEEF,16));
        p.getInventory().setItem(3,new ItemStack(Items.ARROW,16));
        p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);p.getFoodData().setSaturation(20);
    }
    private void enter(ServerPlayer p){
        if(!safeLobby(p))return;
        try{ArenaRecovery.remember(server,p,match);}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
        entered.add(p.getUUID());enterPrepared(p);
    }
    private void enterPrepared(ServerPlayer p){
        OriginService.reset(server,p);
        Lobby.resetPlayer(p);p.setGameMode(GameType.ADVENTURE);spawn(p);kit(p);
    }
    private List<Presence> presence(){
        var list=new ArrayList<Presence>();
        for(var p:onlinePlayers()){
            if(!owns(p)||!entered.contains(p.getUUID())||offline.contains(p.getUUID()))continue;
            list.add(new Presence(p.getUUID(),match,true,!p.isSpectator(),new Position(p.getX(),p.getY(),p.getZ())));
        }
        return list;
    }
    private void enforceBounds(ServerPlayer p){
        if(!owns(p))return;
        if(Math.abs(p.getX()-.5)>X||Math.abs(p.getZ()-.5)>Z||
           p.getY()<floorY+0.5||p.getY()>floorY+HEIGHT+1)spawn(p);
    }
    private void color(int index,boolean active){
        for(int x=POINTS[index]-4;x<=POINTS[index]+4;x++)for(int z=-4;z<=4;z++)
            arena.level.setBlock(new BlockPos(x,floorY,z),(active?Blocks.GOLD_BLOCK:Blocks.GRAY_CONCRETE).defaultBlockState(),2);
    }
    @Override public void tick(){
        requireThread();if(closed)return;
        if(closing){if(server.getTickCount()%20==0)closeAttempt();return;}
        try{
            if(phase==Phase.PREP){buildSlice();return;}
            if(phase==Phase.ENDED){if(++endTicks>=100)stop("Maç tamamlandı.");return;}
            for(var p:onlinePlayers()){
                if(offline.contains(p.getUUID()))continue;
                if(!entered.contains(p.getUUID())){if(!safeLobby(p)){stop("Oyuncu lobi koşullarını karşılamıyor.");return;}enter(p);continue;}
                if(!owns(p)){stop("Kayıtlı oyuncu beklenmeyen boyuta geçti.");return;}
                enforceBounds(p);
                if(phase==Phase.COUNTDOWN && (Math.abs(p.getX()-.5)>.15 || Math.abs(p.getZ()-(roster.side(p.getUUID())==Side.RED?-10.5:11.5))>.15 || Math.abs(p.getY()-floorY-1)>.15))spawn(p);
            }
            if(phase==Phase.COUNTDOWN){
                if(roster.bothSides(roster.active(presence()))&&--countdown<=0){phase=Phase.RUNNING;VoiceBridge.refresh(server);}
                return;
            }
            int old=rules.snapshot().point();
            var update=rules.tick(presence());
            if(update.paused()!=paused){paused=update.paused();for(var p:onlinePlayers())Msg.tell(p,paused?"Takım bekleniyor.":"Maç devam ediyor.");}
            if(update.warning())for(var p:onlinePlayers())Msg.tell(p,"Karakol "+warningSeconds+" saniye sonra değişiyor.");
            if(update.rotated()){color(old,false);color(update.state().point(),true);}
            if(++ticks%20==0)for(var p:onlinePlayers())Msg.actionbar(p,Component.literal(
                "Kırmızı "+update.state().red()+" | Mavi "+update.state().blue()+" | "+
                ((update.state().leftTicks()+19)/20)+" sn"+(paused?" | Bekliyor":update.contested()?" | Çekişmeli":"")));
            if(update.state().winner()!=null){
                phase=Phase.ENDED;
                for(var p:onlinePlayers())if(owns(p)){
                    p.setInvulnerable(true);
                    Msg.title(p,update.state().winner()==Side.RED?"Kırmızı kazandı":"Mavi kazandı","Dönen Karakol",5,80,15);
                }
            }
        }catch(RuntimeException ex){tr.kapisma.Kapisma.LOG.error("Outpost adapter failed",ex);stop("Arena hatası.");}
    }
    @Override public boolean allowDamage(LivingEntity e,DamageSource src,float amount){
        if(!(e instanceof ServerPlayer target))return true;
        boolean targetArena=arena!=null&&target.level()==arena.level;
        if(contains(target.getUUID())&&(!targetArena||phase!=Phase.RUNNING||paused||closing))return false;
        if(src.getEntity() instanceof ServerPlayer attacker){
            boolean attackArena=arena!=null&&attacker.level()==arena.level;
            if(attackArena||targetArena)return roster!=null&&owns(attacker)&&owns(target)&&
                !attacker.isSpectator()&&!target.isSpectator()&&roster.canDamage(attacker.getUUID(),target.getUUID(),true,phase==Phase.RUNNING&&!paused);
        }
        if(!targetArena)return !contains(target.getUUID());
        return owns(target)&&phase==Phase.RUNNING&&!paused&&!target.isSpectator();
    }
    @Override public void onJoin(ServerPlayer p){
        if(!contains(p.getUUID())||closed)return;
        offline.remove(p.getUUID());
        if(phase==Phase.PREP||closing)return;
        if(phase==Phase.ENDED){if(owns(p))p.setInvulnerable(true);return;}
        if(!entered.contains(p.getUUID())){if(!safeLobby(p)){stop("Oyuncu lobi koşullarını karşılamıyor.");return;}enter(p);return;}
        if(!owns(p)){stop("Yeniden bağlanan oyuncu arena dışında.");return;}
        enforceBounds(p);
    }
    @Override public void onDisconnect(ServerPlayer p){
        offline.add(p.getUUID());
        if(roster!=null&&phase==Phase.RUNNING&&!roster.bothSides(roster.active(presence().stream().filter(v->!v.player().equals(p.getUUID())).toList())))paused=true;
    }
    @Override public void onRespawn(ServerPlayer p){
        if(closed||phase!=Phase.RUNNING||!contains(p.getUUID())||!entered.contains(p.getUUID()))return;
        p.setGameMode(GameType.ADVENTURE);spawn(p);kit(p);
    }
    @Override public boolean blocksPortals(Level level){return arena!=null&&level==arena.level;}
    @Override public boolean cheatGuard(Level level){return phase==Phase.RUNNING&&blocksPortals(level);}
    @Override public void stop(String reason){
        requireThread();if(closed)return;
        phase=Phase.ENDED;closing=true;
        closeAttempt();
    }
    private void closeAttempt(){
        // Do not destroy a temporary world while any online player remains inside.
        // If cleanup fails, retain the game object and retry on a later tick.
        try{
            for(var p:onlinePlayers())if(entered.contains(p.getUUID())&&!offline.contains(p.getUUID())){
                if(!ArenaRecovery.returnPlayer(server,p,match))return;
                entered.remove(p.getUUID());
            }
            if(arena!=null&&!arena.level.players().isEmpty()){
                if(server.getTickCount()%100==0)tr.kapisma.Kapisma.LOG.warn("Outpost arena retained: player still present");
                return;
            }
            if(!teams.isEmpty())TeamService.clear(server);
            if(arena!=null){arena.delete();arena=null;}
            pinned.clear();closed=true;
            VoiceBridge.refresh(server);
        }catch(java.io.IOException|RuntimeException ex){
            if(server.getTickCount()%100==0)tr.kapisma.Kapisma.LOG.error("Outpost cleanup deferred",ex);
        }
    }
}