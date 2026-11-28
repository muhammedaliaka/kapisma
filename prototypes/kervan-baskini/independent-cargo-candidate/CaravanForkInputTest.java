package tr.kapisma.arena;
import java.util.UUID;
import static tr.kapisma.arena.CaravanRouteRules.Route.*;
public final class CaravanForkInputTest {
    static final UUID A=new UUID(0,1),B=new UUID(0,2);
    static int passed;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    public static void main(String[] args){
        var f=new CaravanForkInput();
        check(f.accept(1,A,A,false,true,1,false)==null,"mid-edge ignored");
        check(f.accept(1,A,A,true,true,1,false)==null,"held input before junction ignored");
        check(f.accept(1,A,A,true,true,0,false)==null,"neutral arms without selection");
        check(f.accept(1,B,A,true,true,-1,false)==null,"foreign input rejected");
        check(f.accept(1,A,A,true,false,-1,false)==null,"unseated actor rejected");
        check(f.accept(1,A,A,true,true,1,false)==SAFE,"left chooses safe");
        check(f.accept(1,A,A,true,true,-1,false)==null,"one selection per fresh press");
        f.clear();
        f.accept(1,A,A,true,true,0,false);
        check(f.accept(1,A,A,true,true,-1,false)==RISKY,"right chooses risky");
        f.accept(1,A,A,true,true,0,false);
        check(f.accept(1,A,A,true,true,Float.NaN,false)==null&&f.accept(1,A,A,true,true,1,false)==null,"NaN disarms");
        f.accept(1,A,A,true,true,0,false);
        check(f.accept(1,A,A,true,true,2,false)==null&&f.accept(1,A,A,true,true,-1,false)==null,"out of range disarms");
        f.accept(1,A,A,true,true,0,false);
        check(f.accept(1,A,A,true,true,1,true)==null&&f.accept(1,A,A,true,true,1,false)==null,"shift cancels armed state");
        f.accept(1,A,A,true,true,0,false);
        check(f.accept(2,A,A,true,true,-1,false)==null,"new leg requires neutral");
        f.accept(2,A,A,true,true,0,false);
        check(f.accept(2,B,B,true,true,-1,false)==null,"new driver requires neutral");
        f.accept(2,B,B,true,true,0,false);
        check(f.accept(2,B,B,true,true,.3f,false)==null&&f.accept(2,B,B,true,true,-1,false)==RISKY,"small analog drift ignored");
        f.accept(2,B,B,true,true,0,false);f.clear();
        check(f.accept(2,B,B,true,true,-1,false)==null,"same driver remount after lifecycle clear requires neutral");
        f.accept(2,B,B,true,true,0,false);
        check(f.accept(2,B,B,true,true,-1,false)==RISKY,"fresh press after remount accepted");
        System.out.println("CARAVAN_FORK "+passed+"/"+passed+" PASS");
    }
}
