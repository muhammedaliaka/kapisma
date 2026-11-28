package kapisma.rts;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.class_1263;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1792;
import net.minecraft.class_1646;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_9015;
import net.minecraft.server.MinecraftServer;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;
import ydmsama.hundred_years_war.main.entity.navigation.BaseCombatNavigation;
import ydmsama.hundred_years_war.main.utils.RelationSystem;
import ydmsama.hundred_years_war.main.utils.RelationSystem.RelationType;

/** Match-local worker routes and training. Scoreboards and block inventories survive saves. */
public final class RtsAutonomy {
    static final int MAX_STOCK = 8;
    private static final int ORE_RECOVERY_SECONDS = 30;
    private static final double HYW_SEARCH_RANGE = 8;

    private RtsAutonomy() {}

    public static void update(MinecraftServer server, RtsWood wood) {
        int phase = RtsContext.score("krts_ctrl", "#phase");
        if (phase < 1 || phase > 4) return;
        int states = RtsContext.score("krts_ctrl", "#slot");
        if (states < 1 || states > 8) return;
        class_3218 world = server.method_30002();
        if (world == null) return;
        boolean natural = RtsContext.score("krts_ctrl", "#natural_match") == 1;
        var yards = new class_2338[states + 1];
        var workers = new class_1646[states + 1];
        var farms = new class_2338[states + 1];
        var fields = new class_1297[states + 1];
        var academies = new class_1297[states + 1];
        var farmers = new class_1646[states + 1];
        var guardposts = new class_2338[states + 1];
        var owners = new class_3222[states + 1];
        var troops = new ArrayList<BaseCombatEntity>();
        int online = 0;
        for (var player : server.method_3760().method_14571()) {
            int slot = RtsContext.score("krts_slot", player);
            if (slot < 1 || slot > states || player.method_7325() || control("#a", slot) != 1) continue;
            owners[slot] = player;
            online++;
        }
        for (var entity : world.method_27909()) {
            var tags = entity.method_5752();
            if (entity instanceof BaseCombatEntity unit && tags.contains("krts.unit") && unit.method_5805()) {
                troops.add(unit);
            }
            if (!tags.contains("krts.yard") && !tags.contains("krts.wood_depot") && !tags.contains("krts.worker")
                    && !tags.contains("krts.farm") && !tags.contains("krts.farmer")
                    && !tags.contains("krts.guardpost") && !tags.contains("krts.field_anchor")
                    && !tags.contains("krts.academy_anchor")) continue;
            for (int slot = 1; slot <= states; slot++) {
                if (!tags.contains("krts.s" + slot)) continue;
                if (tags.contains(natural ? "krts.wood_depot" : "krts.yard")) yards[slot] = entity.method_24515();
                if (tags.contains("krts.farm")) farms[slot] = entity.method_24515();
                if (tags.contains("krts.field_anchor")) fields[slot] = entity;
                if (tags.contains("krts.academy_anchor")) academies[slot] = entity;
                if (tags.contains("krts.guardpost")) guardposts[slot] = entity.method_24515();
                if (entity instanceof class_1646 worker && worker.method_5805()) {
                    if (tags.contains("krts.farmer")) farmers[slot] = worker;
                    if (tags.contains("krts.worker")) workers[slot] = worker;
                }
                break;
            }
        }
        for (int slot = 1; slot <= states; slot++) {
            var owner = owners[slot];
            boolean active = phase == 2 && online >= 2 && owner != null;
            var worker = workers[slot];
            var yard = yards[slot];
            if (natural) {
                if (active) RtsWood.releaseGoldReserve(world, slot);
                if (worker != null) wood.update(world, worker, slot, yard, active ? owner : null, troops);
                naturalDrill(world, slot, owner, active, worker, yard, academies[slot], troops);
                var farming = active && RtsContext.score("krts_nat_farming", owner) == 1;
                RtsSupply.field(world, slot, farmers[slot], fields[slot], farming ? owner.method_5667() : null,
                        farming ? RtsProfile.of(owner).fieldSeconds : 0, troops);
                if (active && RtsContext.score("krts_nat_rations", owner) == 1) {
                    RtsSupply.municipalRations(slot, owner.method_5667(), yard, troops);
                }
                continue;
            }
            boolean enabled = active && RtsContext.score("krts_auto", owner) == 1;
            int focus = enabled ? RtsContext.score("krts_focus", owner) : 0;
            if (guardposts[slot] != null) {
                int defense = enabled ? RtsContext.score("krts_defense", owner) : 0;
                var ownerId = owner == null ? null : owner.method_5667();
                boolean alert = RtsDefense.update(slot, defense, ownerId, guardposts[slot], troops);
                setControl("#alert", slot, alert ? 1 : 0);
            }
            var farm = farms[slot];
            var farmer = farmers[slot];
            boolean farming = farm != null && (focus & 4) != 0;
            if (farmer != null && !farming) stop(farmer);
            if (farm != null && world.method_8321(farm.method_10069(0, 0, 2)) instanceof class_1263 stock) {
                if (farming && farmer != null) RtsSupply.work(world, farmer, owner.method_5667(), farm, stock, troops,
                        hasResearch("#medicine", slot) ? 6 : 4);
                setControl("#bread", slot, count(stock, 0, class_1802.field_8229));
            }
            boolean mining = yard != null && (focus & 1) != 0;
            if (worker != null && !mining) stop(worker);
            var storage = yard == null ? null : world.method_8321(yard.method_10069(0, 0, 1));
            class_1263 depot = storage instanceof class_1263 inventory ? inventory : null;
            if (depot != null && mining && worker != null) work(world, worker, slot, owner.method_5667(), yard, depot, troops);
            boolean training = (focus & 2) != 0 && depot != null;
            if (owner != null) RtsDrill.handlePurchase(slot, owner, training,
                    () -> put(depot, 0, class_1802.field_8620, MAX_STOCK));
            if (yard != null) {
                int iron = training ? count(depot, 0, class_1802.field_8620) : 0;
                RtsDrill.update(slot, training ? owner.method_5667() : null,
                        List.of(new RtsDrill.Ground(yard,
                                List.of(yard.method_10069(-2, 0, 3), yard.method_10069(-2, 0, -3)),
                                RtsDrill.normalSeconds(slot))), iron,
                        () -> take(depot, 0), troops);
                setControl("#iron", slot, depot == null ? 0 : count(depot, 0, class_1802.field_8620));
            }
        }
    }

    private static void naturalDrill(class_3218 world, int slot, class_3222 owner, boolean active,
                                     class_1646 worker, class_2338 yard, class_1297 academy, List<BaseCombatEntity> troops) {
        if (owner != null) RtsDrill.handlePurchase(slot, owner, active && yard != null, () -> {
            int iron = control("#iron", slot);
            int reserved = RtsWood.carryingIron(worker) ? 1 : 0;
            if (iron + reserved >= MAX_STOCK) return false;
            setControl("#iron", slot, iron + 1);
            return true;
        });
        boolean training = active && yard != null && RtsContext.score("krts_nat_drill", owner) == 1;
        int iron = control("#iron", slot);
        var grounds = new ArrayList<RtsDrill.Ground>(2);
        if (training && iron > 0) {
            var survey = new RtsTerrain.Survey(world);
            if (academy != null && RtsContext.score("krts_post_connected", academy) == 1) {
                var center = academy.method_24515();
                grounds.add(new RtsDrill.Ground(center, RtsDrill.naturalMuster(survey, center), RtsDrill.ACADEMY_SECONDS));
            }
            grounds.add(new RtsDrill.Ground(yard, RtsDrill.naturalMuster(survey, yard), RtsDrill.normalSeconds(slot)));
        }
        RtsDrill.update(slot, training ? owner.method_5667() : null, grounds, iron,
                () -> setControl("#iron", slot, iron - 1), troops);
    }

    private static void work(class_3218 world, class_1646 worker, int slot, UUID owner, class_2338 yard,
                             class_1263 depot, List<BaseCombatEntity> troops) {
        worker.method_5977(false);
        var veins = List.of(yard.method_10069(4, 0, -3), yard.method_10069(4, 0, 0), yard.method_10069(4, 0, 3));
        renewVeins(world, slot, veins);
        if (troops.stream().anyMatch(unit -> threatened(worker, owner, unit))) {
            walk(worker, yard.method_10069(-1, 0, -2));
            return;
        }
        var furnacePos = yard.method_10069(0, 0, 2);
        if (!(world.method_8321(furnacePos) instanceof class_1263 furnace) || furnace.method_5439() < 3) return;
        int cargo = RtsContext.score("krts_cargo", worker);
        var depotAccess = yard.method_10069(1, 0, 1);
        var furnaceAccess = yard.method_10069(1, 0, 2);
        if (cargo == 2) {
            if (walk(worker, depotAccess) && put(depot, 0, class_1802.field_8620, MAX_STOCK)) {
                RtsContext.setScore("krts_cargo", worker, 0);
            }
            return;
        }
        if (cargo == 1) {
            if (walk(worker, furnaceAccess) && put(furnace, 0, class_1802.field_33400, 8)) {
                RtsContext.setScore("krts_cargo", worker, 0);
            }
            return;
        }
        if (count(depot, 0, class_1802.field_8620) >= MAX_STOCK) {
            worker.method_5942().method_6340();
            return;
        }
        if (count(furnace, 2, class_1802.field_8620) > 0) {
            if (walk(worker, furnaceAccess)) {
                take(furnace, 2);
                RtsContext.setScore("krts_cargo", worker, 2);
            }
            return;
        }
        var pickaxe = worker.method_6047();
        if (!pickaxe.method_31574(class_1802.field_8403)) {
            for (int supply = 1; supply <= 2; supply++) {
                var spare = depot.method_5438(supply);
                if (!spare.method_31574(class_1802.field_8403)) continue;
                if (walk(worker, depotAccess)) {
                    depot.method_5447(supply, class_1799.field_8037);
                    depot.method_5431();
                    worker.method_5673(class_1304.field_6173, spare);
                }
                return;
            }
            worker.method_5942().method_6340();
            return;
        }
        var accessible = veins.stream().filter(pos -> world.method_8320(pos).method_27852(class_2246.field_10212))
                .filter(pos -> world.method_8320(pos.method_10069(-1, 0, 0))
                .method_27852(class_2246.field_10124)).toList();
        for (var pos : accessible) {
            if (near(worker, pos.method_10069(-1, 0, 0), 0.85)) {
                worker.method_5942().method_6340();
                if (world.method_8652(pos, class_2246.field_10124.method_9564(), 3)) {
                    RtsContext.setScore("krts_cargo", worker, 1);
                    int damage = pickaxe.method_7919() + 1;
                    if (damage >= pickaxe.method_7936()) worker.method_5673(class_1304.field_6173, class_1799.field_8037);
                    else pickaxe.method_7974(damage);
                }
                return;
            }
        }
        if (!accessible.isEmpty() && count(furnace, 0, class_1802.field_33400) < 8) {
            var navigation = worker.method_5942();
            var path = navigation.method_21643(accessible.stream().map(pos -> pos.method_10069(-1, 0, 0)), 0);
            if (path != null && path.method_21655()) navigation.method_6334(path, 0.85);
        } else {
            worker.method_5942().method_6340();
        }
    }

    private static void renewVeins(class_3218 world, int slot, List<class_2338> veins) {
        var exhausted = veins.stream().filter(pos -> world.method_8320(pos).method_27852(class_2246.field_10124)).toList();
        int wait = control("#renew", slot);
        if (exhausted.isEmpty()) {
            if (wait != 0) setControl("#renew", slot, 0);
            return;
        }
        if (++wait >= ORE_RECOVERY_SECONDS) {
            for (var pos : exhausted) world.method_8652(pos, class_2246.field_10212.method_9564(), 3);
            wait = 0;
        }
        setControl("#renew", slot, wait);
    }

    static void stop(class_1646 worker) {
        worker.method_5942().method_6340();
        worker.method_5977(true);
    }

    static boolean threatened(class_1646 worker, UUID owner, BaseCombatEntity unit) {
        return near(worker, unit.method_24515(), 7) && hostile(owner, unit);
    }

    static boolean hostile(UUID owner, BaseCombatEntity unit) {
        UUID threat = unit.method_6139();
        if (owner == null || threat == null || owner.equals(threat)) return false;
        var relationship = RtsContext.relationship(owner, threat);
        return (relationship == null ? RelationSystem.getRelation(owner, threat) : relationship) == RelationType.HOSTILE;
    }

    static boolean walk(class_1646 worker, class_2338 destination) {
        var navigation = worker.method_5942();
        if (near(worker, destination, 0.85)) {
            navigation.method_6340();
            return true;
        }
        var path = navigation.method_6348(destination, 0);
        if (path != null && path.method_21655()) navigation.method_6334(path, 0.85);
        return false;
    }

    static boolean near(class_1297 entity, class_2338 position, double radius) {
        double x = entity.method_23317() - position.method_10263() - 0.5;
        double y = entity.method_23318() - position.method_10264();
        double z = entity.method_23321() - position.method_10260() - 0.5;
        return x * x + y * y + z * z <= radius * radius;
    }

    static int count(class_1263 inventory, int slot, class_1792 item) {
        var stack = inventory.method_5438(slot);
        return stack.method_31574(item) ? stack.method_7947() : 0;
    }

    static boolean put(class_1263 inventory, int slot, class_1792 item, int capacity) {
        var stack = inventory.method_5438(slot);
        if (stack.method_7960()) inventory.method_5447(slot, new class_1799(item));
        else if (stack.method_31574(item) && stack.method_7947() < capacity) stack.method_7933(1);
        else return false;
        inventory.method_5431();
        return true;
    }

    static void take(class_1263 inventory, int slot) {
        inventory.method_5438(slot).method_7934(1);
        inventory.method_5431();
    }

    static boolean idle(BaseCombatEntity unit) {
        return unit.getCurrentCommandedGoal() == null && unit.method_5968() == null;
    }

    static boolean availableForOrder(BaseCombatEntity unit) {
        return idle(unit) && !unit.commandHold() && !unit.isFormationHold() && !unit.isHolding();
    }

    static boolean moveUnit(BaseCombatEntity unit, class_2338 destination) {
        var navigation = unit.method_5942();
        if (near(unit, destination, 1.3)) {
            navigation.method_6340();
            return true;
        }
        var path = navigation.method_6348(destination, 0);
        if (navigation instanceof BaseCombatNavigation combat && combat.wasSearchDeferred()) return true;
        if (path == null) return false;
        if (!path.method_21655()) {
            // A partial route is useful for a destination outside HYW's unattended search range.
            double remaining = horizontalDistance(unit, destination);
            var end = path.method_45();
            if (remaining <= HYW_SEARCH_RANGE || end == null || end.method_35494(destination) + 1 >= remaining) {
                return false;
            }
        }
        return navigation.method_6334(path, 0.9);
    }

    static double horizontalDistance(BaseCombatEntity unit, class_2338 position) {
        return Math.hypot(unit.method_23317() - position.method_10263() - 0.5,
                          unit.method_23321() - position.method_10260() - 0.5);
    }

    static int control(String prefix, int slot) {
        return RtsContext.score("krts_ctrl", prefix + slot);
    }

    static boolean hasResearch(String prefix, int slot) {
        return RtsContext.structured() && control(prefix, slot) == 1;
    }

    static void setControl(String prefix, int slot, int value) {
        RtsContext.setScore("krts_ctrl", class_9015.method_55422(prefix + slot), value);
    }
}
