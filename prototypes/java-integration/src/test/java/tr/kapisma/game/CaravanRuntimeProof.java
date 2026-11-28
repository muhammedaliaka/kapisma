package tr.kapisma.game;
import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.ai.attributes.Attributes;
import tr.kapisma.Kapisma;
import tr.kapisma.arena.CaravanMatchRules;

/** Isolated named-Fabric fixture. Console only, no route or score injection. */
public final class CaravanRuntimeProof implements ModInitializer {
    private static final Set<UUID> forward=new HashSet<>();
    private static final List<net.minecraft.world.entity.Entity> capturedEntities=new ArrayList<>();
    private static final List<net.minecraft.world.level.saveddata.maps.MapItemSavedData> capturedMaps=new ArrayList<>();
    private static NativeArenaGame lastGame;
    private static net.minecraft.server.level.ServerLevel capturedLevel;
    private static final List<net.minecraft.world.level.saveddata.maps.MapId> capturedIds=new ArrayList<>();
    private static net.minecraft.world.level.saveddata.maps.MapId vanillaId;
    private static net.minecraft.world.level.saveddata.maps.MapItemSavedData vanillaData;
    private static CaravanSceneAdapter pendingScene;
    private static CaravanMatchRules pendingRules;
    private static tr.kapisma.arena.MatchRoster.Position pendingAnchor;
    private static void closePending(){if(pendingScene!=null)pendingScene.close();pendingScene=null;pendingRules=null;pendingAnchor=null;}
    public void onInitialize(){
        if(!Boolean.getBoolean("kapisma.arenaProof")||!Boolean.getBoolean("kapisma.experimentalCaravan2"))throw new IllegalStateException("QA flags required");
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("arena-caravan-proof")
            .requires(s->s.getEntity()==null&&s.hasPermission(4))
            .then(Commands.argument("player",StringArgumentType.word()).then(Commands.argument("op",StringArgumentType.word())
                .executes(c->run(c.getSource(),StringArgumentType.getString(c,"player"),StringArgumentType.getString(c,"op")))))));
        ServerTickEvents.START_SERVER_TICK.register(server->{
            if(Kapisma.GM==null||!(Kapisma.GM.current instanceof NativeArenaGame game)){closePending();forward.clear();lastGame=null;return;}
            if(game!=lastGame){closePending();forward.clear();lastGame=game;}
            CaravanMatchRules.State state;
            try{state=game.caravanQaState();}catch(IllegalStateException ex){forward.clear();return;}
            UUID driver=state.route().driver();
            if(driver!=null&&forward.contains(driver)){
                var p=server.getPlayerList().getPlayer(driver);
                if(p!=null&&p.isPassenger())p.connection.handlePlayerInput(new net.minecraft.network.protocol.game.ServerboundPlayerInputPacket(0,1,false,false));
            }
        });
    }
    // Read only the named test server's vanilla teleport sequence; not a production hook.
    private static int teleportSequence(ServerPlayer p){
        try{var f=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");f.setAccessible(true);return f.getInt(p.connection);}
        catch(ReflectiveOperationException ex){throw new IllegalStateException("QA teleport sequence unavailable",ex);}
    }
    private static int run(CommandSourceStack source,String name,String op){
        if(op.equals("vanilla")){
            var level=source.getServer().overworld();
            if(vanillaId==null){
                var item=net.minecraft.world.item.MapItem.create(level,1000,1000,(byte)0,true,false);
                vanillaId=item.get(DataComponents.MAP_ID);vanillaData=level.getMapData(vanillaId);
            }
            source.sendSuccess(()->Component.literal("CARAVAN_VANILLA id="+vanillaId.id()+" unchanged="+(vanillaData!=null&&level.getMapData(vanillaId)==vanillaData)),false);return 1;
        }
        if(op.equals("worldgen")){
            var level=source.getServer().overworld();
            if(!(level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator))throw new IllegalStateException("normal noise generator required");
            int full=0;
            for(int x=64;x<70;x++)for(int z=64;z<68;z++)if(level.getChunkSource().getChunkNow(x,z)!=null)full++;
            long cpu=ProcessHandle.current().info().totalCpuDuration().map(java.time.Duration::toMillis).orElse(-1L);
            var vm=Runtime.getRuntime();
            int count=full;
            source.sendSuccess(()->Component.literal("WORLDGEN_FULL count="+count+" seed="+level.getSeed()+" cpuMs="+cpu+" heapMiB="+(vm.totalMemory()-vm.freeMemory())/1048576),false);return 1;
        }
        if(op.equals("metrics")){
            var s=source.getServer();var vm=Runtime.getRuntime();
            var times=java.util.Arrays.stream(s.getTickTimesNanos()).filter(v->v>0).sorted().toArray();
            double p95=times.length==0?0:times[(int)Math.ceil(times.length*.95)-1]/1e6;
            source.sendSuccess(()->Component.literal("CARAVAN_METRICS tick="+s.getTickCount()+" mspt="+s.getAverageTickTimeNanos()/1e6+" p95="+p95+" heapUsedMiB="+(vm.totalMemory()-vm.freeMemory())/1048576+" heapCommittedMiB="+vm.totalMemory()/1048576),false);return 1;
        }
        if(op.equals("audit")){
            int removed=0,unregistered=0,blank=0;
            for(var e:capturedEntities){if(e.isRemoved())removed++;if(e.level() instanceof net.minecraft.server.level.ServerLevel l&&l.getEntity(e.getUUID())==null)unregistered++;}
            byte bg=net.minecraft.world.level.material.MapColor.COLOR_BLACK.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.NORMAL);
            for(var map:capturedMaps){boolean ok=true;for(byte px:map.colors)if(px!=bg){ok=false;break;}if(ok)blank++;}
            int released=0;for(var id:capturedIds)if(capturedLevel.getMapData(id)==null)released++;
            int registry=capturedLevel==null?-1:CaravanTransientMaps.count(capturedLevel);
            String report="CARAVAN_AUDIT entities="+capturedEntities.size()+" removed="+removed+" unregistered="+unregistered+" maps="+capturedMaps.size()+" blank="+blank+" forward="+forward.size()+" released="+released+" registry="+registry;
            capturedEntities.clear();capturedMaps.clear();capturedIds.clear();capturedLevel=null;
            source.sendSuccess(()->Component.literal(report),false);return 1;
        }
        var p=source.getServer().getPlayerList().getPlayerByName(name);
        if(p==null||Kapisma.GM==null||!(Kapisma.GM.current instanceof NativeArenaGame game)||!game.arenaLevel(p.level()))return 0;
        var state=game.caravanQaState();String result="snapshot";
        if(op.equals("capture")){
            capturedEntities.clear();capturedMaps.clear();capturedIds.clear();capturedLevel=(net.minecraft.server.level.ServerLevel)p.level();
            var box=new net.minecraft.world.phys.AABB(-40,290,-30,40,320,30);
            capturedEntities.addAll(p.level().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box,e->e instanceof net.minecraft.world.entity.Display.BlockDisplay||e instanceof Interaction||e instanceof Horse||e instanceof net.minecraft.world.entity.decoration.ArmorStand));
            for(var player:source.getServer().getPlayerList().getPlayers())if(player.level()==p.level()){
                var data=net.minecraft.world.item.MapItem.getSavedData(player.getInventory().getItem(4),p.level());
                if(data!=null&&!capturedMaps.contains(data)){capturedMaps.add(data);capturedIds.add(player.getInventory().getItem(4).get(DataComponents.MAP_ID));}
            }
            result="CAPTURED";
        }
        else if(op.equals("race")){
            var target=p.level().getEntitiesOfClass(Interaction.class,p.getBoundingBox().inflate(5),e->e.getTags().contains("kapisma.caravan.WAGON_CARGO")).stream().findFirst().orElseThrow();
            var attackers=source.getServer().getPlayerList().getPlayers().stream().filter(q->q.level()==p.level()&&q.getTeam()!=null&&q.getTeam()==p.getTeam()).sorted(Comparator.comparing(q->q.getGameProfile().getName())).toList();
            int accepted=0;for(var q:attackers)if(UseEntityCallback.EVENT.invoker().interact(q,q.level(),InteractionHand.MAIN_HAND,target,null)==net.minecraft.world.InteractionResult.SUCCESS)accepted++;
            result="RACE_"+accepted+"_OF_"+attackers.size();
        }
        else if(op.equals("summitplace")||op.equals("airplace")){
            // Explicit synthetic actor positioning, NOT a native movement or teleport exploit test.
            p.setPos(19.5,op.equals("summitplace")?309:318,12.5);
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            result="PLACED";
        }
        else if(op.equals("pendingdrop")){
            // An isolated two-actor rules/scene instance, using real QA arena blocks.
            // The one temporary floor change is restored inside this same server command.
            closePending();
            var level=(net.minecraft.server.level.ServerLevel)p.level();
            var at=new tr.kapisma.arena.MatchRoster.Position(28.5,301,-18.5);
            var high=new tr.kapisma.arena.MatchRoster.Position(28.5,318,-18.5);
            var end=new tr.kapisma.arena.MatchRoster.Position(32.5,301,-18.5);
            UUID match=UUID.randomUUID(),r=UUID.randomUUID(),a=UUID.randomUUID();
            var roster=new tr.kapisma.arena.MatchRoster(match,List.of(r),List.of(a));
            var rules=new CaravanMatchRules(roster,List.of(at,new tr.kapisma.arena.MatchRoster.Position(28.5,301,-16.5),new tr.kapisma.arena.MatchRoster.Position(32.5,301,-16.5),end),List.of(at,end),new tr.kapisma.arena.MatchRoster.Position(.5,301,15.5),2,500,10,.075,3.5);
            var safety=new CaravanWorldSafety(level,-36,37,299,317,-24,25);
            var scene=new CaravanSceneAdapter(level,rules,safety);
            var floor=net.minecraft.core.BlockPos.containing(at.x(),at.y()-1,at.z());
            var previous=level.getBlockState(floor);boolean retain=false;
            try{
                var pr=new tr.kapisma.arena.MatchRoster.Presence(r,match,true,true,at);
                var pa=new tr.kapisma.arena.MatchRoster.Presence(a,match,true,true,at);
                scene.tick(1,List.of(pr,pa),List.of(r,a),null);
                if(!safety.crateSafe(at)||!rules.act(1,a,CaravanMatchRules.Action.TAKE,1,List.of(pr,pa)))throw new IllegalStateException("pending setup");
                scene.sync();
                scene.tick(2,List.of(pr,new tr.kapisma.arena.MatchRoster.Presence(a,match,true,true,high)),List.of(r,a),null);
                if(!level.setBlock(floor,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3))throw new IllegalStateException("pending floor fixture");
                if(safety.crateSafe(at)||safety.nearestDrop(high).isPresent()||!rules.death(a))throw new IllegalStateException("pending constraints");
                scene.sync();
                var c=rules.state().crates().getFirst();
                var bounds=new net.minecraft.world.phys.AABB(-40,290,-30,40,320,30);
                int hits=level.getEntitiesOfClass(Interaction.class,bounds,e->scene.owns(e)&&e.getTags().contains("kapisma.caravan.DROPPED")).size();
                int before=rules.state().ticksLeft();
                scene.tick(3,List.of(pr,new tr.kapisma.arena.MatchRoster.Presence(a,match,true,false,high)),List.of(r,a),null);
                if(c.status()!=tr.kapisma.arena.CaravanCargoRules.Status.DROPPED||!c.position().equals(high)||hits!=0||rules.state().ticksLeft()!=before-1)throw new IllegalStateException("pending state");
                pendingScene=scene;pendingRules=rules;pendingAnchor=at;retain=true;result="PENDING_CLOCK_CONTINUES";
            }finally{
                level.setBlock(floor,previous,3);
                if(!retain)scene.close();
            }
        }
        else if(op.equals("pendingretry")){
            if(pendingScene==null)throw new IllegalStateException("no pending fixture");
            try{
                pendingScene.sync();
                var c=pendingRules.state().crates().getFirst();
                var bounds=new net.minecraft.world.phys.AABB(-40,290,-30,40,320,30);
                int hits=p.level().getEntitiesOfClass(Interaction.class,bounds,e->pendingScene.owns(e)&&e.getTags().contains("kapisma.caravan.DROPPED")).size();
                result=c.status()==tr.kapisma.arena.CaravanCargoRules.Status.DROPPED&&c.position().equals(pendingAnchor)&&hits==1?"RESTORED_SINGLE_HIT":"FAIL_"+c+"_"+hits;
            }finally{closePending();}
        }
        else if(op.equals("horsestats")){
            var h=p.level().getEntitiesOfClass(Horse.class,p.getBoundingBox().inflate(96),q->p.getUUID().equals(q.getOwnerUUID())).stream().findFirst().orElseThrow();
            result=h.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)+","+h.getAttributeBaseValue(Attributes.JUMP_STRENGTH)+","+h.getMaxHealth()+","+h.getHealth();
        }
        else if(op.equals("verticaldrop")){
            var level=(net.minecraft.server.level.ServerLevel)p.level();
            var old=new CaravanWorldSafety(level,-36,37,299,310,-24,25);
            var fixed=new CaravanWorldSafety(level,-36,37,299,300+tr.kapisma.arena.CaravanTerrainLayout.HEIGHT+5,-24,25);
            int oldDenied=0,oldRelocated=0,newDirect=0,newAir=0;
            for(int[] c:new int[][]{{18,12},{19,11},{19,12},{19,13},{20,12}}){
                var at=new tr.kapisma.arena.MatchRoster.Position(c[0]+.5,309,c[1]+.5);
                if(!old.crateSafe(at))oldDenied++;
                if(old.nearestDrop(at).filter(q->!q.equals(at)).isPresent())oldRelocated++;
                if(fixed.crateSafe(at)&&fixed.nearestDrop(at).filter(at::equals).isPresent())newDirect++;
                var air=new tr.kapisma.arena.MatchRoster.Position(at.x(),at.y()+2,at.z());
                if(fixed.nearestDrop(air).filter(fixed::crateSafe).isPresent())newAir++;
            }
            result=oldDenied==5&&oldRelocated==5&&newDirect==5&&newAir==5?"OLD5_RELOCATED5_DIRECT5_AIR5":"FAIL_"+oldDenied+"_"+oldRelocated+"_"+newDirect+"_"+newAir;
        }
        else if(op.equals("coverray")){
            // Actual vanilla collider raycast at standing eye height, four fixed lines.
            double y=302.62;
            double[][] rays={{-20.5,-12.5,-28.5,-12.5},{-20.5,-8.5,-28.5,-8.5},{-2.5,8.5,-2.5,13.5},{.5,8.5,.5,13.5}};
            var hits=new ArrayList<Boolean>();
            for(var r:rays){
                var start=new net.minecraft.world.phys.Vec3(r[0],y,r[1]);var end=new net.minecraft.world.phys.Vec3(r[2],y,r[3]);
                var hit=p.level().clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
                hits.add(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK);
            }
            result=hits.equals(List.of(true,false,true,false))?"BLOCK_CLEAR_BLOCK_CLEAR":"FAIL_"+hits.toString().replace(" ","");
        }
        else if(Set.of("friendlyhit","enemyhit","environmenthit").contains(op)){
            var attacker=source.getServer().getPlayerList().getPlayers().stream().filter(q->q!=p&&q.level()==p.level()&&q.getTeam()!=null&&q.isAlive()&&!q.isSpectator()&&(q.getTeam()==p.getTeam())==op.equals("friendlyhit")).findFirst().orElseThrow();
            float before=p.getHealth();
            boolean applied=p.hurt(op.equals("environmenthit")?p.damageSources().generic():p.damageSources().playerAttack(attacker),4);
            result=(applied?"APPLIED":"DENIED")+"_"+before+"_"+p.getHealth();
        }
        else if(op.equals("forward")){forward.add(p.getUUID());result="input";}
        else if(op.equals("halt")){forward.remove(p.getUUID());p.connection.handlePlayerInput(new net.minecraft.network.protocol.game.ServerboundPlayerInputPacket(0,0,false,false));result="halt";}
        else if(Set.of("left","right","neutral").contains(op)){
            float sideways=op.equals("left")?1:op.equals("right")?-1:0;
            p.connection.handlePlayerInput(new net.minecraft.network.protocol.game.ServerboundPlayerInputPacket(sideways,0,false,false));
            result="input";
        }
        else if(Set.of("drive","cargo","dropped").contains(op)){
            String tag="kapisma.caravan."+switch(op){case "drive"->"WAGON";case "cargo"->"WAGON_CARGO";default->"DROPPED";};
            var target=p.level().getEntitiesOfClass(Interaction.class,p.getBoundingBox().inflate(5),e->e.getTags().contains(tag)).stream()
                .min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
            result=target==null?"missing":UseEntityCallback.EVENT.invoker().interact(p,p.level(),InteractionHand.MAIN_HAND,target,null).toString();
        }else if(op.equals("hook")){
            p.getInventory().selected=5;
            result=net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p,p.level(),InteractionHand.MAIN_HAND).getResult().toString();
        }
        else if(Set.of("benchguard","benchpositive","benchleave").contains(op)){
            // Exact known block fixture, not a claimed client packet or ray hit.
            var at=new net.minecraft.core.BlockPos(1,301,22);
            var bs=p.level().getBlockState(at);
            if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString().equals("polydecorations:dark_oak_bench"))throw new IllegalStateException("bench fixture absent");
            if(op.equals("benchleave")){p.stopRiding();result="LEFT";}
            else{
                if(p.distanceToSqr(1.5,301,22.5)>16||p.isPassenger())throw new IllegalStateException("bench fixture player");
                p.getInventory().selected=8;
                if(!p.getMainHandItem().isEmpty())throw new IllegalStateException("expected empty hand");
                var hit=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(1.5,301.5,22.5),net.minecraft.core.Direction.UP,at,false);
                var outcome=op.equals("benchguard")?p.gameMode.useItemOn(p,p.level(),p.getMainHandItem(),InteractionHand.MAIN_HAND,hit):bs.useWithoutItem(p.level(),p,hit);
                var vehicle=p.getVehicle();
                result=outcome+"/"+(vehicle==null?"NONE":net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString());
            }
        }
        else if(op.equals("scenicfoliage")){
            int trunk=0,leaf=0,persistent=0;
            for(int x=-43;x<=43;x++)for(int z=-30;z<=30;z++)if(Math.abs(x)>36||Math.abs(z)>24)
                for(int h=301;h<312;h++){
                    var bs=p.level().getBlockState(new net.minecraft.core.BlockPos(x,h,z));
                    if(bs.is(net.minecraft.world.level.block.Blocks.SPRUCE_LOG))trunk++;
                    if(bs.is(net.minecraft.world.level.block.Blocks.SPRUCE_LEAVES)){
                        leaf++;if(bs.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT))persistent++;
                    }
                }
            result=trunk+","+leaf+","+persistent;
        }
        else if(op.equals("scenicborder")){
            var border=p.level().getWorldBorder();
            result=border.getSize()+","+border.getCenterX()+","+border.getCenterZ();
        }
        else if(op.equals("servercoreconfig")){
            // Read-only QA inspection of the actual config object, no product dependency.
            try{
                var c=Class.forName("me.wesley1808.servercore.common.config.Config");
                var opt=c.getMethod("optimizations").invoke(null);
                var main=c.getMethod("get").invoke(null);
                var optType=c.getMethod("optimizations").getReturnType();
                var mainType=c.getMethod("get").getReturnType();
                var flags=new ArrayList<Boolean>();
                for(String n:List.of("reduceSyncLoads","cacheTickingChunks","optimizeCommandBlocks","fastBiomeLookups","cancelDuplicateFluidTicks"))
                    flags.add((Boolean)optType.getMethod(n).invoke(opt));
                for(String n:List.of("dynamic","activationRange","breedingCap")){
                    var getter=mainType.getMethod(n);
                    flags.add((Boolean)getter.getReturnType().getMethod("enabled").invoke(getter.invoke(main)));
                }
                var features=mainType.getMethod("features");var f=features.invoke(main);
                for(String n:List.of("preventMovingIntoUnloadedChunks","lobotomizeVillagers"))flags.add((Boolean)features.getReturnType().getMethod(n).invoke(f));
                var spawn=mainType.getMethod("mobSpawning");
                for(String n:List.of("zombieReinforcements","portalRandomTicks","monsterSpawner","infested")){
                    var getter=spawn.getReturnType().getMethod(n);
                    flags.add((Boolean)getter.getReturnType().getMethod("enforcesMobcap").invoke(getter.invoke(spawn.invoke(main))));
                }
                if(flags.size()!=14||flags.contains(true))throw new IllegalStateException("unexpected ServerCore flags "+flags);
                result="OFF_14";
            }catch(ReflectiveOperationException e){throw new IllegalStateException("ServerCore config unavailable",e);}
        }
        else if(op.equals("auto8")||op.equals("auto8sample")){
            var actors=source.getServer().getPlayerList().getPlayers().stream().filter(q->q.level()==p.level()).sorted(Comparator.comparing(ServerPlayer::getScoreboardName)).toList();
            if(actors.size()!=8)throw new IllegalStateException("eight test actors required");
            if(op.equals("auto8")){
                int i=0;
                for(var q:actors){
                    q.teleportTo((net.minecraft.server.level.ServerLevel)q.level(),-3.5+(i%4)*2,301,-13.5+(i/4)*2,0,-90);
                    q.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);q.getInventory().selected=5;i++;
                }
                for(var q:actors)net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(q,q.level(),InteractionHand.MAIN_HAND);
            }
            int attached=0,positive=0;double max=0;
            for(var q:actors){
                if(game.caravanQaHook(q.getUUID()).contains("hookAttached=true"))attached++;
                if(q.getDeltaMovement().y>0)positive++;
                max=Math.max(max,q.getDeltaMovement().length());
            }
            result=attached+","+positive+","+max;
        }
        else if(op.equals("hookobstacle")){
            // Reversible QA-only block in the tunnel, after real tagged-item launch.
            var at=new net.minecraft.core.BlockPos(0,304,-12);
            if(!p.level().getBlockState(at).isAir()||p.isPassenger())throw new IllegalStateException("obstacle fixture not clear");
            p.teleportTo((net.minecraft.server.level.ServerLevel)p.level(),.5,301,-11.5,0,-90);
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.getInventory().selected=5;
            var use=net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p,p.level(),InteractionHand.MAIN_HAND).getResult();
            var eye=p.getEyePosition();
            var ray=p.level().clip(new net.minecraft.world.level.ClipContext(eye,eye.add(0,5,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            if(!ray.getBlockPos().equals(at.above())||!game.caravanQaHook(p.getUUID()).contains("hookAttached=true"))throw new IllegalStateException("launch or roof fixture "+use+" "+ray.getBlockPos());
            if(!p.level().setBlock(at,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3))throw new IllegalStateException("obstacle insert");
            result=use.toString();
        }
        else if(op.equals("decoruse")){
            p.getInventory().selected=4;
            var eye=p.getEyePosition();var end=eye.add(p.getViewVector(1).scale(5.5));
            var hit=p.level().clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)result="MISS";
            else{
                var key=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(hit.getBlockPos()).getBlock());
                result=key+"/"+(key.getNamespace().equals("polydecorations")?net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(p,p.level(),InteractionHand.MAIN_HAND,hit).toString():"WRONG_TARGET");
            }
        }
        else if(op.equals("hookblock")||op.equals("ordinaryblock")){
            p.getInventory().selected=op.equals("hookblock")?5:4;
            var eye=p.getEyePosition();var end=eye.add(p.getViewVector(1).scale(5.5));
            var hit=p.level().clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            result=hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK?"MISS":net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(p,p.level(),InteractionHand.MAIN_HAND,hit).toString();
        }
        else if(op.equals("maplore")){
            var lore=p.getInventory().getItem(4).get(DataComponents.LORE);
            var expected=List.of("Beyaz: Konumun · Altın: Kervan","Açık mavi: Seçili güzergâh","Yeşil: Güvenli · Kırmızı: Riskli","Mor kare: Eskort üssü","Turuncu elmas: Saldırgan teslim alanı","Üst taraf kuzey · Kişisel harita");
            result=lore!=null&&lore.lines().stream().map(Component::getString).toList().equals(expected)?"GUIDE":"FAIL";
        }
        else if(Set.of("horseown","horseforeign","horsedrive").contains(op)){
            boolean own=!op.equals("horseforeign");
            var h=p.level().getEntitiesOfClass(Horse.class,p.getBoundingBox().inflate(96),e->e.getOwnerUUID()!=null&&p.getUUID().equals(e.getOwnerUUID())==own).stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
            if(h==null)result="missing";
            else{
                if(op.equals("horsedrive"))h.teleportTo(-13.5,state.route().position().y(),-11.5);
                p.getInventory().selected=4;
                p.teleportTo((net.minecraft.server.level.ServerLevel)p.level(),h.getX()+1.5,h.getY(),h.getZ(),90,0);
                var gate=UseEntityCallback.EVENT.invoker().interact(p,p.level(),InteractionHand.MAIN_HAND,h,null);
                if(gate==net.minecraft.world.InteractionResult.PASS)p.interactOn(h,InteractionHand.MAIN_HAND);
                result=own&&p.getVehicle()==h?"MOUNTED":!own&&gate==net.minecraft.world.InteractionResult.FAIL&&!p.isPassenger()?"DENIED":"FAIL";
                if(p.isPassenger()&&!op.equals("horsedrive"))p.stopRiding();
            }
        }
        else if(Set.of("horseedge","horsefall","horseaway","horsemountedcargo","horsecarrying").contains(op)){
            var h=p.level().getEntitiesOfClass(Horse.class,p.getBoundingBox().inflate(96),e->p.getUUID().equals(e.getOwnerUUID())).stream().findFirst().orElse(null);
            if(h==null)result="missing";
            else{
                var w=state.route().position();
                if(op.equals("horseedge")){h.teleportTo(0.5,w.y(),24.5);result="EDGE";}
                else if(op.equals("horsefall")){h.teleportTo(0.5,w.y()-1,0.5);result="FALL";}
                else if(op.equals("horseaway")){h.teleportTo(-10.5,w.y(),.5);result="AWAY";}
                else if(op.equals("horsecarrying")){
                    // Move only the horse. Teleporting the carrying player would invalidate their cargo.
                    h.teleportTo(p.getX()+1.5,p.getY(),p.getZ());
                    p.getInventory().selected=4;
                    var gate=UseEntityCallback.EVENT.invoker().interact(p,p.level(),InteractionHand.MAIN_HAND,h,null);
                    result=gate==net.minecraft.world.InteractionResult.FAIL&&!p.isPassenger()?"DENIED":"FAIL";
                }else{
                    h.teleportTo(w.x()+2,w.y(),w.z());
                    p.teleportTo((net.minecraft.server.level.ServerLevel)p.level(),w.x()+3.5,w.y(),w.z(),90,0);
                    p.getInventory().selected=4;
                    var gate=UseEntityCallback.EVENT.invoker().interact(p,p.level(),InteractionHand.MAIN_HAND,h,null);
                    if(gate==net.minecraft.world.InteractionResult.PASS)p.interactOn(h,InteractionHand.MAIN_HAND);
                    var hit=p.level().getEntitiesOfClass(Interaction.class,p.getBoundingBox().inflate(5),e->e.getTags().contains("kapisma.caravan.WAGON_CARGO")).stream().findFirst().orElse(null);
                    var cargo=hit==null?net.minecraft.world.InteractionResult.PASS:UseEntityCallback.EVENT.invoker().interact(p,p.level(),InteractionHand.MAIN_HAND,hit,null);
                    result=p.getVehicle()==h&&cargo==net.minecraft.world.InteractionResult.FAIL?"DENIED":"FAIL";
                    if(p.isPassenger())p.stopRiding();
                }
            }
        }
        else if(op.equals("mapdrop")){
            var stack=p.getInventory().getItem(4).copy();p.getInventory().selected=4;
            boolean dropped=p.drop(true);
            p.containerMenu.clicked(40,1,net.minecraft.world.inventory.ClickType.THROW,p);
            result=!dropped&&net.minecraft.world.item.ItemStack.matches(stack,p.getInventory().getItem(4))?"PROTECTED":"FAIL";
        }else if(op.equals("mapmove")){
            var stack=p.getInventory().getItem(4).copy();
            p.containerMenu.clicked(40,0,net.minecraft.world.inventory.ClickType.PICKUP,p);
            boolean picked=net.minecraft.world.item.ItemStack.matches(stack,p.containerMenu.getCarried())&&p.getInventory().getItem(4).isEmpty();
            p.containerMenu.clicked(-999,0,net.minecraft.world.inventory.ClickType.PICKUP,p);
            boolean protectedCursor=net.minecraft.world.item.ItemStack.matches(stack,p.containerMenu.getCarried());
            p.containerMenu.clicked(40,0,net.minecraft.world.inventory.ClickType.PICKUP,p);
            result=picked&&protectedCursor&&net.minecraft.world.item.ItemStack.matches(stack,p.getInventory().getItem(4))&&p.containerMenu.getCarried().isEmpty()?"PROTECTED":"FAIL";
        }else if(!op.equals("snapshot"))return 0;
        state=game.caravanQaState();
        var maps=p.getInventory().getItem(4).get(DataComponents.MAP_ID);
        var mapData=net.minecraft.world.item.MapItem.getSavedData(p.getInventory().getItem(4),p.level());
        int mapHash=mapData==null?0:Arrays.hashCode(mapData.colors);
        int selfPixels=0;
        if(mapData!=null)for(byte px:mapData.colors)if(px==net.minecraft.world.level.material.MapColor.QUARTZ.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.HIGH))selfPixels++;
        var horses=p.level().getEntitiesOfClass(Horse.class,p.getBoundingBox().inflate(96),h->p.getUUID().equals(h.getOwnerUUID()));
        String line="CARAVANQA "+name+" op="+op+" result="+result+" mounted="+p.isPassenger()+
            " spectator="+p.isSpectator()+" map="+(maps==null?"none":maps.id())+" mapHash="+mapHash+" selfPixels="+selfPixels+" horses="+horses.size()+" horseId="+(horses.size()==1?horses.getFirst().getUUID():"none")+" horseX="+(horses.size()==1?horses.getFirst().getX():0)+" horseY="+(horses.size()==1?horses.getFirst().getY():0)+" horseZ="+(horses.size()==1?horses.getFirst().getZ():0)+" horseRider="+(horses.size()==1&&p.getVehicle()==horses.getFirst())+
            " speed="+p.getAttributeValue(Attributes.MOVEMENT_SPEED)+" posY="+p.getY()+" posX="+p.getX()+" posZ="+p.getZ()+" health="+p.getHealth()+" tpSeq="+teleportSequence(p)+" cameraSelf="+(p.getCamera()==p)+game.caravanQaHook(p.getUUID())+" phase="+state.phase()+" leg="+state.leg()+
            " escort="+state.escort()+" driver="+state.route().driver()+" progress="+state.route().distance()+
            " junction="+state.route().waitingAtJunction()+" route="+state.route().route()+" ticks="+state.ticksLeft()+
            " wagon="+state.route().position()+" crates="+state.crates().stream().map(c->c.id()+":"+c.status()).toList()+
            " red="+state.red()+" blue="+state.blue()+" winner="+state.winner()+
            " cargoCoords="+state.crates().stream().filter(c->c.status()==tr.kapisma.arena.CaravanCargoRules.Status.CARRIED||c.status()==tr.kapisma.arena.CaravanCargoRules.Status.DROPPED).map(c->c.id()+":"+c.status()+":"+c.position().x()+","+c.position().y()+","+c.position().z()).collect(java.util.stream.Collectors.joining(";"));
        source.sendSuccess(()->Component.literal(line),false);return 1;
    }
}
