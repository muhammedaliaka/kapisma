package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;
public final class ArenaRulesTest {
    private static int passed;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    private static void rejects(Runnable r,String label){try{r.run();}catch(IllegalArgumentException e){check(true,label);return;}throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        UUID m=UUID.randomUUID(),r=UUID.randomUUID(),b=UUID.randomUUID(),outsider=UUID.randomUUID();
        Position p0=new Position(-24,1,0),p1=new Position(0,1,0),p2=new Position(24,1,0),away=new Position(0,1,11);
        MatchRoster roster=new MatchRoster(m,List.of(r),List.of(b));
        rejects(()->new MatchRoster(m,List.of(r),List.of(r)),"duplicate roster");
        rejects(()->new MatchRoster(m,List.of(r),List.of()),"empty team");
        rejects(()->new Position(Double.NaN,1,2),"invalid coordinate");
        for(int n=2;n<=8;n++){
            var a=new ArrayList<UUID>();var c=new ArrayList<UUID>();
            for(int i=0;i<n;i++)(i%2==0?a:c).add(UUID.randomUUID());
            check(new MatchRoster(m,a,c).members().size()==n,"capacity "+n);
        }
        var red=new Presence(r,m,true,true,p0);
        var blueAway=new Presence(b,m,true,true,away);
        var blue=new Presence(b,m,true,true,p0);
        var foreign=new Presence(outsider,m,true,true,p0);
        var wrong=new Presence(b,UUID.randomUUID(),true,true,p0);
        var spectator=new Presence(b,m,true,false,p0);
        var otherWorld=new Presence(b,m,false,true,p0);
        check(roster.active(List.of(red,red,blue)).size()==1,"duplicate samples fail closed");
        check(roster.active(List.of(foreign,wrong,spectator,otherWorld)).isEmpty(),"match world spectator identity filter");
        check(!roster.canDamage(r,r,true,true)&&!roster.canDamage(r,outsider,true,true)&&!roster.canDamage(r,b,false,true)&&!roster.canDamage(r,b,true,false)&&roster.canDamage(r,b,true,true),"damage isolation");
        var game=new OutpostRules(roster,List.of(p0,p1,p2),30,5,40);
        var before=game.snapshot();
        for(int i=0;i<80;i++)game.tick(List.of(red,wrong,foreign));
        check(game.snapshot().equals(before),"no clock or score while missing side");
        for(int i=0;i<20;i++)game.tick(List.of(red,blue));
        check(game.snapshot().red()==0&&game.snapshot().blue()==0,"contested no score");
        for(int i=0;i<19;i++)game.tick(List.of(red,blueAway));
        check(game.snapshot().red()==0,"no premature point");
        game.tick(List.of(red,blueAway));
        check(game.snapshot().red()==1,"one point per 20 ticks");
        int warnings=0,rotations=0;
        for(int i=40;i<600;i++){
            var update=game.tick(List.of(red,blueAway));
            if(update.warning())warnings++;if(update.rotated())rotations++;
        }
        check(warnings==1&&rotations==1&&game.snapshot().point()==1&&game.snapshot().leftTicks()==600&&game.snapshot().red()==29,"natural 30s rotation and warning");
        var saved=game.snapshot();
        var restored=new OutpostRules(roster,List.of(p0,p1,p2),30,5,40);
        restored.restore(saved);check(restored.snapshot().equals(saved),"snapshot roundtrip");
        rejects(()->restored.restore(new OutpostRules.State(UUID.randomUUID(),1,600,29,0,0,null)),"wrong match checkpoint");
        rejects(()->restored.restore(new OutpostRules.State(m,1,600,40,0,0,null)),"inconsistent winner checkpoint");
        var red2=new Presence(r,m,true,true,p1);
        for(int i=0;i<220;i++)restored.tick(List.of(red2,blueAway));
        check(restored.snapshot().winner()==Side.RED&&restored.snapshot().red()==40,"natural victory");
        saved=restored.snapshot();
        for(int i=0;i<60;i++)restored.tick(List.of(blue,red));
        check(restored.snapshot().equals(saved),"terminal state immutable");
        var crossThread=new java.util.concurrent.atomic.AtomicBoolean();
        Thread t=new Thread(()->{try{restored.tick(List.of(red,blue));}catch(IllegalStateException e){crossThread.set(true);}});
        t.start();t.join();check(crossThread.get(),"server-thread confinement");
        System.out.println("PASS total "+passed);
    }
}
