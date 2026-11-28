package kapisma.rts.qa;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.class_9015;
import net.minecraft.server.MinecraftServer;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;
import ydmsama.hundred_years_war.main.utils.RelationSystem;
import ydmsama.hundred_years_war.main.utils.RelationSystem.RelationType;
import ydmsama.hundred_years_war.main.utils.ServerRelationHelper;

public final class ControlServerTest implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        ServerTickEvents.END_SERVER_TICK.register(this::verify);
    }

    private void verify(MinecraftServer server) {
        var board = server.method_3845();
        var controller = board.method_1170("krts_ctrl");
        if (controller == null) return;
        var key = class_9015.method_55422("#qa_control");
        var request = board.method_55430(key, controller);
        if (request == null || request.method_55397() < 1) return;
        int stage = request.method_55397();
        board.method_1180(key, controller).method_55410(0);
        var owner = server.method_3760().method_14566("Control1");
        var other = server.method_3760().method_14566("Control2");
        if (owner == null || other == null) throw new IllegalStateException("RTS command fixture is missing a player");
        BaseCombatEntity militia = null;
        BaseCombatEntity capital = null;
        for (var entity : server.method_30002().method_27909()) {
            if (!(entity instanceof BaseCombatEntity unit) || !owner.method_5667().equals(unit.method_6139())) continue;
            if (unit.method_5752().contains("krts.unit")) militia = unit;
            if (unit.method_5752().contains("krts.capital")) capital = unit;
        }
        if (militia == null || capital == null) throw new IllegalStateException("RTS command fixture is missing a unit");
        if (stage == 2) {
            RelationSystem.setRelation(owner.method_5667(), other.method_5667(), RelationType.CONTROL);
            RelationSystem.setRelation(other.method_5667(), owner.method_5667(), RelationType.FRIENDLY);
        }
        boolean ownUnit = ServerRelationHelper.hasControlOver(owner, militia);
        boolean ownCapital = ServerRelationHelper.hasControlOver(owner, capital);
        boolean foreignUnit = ServerRelationHelper.hasControlOver(other, militia);
        System.out.printf("RTS_COMMAND_PROOF_%d own=%s capital=%s foreign=%s%n", stage, ownUnit, ownCapital, foreignUnit);
    }
}
