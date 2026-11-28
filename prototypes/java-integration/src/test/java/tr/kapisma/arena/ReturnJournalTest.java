package tr.kapisma.arena;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
public final class ReturnJournalTest {
    private static int passed;
    private static void ok(boolean result,String label){if(!result)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    private static void fails(IO body,String label)throws Exception{try{body.run();}catch(IOException e){ok(true,label);return;}throw new AssertionError(label);}
    interface IO{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]).toAbsolutePath();Files.createDirectories(root);
        Path file=root.resolve("return-journal-fixture.v1");
        // Only the dedicated, reusable proof fixture; never touch player data.
        Files.writeString(file,"KAPISMA_ARENA_RETURN_V1\n");
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),m=UUID.randomUUID(),n=UUID.randomUUID();
        var j=new ReturnJournal(file);
        ok(j.size()==0,"empty journal");
        var e=new ReturnJournal.Entry(m,"minecraft:overworld",20,64,-12,1.25f,true);
        var f=new ReturnJournal.Entry(n,"minecraft:overworld",null,null,null,0,false);
        j.remember(a,e);j.remember(a,e);j.remember(b,f);
        var restored=new ReturnJournal(file);
        ok(restored.size()==2&&restored.get(a).orElseThrow().equals(e)&&restored.get(b).orElseThrow().equals(f),"durable two entries reload");
        fails(()->restored.remember(a,f),"new match cannot overwrite unresolved return");
        ok(!restored.acknowledge(a,n)&&restored.size()==2,"wrong match cannot clear");
        ok(restored.acknowledge(a,m)&&new ReturnJournal(file).get(a).isEmpty(),"ack durable");
        ok(new ReturnJournal(file).get(b).orElseThrow().equals(f),"offline entry retained");
        ok(!restored.acknowledge(a,m),"repeat ack harmless");
        restored.acknowledge(b,n);
        ok(new ReturnJournal(file).size()==0,"empty after final ack");
        Files.writeString(file,"bad\n");
        fails(()->new ReturnJournal(file),"corruption fail closed");
        Files.writeString(file,"KAPISMA_ARENA_RETURN_V1\n"+a+"|"+m+"|minecraft:overworld|20|64|-12|1.25|maybe\n");
        fails(()->new ReturnJournal(file),"invalid bool rejected");
        Files.writeString(file,"KAPISMA_ARENA_RETURN_V1\n");
        Files.writeString(file.resolveSibling(file.getFileName()+".pending"),"interrupted write");
        ok(new ReturnJournal(file).size()==0,"uncommitted stage cannot replace committed ledger");
        var recovered=new ReturnJournal(file);recovered.remember(a,e);
        ok(new ReturnJournal(file).get(a).orElseThrow().equals(e),"interrupted stage safely superseded");
        recovered.acknowledge(a,m);
        var batch=new ReturnJournal(file);
        batch.rememberAll(Map.of(a,e,b,f));
        ok(new ReturnJournal(file).size()==2,"batch committed together");
        var c=UUID.randomUUID();
        var before=Files.readAllBytes(file);
        var conflict=new LinkedHashMap<UUID,ReturnJournal.Entry>();conflict.put(c,e);conflict.put(a,f);
        fails(()->batch.rememberAll(conflict),"batch conflict rejected atomically");
        ok(batch.get(c).isEmpty()&&Arrays.equals(before,Files.readAllBytes(file)),"no partial batch persisted");
        batch.rememberAll(Map.of(a,e,b,f));
        ok(Arrays.equals(before,Files.readAllBytes(file)),"idempotent batch");
        batch.acknowledge(a,m);batch.acknowledge(b,n);
        Files.writeString(file,"KAPISMA_ARENA_RETURN_V1\n1-1-1-1-1|"+m+"|minecraft:overworld||||0.0|false\n");
        fails(()->new ReturnJournal(file),"noncanonical uuid rejected");
        Files.writeString(file,"KAPISMA_ARENA_RETURN_V1\n");
        var oversized=new LinkedHashMap<UUID,ReturnJournal.Entry>();
        for(int i=0;i<513;i++)oversized.put(UUID.randomUUID(),e);
        var empty=new ReturnJournal(file);
        fails(()->empty.rememberAll(oversized),"oversized batch rejected");
        ok(empty.size()==0&&new ReturnJournal(file).size()==0,"oversized batch leaves journal untouched");
        System.out.println("PASS total "+passed);
    }
}