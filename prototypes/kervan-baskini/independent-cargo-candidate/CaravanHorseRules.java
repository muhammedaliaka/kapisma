package tr.kapisma.arena;
import tr.kapisma.arena.MatchRoster.Position;
/** Match-local bounds, not Minecraft's larger square world border. */
public final class CaravanHorseRules {
    private CaravanHorseRules(){}
    public static boolean inside(double x,double y,double z,double halfWidth,double yMin,double yMax,double xRadius,double zRadius){
        return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&
            Double.isFinite(halfWidth)&&halfWidth>=0&&Math.abs(x)+halfWidth<=xRadius&&
            Math.abs(z)+halfWidth<=zRadius&&y>=yMin&&y<=yMax;
    }
    /** Wagon driver may interact to dismount; other mounted actors cannot use cargo/seat targets. */
    public static boolean canInteract(boolean passenger,boolean wagonSeat){return !passenger||wagonSeat;}
}
