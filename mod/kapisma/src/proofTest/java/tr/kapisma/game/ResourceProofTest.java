package tr.kapisma.game;

import java.util.List;
import java.util.Map;

public final class ResourceProofTest {
	private static ResourceProof.Step<String> raw(String item) {
		return node(item, 1, true, List.of(), null, null, 0, null, 0, 0);
	}
	private static ResourceProof.Step<String> node(String item, int yield, boolean raw,
			List<ResourceProof.Input<String>> inputs, ResourceProof.Step<String> station,
			ResourceProof.Step<String> tool, int durability, ResourceProof.Step<String> fuel, int cook, int burn) {
		return new ResourceProof.Step<>(item, yield, raw, inputs, station, tool, durability, fuel, cook, burn);
	}
	private static ResourceProof.Input<String> need(ResourceProof.Step<String> step, int n) {
		return new ResourceProof.Input<>(step, n);
	}
	private static void check(String name, boolean expected, ResourceProof.Step<String> step, int n, Map<String,Integer> raw) {
		boolean result = ResourceProof.proves(step, n, raw);
		if (result != expected) throw new AssertionError(name + ": expected " + expected + ", got " + result);
		System.out.println("PASS " + name);
	}
	public static void main(String[] args) {
		var log = raw("log");
		var plank = node("plank", 4, false, List.of(need(log,1)), null,null,0,null,0,0);
		var stick = node("stick",4,false,List.of(need(plank,2)),null,null,0,null,0,0);
		var table = node("table",1,false,List.of(need(plank,4)),null,null,0,null,0,0);
		var pick = node("pick",1,false,List.of(need(plank,3),need(stick,2)),table,null,0,null,0,0);
		check("one log cannot pay table+pick+sticks",false,pick,1,Map.of("log",1));
		check("two logs still insufficient",false,pick,1,Map.of("log",2));
		check("three logs sufficient",true,pick,1,Map.of("log",3));
		check("two logs cannot pay two picks",false,pick,2,Map.of("log",2));
		check("three logs pay two picks and one reusable table",true,pick,2,Map.of("log",3));
		var stone = node("stone",1,true,List.of(),null,pick,2,null,0,0);
		check("tool durability exhausted",false,stone,5,Map.of("log",4,"stone",5));
		check("replacement tools available",true,stone,5,Map.of("log",5,"stone",5));
		var ore = raw("ore");
		var fuel = raw("fuel");
		var furnace = raw("furnace");
		var ingot = node("ingot",1,false,List.of(need(ore,1)),furnace,null,0,fuel,200,100);
		check("one stick worth 100 ticks insufficient",false,ingot,1,Map.of("ore",1,"furnace",1,"fuel",1));
		check("two fuel units sufficient",true,ingot,1,Map.of("ore",1,"furnace",1,"fuel",2));
		check("fuel quantity for two ingots",false,ingot,2,Map.of("ore",2,"furnace",1,"fuel",3));
		check("station reused for two ingots",true,ingot,2,Map.of("ore",2,"furnace",1,"fuel",4));
		var bucket = raw("bucket");
		var lava = node("lava",1,true,List.of(need(bucket,1)),null,null,0,null,0,0);
		check("single lava source not two buckets",false,lava,2,Map.of("lava",1,"bucket",2));
		check("one lava bucket",true,lava,1,Map.of("lava",1,"bucket",1));
		// Each target is possible alone, but both cannot spend the same ore.
		var one = new ResourceProof.Ledger<String>(Map.of("ore",1,"furnace",1,"fuel",2));
		check("first target possible alone",true,ingot,1,Map.of("ore",1,"furnace",1,"fuel",2));
		if (!one.reserve(List.of(need(ingot,1)))) throw new AssertionError("first card reservation");
		if (one.reserve(List.of(need(ingot,1)))) throw new AssertionError("double-spent ore");
		System.out.println("PASS shared card ore cannot be spent twice");
		// Failed composite reservation rolls back raw, stock, station and wear.
		var two = new ResourceProof.Ledger<String>(Map.of("log",3,"stone",2));
		if (two.reserve(List.of(need(stone,2),need(ore,1)))) throw new AssertionError("impossible composite");
		if (!two.reserve(List.of(need(stone,2)))) throw new AssertionError("failed reservation leaked material or wear");
		System.out.println("PASS atomic composite rollback");
		var three = new ResourceProof.Ledger<String>(Map.of("log",1));
		if (!three.reserve(List.of(need(plank,1))) || !three.reserve(List.of(need(plank,3)))
				|| three.reserve(List.of(need(plank,1)))) throw new AssertionError("leftover ledger");
		System.out.println("PASS leftover shared across targets");
		var four = new ResourceProof.Ledger<String>(Map.of("ore",2,"furnace",1,"fuel",4));
		if (!four.reserve(List.of(need(ingot,1))) || !four.reserve(List.of(need(ingot,1))))
			throw new AssertionError("reusable station ledger");
		System.out.println("PASS station reuse across targets");
		var five = new ResourceProof.Ledger<String>(Map.of("lava",1,"bucket",2));
		if (five.reserve(List.of(need(lava,2))) || !five.reserve(List.of(need(lava,1))))
			throw new AssertionError("team quantity/rollback");
		System.out.println("PASS shared team quantity and rollback");
		var six = new ResourceProof.Ledger<String>(Map.of("log",1));
		var branch = six.fork();
		if (!branch.reserve(List.of(need(plank,4))) || !six.reserve(List.of(need(plank,4))))
			throw new AssertionError("independent search branches");
		System.out.println("PASS independent budget search branches");
		var seven = new ResourceProof.Ledger<String>(Map.of("log",4));
		double cheap = seven.scarcity(List.of(need(plank,1)));
		double costly = seven.scarcity(List.of(need(plank,8)));
		if (!(cheap < costly) || !seven.reserve(List.of(need(plank,16))))
			throw new AssertionError("scarcity must not mutate source");
		System.out.println("PASS scarcity ranking is read-only");
		// The acquired raw item can be credited and then consumed by another cell.
		var observed = new ResourceProof.Ledger<String>(Map.of("ore",1,"furnace",1,"fuel",2));
		if (!observed.observe(List.of(need(ore,1))) || !observed.observe(List.of(need(ingot,1))))
			throw new AssertionError("observed item should remain usable");
		System.out.println("PASS acquired material reused after scoring");
		var other = node("other",1,false,List.of(need(ore,1)),null,null,0,null,0,0);
		if (observed.observe(List.of(need(other,1))))
			throw new AssertionError("observation must not resurrect consumed material");
		System.out.println("PASS acquisition does not duplicate consumed input");
		var observedTeams = new ResourceProof.Ledger<String>(Map.of("ore",1));
		if (observedTeams.observe(List.of(need(ore,2))) || !observedTeams.observe(List.of(need(ore,1))))
			throw new AssertionError("team count and atomic observation");
		System.out.println("PASS observation team count and rollback");
		System.out.println("RESOURCE PROOF 24/24 PASS");
	}
}
