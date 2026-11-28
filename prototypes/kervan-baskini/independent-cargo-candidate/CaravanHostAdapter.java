package tr.kapisma.game;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import tr.kapisma.arena.*;
import tr.kapisma.arena.MatchRoster.*;
import tr.kapisma.arena.CaravanMatchRules.Action;

/** Host-side composition of candidate systems. Host owns world, roster, teleport and kit lifecycle. */
public final class CaravanHostAdapter implements AutoCloseable {
    private final ServerLevel level;
    private final MatchRoster roster;
    private final CaravanMatchRules rules;
    private final CaravanSceneAdapter scene;
    private final CaravanMountAdapter mounts;
    private final CaravanTerrainSampler terrain;
    private final CaravanHookAdapter hook;
    private final CaravanMapRaster raster;
    private final CaravanForkInput forkInput=new CaravanForkInput();
    private final Map<UUID,CaravanMapAdapter> maps=new HashMap<>();
    private final Map<UUID,Integer> lastHelp=new HashMap<>();
    private final Position escortBase,attackBase;
    private final boolean manualClimb;
    private boolean closed;
    public CaravanHostAdapter(ServerLevel level,MatchRoster roster,int y,int xRadius,int zRadius,boolean manualClimb){
        this.level=Objects.requireNonNull(level);this.roster=Objects.requireNonNull(roster);this.manualClimb=manualClimb;thread();
        double h=y+1;
        var safe=CaravanTerrainLayout.safe(h);var risky=CaravanTerrainLayout.risky(h);
        escortBase=CaravanTerrainLayout.escortBase(h);attackBase=CaravanTerrainLayout.attackBase(h);
        rules=new CaravanMatchRules(roster,safe,risky,attackBase,20,6000,100,.075,3.5);
        // Include generated summits plus crate/horse headroom and bounded air landing margin.
        var safety=new CaravanWorldSafety(level,-xRadius,xRadius+1,y-1,Math.min(level.getMaxBuildHeight()-1,y+CaravanTerrainLayout.HEIGHT+5),-zRadius,zRadius+1);
        scene=new CaravanSceneAdapter(level,rules,safety);mounts=new CaravanMountAdapter(level,safety,y+.5,level.getMaxBuildHeight()-3,xRadius,zRadius);
        raster=new CaravanMapRaster(safe,risky,List.of(new Position(-xRadius,h,-zRadius),new Position(xRadius+1,h,zRadius+1)),y+1,escortBase,attackBase);terrain=new CaravanTerrainSampler(level,raster,y,xRadius,zRadius);hook=new CaravanHookAdapter(level,roster,y,xRadius,zRadius,manualClimb);
    }
    private void thread(){if(!level.getServer().isSameThread())throw new IllegalStateException("wrong thread");}
    public CaravanMatchRules.State state(){thread();return rules.state();}
    public boolean waiting(UUID id){thread();return rules.waitingForRespawn(id);}
    public int respawnRemaining(UUID id){thread();return rules.respawnRemaining(id);}
    public Position base(UUID id){thread();return roster.side(id)==state().escort()?escortBase:attackBase;}
    public Position spawnPoint(UUID id){
        thread();var b=base(id);var members=roster.members().stream().filter(member->roster.side(member)==roster.side(id)).toList();
        int slot=members.indexOf(id);if(slot<0)throw new IllegalArgumentException("unknown member");
        return new Position(b.x()+(slot%4)*1.8-2.7,b.y(),b.z()-2-(slot/4)*1.8);
    }
    public List<UUID> readyRespawns(){thread();return rules.readyRespawns();}
    public boolean acknowledge(UUID id){thread();return rules.acknowledgeRespawn(id);}
    public boolean death(ServerPlayer p){
        thread();if(closed||p.level()!=level||!rules.death(p.getUUID()))return false;
        if(rules.state().route().driver()==null||!mounts.seated(rules.state().route().driver())){forkInput.clear();mounts.release();}
        mounts.recall(p.getUUID());hook.cancel(p.getUUID());CaravanSpeedAdapter.clear(p);scene.sync();return true;
    }
    public void externalTeleport(ServerPlayer p){thread();if(!mounts.internalTeleport(p.getUUID()))invalidate(p);}
    public void disconnect(ServerPlayer p){thread();invalidate(p);mounts.recall(p.getUUID());}
    public void invalidate(ServerPlayer p){
        thread();if(closed)return;
        if(mounts.seated(p.getUUID())){forkInput.clear();mounts.release();}
        rules.invalidate(p.getUUID());hook.cancel(p.getUUID());CaravanSpeedAdapter.clear(p);scene.sync();
    }
    public void preparePlayer(ServerPlayer p){
        thread();if(closed||p.level()!=level||roster.side(p.getUUID())==null)throw new IllegalArgumentException("map owner");
        var map=maps.computeIfAbsent(p.getUUID(),id->new CaravanMapAdapter(level,raster,id));
        p.getInventory().setItem(4,map.issue(p));p.getInventory().setItem(5,CaravanHookAdapter.item());
    }
    public void brief(ServerPlayer p){
        thread();if(closed||p.level()!=level||roster.side(p.getUUID())==null)return;
        boolean escort=roster.side(p.getUUID())==state().escort();
        tr.kapisma.Msg.tell(p,escort?"ESKORT · Sağ tık: sürücü. W: sür, A/D: rota.":"SALDIRGAN · Sandığı al, kendi üssüne götür.");
        tr.kapisma.Msg.tell(p,"5 Harita · 6 Kanca · 7 Kalkan · /arena kervan yardim");
    }
    public boolean help(ServerPlayer p){
        thread();if(closed||p.level()!=level||roster.side(p.getUUID())==null)return false;
        int now=level.getServer().getTickCount();
        Integer last=lastHelp.get(p.getUUID());if(last!=null&&now-last>=0&&now-last<40)return false;
        lastHelp.put(p.getUUID(),now);
        boolean escort=roster.side(p.getUUID())==state().escort();
        tr.kapisma.Msg.tell(p,escort?"ESKORT · Arabayı hedefe ulaştır, sandıklarını koru.":"SALDIRGAN · Sandığı ele geçir, kendi üssüne götür.");
        tr.kapisma.Msg.tell(p,escort?"Arabaya sağ tıkla. İleri (W) sürer; ayrımda sol (A) güvenli, sağ (D) riskli. Shift ile in.":"Sandığa sağ tıkla; bir defada bir sandık taşırsın ve yavaşlarsın.");
        tr.kapisma.Msg.tell(p,escort?"Düşen sandığı alıp arabaya yaklaşarak geri koy.":"Üssüne yürüyerek teslim et. İsteğe bağlı bırakma: /arena kervan birak.");
        tr.kapisma.Msg.tell(p,"Üst üste düşen sandıklar sırayla alınır; biri alınınca sıradaki görünür.");
        tr.kapisma.Msg.tell(p,"5: Kişisel harita · 6: Kanca · 7: Kalkan (F ile sol el). Ölüm: 10 sn (aktif maç).");
        tr.kapisma.Msg.tell(p,"Büyük harita için sol el boş olmalı. 8. boş yuvadayken F ile kalkanı kaldır.");
        tr.kapisma.Msg.tell(p,"Mor kare: eskort üssü · Turuncu elmas: teslim alanı · Altın şerit: kervan hedefi.");
        tr.kapisma.Msg.tell(p,"Zemindeki yeşil şerit güvenli, kırmızı şerit riskli güzergâhı gösterir.");
        tr.kapisma.Msg.tell(p,manualClimb?"Kanca: bloğa nişan al, sağ tıkla; tekrar sağ tıklayarak çekil. 30 sn bekleme.":"Kanca: bloğa nişan al ve sağ tıkla, otomatik çekiş. 30 sn bekleme.");
        tr.kapisma.Msg.tell(p,"Kanca yük taşırken veya binek üzerindeyken kullanılamaz. Kendi atına sağ tıklayarak bin.");
        return true;
    }
    public void ensureHorse(ServerPlayer p){
        thread();if(p.level()!=level||roster.side(p.getUUID())==null||mounts.hasHorse(p.getUUID()))return;
        var b=base(p.getUUID());var members=roster.members().stream().filter(id->roster.side(id)==roster.side(p.getUUID())).toList();
        double dx=members.indexOf(p.getUUID())*1.8-2.7;
        if(!mounts.horse(p.getUUID(),new Position(b.x()+dx,b.y(),b.z()+3)))throw new IllegalStateException("horse spawn");
    }
    public void newLeg(Collection<ServerPlayer> online){
        thread();forkInput.clear();hook.newLeg();mounts.release();mounts.clearHorses();for(var p:online)ensureHorse(p);
    }
    public CaravanMatchRules.State tick(long tick,Collection<Presence> observations,Collection<ServerPlayer> online){
        thread();if(closed)throw new IllegalStateException("closed");
        var before=rules.state();UUID driver=before.route().driver();
        if(driver!=null&&!mounts.seated(driver)){
            forkInput.clear();mounts.release();rules.invalidate(driver);
        }
        if(!bothConnected(online)||before.phase()!=CaravanMatchRules.Phase.LEG){forkInput.clear();mounts.resetInput();}
        var ids=online.stream().filter(p->p.level()==level).map(ServerPlayer::getUUID).toList();
        // Third-party seating/vehicles must not add a second mount authority.
        // Normal use events deny them; enforce the actual passenger relationship too.
        for(var p:online)if(p.level()==level&&p.isPassenger()){
            var id=p.getUUID();
            boolean canRide=roster.side(id)!=null&&p.isAlive()&&!p.isSpectator()
                &&!rules.waitingForRespawn(id)&&!rules.carrying(id);
            boolean authorized=canRide&&(mounts.seated(id)||mounts.horseOwner(p,p.getVehicle()));
            if(!authorized){p.stopRiding();hook.cancel(id);}
        }
        var active=new HashSet<UUID>();for(var p:online)if(p.level()==level&&p.isAlive()&&!p.isSpectator()&&!rules.waitingForRespawn(p.getUUID())&&!rules.carrying(p.getUUID()))active.add(p.getUUID());
        mounts.tickHorses(active);
        var after=scene.tick(tick,observations,ids,mounts.forward());
        if(!after.route().waitingAtJunction()||after.route().driver()==null||!Objects.equals(driver,after.route().driver())||!bothConnected(online))forkInput.clear();
        if(after.phase()!=CaravanMatchRules.Phase.LEG||after.route().driver()==null)mounts.release();
        else mounts.moveSeat(after.route().position(),rules.heading());
        if(after.phase()==CaravanMatchRules.Phase.LEG){
            for(var p:online)if(p.level()==level&&rules.carrying(p.getUUID())){
                if(rules.act(after.leg(),p.getUUID(),Action.DELIVER,0,observations)){scene.sync();after=rules.state();}
            }
        }
        hook.tick(tick,hookEligible(observations),after.phase()==CaravanMatchRules.Phase.LEG&&bothConnected(online));
        terrain.tick(tick);var heights=raster.prepare(terrain.snapshot());
        for(var p:online)if(p.level()==level&&roster.side(p.getUUID())!=null){
            CaravanSpeedAdapter.sync(p,rules,true);
            var map=maps.get(p.getUUID());
            if(map!=null){
                // Inventory guard is provided by host; never overwrite unrelated items.
                map.refresh(tick,after,p,heights);
            }
        }
        return after;
    }
    public void input(ServerPlayer p,float forward,float sideways,boolean shift,java.util.function.Supplier<Collection<Presence>> fresh,boolean eligible){
        thread();if(closed||!mounts.seated(p.getUUID()))return;
        if(!eligible){forkInput.clear();mounts.resetInput();return;}
        mounts.input(p,forward,shift);
        var state=rules.state();var id=p.getUUID();
        var choice=forkInput.accept(state.leg(),id,state.route().driver(),eligible&&state.route().waitingAtJunction(),mounts.seated(id),sideways,shift);
        if(choice!=null&&rules.act(state.leg(),id,choice==CaravanRouteRules.Route.SAFE?Action.SAFE:Action.RISKY,0,fresh.get())){
            p.displayClientMessage(Component.literal(choice==CaravanRouteRules.Route.SAFE?"Güvenli güzergâh seçildi":"Riskli güzergâh seçildi"),true);
        }
    }
    public boolean bothConnected(Collection<ServerPlayer> players){
        thread();boolean a=false,b=false;
        for(var p:players)if(p.level()==level){a|=roster.side(p.getUUID())==Side.RED;b|=roster.side(p.getUUID())==Side.BLUE;}
        return a&&b;
    }
    public InteractionResult useEntity(ServerPlayer p,Entity target,Collection<Presence> fresh,boolean eligible){
        thread();if(closed||target.level()!=level)return InteractionResult.PASS;
        if(mounts.owns(target))return eligible&&!rules.carrying(p.getUUID())&&mounts.horseOwner(p,target)&&!p.isShiftKeyDown()?InteractionResult.PASS:InteractionResult.FAIL;
        if(!scene.owns(target))return InteractionResult.PASS;
        if(!eligible||!CaravanHorseRules.canInteract(p.isPassenger(),mounts.seated(p.getUUID())))return InteractionResult.FAIL;
        var before=rules.state();
        if(!scene.interact(p,target,()->fresh))return InteractionResult.FAIL;
        var after=rules.state();
        if(!Objects.equals(before.route().driver(),after.route().driver()))forkInput.clear();
        if(p.getUUID().equals(after.route().driver())&&!p.getUUID().equals(before.route().driver())){
            if(!mounts.mount(p,after.route().position(),rules.heading())){
                rules.act(after.leg(),p.getUUID(),Action.LEAVE_DRIVER,0,fresh);return InteractionResult.FAIL;
            }
        }else if(p.getUUID().equals(before.route().driver())&&!p.getUUID().equals(after.route().driver()))mounts.release();
        return InteractionResult.SUCCESS;
    }
    private List<Presence> hookEligible(Collection<Presence> fresh){
        return roster.active(fresh).stream().filter(o->{
            var p=level.getServer().getPlayerList().getPlayer(o.player());
            return p!=null&&p.level()==level&&!p.isPassenger()&&!rules.carrying(o.player())&&!rules.waitingForRespawn(o.player());
        }).toList();
    }
    public boolean useHook(ServerPlayer p,long tick,Collection<Presence> fresh,boolean running){
        thread();return !closed&&p.level()==level&&hook.use(p,tick,hookEligible(fresh),running&&state().phase()==CaravanMatchRules.Phase.LEG);
    }
    public String qaDrive(){thread();return " input="+mounts.forward()+" clear="+scene.currentClear()+" heading="+rules.heading()+" wagonGeometry="+scene.qaGeometry()+" seatGeometry="+mounts.qaSeat(state().route().position(),rules.heading());}
    public boolean qaHorseHome(UUID id){thread();return mounts.horseAtHome(id);}
    public String qaMap(UUID id){thread();var map=maps.get(id);return (map==null?" rendered=0 skipped=0 pixelWrites=0":map.qaCounters())+terrain.qaStats();}
    public long hookRemaining(UUID id){thread();return hook.remaining(id);}
    public boolean hookAttached(UUID id){thread();return hook.attached(id);}
    public boolean protects(Entity e){thread();return mounts.owns(e)||scene.protects(e);}
    public boolean command(ServerPlayer p,String action,Collection<Presence> fresh){
        thread();if(closed||p.level()!=level||p.isSpectator()||!p.isAlive())return false;
        Action a=switch(action){
            case "caravan_safe"->Action.SAFE;case "caravan_risky"->Action.RISKY;
            case "caravan_drop"->Action.DROP;case "caravan_deliver"->Action.DELIVER;default->null;};
        if(a==null)return false;
        boolean ok=rules.act(state().leg(),p.getUUID(),a,0,fresh);
        if(ok){scene.sync();CaravanSpeedAdapter.sync(p,rules,true);}
        return ok;
    }
    @Override public void close(){
        thread();if(closed)return;closed=true;
        rules.stop();hook.close();scene.close();mounts.close();for(var m:maps.values())m.close();maps.clear();lastHelp.clear();
        for(var p:level.players())if(roster.side(p.getUUID())!=null)CaravanSpeedAdapter.clear(p);
    }
}
