package kapisma.packetqa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_310;
import net.minecraft.class_1713;
import java.nio.file.*;
/** Synthetic local QA only, ordinary vanilla inventory click method. */
public final class InventoryClientQa implements ClientModInitializer {
    private int last,delay;
    private Path trigger;
    public void onInitializeClient(){
        String f=System.getProperty("kapisma.packetqa.trigger");
        if(f!=null){trigger=Path.of(f);ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    }
    private void tick(class_310 c){
        var p=c.field_1724;
        if(p==null||c.field_1761==null||p.field_7512!=p.field_7498)return;
        var m=p.field_7512;
        if(delay>0&&--delay==0){
            System.out.println("PACKETQA_STATE "+last+" slot="+m.method_7611(36).method_7677().method_7947()+" cursor="+m.method_34255().method_7947());
        }
        if(delay>0||!Files.isRegularFile(trigger))return;
        try{
            int step=Integer.parseInt(Files.readString(trigger).trim());
            if(step!=last+1||step>6)return;
            int slot=step==2||step==6?-999:36;
            var type=step==4?class_1713.field_7795:class_1713.field_7790;
            c.field_1761.method_2906(m.field_7763,slot,step==4?1:0,type,p);
            last=step;delay=30;
            System.out.println("PACKETQA_SENT "+step);
        }catch(Exception e){System.out.println("PACKETQA_ERROR "+e);}
    }
}
