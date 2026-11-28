package tr.kapisma.arena;
import java.util.UUID;
/** Server-thread fork edge trigger. Minecraft xxa positive=left (SAFE), negative=right (RISKY). */
public final class CaravanForkInput {
    private UUID driver;private int leg=-1;private boolean armed;
    public CaravanRouteRules.Route accept(int currentLeg,UUID actor,UUID currentDriver,boolean waiting,boolean seated,float sideways,boolean shift){
        if(actor==null||!actor.equals(currentDriver)||!seated)return null;
        if(!waiting||shift){clear();return null;}
        if(leg!=currentLeg||!actor.equals(driver)){driver=actor;leg=currentLeg;armed=false;}
        if(!Float.isFinite(sideways)||Math.abs(sideways)>1){armed=false;return null;}
        if(Math.abs(sideways)<=.1f){armed=true;return null;}
        if(!armed||Math.abs(sideways)<.5f)return null;
        armed=false;return sideways>0?CaravanRouteRules.Route.SAFE:CaravanRouteRules.Route.RISKY;
    }
    public void clear(){driver=null;leg=-1;armed=false;}
}
