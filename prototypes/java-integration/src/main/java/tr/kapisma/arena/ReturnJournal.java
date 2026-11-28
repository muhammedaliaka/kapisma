package tr.kapisma.arena;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small durable return ledger. Never contains inventory or secrets; written before entering a temporary dimension. */
public final class ReturnJournal {
    public record Entry(UUID match,String dimension,Integer x,Integer y,Integer z,float angle,boolean forced){
        public Entry{
            Objects.requireNonNull(match);Objects.requireNonNull(dimension);
            if(!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||dimension.length()>256||!Float.isFinite(angle)||
                ((x==null)!=(y==null))||((x==null)!=(z==null))||
                (x!=null&&(Math.abs((long)x)>30_000_000||Math.abs((long)z)>30_000_000||y< -2048||y>2048)))
                throw new IllegalArgumentException("invalid respawn");
        }
    }
    private final Path file,stage;
    private Map<UUID,Entry> entries;
    public ReturnJournal(Path file)throws IOException{
        this.file=file.toAbsolutePath().normalize();
        this.stage=this.file.resolveSibling(this.file.getFileName()+".pending");
        guard(this.file);guard(stage);
        entries=read();
    }
    private static void guard(Path p)throws IOException{
        if(Files.isSymbolicLink(p)||Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("unsafe journal path");
    }
    private Map<UUID,Entry> read()throws IOException{
        if(!Files.exists(file))return new LinkedHashMap<>();
        if(Files.size(file)>128*1024)throw new IOException("journal too large");
        var map=new LinkedHashMap<UUID,Entry>();
        List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);
        if(lines.isEmpty()||!lines.getFirst().equals("KAPISMA_ARENA_RETURN_V1"))throw new IOException("unknown journal format");
        try{
            for(var line:lines.subList(1,lines.size())){
                var a=line.split("\\|",-1);if(a.length!=8)throw new IllegalArgumentException("entry shape");
                UUID id=UUID.fromString(a[0]),match=UUID.fromString(a[1]);
                if(!id.toString().equals(a[0])||!match.toString().equals(a[1]))throw new IllegalArgumentException("noncanonical uuid");
                if(!a[7].equals("true")&&!a[7].equals("false"))throw new IllegalArgumentException("bool");
                Entry e=new Entry(match,a[2],num(a[3]),num(a[4]),num(a[5]),Float.parseFloat(a[6]),Boolean.parseBoolean(a[7]));
                if(map.putIfAbsent(id,e)!=null)throw new IllegalArgumentException("duplicate uuid");
                if(map.size()>512)throw new IllegalArgumentException("too many returns");
            }
        }catch(IllegalArgumentException ex){throw new IOException("corrupt arena journal",ex);}
        return map;
    }
    private static Integer num(String s){return s.isEmpty()?null:Integer.valueOf(s);}
    private static String n(Integer v){return v==null?"":v.toString();}
    public synchronized Optional<Entry> get(UUID actor){return Optional.ofNullable(entries.get(actor));}
    public synchronized int size(){return entries.size();}
    public synchronized void remember(UUID actor,Entry entry)throws IOException{
        rememberAll(Map.of(actor,entry));
    }
    /** Validate the entire batch before touching disk. No partially journaled team. */
    public synchronized void rememberAll(Map<UUID,Entry> additions)throws IOException{
        Objects.requireNonNull(additions);
        var next=new LinkedHashMap<>(entries);
        for(var row:additions.entrySet()){
            UUID actor=Objects.requireNonNull(row.getKey());Entry entry=Objects.requireNonNull(row.getValue());
            Entry old=next.putIfAbsent(actor,entry);
            if(old!=null&&!old.equals(entry))throw new IOException("unresolved prior arena return");
        }
        if(next.size()>512)throw new IOException("too many pending arena returns");
        if(!next.equals(entries))commit(next);
    }
    public synchronized boolean acknowledge(UUID actor,UUID match)throws IOException{
        Entry e=entries.get(actor);if(e==null||!e.match().equals(match))return false;
        var next=new LinkedHashMap<>(entries);next.remove(actor);commit(next);return true;
    }
    private void commit(Map<UUID,Entry> next)throws IOException{
        guard(file);guard(stage);
        StringBuilder data=new StringBuilder("KAPISMA_ARENA_RETURN_V1\n");
        next.forEach((id,e)->data.append(id).append('|').append(e.match()).append('|').append(e.dimension()).append('|')
            .append(n(e.x())).append('|').append(n(e.y())).append('|').append(n(e.z())).append('|').append(e.angle()).append('|').append(e.forced()).append('\n'));
        if(data.toString().getBytes(StandardCharsets.UTF_8).length>128*1024)throw new IOException("journal exceeds readable size");
        try(var channel=FileChannel.open(stage,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer b=StandardCharsets.UTF_8.encode(data.toString());while(b.hasRemaining())channel.write(b);channel.force(true);
        }
        Files.move(stage,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        entries=next;
    }
}