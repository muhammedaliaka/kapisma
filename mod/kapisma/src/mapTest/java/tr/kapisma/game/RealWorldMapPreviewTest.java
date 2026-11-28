package tr.kapisma.game;

import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;

/** Real vanilla ServerLevel proof, in a private local disposable test world.
 * No Fabric mod package, external clients, or live game lifecycle. */
public final class RealWorldMapPreviewTest {
    private static final long SEED = 5743893257777525637L;
    private static final int[][] CASES = {{16,-48,2},{-257,259,2},{1024,-1024,2},{-2048,-2048,2},{0,0,4}};

    public static void main(String[] args) throws Exception {
        Path root=Path.of("").toAbsolutePath();
        if(!root.getFileName().toString().equals("map-world-proof"))
            throw new IllegalStateException("isolated working directory required");
        if(!Files.exists(root.resolve("server.properties")))
            Files.writeString(root.resolve("server.properties"),"server-ip=127.0.0.1\nserver-port=0\nonline-mode=true\nmax-players=1\nview-distance=2\nsimulation-distance=2\nlevel-seed="+SEED+"\nlevel-name=proof-world\nenable-rcon=false\nenable-query=false\n");
        boolean reuse=Arrays.equals(args,new String[]{"--reuse"});
        if(args.length!=0&&!reuse)throw new IllegalArgumentException("unsupported arguments");
        if(Files.exists(root.resolve("worlds/proof-world/level.dat"))&&!reuse) throw new IllegalStateException("existing proof world; explicit reuse required");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var settings=new DedicatedServerSettings(root.resolve("server.properties"));
        var props=settings.getProperties();
        if(!"127.0.0.1".equals(props.serverIp) || props.serverPort!=0)
            throw new IllegalStateException("loopback ephemeral port required");
        var storage=LevelStorageSource.createDefault(root.resolve("worlds")).validateAndCreateAccess("proof-world");
        var packs=ServerPacksSource.createPackRepository(storage);
        var saved=storage.hasWorldData()?storage.getDataTag():null;
        if(reuse&&saved==null)throw new IllegalStateException("no proof world to reuse");
        var dataConfig=saved==null?WorldDataConfiguration.DEFAULT:LevelStorageSource.readDataConfig(saved);
        var config=new WorldLoader.InitConfig(new WorldLoader.PackConfig(packs,dataConfig,true,saved==null),
                Commands.CommandSelection.DEDICATED,2);
        WorldStem stem=Util.blockUntilDone(executor -> WorldLoader.load(config,ctx -> {
            if(saved!=null){
                var loaded=LevelStorageSource.getLevelDataAndDimensions(saved,ctx.dataConfiguration(),
                    ctx.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM),ctx.datapackWorldgen());
                if(loaded.worldData().worldGenOptions().seed()!=SEED||!loaded.worldData().getLevelName().equals("Map Proof"))
                    throw new IllegalStateException("not the expected isolated proof world");
                return new WorldLoader.DataLoadOutput<>(loaded.worldData(),loaded.dimensions().dimensionsRegistryAccess());
            }
            var dimensions=props.createDimensions(ctx.datapackWorldgen()).bake(ctx.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM));
            var rules=new GameRules();
            rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);
            var ls=new LevelSettings("Map Proof",net.minecraft.world.level.GameType.SURVIVAL,false,props.difficulty,false,rules,ctx.dataConfiguration());
            var data=new PrimaryLevelData(ls,props.worldOptions,dimensions.specialWorldProperty(),
                dimensions.lifecycle().add(ctx.datapackWorldgen().allRegistriesLifecycle()));
            return new WorldLoader.DataLoadOutput<net.minecraft.world.level.storage.WorldData>(data,dimensions.dimensionsRegistryAccess());
        },WorldStem::new,Util.backgroundExecutor(),executor)).get(90,TimeUnit.SECONDS);
        var services=Services.create(new YggdrasilAuthenticationService(Proxy.NO_PROXY),root.toFile());
        CompletableFuture<Integer> result=new CompletableFuture<>();
        ProofServer server=MinecraftServer.spin(thread -> new ProofServer(thread,storage,packs,stem,settings,services,result));
        int count;
        try { count=result.get(180,TimeUnit.SECONDS); }
        finally { server.halt(true); }
        if(count!=CASES.length||!server.isStopped()) throw new AssertionError("proof incomplete or server not stopped");
        System.out.println("REAL_WORLD_MAP_PROOF "+count+"/"+CASES.length+" PASS pixels="+(count*16384)+" seed="+SEED);
        System.exit(0);
    }

    private static final class ProofServer extends DedicatedServer {
        private final CompletableFuture<Integer> result;
        private IncrementalBiomePreview preview;
        private ItemStack map;
        private int index, steps;
        private boolean guardsVerified;
        private long maxSliceNs;
        private static final boolean TREASURE_PROOF="1".equals(System.getenv("KAPISMA_TREASURE_PROOF"));
        private UhcTreasure treasure;
        private net.minecraft.world.level.ChunkPos proofCenter;
        private int treasureTicks, peakTickets, peakChestChunks;
        ProofServer(Thread thread,LevelStorageSource.LevelStorageAccess storage,
                    net.minecraft.server.packs.repository.PackRepository packs,WorldStem stem,
                    DedicatedServerSettings settings,Services services,CompletableFuture<Integer> result) {
            super(thread,storage,packs,stem,settings,DataFixers.getDataFixer(),services,LoggerChunkProgressListener::create);
            this.result=result;
            setPort(0);
        }
        @Override public void tickChildren(BooleanSupplier time) {
            super.tickChildren(time);
            if(result.isDone())return;
            try {
                var level=overworld();
                if(level.getSeed()!=SEED)throw new AssertionError("unexpected seed");
                if(!guardsVerified){
                    verifyGuards(level);verifyLoadedSurface(level);verifySpawnCandidates(level);
                    if("1".equals(System.getenv("KAPISMA_XRAY_WORLD_PROOF")))tr.kapisma.xray.XrayWorldProof.run(level);
                    guardsVerified=true;
                }
                if(index==CASES.length){
                    advanceTreasureProof(level);
                    return;
                }
                if(preview==null){
                    int[] c=CASES[index];
                    map=GameWorld.createCenteredMap(level,c[0],c[1],(byte)c[2],c[2]==2);
                    preview=new IncrementalBiomePreview(level,map);
                    try {preview.take();throw new AssertionError("early take allowed");}
                    catch(IllegalStateException expected){}
                    steps=0;maxSliceNs=0;
                }
                long start=System.nanoTime();
                int ops=preview.step(512,8_000_000L);
                maxSliceNs=Math.max(maxSliceNs,System.nanoTime()-start);
                if(ops<=0||ops>IncrementalBiomePreview.MAX_PAINT_OPERATIONS||++steps>4000)throw new AssertionError("invalid preview progress");
                if(!preview.complete())return;
                // Independent original function on a second identically centered map.
                int[] c=CASES[index];
                long vanillaStart=System.nanoTime();
                var oracle=GameWorld.centeredMap(level,c[0],c[1],(byte)c[2],c[2]==2);
                long vanillaNs=System.nanoTime()-vanillaStart;
                var delivered=preview.take();
                var actual=MapItem.getSavedData(delivered,level);
                var expected=MapItem.getSavedData(oracle,level);
                if(actual==null||expected==null||actual.centerX!=expected.centerX||actual.centerZ!=expected.centerZ
                    ||actual.scale!=expected.scale||actual.dimension!=expected.dimension
                    ||!Arrays.equals(actual.colors,expected.colors))throw new AssertionError("real vanilla mismatch case "+index);
                try{preview.take();throw new AssertionError("double take allowed");}
                catch(IllegalStateException expectedFailure){}
                long nonzero=0;boolean[] palette=new boolean[256];
                for(byte pixel:actual.colors){if(pixel!=0)nonzero++;palette[Byte.toUnsignedInt(pixel)]=true;}
                int distinct=0;for(boolean used:palette)if(used)distinct++;
                if(nonzero==0||distinct<2)throw new AssertionError("trivial map fixture");
                if(c[2]==2){
                    var target=new net.minecraft.core.BlockPos(c[0],0,c[1]);
                    net.minecraft.world.level.saveddata.maps.MapItemSavedData.addTargetDecoration(delivered,target,"hazine",
                        net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X);
                    var decorations=delivered.get(net.minecraft.core.component.DataComponents.MAP_DECORATIONS);
                    var mark=decorations==null?null:decorations.decorations().get("hazine");
                    if(mark==null||!mark.type().equals(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X)
                        ||mark.x()!=c[0]||mark.z()!=c[1])throw new AssertionError("treasure marker lost");
                } else {
                    var locked=actual.locked();
                    level.setMapData(delivered.get(net.minecraft.core.component.DataComponents.MAP_ID),locked);
                    var fetched=MapItem.getSavedData(delivered,level);
                    if(fetched!=locked||!fetched.locked||!Arrays.equals(fetched.colors,expected.colors))
                        throw new AssertionError("region lock lost");
                }
                System.out.println("REAL_WORLD_MAP case="+index+" pixels=16384 steps="+steps+" max_slice_ns="+maxSliceNs+
                    " vanilla_ns="+vanillaNs+" nonzero="+nonzero+" distinct="+distinct+
                    " actual_id="+delivered.get(net.minecraft.core.component.DataComponents.MAP_ID).id()+
                    " oracle_id="+oracle.get(net.minecraft.core.component.DataComponents.MAP_ID).id()+" matched=true metadata=true");
                preview=null;map=null;
                if(++index==CASES.length && !TREASURE_PROOF)result.complete(index);
            } catch(Throwable ex) {
                try { if(treasure!=null)treasure.close(); releaseProofCenter(overworld()); }
                catch(Throwable cleanup){ex.addSuppressed(cleanup);}
                result.completeExceptionally(ex);
            }
        }
        // Reflection is test-only, reads the real vanilla distance manager rather than trusting the wrapper count.
        private int actualTreasureTickets(net.minecraft.server.level.ServerLevel level) throws Exception {
            var f=net.minecraft.server.level.DistanceManager.class.getDeclaredField("tickets");
            f.setAccessible(true);
            var t=UhcTreasure.class.getDeclaredField("LOAD");
            t.setAccessible(true);
            Object expectedType=t.get(null);
            var manager=level.getChunkSource().chunkMap.getDistanceManager();
            int count=0;
            for(Object set:((java.util.Map<?,?>)f.get(manager)).values())
                for(Object ticket:(Iterable<?>)set)
                    if(((net.minecraft.server.level.Ticket<?>)ticket).getType()==expectedType)count++;
            return count;
        }
        private void releaseProofCenter(net.minecraft.server.level.ServerLevel level){
            if(proofCenter!=null){
                level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED,proofCenter,1,proofCenter);
                proofCenter=null;
            }
        }
        private void advanceTreasureProof(net.minecraft.server.level.ServerLevel level) throws Exception {
            if(treasure==null){
                int cx=16,cz=-48;
                proofCenter=new net.minecraft.world.level.ChunkPos(cx>>4,cz>>4);
                level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,proofCenter,1,proofCenter);
                if(actualTreasureTickets(level)!=0)throw new AssertionError("unexpected preexisting treasure tickets");
                var cancelled=new UhcTreasure(level,cx,cz,40,new java.util.Random(42),24,4);
                cancelled.prepare(1);
                if(cancelled.outstandingTickets()==0||actualTreasureTickets(level)!=cancelled.outstandingTickets())
                    throw new AssertionError("cancellation fixture did not acquire real tickets");
                cancelled.close();cancelled.close();
                if(cancelled.outstandingTickets()!=0||actualTreasureTickets(level)!=0||cancelled.prepare(4))
                    throw new AssertionError("cancelled treasure revived or leaked tickets");
                System.out.println("REAL_WORLD_TREASURE cancel_reentry=false tickets=0 PASS");
                treasure=new UhcTreasure(level,cx,cz,40,new java.util.Random(817),24,28);
            }
            if(++treasureTicks>2800)throw new AssertionError("treasure proof timeout");
            treasure.advancePreview();
            if(treasureTicks%4!=0)return;
            // Deliberately change admission cap mid-flight, including a lower cap than outstanding requests.
            int cap=treasureTicks<40?4:treasureTicks<120?1:2;
            int before=treasure.outstandingChestChunks();
            boolean done=treasure.prepare(cap);
            if(treasure.failed())throw new AssertionError("treasure preparation failed");
            int after=treasure.outstandingChestChunks();
            if(after>Math.max(before,cap))throw new AssertionError("chest admission exceeded");
            int actual=actualTreasureTickets(level);
            if(actual!=treasure.outstandingTickets())throw new AssertionError("ticket tracking mismatch");
            peakTickets=Math.max(peakTickets,actual);
            peakChestChunks=Math.max(peakChestChunks,after);
            if(!done)return;
            if(actual!=0||treasure.outstandingChestChunks()!=0||!treasure.isComplete())
                throw new AssertionError("completed treasure leaked tickets");
            var positions=treasure.chestPositions();
            var depots=treasure.depotPositions();
            if(positions.size()!=28||new java.util.HashSet<>(positions).size()!=28||depots.size()!=2)
                throw new AssertionError("wrong chest/barrel count");
            int maps=0;
            for(int b=0;b<depots.size();b++){
                var bp=depots.get(b);
                if(positions.contains(bp))throw new AssertionError("depot overlaps chest");
                if(!(level.getBlockEntity(bp) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel))
                    throw new AssertionError("depot missing");
                int expected=b==0?27:1;
                for(int slot=0;slot<27;slot++){
                    var item=barrel.getItem(slot);
                    if(slot>=expected){if(!item.isEmpty())throw new AssertionError("extra barrel item");continue;}
                    var target=positions.get(maps++);
                    var decorations=item.get(net.minecraft.core.component.DataComponents.MAP_DECORATIONS);
                    var mark=decorations==null?null:decorations.decorations().get("hazine");
                    var data=MapItem.getSavedData(item,level);
                    if(!item.is(net.minecraft.world.item.Items.FILLED_MAP)||data==null||data.scale!=2||
                       mark==null||!mark.type().equals(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X)||
                       mark.x()!=target.getX()||mark.z()!=target.getZ()||
                       !item.getHoverName().getString().equals("Hazine Haritası "+maps))
                        throw new AssertionError("wrong treasure map metadata "+maps);
                }
            }
            for(var pos:positions){
                level.getChunk(pos.getX()>>4,pos.getZ()>>4); // proof-only inspection, not production.
                if(!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)||
                   !chest.getItem(4).is(net.minecraft.world.item.Items.GOLDEN_APPLE)||
                   chest.getItem(4).getCount()<1||chest.getItem(4).getCount()>2||
                   !chest.getItem(11).is(net.minecraft.world.item.Items.GOLD_INGOT)||
                   chest.getItem(11).getCount()<3||chest.getItem(11).getCount()>7||
                   !chest.getItem(13).is(net.minecraft.world.item.Items.ARROW)||
                   chest.getItem(13).getCount()<6||chest.getItem(13).getCount()>12||
                   !chest.getItem(15).is(net.minecraft.world.item.Items.IRON_INGOT)||
                   chest.getItem(15).getCount()<3||chest.getItem(15).getCount()>7)
                    throw new AssertionError("chest loot mismatch "+pos);
            }
            treasure.close();
            if(maps!=28||actualTreasureTickets(level)!=0)throw new AssertionError("final treasure count or tickets");
            releaseProofCenter(level);
            System.out.println("REAL_WORLD_TREASURE chests=28 barrels=2 maps="+maps+" tickets=0 peak_tickets="+peakTickets+
                " peak_chest_chunks="+peakChestChunks+" ticks="+treasureTicks+" PASS");
            result.complete(index);
        }
        private void verifyGuards(net.minecraft.server.level.ServerLevel level) throws Exception {
            var item=GameWorld.createCenteredMap(level,16,-48,(byte)2,true);
            var cancelled=new IncrementalBiomePreview(level,item);
            cancelled.step(8,8_000_000L);
            cancelled.cancel();
            if(cancelled.complete()||cancelled.step(8,8_000_000L)!=0)throw new AssertionError("cancel failed");
            reject(cancelled::take);
            System.out.println("REAL_WORLD_GUARD cancel PASS");
            var replaced=new IncrementalBiomePreview(level,item);
            var data=MapItem.getSavedData(item,level);
            level.setMapData(item.get(net.minecraft.core.component.DataComponents.MAP_ID),data.locked());
            reject(()->replaced.step(8,8_000_000L));
            reject(replaced::take);
            System.out.println("REAL_WORLD_GUARD identity_replacement PASS");
            var nether=getLevel(net.minecraft.world.level.Level.NETHER);
            if(nether==null)throw new AssertionError("missing nether");
            reject(()->new IncrementalBiomePreview(nether,item));
            System.out.println("REAL_WORLD_GUARD wrong_dimension PASS");
            var result=new CompletableFuture<Boolean>();
            Thread thread=new Thread(()->{
                try{new IncrementalBiomePreview(level,item);result.complete(false);}
                catch(IllegalStateException expected){result.complete(true);}
                catch(Throwable ex){result.completeExceptionally(ex);}
            },"Map-proof-guard");
            thread.start();
            if(!result.get(3,TimeUnit.SECONDS))throw new AssertionError("wrong thread accepted");
            thread.join(3000);
            System.out.println("REAL_WORLD_GUARD wrong_thread PASS");
        }
        private void verifyLoadedSurface(net.minecraft.server.level.ServerLevel level) {
            var spawn=level.getSharedSpawnPos();
            int baseX=(spawn.getX()>>4)<<4, baseZ=(spawn.getZ()>>4)<<4;
            level.getChunk(baseX>>4,baseZ>>4); // Explicit generation ONLY in proof fixture.
            if(level.getChunkSource().getChunkNow(baseX>>4,baseZ>>4)==null)
                throw new AssertionError("proof chunk unavailable");
            var gen=level.getChunkSource().getGenerator();
            var randomState=level.getChunkSource().randomState();
            int count=0,land=0;
            for(int x=baseX;x<baseX+16;x+=3)for(int z=baseZ;z<baseZ+16;z+=3){
                var expected=GameWorld.safeSurface(level,x,z);
                var actual=GameWorld.safeSurfaceLoaded(level,x,z,false);
                if(!java.util.Objects.equals(expected,actual))throw new AssertionError("surface mismatch");
                count++;
                if(gen.getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,level,randomState)>gen.getSeaLevel()+1){
                    if(!java.util.Objects.equals(expected,GameWorld.safeSurfaceLoaded(level,x,z,true)))
                        throw new AssertionError("prechecked surface mismatch");
                    land++;
                }
            }
            if(land==0)throw new AssertionError("no land sample");
            int distant=1000000;
            if(level.getChunkSource().getChunkNow(distant>>4,distant>>4)!=null)
                throw new AssertionError("distant fixture unexpectedly loaded");
            if(GameWorld.safeSurfaceLoaded(level,distant,distant,true)!=null ||
                level.getChunkSource().getChunkNow(distant>>4,distant>>4)!=null)
                throw new AssertionError("unexpected synchronous generation");
            System.out.println("REAL_WORLD_SURFACE parity="+count+" prechecked_land="+land+" unloaded_no_generation=true");
        }
        private void verifySpawnCandidates(net.minecraft.server.level.ServerLevel level) {
            var gen=level.getChunkSource().getGenerator();
            var state=level.getChunkSource().randomState();
            int cases=0,found=0;
            long maxSlice=0;
            for(long seed=0;seed<4;seed++)for(int radius:new int[]{0,128,512}){
                var oldRandom=new java.util.Random(seed);
                var newRandom=new java.util.Random(seed);
                var taken=java.util.List.of(new int[]{16,-48});
                double separation=radius==0?0:radius/2.0;
                int[] expected=null;
                int r=radius<=0?8:radius;
                for(int i=0;i<200;i++){
                    int x=16+oldRandom.nextInt(2*r+1)-r,z=-48+oldRandom.nextInt(2*r+1)-r;
                    double sep=separation*(i<100?1:i<150?0.6:0.3);
                    double dx=16-x,dz=-48-z;
                    if(dx*dx+dz*dz<sep*sep)continue;
                    if(gen.getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,level,state)>gen.getSeaLevel()+1){
                        expected=new int[]{x,z};break;
                    }
                }
                var work=GameWorld.candidateWork(16,-48,radius,newRandom,taken,separation);
                int attempts=0;
                while(!work.complete()){
                    long start=System.nanoTime();
                    int n=GameWorld.advanceCandidate(level,work);
                    maxSlice=Math.max(maxSlice,System.nanoTime()-start);
                    if(n<1||n>8||(attempts+=n)>200)throw new AssertionError("candidate progress");
                }
                if(!Arrays.equals(expected,work.result())||oldRandom.nextLong()!=newRandom.nextLong())
                    throw new AssertionError("real spawn parity");
                if(expected!=null)found++;
                cases++;
            }
            if(found==0)throw new AssertionError("no real candidate");
            System.out.println("REAL_WORLD_SPAWN parity="+cases+" found="+found+" rng_equal=true max_slice_ns="+maxSlice);
        }
        private static void reject(Runnable action){
            try{action.run();throw new AssertionError("expected rejection");}
            catch(IllegalStateException|IllegalArgumentException expected){}
        }
        @Override public void onServerExit() {
            if(!result.isDone())result.completeExceptionally(new IllegalStateException("server stopped before proof"));
            // DedicatedServer normally exits the JVM; the test main owns the exit status.
        }
    }
}
