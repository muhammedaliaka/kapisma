package kapisma.rts;

import java.util.UUID;
import net.minecraft.class_1282;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_3222;
import net.minecraft.class_266;
import net.minecraft.class_269;
import net.minecraft.class_9013;
import net.minecraft.class_9015;
import net.minecraft.server.MinecraftServer;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;
import ydmsama.hundred_years_war.main.utils.RelationSystem.RelationType;

/** Tick context and diplomacy lookup for the pinned Fabric/HYW profile. */
public final class RtsContext {
    private static volatile MinecraftServer server;

    private RtsContext() {}

    public static int score(String objectiveName, String holder) {
        return score(objectiveName, class_9015.method_55422(holder));
    }

    public static int score(String objectiveName, class_9015 holder) {
        var current = server;
        if (current == null) return 0;
        class_269 board = current.method_3845();
        class_266 objective = board.method_1170(objectiveName);
        if (objective == null) return 0;
        class_9013 score = board.method_55430(holder, objective);
        return score == null ? 0 : score.method_55397();
    }

    static void setScore(String objectiveName, class_9015 holder, int value) {
        var board = server.method_3845();
        var objective = board.method_1170(objectiveName);
        if (objective != null) board.method_1180(holder, objective).method_55410(value);
    }

    public static void heartbeat(MinecraftServer current) {
        boolean starting = server != current;
        server = current;
        var board = current.method_3845();
        if (starting) {
            var controller = board.method_1170("krts_ctrl");
            if (controller != null) {
                board.method_1180(class_9015.method_55422("#boot"), controller).method_55410(1);
            }
        }
        if (score("krts_ctrl", "#strict") != 1 || score("krts_ctrl", "#phase") < 1) return;
        var slots = board.method_1170("krts_slot");
        var identities = board.method_1170("krts_identity");
        if (slots == null || identities == null) return;
        for (var player : current.method_3760().method_14571()) {
            var slot = board.method_55430(player, slots);
            if (slot == null || slot.method_55397() < 1 || slot.method_55397() > 8) continue;
            var key = class_9015.method_55422(player.method_5667().toString());
            var recorded = board.method_55430(key, identities);
            if (recorded == null || recorded.method_55397() != slot.method_55397()) {
                board.method_1180(key, identities).method_55410(slot.method_55397());
            }
        }
    }

    public static boolean structured() {
        return score("krts_ctrl", "#strict") == 1 && score("krts_ctrl", "#phase") > 0;
    }

    public static RelationType relationship(UUID source, UUID target) {
        if (!structured() || source == null || target == null || source.equals(target)) return null;
        int first = score("krts_identity", source.toString());
        int second = score("krts_identity", target.toString());
        if (first < 1 || first > 8 || second < 1 || second > 8 || first == second) return null;
        String pair = "#pact" + Math.min(first, second) + Math.max(first, second);
        boolean war = score("krts_ctrl", "#phase") == 2 && score("krts_ctrl", pair) == 2;
        return war ? RelationType.HOSTILE : RelationType.FRIENDLY;
    }

    private static UUID owner(class_1297 entity) {
        if (entity instanceof BaseCombatEntity unit) return unit.method_6139();
        if (entity instanceof class_3222 player) return player.method_5667();
        return null;
    }

    public static boolean blocksDamage(class_1297 target, class_1282 source) {
        if (!structured()) return false;
        var from = owner(source.method_5529());
        var to = owner(target);
        return relationship(from, to) == RelationType.FRIENDLY;
    }

    public static void recordInjury(class_1297 target) {
        if (target instanceof BaseCombatEntity unit && unit.method_5752().contains("krts.unit")
                && score("krts_ctrl", "#natural_match") == 1 && score("krts_ctrl", "#phase") == 2) {
            setScore("krts_ration_hit", unit, score("krts_ctrl", "#left"));
        }
    }

    public static boolean blocksControl(class_1657 player, class_1297 target) {
        if (!(target instanceof BaseCombatEntity unit)) return false;
        var tags = unit.method_5752();
        if (tags.contains("krts.capital") || tags.contains("krts.post_core") || tags.contains("krts.district_core")
                || tags.contains("krts.field_core") || tags.contains("krts.academy_core")) return true;
        if (!tags.contains("krts.unit")) return false;

        if (!(player instanceof class_3222 participant) || participant.method_7325()
                || score("krts_ctrl", "#phase") != 2
                || !participant.method_5667().equals(unit.method_6139())) return true;
        int slot = score("krts_slot", participant);
        return slot < 1 || slot > 8 || !tags.contains("krts.s" + slot);
    }
}
