package tr.kapisma.game;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import tr.kapisma.arena.ReturnJournal;
import tr.kapisma.lobby.Lobby;
import tr.kapisma.origin.OriginService;

/** Return ledger is server-owned; only clean lobby entrants may be journaled. */
public final class ArenaRecovery {
    private ArenaRecovery(){}
    private static MinecraftServer owner;
    private static ReturnJournal journal;
    private static Path path(MinecraftServer server){return server.getServerDirectory().resolve("kapisma-arena-return.v1");}
    private static ReturnJournal journal(MinecraftServer server)throws IOException{
        if(!server.isSameThread())throw new IllegalStateException("server thread");
        if(owner!=server){journal=new ReturnJournal(path(server));owner=server;}
        return journal;
    }
    public static boolean unresolved(MinecraftServer server,UUID id)throws IOException{
        if(owner!=server&&!Files.exists(path(server),LinkOption.NOFOLLOW_LINKS))return false;
        return journal(server).get(id).isPresent();
    }
    public static void remember(MinecraftServer server,ServerPlayer p,UUID match)throws IOException{
        rememberAll(server,List.of(p),match);
    }
    public static void rememberAll(MinecraftServer server,List<ServerPlayer> players,UUID match)throws IOException{
        var batch=new LinkedHashMap<UUID,ReturnJournal.Entry>();
        for(var p:players){
            BlockPos pos=p.getRespawnPosition();
            var e=new ReturnJournal.Entry(match,p.getRespawnDimension().location().toString(),
                pos==null?null:pos.getX(),pos==null?null:pos.getY(),pos==null?null:pos.getZ(),p.getRespawnAngle(),p.isRespawnForced());
            if(batch.putIfAbsent(p.getUUID(),e)!=null)throw new IOException("duplicate player");
        }
        journal(server).rememberAll(batch);
    }
    public static boolean returnPlayer(MinecraftServer server,ServerPlayer p,UUID match)throws IOException{
        var j=journal(server);var saved=j.get(p.getUUID());
        if(saved.isEmpty()||!saved.get().match().equals(match))return false;
        var entry=saved.get();
        Lobby.send(server,p);OriginService.reset(server,p);
        var dimension=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(entry.dimension()));
        BlockPos pos=entry.x()==null?null:new BlockPos(entry.x(),entry.y(),entry.z());
        p.setRespawnPosition(dimension,pos,entry.angle(),entry.forced(),false);
        // Vanilla save may catch I/O errors; verify actual persisted player data before acknowledging.
        CompoundTag expected=p.saveWithoutId(new CompoundTag());
        server.getPlayerList().saveAll();
        Path disk=server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat");
        if(!Files.isRegularFile(disk,LinkOption.NOFOLLOW_LINKS)){tr.kapisma.Kapisma.LOG.warn("Arena return player file missing: {}",p.getUUID());return false;}
        CompoundTag actual=NbtIo.readCompressed(disk,NbtAccounter.create(8L*1024*1024));
        for(String key:List.of("Inventory","EnderItems","Dimension","SpawnDimension","SpawnX","SpawnY","SpawnZ","SpawnAngle","SpawnForced","playerGameType","Pos")){
            if(!Objects.equals(expected.get(key),actual.get(key))){tr.kapisma.Kapisma.LOG.warn("Arena return persisted field mismatch: {} {}",p.getUUID(),key);return false;}
        }
        return j.acknowledge(p.getUUID(),match);
    }
    /** Called after join completes, before ordinary lobby reset. No active match may be superseded. */
    public static boolean recoverOrLobby(MinecraftServer server,ServerPlayer p){
        try{
            if(!unresolved(server,p.getUUID())){Lobby.send(server,p);return true;}
            var entry=journal(server).get(p.getUUID()).orElseThrow();
            boolean recovered=returnPlayer(server,p,entry.match());
            if(recovered)tr.kapisma.Msg.tell(p,"Önceki arena oturumunun güvenli lobi dönüşü tamamlandı. Yarım kalan maç yeniden başlatılmalıdır.");
            return recovered;
        }catch(IOException|RuntimeException ex){tr.kapisma.Kapisma.LOG.error("Arena return recovery failed for {}",p.getUUID(),ex);return false;}
    }
}