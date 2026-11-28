package kapisma.rts;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.class_1320;
import net.minecraft.class_1322;
import net.minecraft.class_2338;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_5134;
import net.minecraft.class_6880;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;
import ydmsama.hundred_years_war.main.registry.HywAttributes;

/** A single drill assignment per state. The soldiers keep progress between assignments. */
final class RtsDrill {
    private static final int DRILL_SECONDS = 20;
    private static final int DOCTRINE_DRILL_SECONDS = 15;
    static final int ACADEMY_SECONDS = 12;
    private static final int MAX_RANK = 3;
    private static final int DRILL_GOLD = 2;
    private static final int IRON_PRICE = 3;
    private static final class_2960 DRILL_HEALTH = class_2960.method_60655("kapisma", "drill_health");
    private static final class_2960 DRILL_ATTACK = class_2960.method_60655("kapisma", "drill_attack");
    private static final class_2960 DRILL_RANGED = class_2960.method_60655("kapisma", "drill_ranged");

    private RtsDrill() {}

    record Ground(class_2338 center, List<class_2338> positions, int seconds) {}

    static int normalSeconds(int slot) {
        return RtsAutonomy.hasResearch("#doctrine", slot) ? DOCTRINE_DRILL_SECONDS : DRILL_SECONDS;
    }

    static List<class_2338> naturalMuster(RtsTerrain.Survey survey, class_2338 yard) {
        var positions = List.of(yard.method_10069(-2, 0, 3), yard.method_10069(-2, 0, -3),
                yard.method_10069(2, 0, 3), yard.method_10069(2, 0, -3));
        return positions.stream()
                .map(pos -> new class_2338(pos.method_10263(), survey.surface(pos.method_10263(), pos.method_10260()),
                        pos.method_10260()))
                .filter(pos -> Math.abs((long) pos.method_10264() - yard.method_10264()) <= 3)
                .filter(survey::walkable).toList();
    }

    static void update(int slot, UUID owner, List<Ground> grounds,
                       int iron, Runnable spendIron, List<BaseCombatEntity> troops) {
        String state = "krts.s" + slot;
        var own = troops.stream().filter(unit -> unit.method_5752().contains(state)).toList();
        int gold = RtsAutonomy.control("#g", slot);
        BaseCombatEntity trainee = null;
        List<Ground> availableGrounds = List.of();
        if (owner != null && gold >= DRILL_GOLD && iron > 0) {
            var safe = grounds.stream().filter(ground -> !ground.positions.isEmpty()
                    && troops.stream().noneMatch(unit -> RtsAutonomy.near(unit, ground.positions.getFirst(), 13)
                        && RtsAutonomy.hostile(owner, unit))).toList();
            availableGrounds = safe;
            trainee = own.stream()
                    .filter(unit -> owner.equals(unit.method_6139()) && available(unit)
                            && rank(unit) < MAX_RANK && safe.stream().anyMatch(ground -> RtsAutonomy.near(unit, ground.center, 18)))
                    .min(Comparator.comparingInt(RtsDrill::rank)
                            .thenComparingInt(unit -> -RtsContext.score("krts_training", unit))
                            .thenComparingInt(unit -> -RtsContext.score("krts_drill", unit))
                            .thenComparingDouble(unit -> safe.stream()
                                    .mapToDouble(ground -> RtsAutonomy.horizontalDistance(unit, ground.positions.getFirst()))
                                    .min().orElse(Double.MAX_VALUE)))
                    .orElse(null);
        }

        for (var unit : own) {
            if (unit == trainee || RtsContext.score("krts_training", unit) != 1) continue;
            release(unit);
        }
        if (trainee == null) return;
        var unit = trainee;
        var nearbyGrounds = availableGrounds.stream().filter(ground -> RtsAutonomy.near(unit, ground.center, 18)).toList();
        var ground = nearbyGrounds.stream().filter(site -> site.positions.stream()
                .anyMatch(pos -> RtsAutonomy.near(unit, pos, 1.3))).findFirst().orElse(null);
        if (ground == null) {
            RtsContext.setScore("krts_training", trainee, 1);
            if (nearbyGrounds.stream().flatMap(site -> site.positions.stream())
                    .noneMatch(pos -> RtsAutonomy.moveUnit(unit, pos))) release(trainee);
            return;
        }
        trainee.method_5942().method_6340();
        RtsContext.setScore("krts_training", trainee, 1);
        int progress = RtsContext.score("krts_drill", trainee) + 1;
        int required = ground.seconds;
        if (progress < required) {
            RtsContext.setScore("krts_drill", trainee, progress);
            return;
        }

        int rank = rank(trainee) + 1;
        spendIron.run();
        RtsAutonomy.setControl("#g", slot, gold - DRILL_GOLD);
        RtsContext.setScore("krts_rank", trainee, rank);
        RtsContext.setScore("krts_drill", trainee, 0);
        release(trainee);
        bonus(trainee, class_5134.field_23716, DRILL_HEALTH, rank * 2.0);
        bonus(trainee, class_5134.field_23721, DRILL_ATTACK, rank * 0.5);
        bonus(trainee, HywAttributes.RANGED_ATTACK_DAMAGE, DRILL_RANGED, rank * 0.5);
    }

    static void handlePurchase(int slot, class_3222 owner, boolean available, BooleanSupplier deposit) {
        if (RtsContext.score("krts_drill_buy", owner) != 1) return;
        RtsContext.setScore("krts_drill_buy", owner, 0);
        String message;
        int gold = RtsAutonomy.control("#g", slot);
        if (!available) {
            message = "Demir alımı aktif maçta yapılır. Atölyeli maçta talim açık olmalı.";
        } else if (gold < IRON_PRICE) {
            message = "Bir talim demiri için 3 altın gerekir.";
        } else if (!deposit.getAsBoolean()) {
            message = "Demir deposunda yer yok.";
        } else {
            RtsAutonomy.setControl("#g", slot, gold - IRON_PRICE);
            message = "Depoya 1 demir eklendi.";
        }
        owner.method_7353(class_2561.method_43470("[RTS] " + message), false);
    }

    private static boolean available(BaseCombatEntity unit) {
        return RtsAutonomy.availableForOrder(unit) && RtsContext.score("krts_guarding", unit) == 0;
    }

    private static int rank(BaseCombatEntity unit) {
        return RtsContext.score("krts_rank", unit);
    }

    private static void release(BaseCombatEntity unit) {
        if (available(unit)) unit.method_5942().method_6340();
        RtsContext.setScore("krts_training", unit, 0);
    }

    private static void bonus(BaseCombatEntity unit, class_6880<class_1320> attribute, class_2960 id, double amount) {
        var instance = unit.method_5996(attribute);
        if (instance == null) return;
        instance.method_6200(id);
        instance.method_26837(new class_1322(id, amount, class_1322.class_1323.field_6328));
    }
}
