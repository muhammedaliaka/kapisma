package tr.kapisma.arena;
import tr.kapisma.arena.MatchRoster.Position;
public final class CaravanHorseRulesTest {
    private static int n;
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);n++;}
    private static boolean in(double x,double y,double z){return CaravanHorseRules.inside(x,y,z,.7,300.5,313,36,24);}
    public static void main(String[] args){
        check(in(0,301,0),"center");check(in(35.3,301,23.3),"edge");
        check(!in(35.4,301,0),"width");check(!in(0,301,-23.4),"depth");
        check(!in(0,300.49,0),"fall");check(!in(0,313.01,0),"above");
        check(!in(Double.NaN,301,0),"NaN");check(!in(0,Double.POSITIVE_INFINITY,0),"infinite");
        check(CaravanHorseRules.canInteract(false,false),"walking");check(!CaravanHorseRules.canInteract(true,false),"horse mounted");check(CaravanHorseRules.canInteract(true,true),"wagon seat can dismount");check(CaravanHorseRules.canInteract(false,true),"unmounted");
        System.out.println("CARAVAN_HORSE "+n+"/"+n+" PASS");
    }
}
