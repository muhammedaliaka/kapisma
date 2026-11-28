package kapisma.caravanqa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_310;
import java.nio.file.*;
/** Local synthetic client. Only ordinary vanilla key state; no custom packets or position edits. */
public final class CaravanClientQa implements ClientModInitializer {
    private Path trigger;private String last="";private int ticks,mapTicks,stillTicks;
    public void onInitializeClient(){
        String f=System.getProperty("kapisma.caravanqa.trigger");
        if(f!=null){trigger=Path.of(f);ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    }
    private void tick(class_310 c){
        var p=c.field_1724;if(p==null||c.field_1687==null)return;
        String control="";
        try{if(Files.isRegularFile(trigger))control=Files.readString(trigger).trim();}catch(Exception ignored){}
        // Select the actual issued map by ordinary hotbar state; read client-side network-delivered pixels.
        var inventory=p.method_31548();
        var stack=inventory.method_5438(4);
        var id=stack.method_57824(net.minecraft.class_9334.field_49646);
        if(id!=null&&!control.isEmpty()){
            inventory.field_7545=control.equals("shot-wagon")?0:4;
            if(++mapTicks%10==0){
                var map=net.minecraft.class_1806.method_8001(stack,c.field_1687);
                if(map!=null)System.out.println("CARAVAN_CLIENT_MAP "+id.comp_2315()+" "+java.util.Arrays.hashCode(map.field_122)+" "+map.field_122.length);
            }
        }
        if(!control.equals(last))stillTicks=0;else stillTicks++;
        // Capture only this synthetic game's render target, never a desktop/window capture.
        if(control.matches("shot-[a-z0-9-]{1,40}")&&stillTicks==30){
            String name=trigger.getFileName().toString()+"-"+control+".png";
            net.minecraft.class_318.method_22690(trigger.getParent().toFile(),name,c.method_1522(),msg->System.out.println("CARAVAN_SCREEN "+name));
        }
        boolean horse=control.matches("horse:-?[0-9]{1,3}");
        boolean valid=control.equals("W")||control.equals("WA")||control.equals("WD")||horse;
        c.field_1690.field_1894.method_23481(valid);
        c.field_1690.field_1913.method_23481(valid&&control.equals("WA"));
        c.field_1690.field_1849.method_23481(valid&&control.equals("WD"));
        if(valid)p.method_36456(horse?Float.parseFloat(control.substring(6)):-90);
        if(!control.equals(last)){last=control;System.out.println("CARAVAN_CLIENT_KEYS "+control);}
        if(valid&&++ticks%20==0)System.out.printf(java.util.Locale.ROOT,"CARAVAN_CLIENT_POSITION %d %.3f %.3f %.3f%n",ticks,p.method_23317(),p.method_23318(),p.method_23321());
    }
}
