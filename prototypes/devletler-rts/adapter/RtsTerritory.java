package kapisma.rts;

import java.util.ArrayList;
import java.util.EnumMap;
import net.minecraft.class_1297;
import net.minecraft.class_2338;
import net.minecraft.class_239;
import net.minecraft.class_2561;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3965;
import net.minecraft.class_9015;
import net.minecraft.server.MinecraftServer;

/** Owns the construction rules and the continuous territory around a capital. */
public final class RtsTerritory {
    private static final int HOME_RADIUS = 12;
    private static final int POST_RADIUS = 8;
    private static final int MIN_SPACING = 10;
    private static final int DISTRICT_CAPACITY = 3;
    private static final String AIM = "krts_post_aim";

    private enum Kind {
        POST("post", "Karakol", 1, 1, 3, 3, 3, 3, 2, 0, 36),
        DISTRICT("district", "Mahalle", 2, 4, 2, 4, 3, 4, 3, 0, 32),
        FARM("field", "Tarla", 3, 6, 1, 3, 3, 3, 2, 0, 26),
        ACADEMY("academy", "Talim Ocağı", 4, 8, 1, 4, 3, 3, 2, 2, 34);

        final String key;
        final String label;
        final int aim;
        final int action;
        final int limit;
        final int halfX;
        final int halfZ;
        final int wood;
        final int stone;
        final int iron;
        final int health;

        Kind(String key, String label, int aim, int action, int limit, int halfX, int halfZ, int wood, int stone,
             int iron, int health) {
            this.key = key;
            this.label = label;
            this.aim = aim;
            this.action = action;
            this.limit = limit;
            this.halfX = halfX;
            this.halfZ = halfZ;
            this.wood = wood;
            this.stone = stone;
            this.iron = iron;
            this.health = health;
        }

        String sizes() {
            return (2 * halfX + 1) + "×" + (2 * halfZ + 1);
        }

        int limitFor(RtsProfile profile) {
            return this == POST && profile == RtsProfile.DEFENSE ? 2 : limit;
        }

        int stoneFor(RtsProfile profile) {
            return this == POST && profile == RtsProfile.TRADE ? stone + 1 : stone;
        }

        int healthFor(RtsProfile profile) {
            if (this != POST) return health;
            return switch (profile) {
                case DEFENSE -> 46;
                case EXPLORATION -> 30;
                default -> health;
            };
        }
    }

    private static final Kind[] KINDS = Kind.values();

    private record Plot(class_2338 center, int floor, int bottom) {}

    private record Structures(class_1297[][] anchors, class_1297[][] cores) {
        Structures(Kind kind) {
            this(new class_1297[9][kind.limit + 1], new class_1297[9][kind.limit + 1]);
        }
    }

    private static final class Board {
        final class_2338[] homes = new class_2338[9];
        final class_2338[] halls = new class_2338[9];
        final EnumMap<Kind, Structures> buildings = new EnumMap<>(Kind.class);
        final boolean[][] linked = new boolean[9][Kind.POST.limit + 1];
        final ArrayList<class_1297> tickets = new ArrayList<>();

        Board(class_3218 world) {
            for (var kind : KINDS) buildings.put(kind, new Structures(kind));
            for (var entity : world.method_27909()) {
                var tags = entity.method_5752();
                if (tags.contains("krts.post_ticket")) {
                    tickets.add(entity);
                    continue;
                }
                if (!tags.contains("krts.wood_depot") && !tags.contains("krts.hall_foundation")
                        && java.util.Arrays.stream(KINDS).noneMatch(kind -> tags.contains("krts." + kind.key + "_anchor")
                                || tags.contains("krts." + kind.key + "_core"))) continue;
                int slot = index(tags, "krts.s", 8);
                if (slot == 0 || RtsAutonomy.control("#a", slot) != 1) continue;
                if (tags.contains("krts.wood_depot")) {
                    homes[slot] = entity.method_24515();
                    continue;
                }
                if (tags.contains("krts.hall_foundation")) {
                    halls[slot] = entity.method_24515();
                    continue;
                }
                for (var kind : KINDS) {
                    int building = index(tags, "krts." + kind.key, kind.limit);
                    if (building == 0) continue;
                    var group = buildings.get(kind);
                    if (tags.contains("krts." + kind.key + "_anchor")) group.anchors[slot][building] = entity;
                    if (tags.contains("krts." + kind.key + "_core") && entity.method_5805()) group.cores[slot][building] = entity;
                    break;
                }
            }
            for (int slot = 1; slot <= 8; slot++) link(slot);
        }

        private void link(int slot) {
            var posts = buildings.get(Kind.POST);
            for (int round = 0; round < Kind.POST.limit; round++) {
                for (int post = 1; post <= Kind.POST.limit; post++) {
                    if (!linked[slot][post] && posts.anchors[slot][post] != null && posts.cores[slot][post] != null) {
                        linked[slot][post] = bordersOwn(slot, posts.anchors[slot][post].method_24515(), POST_RADIUS);
                    }
                }
            }
        }

        private boolean bordersOwn(int slot, class_2338 pos, int radius) {
            if (homes[slot] == null) return false;
            if (inRange(pos, homes[slot], HOME_RADIUS + radius)) return true;
            for (int post = 1; post <= Kind.POST.limit; post++) {
                if (linked[slot][post] && inRange(pos, postPosition(slot, post), POST_RADIUS + radius)) return true;
            }
            return false;
        }

        private class_2338 postPosition(int slot, int post) {
            return buildings.get(Kind.POST).anchors[slot][post].method_24515();
        }

        private boolean canBuild(int slot, class_2338 pos, Kind kind) {
            if (kind == Kind.POST) return canClaim(slot, pos);
            if (!bordersOwn(slot, pos, 0)) return false;
            for (int other = 1; other <= 8; other++) {
                if (other == slot) continue;
                if (homes[other] != null && plotTouchesRadius(pos, kind, homes[other], HOME_RADIUS)) return false;
                for (int post = 1; post <= Kind.POST.limit; post++) {
                    if (linked[other][post] && plotTouchesRadius(pos, kind, postPosition(other, post), POST_RADIUS)) return false;
                }
            }
            return true;
        }

        private boolean canClaim(int slot, class_2338 pos) {
            if (!bordersOwn(slot, pos, POST_RADIUS) || bordersOwn(slot, pos, 0)) return false;
            for (int other = 1; other <= 8; other++) {
                if (other != slot && bordersOwn(other, pos, POST_RADIUS)) return false;
                if (other != slot) {
                    for (var kind : KINDS) {
                        if (kind == Kind.POST) continue;
                        for (int id = 1; id <= kind.limit; id++) {
                            var anchor = buildings.get(kind).anchors[other][id];
                            if (anchor != null && plotTouchesRadius(anchor.method_24515(), kind, pos, POST_RADIUS)) return false;
                        }
                    }
                }
                for (int post = 1; post <= Kind.POST.limit; post++) {
                    var existing = buildings.get(Kind.POST).anchors[other][post];
                    int spacing = other == slot ? MIN_SPACING : 2 * POST_RADIUS;
                    // Reserve construction clearance around disconnected rival posts, too.
                    // Restoring their connection must not create mutually claimed land.
                    if (existing != null && inRange(pos, existing.method_24515(), spacing)) return false;
                }
            }
            return true;
        }

        private boolean clearOfStructures(class_2338 pos, Kind kind) {
            for (int slot = 1; slot <= 8; slot++) {
                var hall = halls[slot];
                if (hall != null && clearanceOverlapsHall(pos, hall, kind)) return false;
                var home = homes[slot];
                if (home != null && overlaps(pos, home.method_10069(-2, 0, -1), kind.halfX + 2, kind.halfZ + 2)) return false;
                for (var neighbor : KINDS) {
                    var group = buildings.get(neighbor);
                    for (int id = 1; id <= neighbor.limit; id++) {
                        var anchor = group.anchors[slot][id];
                        if (anchor != null && overlaps(pos, anchor.method_24515(),
                                kind.halfX + neighbor.halfX, kind.halfZ + neighbor.halfZ)) return false;
                    }
                }
            }
            return true;
        }

        private int freeBuilding(int slot, Kind kind, RtsProfile profile) {
            var group = buildings.get(kind);
            int total = 0;
            int free = 0;
            for (int id = kind.limit; id >= 1; id--) {
                if (group.anchors[slot][id] == null) free = id;
                else total++;
            }
            return total < kind.limitFor(profile) ? free : 0;
        }

        private boolean needsChunk(int x, int z) {
            for (var kind : KINDS) {
                var group = buildings.get(kind);
                for (int slot = 1; slot <= 8; slot++) {
                    for (int id = 1; id <= kind.limit; id++) {
                        var anchor = group.anchors[slot][id];
                        if (anchor == null || group.cores[slot][id] == null) continue;
                        var pos = anchor.method_24515();
                        int px = pos.method_10263();
                        int pz = pos.method_10260();
                        if (x >= ((px - kind.halfX) >> 4) && x <= ((px + kind.halfX) >> 4)
                                && z >= ((pz - kind.halfZ) >> 4) && z <= ((pz + kind.halfZ) >> 4)) return true;
                    }
                }
            }
            return false;
        }
    }

    private RtsTerritory() {}

    public static void update(MinecraftServer server) {
        if (!active()) return;
        var world = server.method_30002();
        var board = new Board(world);
        for (int slot = 1; slot <= 8; slot++) {
            for (var kind : KINDS) {
                var group = board.buildings.get(kind);
                int total = 0;
                int linked = 0;
                for (int id = 1; id <= kind.limit; id++) {
                    var anchor = group.anchors[slot][id];
                    if (anchor == null) continue;
                    if (group.cores[slot][id] == null) {
                        remove(server, slot, kind, id);
                        continue;
                    }
                    total++;
                    boolean connected = kind == Kind.POST ? board.linked[slot][id] : board.bordersOwn(slot, anchor.method_24515(), 0);
                    if (connected) linked++;
                    RtsContext.setScore("krts_post_connected", anchor, connected ? 1 : 0);
                }
                if (kind == Kind.POST) {
                    RtsAutonomy.setControl("#posts", slot, total);
                    RtsAutonomy.setControl("#linked", slot, linked);
                } else if (kind == Kind.DISTRICT) {
                    RtsAutonomy.setControl("#districts", slot, total);
                    RtsAutonomy.setControl("#housing", slot, linked * DISTRICT_CAPACITY);
                } else if (kind == Kind.FARM) {
                    RtsAutonomy.setControl("#fields", slot, total);
                    RtsAutonomy.setControl("#fields_linked", slot, linked);
                } else {
                    RtsAutonomy.setControl("#academies", slot, total);
                    RtsAutonomy.setControl("#academy_linked", slot, linked);
                }
            }
        }
        for (var ticket : board.tickets) {
            var pos = ticket.method_24515();
            int x = pos.method_10263() >> 4;
            int z = pos.method_10260() >> 4;
            if (board.needsChunk(x, z)) continue;
            ticket.method_31472();
            world.method_17988(x, z, false);
        }
        for (var player : server.method_3760().method_14571()) {
            int action = RtsContext.score("krts_build", player);
            if (action == 0) continue;
            RtsContext.setScore("krts_build", player, 0);
            int slot = eligible(player, world);
            if (slot == 0) continue;
            if (action == 2) {
                RtsContext.setScore(AIM, player, 0);
                continue;
            }
            for (var kind : KINDS) {
                if (action == kind.action) {
                    RtsContext.setScore("krts_wood_aim", player, 0);
                    RtsContext.setScore(AIM, player, kind.aim);
                    tell(player, "Üstten kamera açıkken " + kind.label.toLowerCase(java.util.Locale.forLanguageTag("tr"))
                            + " için seçtiğin zemine sağ tıkla.");
                }
                if (action == (kind == Kind.POST ? 3 : kind.action + 1)) suggest(player, world, board, slot, kind);
            }
        }
    }

    public static boolean receiveOrder(class_3222 player, class_239 hit) {
        var server = player.method_5682();
        if (server == null || !server.method_18854() || !active()) return false;
        int aim = RtsContext.score(AIM, player);
        var kind = switch (aim) {
            case 1 -> Kind.POST;
            case 2 -> Kind.DISTRICT;
            case 3 -> Kind.FARM;
            case 4 -> Kind.ACADEMY;
            default -> null;
        };
        if (kind == null) return false;
        var world = server.method_30002();
        int slot = eligible(player, world);
        if (slot == 0) return false;
        // A click is a single construction attempt. Re-arm from the menu to try another plot.
        RtsContext.setScore(AIM, player, 0);
        if (!(hit instanceof class_3965 block) || hit.method_17783() != class_239.class_240.field_1332) {
            return reject(player, kind.label + " için doğal zemine tıkla.");
        }
        var point = block.method_17777();
        var board = new Board(world);
        var profile = RtsProfile.of(player);
        int id = board.freeBuilding(slot, kind, profile);
        if (id == 0) return reject(player, "%s sınırına ulaştın (%d/%d)."
                .formatted(kind.label, kind.limitFor(profile), kind.limitFor(profile)));
        if (kind == Kind.POST && !board.canClaim(slot, point)) {
            return reject(player, "Karakol bağlı sınırına bitişik olmalı ve başka devletin toprağına taşmamalı.");
        }
        if (kind != Kind.POST && !board.canBuild(slot, point, kind)) {
            return reject(player, kind.label + " bağlı toprağında olmalı ve yapı rakip toprağına taşmamalı.");
        }
        if (!board.clearOfStructures(point, kind)) return reject(player, "Yapının alanı mevcut bir yapıyla çakışıyor.");
        var plot = plot(new RtsTerrain.Survey(world), point.method_10263(), point.method_10260(), kind);
        if (plot == null || Math.abs(plot.center.method_10264() - point.method_10264() - 1) > 2) {
            return reject(player, kind.sizes() + " açıklık ve sağlam bir zemin gerekli.");
        }
        int wood = RtsAutonomy.control("#wood", slot);
        int stone = RtsAutonomy.control("#stone", slot);
        int iron = RtsAutonomy.control("#iron", slot);
        int stoneCost = kind.stoneFor(profile);
        if (wood < kind.wood || stone < stoneCost || iron < kind.iron) {
            String cost = "%d odun ve %d kırık taş".formatted(kind.wood, stoneCost);
            if (kind.iron > 0) cost += " ve %d demir".formatted(kind.iron);
            return reject(player, kind.label + " için " + cost + " gerekir.");
        }
        if (!build(server, world, player, plot, slot, kind, id, kind.healthFor(profile))) {
            return reject(player, kind.label + " kurulamadı. Malzemeler depoda duruyor.");
        }
        RtsAutonomy.setControl("#wood", slot, wood - kind.wood);
        RtsAutonomy.setControl("#stone", slot, stone - stoneCost);
        if (kind.iron > 0) RtsAutonomy.setControl("#iron", slot, iron - kind.iron);
        tell(player, switch (kind) {
            case POST -> "Karakol kuruldu. Devletin bağlı toprağı genişledi.";
            case DISTRICT -> "Mahalle kuruldu. Bağlı olduğu sürece birlik sınırın 3 artar.";
            case FARM -> "Tarla kuruldu. Üretim için menüden çiftçiyi çalıştır.";
            case ACADEMY -> "Talim Ocağı kuruldu. Bağlı ocakta talim 12 saniye sürer.";
        });
        return true;
    }

    private static boolean build(MinecraftServer server, class_3218 world, class_3222 player, Plot plot, int slot,
                                 Kind kind, int id, int health) {
        var center = plot.center;
        int x = center.method_10263();
        int y = center.method_10264();
        int z = center.method_10260();
        String args = ("{kind:\"%s\",label:\"%s\",health:%d,slot:%d,id:%d,x:%d,y:%d,z:%d,floor:%d,bottom:%d,height:%d,owner:%s}")
                .formatted(kind.key, kind.label, health, slot, id, x, y, z, plot.floor, plot.bottom - plot.floor, plot.floor - plot.bottom + 1,
                        RtsTerrain.ownerTag(player.method_5667()));
        RtsContext.setScore("krts_ctrl", class_9015.method_55422("#post_spawn"), 0);
        RtsTerrain.run(server, "data modify storage krts:outpost args set value " + args);
        RtsTerrain.run(server, "execute store result score #post_spawn krts_ctrl run function krts:natural/structure/build with storage krts:outpost args");
        if (RtsContext.score("krts_ctrl", "#post_spawn") != 1) {
            remove(server, slot, kind, id);
            return false;
        }
        for (int cx = (x - kind.halfX) >> 4; cx <= ((x + kind.halfX) >> 4); cx++) {
            for (int cz = (z - kind.halfZ) >> 4; cz <= ((z + kind.halfZ) >> 4); cz++) {
                if (world.method_17988(cx, cz, true)) {
                    RtsTerrain.run(server, "summon minecraft:marker %d %d %d {Tags:[\"krts.post_ticket\"]}"
                            .formatted(cx * 16 + 8, y, cz * 16 + 8));
                }
            }
        }
        return true;
    }

    private static void remove(MinecraftServer server, int slot, Kind kind, int id) {
        RtsTerrain.run(server, "kill @e[tag=krts.%s_part,tag=krts.s%d,tag=krts.%s%d]".formatted(kind.key, slot, kind.key, id));
    }

    private static void suggest(class_3222 player, class_3218 world, Board board, int slot, Kind kind) {
        if (board.homes[slot] == null || board.freeBuilding(slot, kind, RtsProfile.of(player)) == 0) {
            tell(player, kind.label + " için boş bir yapı hakkı yok.");
            return;
        }
        var survey = new RtsTerrain.Survey(world);
        var home = board.homes[slot];
        for (int post = Kind.POST.limit; post >= 0; post--) {
            var origin = post == 0 ? home : board.linked[slot][post] ? board.postPosition(slot, post) : null;
            if (origin == null) continue;
            int radius = (post == 0 ? HOME_RADIUS : POST_RADIUS) + (kind == Kind.POST ? POST_RADIUS : 0);
            int step = kind == Kind.POST ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    var pos = origin.method_10069(dx, 0, dz);
                    if (kind == Kind.POST && post > 0 && inRange(pos, home, HOME_RADIUS + POST_RADIUS)) continue;
                    if (!inRange(origin, pos, radius)) continue;
                    if (!board.canBuild(slot, pos, kind)) continue;
                    if (!board.clearOfStructures(pos, kind)) continue;
                    var candidate = plot(survey, pos.method_10263(), pos.method_10260(), kind);
                    if (candidate == null) continue;
                    tell(player, "%s yeri · X %d · Y %d · Z %d".formatted(kind.label, candidate.center.method_10263(),
                            candidate.center.method_10264() - 1, candidate.center.method_10260()));
                    return;
                }
            }
        }
        tell(player, "%s için bağlı toprakta uygun bir %s açıklık bulunamadı.".formatted(kind.label, kind.sizes()));
    }

    private static Plot plot(RtsTerrain.Survey survey, int x, int z, Kind kind) {
        int center = survey.surface(x, z);
        int highest = center;
        int lowest = center;
        if (center == Integer.MIN_VALUE) return null;
        for (int dx = -kind.halfX; dx <= kind.halfX; dx++) {
            for (int dz = -kind.halfZ; dz <= kind.halfZ; dz++) {
                int y = survey.surface(x + dx, z + dz);
                if (y == Integer.MIN_VALUE || !survey.walkable(new class_2338(x + dx, y, z + dz))) return null;
                highest = Math.max(highest, y);
                lowest = Math.min(lowest, y);
                if (highest - lowest > 4) return null;
            }
        }
        var foundation = new class_2338(x, center, z);
        if (kind == Kind.FARM && RtsSupply.fieldBenches(survey, foundation).isEmpty()) return null;
        if (kind == Kind.ACADEMY && RtsDrill.naturalMuster(survey, foundation).isEmpty()) return null;
        return new Plot(foundation, highest, lowest - 1);
    }

    private static int eligible(class_3222 player, class_3218 world) {
        int slot = RtsContext.score("krts_slot", player);
        if (player.method_7325() || player.method_37908() != world || slot < 1 || slot > 8
                || RtsAutonomy.control("#a", slot) != 1
                || RtsContext.score("krts_identity", player.method_5667().toString()) != slot) return 0;
        return slot;
    }

    private static boolean active() {
        return RtsContext.score("krts_ctrl", "#natural_match") == 1 && RtsContext.score("krts_ctrl", "#phase") == 2;
    }

    private static int index(java.util.Set<String> tags, String prefix, int limit) {
        for (int index = 1; index <= limit; index++) if (tags.contains(prefix + index)) return index;
        return 0;
    }

    private static boolean inRange(class_2338 pos, class_2338 center, int radius) {
        long dx = (long) pos.method_10263() - center.method_10263();
        long dz = (long) pos.method_10260() - center.method_10260();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static boolean overlaps(class_2338 pos, class_2338 anchor, int dx, int dz) {
        return Math.abs((long) pos.method_10263() - anchor.method_10263()) <= dx
                && Math.abs((long) pos.method_10260() - anchor.method_10260()) <= dz;
    }

    private static boolean plotTouchesRadius(class_2338 plot, Kind kind, class_2338 center, int radius) {
        long dx = Math.max(0, Math.abs((long) plot.method_10263() - center.method_10263()) - kind.halfX);
        long dz = Math.max(0, Math.abs((long) plot.method_10260() - center.method_10260()) - kind.halfZ);
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static boolean clearanceOverlapsHall(class_2338 pos, class_2338 foundation, Kind kind) {
        long x = (long) pos.method_10263() - foundation.method_10263();
        long z = (long) pos.method_10260() - foundation.method_10260();
        // The display position is the northwest corner of the hall's foundation.
        return x >= -1 - kind.halfX && x <= 11 + kind.halfX && z >= -1 - kind.halfZ && z <= 10 + kind.halfZ;
    }

    private static boolean reject(class_3222 player, String message) {
        tell(player, message);
        return true;
    }

    private static void tell(class_3222 player, String message) {
        player.method_7353(class_2561.method_43470("[RTS] " + message), false);
    }
}
