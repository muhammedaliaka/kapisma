package kapisma.rts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.class_1263;
import net.minecraft.class_1297;
import net.minecraft.class_1646;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2302;
import net.minecraft.class_2338;
import net.minecraft.class_3218;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;

/** A civilian farmer tends wheat, stocks bread and makes short trips to resting troops. */
final class RtsSupply {
    private static final int BREAD_LIMIT = 8;
    private static final int WHEAT_PER_BREAD = 3;
    private static final int RATION_INTERVAL = 15;
    private static final int LAST_HIT_GRACE = 8;
    private static final int MUNICIPAL_INTERVAL = 5;
    private static final int[][] FIELD_LANES = {{-1, 0}, {1, 0}, {0, -1}};
    private static final class_2302 WHEAT = (class_2302) class_2246.field_10293;

    private RtsSupply() {}

    static void field(class_3218 world, int slot, class_1646 farmer, class_1297 anchor,
                      UUID owner, int required, List<BaseCombatEntity> troops) {
        if (farmer == null) return;
        if (owner == null || anchor == null || RtsContext.score("krts_post_connected", anchor) != 1) {
            RtsAutonomy.stop(farmer);
            return;
        }
        farmer.method_5977(false);
        var depot = anchor.method_24515();
        if (troops.stream().anyMatch(unit -> RtsAutonomy.threatened(farmer, owner, unit))) {
            RtsAutonomy.walk(farmer, depot);
            return;
        }
        int bread = RtsAutonomy.control("#bread", slot);
        if (bread >= BREAD_LIMIT) {
            RtsAutonomy.stop(farmer);
            return;
        }
        int progress = RtsContext.score("krts_field_work", anchor);
        if (progress >= required) {
            if (RtsAutonomy.walk(farmer, depot)) {
                RtsAutonomy.setControl("#bread", slot, bread + 1);
                RtsContext.setScore("krts_field_work", anchor, 0);
                showGrowth(world, slot, anchor, 0);
            }
            return;
        }
        var benches = fieldBenches(new RtsTerrain.Survey(world), depot);
        for (var bench : benches) {
            if (RtsAutonomy.near(farmer, bench, 0.85)) {
                farmer.method_5942().method_6340();
                farmer.method_6104(net.minecraft.class_1268.field_5808);
                int next = progress + 1;
                RtsContext.setScore("krts_field_work", anchor, next);
                int age = next * 7 / required;
                if (age != progress * 7 / required) showGrowth(world, slot, anchor, age);
                return;
            }
        }
        for (var bench : benches) {
            var navigation = farmer.method_5942();
            var path = navigation.method_6348(bench, 0);
            if (path != null && path.method_21655() && navigation.method_6334(path, 0.85)) return;
        }
        farmer.method_5942().method_6340();
    }

    static List<class_2338> fieldBenches(RtsTerrain.Survey survey, class_2338 depot) {
        var reachable = new ArrayList<class_2338>(FIELD_LANES.length);
        for (var lane : FIELD_LANES) {
            var step = surfaceAt(survey, depot, lane[0], lane[1]);
            var bench = surfaceAt(survey, depot, 2 * lane[0], 2 * lane[1]);
            if (Math.abs((long) step.method_10264() - depot.method_10264()) <= 1
                    && Math.abs((long) bench.method_10264() - step.method_10264()) <= 1
                    && survey.walkable(step) && survey.walkable(bench)) reachable.add(bench);
        }
        return reachable;
    }

    private static class_2338 surfaceAt(RtsTerrain.Survey survey, class_2338 depot, int dx, int dz) {
        int x = depot.method_10263() + dx;
        int z = depot.method_10260() + dz;
        return new class_2338(x, survey.surface(x, z), z);
    }

    private static void showGrowth(class_3218 world, int slot, class_1297 anchor, int age) {
        var pos = anchor.method_24515();
        RtsTerrain.run(world.method_8503(), ("execute in minecraft:overworld positioned %d %d %d "
                + "as @e[type=minecraft:block_display,tag=krts.field_crop,tag=krts.s%d,distance=..12] "
                + "run data modify entity @s block_state.Properties.age set value \"%d\"")
                .formatted(pos.method_10263(), pos.method_10264(), pos.method_10260(), slot, age));
    }

    static void municipalRations(int slot, UUID owner, class_2338 depot, List<BaseCombatEntity> troops) {
        if (owner == null || depot == null) return;
        int bread = RtsAutonomy.control("#bread", slot);
        int clock = RtsContext.score("krts_ctrl", "#left");
        int last = RtsAutonomy.control("#ration_at", slot);
        if (bread <= 0 || (last != 0 && last - clock < MUNICIPAL_INTERVAL)
                || troops.stream().anyMatch(unit -> RtsAutonomy.near(unit, depot, 12) && RtsAutonomy.hostile(owner, unit))) return;
        var patient = troops.stream().filter(unit -> owner.equals(unit.method_6139()) && RtsAutonomy.idle(unit))
                .filter(unit -> RtsAutonomy.near(unit, depot, 10) && unit.method_6063() - unit.method_6032() >= 2)
                .filter(unit -> elapsed(unit, "krts_ration", clock, RATION_INTERVAL)
                        && elapsed(unit, "krts_ration_hit", clock, LAST_HIT_GRACE))
                .max(Comparator.comparingDouble(unit -> unit.method_6063() - unit.method_6032()))
                .orElse(null);
        if (patient == null) return;
        RtsAutonomy.setControl("#bread", slot, bread - 1);
        RtsAutonomy.setControl("#ration_at", slot, clock);
        RtsContext.setScore("krts_ration", patient, clock);
        patient.method_6025(RtsAutonomy.hasResearch("#medicine", slot) ? 6 : 4);
    }

    private static boolean elapsed(BaseCombatEntity unit, String objective, int clock, int interval) {
        int marked = RtsContext.score(objective, unit);
        return marked == 0 || marked - clock >= interval;
    }

    static void work(class_3218 world, class_1646 farmer, UUID owner, class_2338 farm,
                     class_1263 depot, List<BaseCombatEntity> troops, int healing) {
        farmer.method_5977(false);
        if (troops.stream().anyMatch(unit -> RtsAutonomy.threatened(farmer, owner, unit))) {
            RtsAutonomy.walk(farmer, farm.method_10069(1, 0, -1));
            return;
        }
        var depotAccess = farm.method_10069(1, 0, 2);
        int clock = RtsContext.score("krts_ctrl", "#left");
        var patients = troops.stream().filter(unit -> owner.equals(unit.method_6139()) && RtsAutonomy.idle(unit))
                .filter(unit -> RtsAutonomy.near(unit, farm, 14) && unit.method_6063() - unit.method_6032() >= 2)
                .filter(unit -> {
                    int ration = RtsContext.score("krts_ration", unit);
                    return ration == 0 || ration - clock >= RATION_INTERVAL;
                })
                .sorted(Comparator.comparingDouble((BaseCombatEntity unit) -> unit.method_6063() - unit.method_6032()).reversed())
                .toList();

        if (RtsContext.score("krts_meal", farmer) == 1) {
            if (deliver(farmer, patients, clock, healing)) return;
            if (RtsAutonomy.walk(farmer, depotAccess) && RtsAutonomy.put(depot, 0, class_1802.field_8229, BREAD_LIMIT)) {
                RtsContext.setScore("krts_meal", farmer, 0);
            }
            return;
        }

        int bread = RtsAutonomy.count(depot, 0, class_1802.field_8229);
        if (bread > 0 && patients.stream().anyMatch(unit -> canReach(farmer, unit))) {
            if (RtsAutonomy.walk(farmer, depotAccess)) {
                RtsAutonomy.take(depot, 0);
                RtsContext.setScore("krts_meal", farmer, 1);
            }
            return;
        }
        if (bread >= BREAD_LIMIT) {
            farmer.method_5942().method_6340();
            return;
        }

        int wheat = RtsContext.score("krts_crop", farmer);
        if (wheat >= WHEAT_PER_BREAD) {
            if (RtsAutonomy.walk(farmer, depotAccess) && RtsAutonomy.put(depot, 0, class_1802.field_8229, BREAD_LIMIT)) {
                RtsContext.setScore("krts_crop", farmer, wheat - WHEAT_PER_BREAD);
            }
            return;
        }
        tend(world, farmer, farm, wheat);
    }

    private static boolean canReach(class_1646 farmer, BaseCombatEntity unit) {
        if (RtsAutonomy.near(farmer, unit.method_24515(), 1.65)) return true;
        var path = farmer.method_5942().method_6349(unit, 1);
        return path != null && path.method_21655();
    }

    private static boolean deliver(class_1646 farmer, List<BaseCombatEntity> patients, int clock, int healing) {
        var navigation = farmer.method_5942();
        for (var unit : patients) {
            if (RtsAutonomy.near(farmer, unit.method_24515(), 1.65)) {
                navigation.method_6340();
                unit.method_6025(healing);
                RtsContext.setScore("krts_ration", unit, clock);
                RtsContext.setScore("krts_meal", farmer, 0);
                return true;
            }
            var path = navigation.method_6349(unit, 1);
            if (path != null && path.method_21655() && navigation.method_6334(path, 0.9)) return true;
        }
        return false;
    }

    private record Plot(class_2338 crop, class_2338 approach, int age, boolean ripe) {}

    private static void tend(class_3218 world, class_1646 farmer, class_2338 farm, int wheat) {
        var plots = new ArrayList<Plot>(6);
        for (int row : new int[]{-3, -5}) {
            for (int z = -1; z <= 1; z++) {
                var crop = farm.method_10069(row, 0, z);
                var approach = farm.method_10069(-4, 0, z);
                var state = world.method_8320(crop);
                if (state.method_27852(WHEAT) && world.method_8320(approach).method_27852(class_2246.field_10124)) {
                    plots.add(new Plot(crop, approach, WHEAT.method_9829(state), WHEAT.method_9825(state)));
                }
            }
        }
        var navigation = farmer.method_5942();
        if (plots.isEmpty()) {
            navigation.method_6340();
            return;
        }
        var ripe = plots.stream().filter(Plot::ripe).toList();
        if (!ripe.isEmpty() && workPlots(world, farmer, ripe, wheat)) return;
        if (!workPlots(world, farmer, plots.stream().filter(plot -> !plot.ripe()).toList(), wheat)) {
            navigation.method_6340();
        }
    }

    private static boolean workPlots(class_3218 world, class_1646 farmer, List<Plot> candidates, int wheat) {
        if (candidates.isEmpty()) return false;
        var navigation = farmer.method_5942();
        for (var plot : candidates) {
            if (!RtsAutonomy.near(farmer, plot.approach(), 0.85)) continue;
            navigation.method_6340();
            if (plot.ripe() && world.method_8652(plot.crop(), WHEAT.method_9828(0), 3)) {
                RtsContext.setScore("krts_crop", farmer, wheat + 1);
            } else if (!plot.ripe()) {
                int waited = RtsContext.score("krts_tend", farmer) + 1;
                if (waited >= 3) {
                    world.method_8652(plot.crop(), WHEAT.method_9828(plot.age() + 1), 3);
                    waited = 0;
                }
                RtsContext.setScore("krts_tend", farmer, waited);
            }
            return true;
        }
        var path = navigation.method_21643(candidates.stream().map(Plot::approach).distinct(), 0);
        return path != null && path.method_21655() && navigation.method_6334(path, 0.85);
    }
}
