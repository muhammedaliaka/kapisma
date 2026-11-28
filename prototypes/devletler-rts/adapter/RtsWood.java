package kapisma.rts;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.class_11;
import net.minecraft.class_1268;
import net.minecraft.class_1304;
import net.minecraft.class_1646;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_239;
import net.minecraft.class_2561;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3481;
import net.minecraft.class_3965;
import net.minecraft.class_9015;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;

/** Runs one village worker per state against reachable, finite natural sources. */
public final class RtsWood {
    private static final int MAX_STOCK = 12;
    private static final int IRON_DEPTH = 12;
    private static final int GOLD_DEPTH = 32;
    private static final int TREASURY_LIMIT = 60;
    private static final int WORK_SECONDS = 3;
    private static final int RETRY_SECONDS = 10;
    private static final int WALK_TIMEOUT = 25;
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final String CARGO = "krts_wood_cargo";
    private static final String AIM = "krts_wood_aim";
    private static final String TASK = "krts_wood_task";

    private enum Material {
        WOOD(1, "wood.", "#wood", "krts_wood_used", 2, MAX_STOCK, "Gövde"),
        STONE(2, "stone.", "#stone", "krts_stone_used", 1, MAX_STOCK, "Kaya"),
        IRON(3, "iron.", "#iron", "krts_iron_used", 1, RtsAutonomy.MAX_STOCK, "Demir damarı"),
        GOLD(4, "gold.", "#g", "krts_gold_used", 1, TREASURY_LIMIT, "Altın damarı");

        final int id;
        final String key;
        final String stock;
        final String objective;
        final int quota;
        final int capacity;
        final String label;

        Material(int id, String key, String stock, String objective, int quota, int capacity, String label) {
            this.id = id;
            this.key = key;
            this.stock = stock;
            this.objective = objective;
            this.quota = quota;
            this.capacity = capacity;
            this.label = label;
        }

        static Material from(int value) {
            return switch (value) {
                case 2 -> STONE;
                case 3 -> IRON;
                case 4 -> GOLD;
                default -> WOOD;
            };
        }
    }

    private final Job[] jobs = new Job[9];
    private int generation = -1;

    private static final class Job {
        class_2338 trunk;
        class_2338 stand;
        class_2338 skipped;
        class_2338 manual;
        Material material;
        int travel;
        int work;
        int retry;
        int settling;

        void clearRoute() {
            trunk = null;
            stand = null;
            travel = 0;
            work = 0;
            settling = 0;
        }
    }

    static boolean carryingIron(class_1646 worker) {
        return worker != null && RtsContext.score(CARGO, worker) == Material.IRON.id;
    }

    void update(class_3218 world, class_1646 worker, int slot, class_2338 depot, class_3222 owner,
                List<BaseCombatEntity> troops) {
        int currentGeneration = RtsContext.score("krts_ctrl", "#generation");
        if (currentGeneration != generation) {
            java.util.Arrays.fill(jobs, null);
            generation = currentGeneration;
        }
        if (jobs[slot] == null) jobs[slot] = new Job();
        var job = jobs[slot];
        boolean automatic = owner != null && RtsContext.score("krts_auto", owner) == 1;
        int task = owner == null ? 0 : RtsContext.score(TASK, owner);
        class_2338 manual = !automatic && task > 0
                ? new class_2338(RtsContext.score("krts_wood_x", owner), RtsContext.score("krts_wood_y", owner),
                        RtsContext.score("krts_wood_z", owner)) : null;
        if ((!automatic && manual == null) || depot == null) {
            RtsAutonomy.stop(worker);
            job.clearRoute();
            return;
        }
        Material material = Material.from(automatic ? RtsContext.score("krts_work_kind", owner) : task);
        var profile = RtsProfile.of(owner);
        if (!Objects.equals(manual, job.manual) || material != job.material) {
            worker.method_5942().method_6340();
            job.manual = manual;
            job.material = material;
            worker.method_5673(class_1304.field_6173,
                    new class_1799(material == Material.WOOD ? class_1802.field_8475 : class_1802.field_8403));
            job.skipped = null;
            job.retry = 0;
            job.clearRoute();
        }
        worker.method_5977(false);
        if (troops.stream().anyMatch(unit -> RtsAutonomy.threatened(worker, owner.method_5667(), unit))) {
            RtsAutonomy.walk(worker, depot);
            job.clearRoute();
            return;
        }
        int cargo = RtsContext.score(CARGO, worker);
        if (cargo > 0) {
            Material carried = Material.from(cargo);
            int stock = RtsAutonomy.control(carried.stock, slot);
            boolean reserveGold = carried == Material.GOLD && stock >= TREASURY_LIMIT
                    && RtsAutonomy.control("#goldPending", slot) == 0;
            if ((stock < carried.capacity || reserveGold) && RtsAutonomy.walk(worker, depot)) {
                if (reserveGold) RtsAutonomy.setControl("#goldPending", slot, 1);
                else if (carried == Material.GOLD) creditGold(world, slot);
                else RtsAutonomy.setControl(carried.stock, slot, stock + 1);
                RtsContext.setScore(CARGO, worker, 0);
            }
            return;
        }
        if (RtsAutonomy.control(material.stock, slot) >= material.capacity) {
            RtsAutonomy.stop(worker);
            return;
        }
        if (job.trunk == null) {
            if (job.retry > 0) {
                --job.retry;
                return;
            }
            // A previously frozen villager needs a movement tick to register its ground contact.
            if (!worker.method_24828() && job.settling++ < 2) return;
            var survey = new RtsTerrain.Survey(world);
            if (!startRoute(worker, depot, survey, job, profile.workRadius)) {
                if (manual != null) {
                    RtsContext.setScore(TASK, owner, 0);
                    tell(owner, material.label + " tükenmiş veya rotası kapanmış.");
                    RtsAutonomy.stop(worker);
                    return;
                }
                RtsAutonomy.setControl(material.stock + "_blocked", slot, 1);
                job.skipped = null;
                job.retry = RETRY_SECONDS;
            } else {
                RtsAutonomy.setControl(material.stock + "_blocked", slot, 0);
            }
            return;
        }
        if (!RtsAutonomy.near(worker, job.stand, 0.9)) {
            job.work = 0;
            if (++job.travel >= WALK_TIMEOUT) {
                if (manual != null) {
                    RtsContext.setScore(TASK, owner, 0);
                    tell(owner, "İşçi seçilen kaynağa ulaşamadı.");
                }
                job.skipped = job.trunk;
                job.clearRoute();
            } else {
                RtsAutonomy.walk(worker, job.stand);
            }
            return;
        }
        worker.method_5942().method_6340();
        worker.method_6104(class_1268.field_5808);
        int workSeconds = material == Material.IRON || material == Material.GOLD ? profile.oreSeconds : WORK_SECONDS;
        if (++job.work < workSeconds) return;
        var id = sourceId(material, job.trunk);
        int used = RtsContext.score(material.objective, id);
        if (exists(new RtsTerrain.Survey(world), job.trunk, material) && used < material.quota) {
            RtsContext.setScore(material.objective, id, used + 1);
            RtsContext.setScore(CARGO, worker, material.id);
        }
        job.clearRoute();
    }

    private static void creditGold(class_3218 world, int slot) {
        RtsTerrain.run(world.method_8503(), "function krts:earn/" + slot);
        RtsAutonomy.setControl("#minedGold", slot, RtsAutonomy.control("#minedGold", slot) + 1);
    }

    static void releaseGoldReserve(class_3218 world, int slot) {
        if (RtsAutonomy.control("#goldPending", slot) == 1 && RtsAutonomy.control("#g", slot) < TREASURY_LIMIT) {
            creditGold(world, slot);
            RtsAutonomy.setControl("#goldPending", slot, 0);
        }
    }

    public static boolean receiveOrder(class_3222 player, class_239 hit) {
        var server = player.method_5682();
        int aim = RtsContext.score(AIM, player);
        if (server == null || !server.method_18854() || RtsContext.score("krts_ctrl", "#natural_match") != 1
                || aim < 1 || aim > Material.GOLD.id || RtsContext.score("krts_ctrl", "#phase") != 2) return false;
        Material material = Material.from(aim);
        var world = server.method_30002();
        int slot = RtsContext.score("krts_slot", player);
        if (player.method_7325() || player.method_37908() != world || slot < 1 || slot > 8
                || RtsAutonomy.control("#a", slot) != 1
                || RtsContext.score("krts_identity", player.method_5667().toString()) != slot) return false;
        int lastClick = RtsContext.score("krts_wood_click", player);
        int now = server.method_3780();
        if (lastClick > 0 && now >= lastClick && now - lastClick < 4) return true;
        RtsContext.setScore("krts_wood_click", player, now);
        if (!(hit instanceof class_3965 block) || hit.method_17783() != class_239.class_240.field_1332) {
            tell(player, material.label + " olarak görünen bir bloğa sağ tıkla.");
            return true;
        }
        class_2338 depot = null;
        class_1646 worker = null;
        for (var entity : world.method_27909()) {
            var tags = entity.method_5752();
            if (!tags.contains("krts.s" + slot)) continue;
            if (tags.contains("krts.wood_depot")) depot = entity.method_24515();
            if (entity instanceof class_1646 villager && tags.contains("krts.woodworker") && villager.method_5805()) {
                worker = villager;
            }
            if (depot != null && worker != null) break;
        }
        if (depot == null || worker == null) {
            tell(player, "Kendi belediyene yakınken işçine emir verebilirsin.");
            return true;
        }
        var target = block.method_17777();
        int radius = RtsProfile.of(player).workRadius;
        if (!withinRange(depot, target, radius)) {
            tell(player, "Bu kaynak işçinin " + radius + " blokluk çalışma alanı dışında.");
            return true;
        }
        var survey = new RtsTerrain.Survey(world);
        if (material == Material.STONE && !exists(survey, target, material)
                && survey.surface(target.method_10263(), target.method_10260()) == target.method_10264() + 1) {
            var shallow = findShallowStone(survey, target.method_10069(0, 1, 0));
            if (shallow != null) target = shallow;
        }
        if (!exists(survey, target, material)) {
            tell(player, switch (material) {
                case WOOD -> "Odun kaynağı olarak bir ağaç gövdesi seç.";
                case STONE -> "Kırık taş kaynağı olarak bir doğal kaya seç.";
                case IRON -> "Bir demir cevheri bloğu seç.";
                case GOLD -> "Bir altın cevheri bloğu seç.";
            });
            return true;
        }
        var trunk = material == Material.WOOD ? root(survey, target) : target;
        var stands = new HashMap<class_2338, class_2338>();
        addStands(survey, trunk, stands, material);
        if (material == Material.STONE) addQuarryStand(survey, trunk, stands);
        if (stands.isEmpty() || (worker.method_24828() && route(worker, stands) == null)) {
            tell(player, switch (material) {
                case WOOD -> "Bu gövdenin kotası dolmuş veya oduncunun yolu kapalı.";
                case STONE -> "Bu kayanın kotası dolmuş veya işçinin yolu kapalı.";
                case IRON -> "Bu demir damarının kotası dolmuş veya işçinin yolu kapalı.";
                case GOLD -> "Bu altın damarının kotası dolmuş veya işçinin yolu kapalı.";
            });
            return true;
        }
        RtsContext.setScore("krts_wood_x", player, trunk.method_10263());
        RtsContext.setScore("krts_wood_y", player, trunk.method_10264());
        RtsContext.setScore("krts_wood_z", player, trunk.method_10260());
        RtsContext.setScore(TASK, player, material.id);
        RtsContext.setScore(AIM, player, 0);
        tell(player, switch (material) {
            case WOOD -> "Oduncu seçtiğin kaynağa gidiyor.";
            case STONE -> "İşçi seçtiğin kayaya gidiyor.";
            case IRON -> "İşçi seçtiğin demir damarına gidiyor.";
            case GOLD -> "İşçi seçtiğin altın damarına gidiyor.";
        });
        return true;
    }

    private static boolean withinRange(class_2338 depot, class_2338 target, int radius) {
        long dx = (long) depot.method_10263() - target.method_10263();
        long dz = (long) depot.method_10260() - target.method_10260();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static void tell(class_3222 player, String text) {
        player.method_7353(class_2561.method_43470("[RTS] " + text), false);
    }

    private static boolean startRoute(class_1646 worker, class_2338 depot, RtsTerrain.Survey survey, Job job, int radius) {
        var stands = new HashMap<class_2338, class_2338>();
        if (job.manual != null) {
            addStands(survey, job.manual, stands, job.material);
        } else {
            scan(survey, depot, stands, job, radius);
        }
        var path = route(worker, stands);
        if (path == null && job.material == Material.STONE) {
            stands.clear();
            if (job.manual == null) scanShallowStone(survey, depot, stands, job.skipped, radius);
            else addQuarryStand(survey, job.manual, stands);
            path = route(worker, stands);
        }
        if (path == null) return false;
        job.stand = path.method_48();
        job.trunk = stands.get(job.stand);
        worker.method_5942().method_6334(path, 0.85);
        return true;
    }

    private static void scan(RtsTerrain.Survey survey, class_2338 depot, HashMap<class_2338, class_2338> stands, Job job, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int x = depot.method_10263() + dx;
                int z = depot.method_10260() + dz;
                int surface = survey.surface(x, z);
                if (surface == Integer.MIN_VALUE) continue;
                var top = new class_2338(x, surface - 1, z);
                if (job.material == Material.WOOD) {
                    if (!exists(survey, top, Material.WOOD)) continue;
                    var trunk = root(survey, top);
                    if (!trunk.equals(job.skipped)) addStands(survey, trunk, stands, Material.WOOD);
                } else {
                    int maxDepth = switch (job.material) {
                        case IRON -> IRON_DEPTH;
                        case GOLD -> GOLD_DEPTH;
                        default -> 3;
                    };
                    for (int depth = 0; depth <= maxDepth; depth++) {
                        var rock = top.method_10069(0, -depth, 0);
                        if (!rock.equals(job.skipped)) addStands(survey, rock, stands, job.material);
                    }
                }
            }
        }
    }

    private static void addStands(RtsTerrain.Survey survey, class_2338 trunk, HashMap<class_2338, class_2338> stands,
                                  Material material) {
        if (!exists(survey, trunk, material) || (material == Material.WOOD && !exists(survey, trunk.method_10069(0, 1, 0), material))
                || RtsContext.score(material.objective, sourceId(material, trunk)) >= material.quota) return;
        var above = material != Material.WOOD ? survey.state(trunk.method_10069(0, 1, 0)) : null;
        int topApproach = above != null && above.method_26215() ? 1 : 0;
        for (var offset : NEIGHBORS) {
            for (int dy = 0; dy <= topApproach; dy++) {
                var stand = trunk.method_10069(offset[0], dy, offset[1]);
                if (survey.walkable(stand)) stands.putIfAbsent(stand, trunk);
            }
        }
    }

    private static void scanShallowStone(RtsTerrain.Survey survey, class_2338 depot,
                                         HashMap<class_2338, class_2338> stands, class_2338 skipped, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int x = depot.method_10263() + dx;
                int z = depot.method_10260() + dz;
                int height = survey.surface(x, z);
                if (height == Integer.MIN_VALUE) continue;
                var stand = new class_2338(x, height, z);
                var rock = findShallowStone(survey, stand);
                if (rock != null && !rock.equals(skipped)) stands.put(stand, rock);
            }
        }
    }

    private static class_2338 findShallowStone(RtsTerrain.Survey survey, class_2338 stand) {
        if (!survey.walkable(stand)) return null;
        for (int depth = 1; depth <= 4; depth++) {
            var rock = stand.method_10069(0, -depth, 0);
            if (availableStone(survey, rock)) return rock;
        }
        return null;
    }

    private static void addQuarryStand(RtsTerrain.Survey survey, class_2338 rock,
                                       HashMap<class_2338, class_2338> stands) {
        int x = rock.method_10263();
        int z = rock.method_10260();
        int height = survey.surface(x, z);
        if (height == Integer.MIN_VALUE || height <= rock.method_10264() || height - rock.method_10264() > 4) return;
        var stand = new class_2338(x, height, z);
        if (survey.walkable(stand) && availableStone(survey, rock)) stands.putIfAbsent(stand, rock);
    }

    private static boolean availableStone(RtsTerrain.Survey survey, class_2338 rock) {
        return exists(survey, rock, Material.STONE)
                && RtsContext.score(Material.STONE.objective, sourceId(Material.STONE, rock)) < Material.STONE.quota;
    }

    private static class_11 route(class_1646 worker, HashMap<class_2338, class_2338> stands) {
        if (stands.isEmpty()) return null;
        var positions = stands.keySet().stream().sorted(Comparator.comparingDouble(pos -> distance(worker, pos)));
        var path = worker.method_5942().method_21643(positions.limit(16), 0);
        return path != null && path.method_21655() && stands.containsKey(path.method_48()) ? path : null;
    }

    private static class_2338 root(RtsTerrain.Survey survey, class_2338 trunk) {
        while (exists(survey, trunk.method_10069(0, -1, 0), Material.WOOD)) trunk = trunk.method_10069(0, -1, 0);
        return trunk;
    }

    private static double distance(class_1646 worker, class_2338 pos) {
        double dx = worker.method_23317() - pos.method_10263() - 0.5;
        double dy = worker.method_23318() - pos.method_10264();
        double dz = worker.method_23321() - pos.method_10260() - 0.5;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean exists(RtsTerrain.Survey survey, class_2338 pos, Material material) {
        var state = survey.state(pos);
        return state != null && state.method_26164(switch (material) {
            case WOOD -> class_3481.field_39030;
            case STONE -> class_3481.field_25806;
            case IRON -> class_3481.field_28988;
            case GOLD -> class_3481.field_23062;
        });
    }

    private static class_9015 sourceId(Material material, class_2338 pos) {
        return class_9015.method_55422(material.key + pos.method_10063());
    }
}
