package kapisma.flightqa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_2848;
import net.minecraft.class_310;
import net.minecraft.class_1268;

/** Local test-only controls. No custom movement packets, no position writes or teleport. */
public final class FlightClientQa implements ClientModInitializer {
    private int cooldown,ticks,gate,boostCooldown;
    private boolean wasFlying;
    private double oldX=Double.NaN;
    private static final double[][] GATES={{-30,288,0},{0,281,12},{28,274,-8},{52,267,0}};
    public void onInitializeClient() {
        if(Boolean.getBoolean("ai.qa.enabled"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(class_310 client) {
        var p=client.field_1724;
        if(p==null || client.field_1687==null)return;
        boolean flying=p.method_6128();
        if(flying&&!wasFlying)System.out.println("AIQA_NATIVE_GLIDE");
        wasFlying=flying;
        if(Boolean.getBoolean("ai.qa.walk")){
            // Actual vanilla key state; bounded input for synthetic local players only.
            int duration=Integer.getInteger("ai.qa.walkTicks",1200);
            String trigger=System.getProperty("ai.qa.walkTrigger","");
            if(!trigger.isEmpty()&&!java.nio.file.Files.isRegularFile(java.nio.file.Path.of(trigger))){
                client.field_1690.field_1894.method_23481(false);return;
            }
            if(ticks++<Math.min(2400,duration)){
                p.method_36456(Float.parseFloat(System.getProperty("ai.qa.yaw","-90")));
                client.field_1690.field_1894.method_23481(true);
            }else client.field_1690.field_1894.method_23481(false);
            if(ticks%20==0)System.out.printf(java.util.Locale.ROOT,"AIQA_WALK %d %.3f %.3f %.3f%n",ticks,p.method_23317(),p.method_23318(),p.method_23321());
            return;
        }
        boolean auto=Boolean.getBoolean("ai.qa.autoflight");
        double x=p.method_23317(),y=p.method_23318(),z=p.method_23321();
        if(auto){
            // Only the known synthetic arena altitude. Lobbies are never driven.
            if(y<245||y>310){client.field_1690.field_1894.method_23481(false);oldX=Double.NaN;gate=0;return;}
            if(x< -47.4&&y>294&&!flying){gate=0;p.method_36456(-90);p.method_36457(0);client.field_1690.field_1894.method_23481(true);}
            else client.field_1690.field_1894.method_23481(false);
            if(gate<4 && flying){
                var g=GATES[gate];
                if(oldX<g[0]&&x>=g[0]&&Math.abs(y-g[1])<=5&&Math.abs(z-g[2])<=5){
                    System.out.printf(java.util.Locale.ROOT,"AIQA_LOCAL_GATE %d %.3f %.3f %.3f%n",++gate,x,y,z);
                }
                if(gate<4){
                    g=GATES[gate];
                    double dx=g[0]-x, dz=g[2]-z, dy=g[1]-y;
                    var vel=p.method_18798();
                    // Look toward the next opening; lead lateral velocity to avoid overshoot.
                    double lead=Math.min(5,Math.max(0,Math.hypot(dx,dz)*.18));
                    dx-=vel.field_1352*lead;dz-=vel.field_1350*lead;
                    float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
                    float pitch=(float)Math.toDegrees(Math.atan2(-dy,Math.max(2,Math.hypot(dx,dz))));
                    p.method_36456(yaw);p.method_36457(Math.max(-35,Math.min(45,pitch)));
                    if(boostCooldown>0)boostCooldown--;
                    // Use the ordinary client interactItem path with the supplied hotbar rocket.
                    if(boostCooldown==0&&dx>10&&Math.hypot(vel.field_1352,vel.field_1350)<.65&&client.field_1761!=null){
                        client.field_1761.method_2919(p,class_1268.field_5808);
                        boostCooldown=35;System.out.println("AIQA_NATIVE_ROCKET");
                    }
                }
            }
            if(++ticks%10==0)System.out.printf(java.util.Locale.ROOT,"AIQA_FLIGHT %d gate=%d glide=%s %.3f %.3f %.3f%n",ticks,gate,flying,x,y,z);
            oldX=x;
        }
        if(cooldown>0){cooldown--;return;}
        if(!p.method_24828()&&!flying && (auto?y>245&&y<297&&x> -50:p.method_23318()<-15)) {
            p.field_3944.method_52787(new class_2848(p,class_2848.class_2849.field_12982));
            System.out.println("AIQA_START_FALL_FLYING_PACKET");
            cooldown=5;
        }
    }
}