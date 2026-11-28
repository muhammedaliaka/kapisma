package kapisma.rts;

import java.util.List;
import java.util.UUID;
import net.minecraft.class_2338;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;

/** Moves one available unit around its own base. HYW retains combat and player orders. */
final class RtsDefense {
    private static final int[][] WAYPOINTS = {{-5, -5}, {5, -5}, {5, 5}, {-5, 5}};

    private RtsDefense() {}

    static boolean update(int slot, int mode, UUID owner, class_2338 base, List<BaseCombatEntity> troops) {
        String state = "krts.s" + slot;
        var localUnits = troops.stream().filter(unit -> unit.method_5752().contains(state)).toList();
        BaseCombatEntity guard = null;
        BaseCombatEntity enemy = null;
        if (mode != 0 && owner != null) {
            double closest = Double.MAX_VALUE;
            for (var unit : localUnits) {
                if (!owner.equals(unit.method_6139()) || !RtsAutonomy.availableForOrder(unit)
                        || !RtsAutonomy.near(unit, base, 18)) continue;
                if (RtsContext.score("krts_guarding", unit) == 1) {
                    guard = unit;
                    break;
                }
                double distance = RtsAutonomy.horizontalDistance(unit, base);
                if (distance < closest) {
                    closest = distance;
                    guard = unit;
                }
            }
            closest = Double.MAX_VALUE;
            for (var unit : troops) {
                if (!RtsAutonomy.near(unit, base, 16) || !RtsAutonomy.hostile(owner, unit)) continue;
                double distance = RtsAutonomy.horizontalDistance(unit, base);
                if (distance < closest) {
                    closest = distance;
                    enemy = unit;
                }
            }
        }

        for (var unit : localUnits) {
            if (unit == guard || RtsContext.score("krts_guarding", unit) != 1) continue;
            if (RtsAutonomy.availableForOrder(unit)) unit.method_5942().method_6340();
            RtsContext.setScore("krts_guarding", unit, 0);
        }
        if (guard == null) return enemy != null;
        RtsContext.setScore("krts_guarding", guard, 1);
        if (enemy == null || !RtsAutonomy.moveUnit(guard, intercept(base, enemy))) {
            if (mode == 2) patrol(guard, base);
            else RtsAutonomy.moveUnit(guard, base.method_10069(2, 0, 0));
        }
        return enemy != null;
    }

    private static void patrol(BaseCombatEntity guard, class_2338 base) {
        int leg = Math.floorMod(RtsContext.score("krts_route", guard), WAYPOINTS.length);
        for (int remaining = WAYPOINTS.length; remaining > 0; remaining--) {
            var point = base.method_10069(WAYPOINTS[leg][0], 0, WAYPOINTS[leg][1]);
            if (!RtsAutonomy.near(guard, point, 1.3) && RtsAutonomy.moveUnit(guard, point)) {
                RtsContext.setScore("krts_route", guard, leg);
                return;
            }
            leg = (leg + 1) % WAYPOINTS.length;
        }
        guard.method_5942().method_6340();
    }

    private static class_2338 intercept(class_2338 base, BaseCombatEntity enemy) {
        double x = enemy.method_23317() - base.method_10263() - 0.5;
        double z = enemy.method_23321() - base.method_10260() - 0.5;
        double scale = 10 / Math.max(10, Math.hypot(x, z));
        return base.method_10069((int) Math.round(x * scale), 0, (int) Math.round(z * scale));
    }

}
