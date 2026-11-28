package kapisma.rts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.class_2338;
import net.minecraft.class_2558;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_2902.class_2903;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3481;
import net.minecraft.class_3486;
import net.minecraft.class_9015;
import net.minecraft.server.MinecraftServer;

/** Surveys loaded Overworld terrain and binds founding choices to a capital match. */
public final class RtsTerrain {
    private static final int SCAN_RADIUS = 80;
    private static final int CANDIDATE_SPACING = 16;
    private static final int DENSE_SCAN_RADIUS = 104;
    private static final int DENSE_SPACING = 8;
    private static final int MIN_SEPARATION = 40;
    private static final int RESULT_LIMIT = 12;
    private static final int REQUEST_INTERVAL = 10;
    private static final int FOUNDING_SECONDS = 180;
    private static final int ARENA_MARGIN = 32;
    private static final String[] ORIGINS = {"Köylüler", "Nether", "Yağmacılar", "Gece Halkı"};
    private static final String[] PROFILES = RtsProfile.labels();
    private static final WeakHashMap<MinecraftServer, RtsTerrain> ACTIVE = new WeakHashMap<>();
    private List<Site> foundingSites = List.of();
    private final int[] reservedBy = new int[RESULT_LIMIT + 1];
    private List<UUID> founders = List.of();
    private final HashMap<UUID, Integer> pendingArrivals = new HashMap<>();

    public RtsTerrain() {}

    public void processRequests(MinecraftServer server) {
        boolean foundingWorld = RtsContext.score("krts_ctrl", "#founding") == 1;
        if (foundingWorld) {
            ACTIVE.put(server, this);
            processFounding(server);
        }
        for (var player : server.method_3760().method_14571()) {
            int cooldown = RtsContext.score("krts_survey_cd", player);
            if (cooldown > 0) RtsContext.setScore("krts_survey_cd", player, cooldown - 1);
            if (RtsContext.score("krts_survey", player) != 1) continue;
            RtsContext.setScore("krts_survey", player, 0);
            if (RtsContext.score("krts_ctrl", "#phase") != 0 || !player.method_5752().contains("krts.host")) continue;
            if (cooldown > 0) {
                tell(player, "Yeniden taramak için " + cooldown + " saniye bekle.");
                continue;
            }
            var world = server.method_30002();
            if (player.method_37908() != world) {
                tell(player, "Arazi taraması için Overworld’e dön.");
                continue;
            }
            RtsContext.setScore("krts_survey_cd", player, REQUEST_INTERVAL);
            var sites = new Survey(world).find(origin(world, player, foundingWorld));
            tell(player, "Arazi taraması · " + sites.size() + " kuruluş noktası.");
            for (var site : sites) tell(player, description(world, site));
            if (sites.isEmpty()) tell(player, "Yer değiştirdikten sonra yüklü çevreyi yeniden tarayabilirsin.");
        }
    }

    private static class_2338 origin(class_3218 world, class_3222 player, boolean foundingWorld) {
        return foundingWorld ? world.method_43126() : player.method_24515();
    }

    private void processFounding(MinecraftServer server) {
        int seconds = RtsContext.score("krts_ctrl", "#found_left");
        boolean natural = RtsContext.score("krts_ctrl", "#natural_match") == 1;
        var players = server.method_3760().method_14571();
        var world = server.method_30002();
        if (seconds < 0) {
            clearDraft();
            setSeconds(0);
            return;
        }
        if (natural && RtsContext.score("krts_ctrl", "#phase") != 0) {
            attachFounders(server);
            return;
        }
        if (seconds > 0 && founders.isEmpty()) {
            setSeconds(0);
            seconds = 0;
        }
        boolean started = false;
        for (var player : players) {
            if (take(player, "krts_found_start") == 1 && seconds == 0 && player.method_5752().contains("krts.host")
                    && player.method_37908() == world) {
                var participants = players.stream().filter(peer -> !peer.method_7325()
                        && peer.method_37908() == world).toList();
                if (participants.size() < 2 || participants.size() > 8) {
                    tell(player, "Kuruluş için Overworld’de 2–8 oyuncu gerekir.");
                    continue;
                }
                var survey = new Survey(world);
                var sites = survey.find(world.method_43126());
                if (natural && sites.size() < participants.size()) {
                    sites = survey.find(world.method_43126(), DENSE_SCAN_RADIUS, DENSE_SPACING);
                }
                if (sites.size() < participants.size()) {
                    tell(player, "Kuruluş için yeterli güvenli bölge bulunamadı.");
                    continue;
                }
                foundingSites = sites;
                founders = participants.stream().map(class_3222::method_5667).toList();
                java.util.Arrays.fill(reservedBy, 0);
                pendingArrivals.clear();
                seconds = FOUNDING_SECONDS;
                started = true;
                for (var peer : participants) {
                    for (String objective : List.of("krts_found_site", "krts_origin", "krts_profile",
                            "krts_found", "krts_found_origin", "krts_found_profile")) {
                        RtsContext.setScore(objective, peer, 0);
                    }
                    if (natural) {
                        var key = class_9015.method_55422(peer.method_5667().toString());
                        RtsContext.setScore("krts_origin", key, 0);
                        RtsContext.setScore("krts_profile", key, 0);
                    }
                    tell(peer, "Kuruluş başladı · 180 saniye. /trigger krts_found set 99");
                    showChoices(world, peer, founders.indexOf(peer.method_5667()) + 1, seconds);
                }
            }
            int site = take(player, "krts_found");
            int race = take(player, "krts_found_origin");
            int profile = take(player, "krts_found_profile");
            int slot = founders.indexOf(player.method_5667()) + 1;
            if (seconds < 1 || slot < 1 || player.method_7325() || player.method_37908() != world) continue;
            if (site == 99) showChoices(world, player, slot, seconds);
            choose(player, "krts_origin", ORIGINS, race, "Köken", natural);
            choose(player, "krts_profile", PROFILES, profile, "Devlet profili", natural);
        }
        if (seconds > 0) {
            if (seconds == 1) {
                finalizeRegions();
                if (natural) {
                    setSeconds(0);
                    openNaturalMatch(server);
                    clearDraft();
                    return;
                }
            }
            setSeconds(started ? FOUNDING_SECONDS : seconds - 1);
        }
        for (var player : players) {
            if (player.method_7325() || player.method_37908() != world) continue;
            Integer site = pendingArrivals.remove(player.method_5667());
            if (site == null) continue;
            if (RtsContext.score("krts_found_site", player) == 0) {
                tell(player, "Boş kalan doğrulanmış bir bölge otomatik ayrıldı.");
            }
            RtsContext.setScore("krts_found_site", player, site);
            var destination = foundingSites.get(site - 1).position();
            player.method_5814(destination.method_10263() + 0.5, destination.method_10264(),
                    destination.method_10260() + 0.5);
            tell(player, "Kuruluş seçim süresi doldu.");
        }
    }

    private void clearDraft() {
        foundingSites = List.of();
        founders = List.of();
        pendingArrivals.clear();
        java.util.Arrays.fill(reservedBy, 0);
    }

    private void openNaturalMatch(MinecraftServer server) {
        var world = server.method_30002();
        var center = world.method_43126();
        var survey = new Survey(world);
        int radius = SCAN_RADIUS;
        for (var uuid : founders) {
            var position = foundingSites.get(pendingArrivals.get(uuid) - 1).position();
            if (!survey.hasSafeFootprint(position)) {
                run(server, "say [RTS] Bir kuruluş noktası artık güvenli veya yüklü değil. Kuruluşu yeniden başlatın.");
                return;
            }
            radius = Math.max(radius, Math.abs(position.method_10263() - center.method_10263()));
            radius = Math.max(radius, Math.abs(position.method_10260() - center.method_10260()));
        }
        run(server, "function krts:match/settings");
        run(server, "data modify storage krts:arena center set value {x:%d,y:%d,z:%d,width:%d}"
                .formatted(center.method_10263(), center.method_10264(), center.method_10260(), (radius + ARENA_MARGIN) * 2));
        for (int i = 0; i < founders.size(); i++) {
            var uuid = founders.get(i);
            int slot = i + 1;
            int site = pendingArrivals.get(uuid);
            var position = foundingSites.get(site - 1).position();
            int x = position.method_10263();
            int y = position.method_10264();
            int z = position.method_10260();
            var hall = survey.hallPlot(position);
            int origin = Math.max(1, RtsContext.score("krts_origin", uuid.toString()));
            int profile = Math.max(1, RtsContext.score("krts_profile", uuid.toString()));
            run(server, ("data modify storage krts:arena h%d set value "
                    + "{slot:%d,site:%d,origin:%d,profile:%d,x:%d,y:%d,z:%d,bx:%d,cz:%d,entry_z:%d,"
                    + "hall_x:%d,hall_y:%d,hall_z:%d,foundation_y:%d,foundation_height:%d,min_x:%d,min_z:%d,max_x:%d,max_z:%d,owner:%s}")
                    .formatted(slot, slot, site, origin, profile, x, y, z, x + 2, z - 1, z - 2,
                            hall.x(), hall.y(), hall.z(), hall.bottom() - hall.y(), hall.y() - hall.bottom() + 1,
                            Math.min(x - 2, hall.x() - 6), Math.min(z - 2, hall.z() - 1),
                            Math.max(x + 2, hall.x() + 6), Math.max(z + 2, hall.z() + 10), ownerTag(uuid)));
            RtsContext.setScore("krts_identity", class_9015.method_55422(uuid.toString()), slot);
        }
        RtsContext.setScore("krts_ctrl", class_9015.method_55422("#slot"), founders.size());
        run(server, "function krts:natural/open");
        attachFounders(server);
    }

    static String ownerTag(UUID uuid) {
        long high = uuid.getMostSignificantBits();
        long low = uuid.getLeastSignificantBits();
        return "[I;%d,%d,%d,%d]".formatted((int) (high >>> 32), (int) high, (int) (low >>> 32), (int) low);
    }

    private static void attachFounders(MinecraftServer server) {
        int phase = RtsContext.score("krts_ctrl", "#phase");
        if (phase != 1 && phase != 2 && phase != 4) return;
        for (var player : server.method_3760().method_14571()) {
            int slot = RtsContext.score("krts_identity", player.method_5667().toString());
            if (slot < 1 || slot > 8 || RtsContext.score("krts_slot", player) == slot
                    || RtsContext.score("krts_ctrl", "#a" + slot) != 1) continue;
            if (RtsContext.score("krts_found_site", player) == 0) {
                tell(player, "Boş kalan doğrulanmış bir bölge otomatik ayrıldı.");
            }
            RtsContext.setScore("krts_slot", player, slot);
            var source = server.method_3739().method_9232(player).method_9217();
            server.method_3734().method_44252(source, "function krts:natural/arrive with storage krts:arena h" + slot);
            tell(player, "Kuruluş seçim süresi doldu.");
        }
    }

    static void run(MinecraftServer server, String command) {
        server.method_3734().method_44252(server.method_3739().method_9217(), command);
    }

    private void finalizeRegions() {
        for (int slot = 1; slot <= founders.size(); slot++) {
            int selected = 0;
            int free = 0;
            for (int site = 1; site <= foundingSites.size(); site++) {
                if (reservedBy[site] == 0 && free == 0) free = site;
                if (reservedBy[site] == slot) {
                    selected = site;
                    break;
                }
            }
            if (selected == 0) {
                if (free == 0) continue;
                selected = free;
                reservedBy[selected] = slot;
            }
            pendingArrivals.put(founders.get(slot - 1), selected);
        }
    }

    public static void processRegionChoice(class_3222 player, int site) {
        if (site < 1 || site > RESULT_LIMIT || RtsContext.score("krts_ctrl", "#founding") != 1) return;
        RtsContext.setScore("krts_found", player, 0);
        var server = player.method_5682();
        var terrain = ACTIVE.get(server);
        if (terrain == null || player.method_7325() || player.method_37908() != server.method_30002()
                || RtsContext.score("krts_ctrl", "#found_left") < 1 || site > terrain.foundingSites.size()) return;
        int slot = terrain.founders.indexOf(player.method_5667()) + 1;
        if (slot > 0) terrain.reserve(player, slot, site);
    }

    private void reserve(class_3222 player, int slot, int site) {
        if (reservedBy[site] != 0 && reservedBy[site] != slot) {
            tell(player, "Bölge " + site + " başka bir devlet için ayrıldı.");
            return;
        }
        int previous = RtsContext.score("krts_found_site", player);
        if (previous > 0 && previous < reservedBy.length && reservedBy[previous] == slot) reservedBy[previous] = 0;
        reservedBy[site] = slot;
        RtsContext.setScore("krts_found_site", player, site);
        tell(player, "Bölge " + site + " devletine ayrıldı.");
    }

    private void showChoices(class_3218 world, class_3222 player, int slot, int seconds) {
        tell(player, "Kuruluş seçimleri · " + seconds + " saniye.");
        for (int i = 1; i <= foundingSites.size(); i++) {
            var line = class_2561.method_43470("[RTS] Bölge " + i + " · " + description(world, foundingSites.get(i - 1)));
            if (reservedBy[i] == 0) line.method_10852(button(" [Ayır]", "krts_found", i));
            else line.method_10852(class_2561.method_43470(reservedBy[i] == slot ? " · Seçimin" : " · Ayrıldı"));
            player.method_7353(line, false);
        }
        showGroup(player, "Köken", "krts_found_origin", ORIGINS);
        showGroup(player, "Devlet profili", "krts_found_profile", PROFILES);
        if (RtsContext.score("krts_ctrl", "#natural_match") == 1) {
            for (var profile : RtsProfile.values()) {
                tell(player, profile.label + " · " + profile.description);
            }
        }
    }

    private static void showGroup(class_3222 player, String label, String trigger, String[] options) {
        var line = class_2561.method_43470("[RTS] " + label + " ");
        for (int i = 0; i < options.length; i++) line.method_10852(button("[" + options[i] + "] ", trigger, i + 1));
        player.method_7353(line, false);
    }

    private static class_2561 button(String label, String trigger, int value) {
        return class_2561.method_43470(label).method_27694(style -> style.method_10958(
                new class_2558(class_2558.class_2559.field_11750, "/trigger " + trigger + " set " + value)));
    }

    private static void choose(class_3222 player, String objective, String[] choices, int value, String label,
                               boolean rememberOwner) {
        if (value < 1 || value > choices.length) return;
        RtsContext.setScore(objective, player, value);
        if (rememberOwner) RtsContext.setScore(objective, class_9015.method_55422(player.method_5667().toString()), value);
        tell(player, label + " · " + choices[value - 1]);
    }

    private static int take(class_3222 player, String objective) {
        int value = RtsContext.score(objective, player);
        if (value != 0) RtsContext.setScore(objective, player, 0);
        return value;
    }

    private static void setSeconds(int seconds) {
        RtsContext.setScore("krts_ctrl", class_9015.method_55422("#found_left"), seconds);
    }

    private static String description(class_3218 world, Site site) {
        var pos = site.position();
        return "Kuruluş noktası · X " + pos.method_10263() + " · Y " + pos.method_10264() + " · Z " + pos.method_10260()
                + " · " + world.method_23753(pos).method_55840() + " · su " + site.water()
                + " · odun " + site.wood() + " · taş " + site.stone() + " · yükseklik farkı " + site.relief();
    }

    private static void tell(class_3222 player, String text) {
        player.method_7353(class_2561.method_43470("[RTS] " + text), false);
    }

    private record Site(class_2338 position, int water, int wood, int stone, int relief) {
        int score() {
            int resourceTypes = (water > 0 ? 1 : 0) + (wood > 0 ? 1 : 0) + (stone > 0 ? 1 : 0);
            return resourceTypes * 100 + Math.min(relief, 12);
        }

        boolean closeTo(Site other) {
            int dx = position.method_10263() - other.position.method_10263();
            int dz = position.method_10260() - other.position.method_10260();
            return dx * dx + dz * dz < MIN_SEPARATION * MIN_SEPARATION;
        }
    }

    private record HallPlot(int x, int y, int z, int bottom, int cost) {}

    private record GroundColumn(int ground, boolean obstacle) {}

    static final class Survey {
        private final class_3218 world;
        private final HashMap<Long, class_2818> chunks = new HashMap<>();

        Survey(class_3218 world) {
            this.world = world;
        }

        List<Site> find(class_2338 center) {
            return find(center, SCAN_RADIUS, CANDIDATE_SPACING);
        }

        List<Site> find(class_2338 center, int radius, int spacing) {
            var candidates = new ArrayList<Site>();
            for (int x = -radius; x <= radius; x += spacing) {
                for (int z = -radius; z <= radius; z += spacing) {
                    var site = sample(center.method_10263() + x, center.method_10260() + z);
                    if (site != null && site.score() >= 100) candidates.add(site);
                }
            }
            candidates.sort(Comparator.comparingInt(Site::score).reversed());
            var selected = new ArrayList<Site>(RESULT_LIMIT);
            for (var site : candidates) {
                if (selected.stream().anyMatch(site::closeTo)) continue;
                selected.add(site);
                if (selected.size() == RESULT_LIMIT) break;
            }
            return selected;
        }

        private Site sample(int x, int z) {
            int y = surface(x, z);
            if (y == Integer.MIN_VALUE) return null;
            var site = new class_2338(x, y, z);
            if (!hasSafeFootprint(site)) return null;

            int water = 0;
            int wood = 0;
            int stone = 0;
            int low = y;
            int high = y;
            int covered = 0;
            // Count sampled columns with water, trunks and shallow stone.
            for (int dx = -20; dx <= 20; dx += 2) {
                for (int dz = -20; dz <= 20; dz += 2) {
                    int ground = surface(x + dx, z + dz);
                    if (ground == Integer.MIN_VALUE) continue;
                    if (!world.method_8621().method_11952(new class_2338(x + dx, ground, z + dz))) continue;
                    covered++;
                    low = Math.min(low, ground);
                    high = Math.max(high, ground);
                    var top = new class_2338(x + dx, ground - 1, z + dz);
                    var surface = state(top);
                    if (surface.method_26227().method_15767(class_3486.field_15517)) water++;
                    if (surface.method_26164(class_3481.field_39030)) wood++;
                    for (int depth = 0; depth < 4; depth++) {
                        if (state(top.method_10069(0, -depth, 0)).method_26164(class_3481.field_25806)) {
                            stone++;
                            break;
                        }
                    }
                }
            }
            if (covered < 400) return null;
            return new Site(site, water, wood, stone, high - low);
        }

        private boolean hasSafeFootprint(class_2338 site) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    var foot = site.method_10069(dx, 0, dz);
                    if (!walkable(foot)) return false;
                }
            }
            return true;
        }

        private HallPlot hallPlot(class_2338 site) {
            int base = site.method_10264();
            int homeX = site.method_10263();
            int homeZ = site.method_10260();
            var columns = new HashMap<Long, GroundColumn>();
            HallPlot best = null;
            // A hall can sit behind or to the side of the rally area. Favor an unobstructed
            // footprint and an open approach, even on sites surrounded by spruce trees.
            for (int dx : new int[]{0, -8, 8, -11, 11}) {
                for (int dz : new int[]{3, 5, 7, -1, -5, -12, -14}) {
                    if (Math.abs(dx) < 8 && dz <= 2 && dz + 8 >= -2) continue;
                    int farX = Math.abs(dx) + 5;
                    int farZ = Math.max(Math.abs(dz), Math.abs(dz + 8));
                    if (farX * farX + farZ * farZ > 19 * 19) continue;
                    var plot = measureHall(columns, homeX + dx, homeZ + dz, base, Math.abs(dx) + Math.abs(dz));
                    if (best == null || plot.cost() < best.cost()) best = plot;
                }
            }
            return best;
        }

        private HallPlot measureHall(HashMap<Long, GroundColumn> columns, int center, int front, int base, int distance) {
            int highest = Integer.MIN_VALUE;
            int lowest = Integer.MAX_VALUE;
            int obstructions = 0;
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -6; dz <= 8; dz++) {
                    boolean approach = dz < 0;
                    if (approach && Math.abs(dx) > 3) continue;
                    int x = center + dx;
                    int z = front + dz;
                    long key = ((long) x << 32) | Integer.toUnsignedLong(z);
                    var column = columns.computeIfAbsent(key, unused -> readHallColumn(x, z));
                    if (column.ground() == Integer.MIN_VALUE) {
                        obstructions += approach ? 8 : 40;
                        continue;
                    }
                    if (approach) {
                        if (column.obstacle()) obstructions += 9;
                        continue;
                    }
                    if (column.obstacle()) obstructions += 18;
                    highest = Math.max(highest, column.ground());
                    lowest = Math.min(lowest, column.ground());
                }
            }
            if (highest == Integer.MIN_VALUE) highest = lowest = base;
            int cost = (highest - lowest) * 5 + Math.abs(highest - base) * 5 + obstructions + distance;
            return new HallPlot(center, highest, front, lowest, cost);
        }

        private GroundColumn readHallColumn(int x, int z) {
            int top = surface(x, z);
            if (top == Integer.MIN_VALUE) return new GroundColumn(top, true);
            int ground = top;
            for (int step = 0; step < 16; step++) {
                var soil = state(new class_2338(x, ground - 1, z));
                if (soil == null || (!soil.method_26164(class_3481.field_39030) && soil.method_26227().method_15769())) break;
                ground--;
            }
            return new GroundColumn(ground, top != ground);
        }

        boolean walkable(class_2338 foot) {
            if (!world.method_8621().method_11952(foot)) return false;
            var ground = foot.method_10069(0, -1, 0);
            var support = state(ground);
            return support != null && support.method_26234(world, ground) && support.method_26227().method_15769()
                    && !support.method_26164(class_3481.field_39030)
                    && isClear(foot) && isClear(foot.method_10069(0, 1, 0));
        }

        private boolean isClear(class_2338 pos) {
            var state = state(pos);
            return state != null && state.method_26227().method_15769() && state.method_26220(world, pos).method_1110();
        }

        class_2680 state(class_2338 pos) {
            var chunk = chunk(pos.method_10263(), pos.method_10260());
            return chunk == null ? null : chunk.method_8320(pos);
        }

        int surface(int x, int z) {
            var chunk = chunk(x, z);
            return chunk == null ? Integer.MIN_VALUE : chunk.method_12005(class_2903.field_13203, x & 15, z & 15) + 1;
        }

        private class_2818 chunk(int x, int z) {
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            long key = (long) chunkX << 32 | Integer.toUnsignedLong(chunkZ);
            if (!chunks.containsKey(key)) chunks.put(key, world.method_14178().method_21730(chunkX, chunkZ));
            return chunks.get(key);
        }
    }
}
