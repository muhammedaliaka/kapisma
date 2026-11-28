package kapisma.rts.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_318;
import net.minecraft.class_3965;
import ydmsama.hundred_years_war.client.freecam.Freecam;
import ydmsama.hundred_years_war.client.network.ClientPacketHandler;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;

/** Runs only in the synthetic loopback client. */
public final class FormationClientTest implements ClientModInitializer {
    private final boolean npcScenario = Boolean.parseBoolean(System.getenv("KRTS_FORMATION_QA_NPC"));
    private final boolean terrainScenario = Boolean.parseBoolean(System.getenv("KRTS_FORMATION_QA_TERRAIN"));
    private final boolean hallScenario = Boolean.parseBoolean(System.getenv("KRTS_HALL_QA"));
    private final boolean manualScenario = Boolean.parseBoolean(System.getenv("KRTS_WOOD_QA"));
    private int ticksUntilOrder = -1;
    private class_2338 terrainRally;
    private boolean terrainReady;
    private int captureIn;
    private boolean originalHudHidden;
    private class_2338 manualTarget;

    @Override
    public void onInitializeClient() {
        if (terrainScenario || hallScenario || manualScenario) {
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                String text = message.getString();
                if (text.endsWith("RTS_TERRAIN_ORDER_READY")) terrainReady = true;
                if (hallScenario && text.equals("RTS_HALL_CAPTURE")) captureIn = 30;
                if (manualScenario && text.startsWith("RTS_WOOD_PICK ")) {
                    String[] coords = text.split(" ");
                    manualTarget = new class_2338(Integer.parseInt(coords[1]), Integer.parseInt(coords[2]),
                            Integer.parseInt(coords[3]));
                }
            });
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(class_310 client) {
        if (client.field_1724 == null || client.field_1687 == null) return;
        if (captureIn > 0) {
            if (captureIn == 30) {
                originalHudHidden = client.field_1690.field_1842;
                client.field_1690.field_1842 = true;
            }
            if (--captureIn == 0) {
                class_318.method_22690(client.field_1697, "rts-belediye-0.11.1.png", client.method_1522(),
                        result -> System.out.println("RTS_HALL_SCREENSHOT " + result.getString()));
                client.field_1690.field_1842 = originalHudHidden;
            }
        }
        if (manualTarget != null) {
            if (!Freecam.isEnabled()) Freecam.toggle();
            sendMove(manualTarget);
            System.out.println("RTS_WOOD_NATIVE_CLICK " + manualTarget);
            manualTarget = null;
        }
        if (((hallScenario || manualScenario) && !terrainScenario) || ticksUntilOrder == 0) return;
        if (terrainScenario && !terrainReady) return;
        if (ticksUntilOrder > 0) {
            if (--ticksUntilOrder == 0) {
                if (terrainScenario) {
                    sendMove(terrainRally);
                    System.out.println("RTS_TERRAIN_HYW_TARGET " + terrainRally);
                } else {
                    int targetZ = npcScenario ? -37 : 0;
                    ClientPacketHandler.sendDraggedFormationPacket(
                        new class_2338(-8, -61, targetZ), new class_2338(8, -61, targetZ), 1);
                }
                System.out.println("RTS_FORMATION_HYW_ORDER_SENT");
            }
            return;
        }

        if (npcScenario && Math.abs(client.field_1724.method_23321() + 50.5) > 1) return;
        UUID owner = client.field_1724.method_5667();
        List<UUID> units = new ArrayList<>(2);
        for (var entity : client.field_1687.method_18112()) {
            if (entity instanceof BaseCombatEntity unit && unit.method_5805() && owner.equals(unit.method_6139())
                    && isInScenario(unit)) {
                units.add(unit.method_5667());
            }
        }
        if (units.size() != 2) return;
        if (terrainScenario) {
            terrainRally = client.field_1724.method_24515().method_10069(0, -1, 4);
        }
        if (!Freecam.isEnabled()) Freecam.toggle();
        ClientPacketHandler.sendSelectionPacket(units);
        ticksUntilOrder = terrainScenario ? 32 : 16;
        System.out.println("RTS_FORMATION_HYW_TWO_OWNED_UNITS_SELECTED");
    }

    private static void sendMove(class_2338 target) {
        var hit = new class_243(target.method_10263() + 0.5, target.method_10264() + 1.0, target.method_10260() + 0.5);
        ClientPacketHandler.sendMoveCommandPacket(new class_3965(hit, class_2350.field_11036, target, false), false);
    }

    private boolean isInScenario(BaseCombatEntity unit) {
        double z = unit.method_23321();
        if (terrainScenario) return unit.method_23318() > 0 && unit.method_6063() < 50;
        if (npcScenario) {
            return unit.method_6063() < 50 && Math.abs(unit.method_23317()) < 14 && z > -74 && z < -54;
        }
        return Math.abs(unit.method_23317()) < 6 && z > -20 && z < -6;
    }
}
