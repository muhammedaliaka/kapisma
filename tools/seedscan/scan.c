/*
 * Kapisma - Asama 1 seed taramasi (cubiomes, Minecraft 1.21.1)
 *
 * Kullanim: scan.exe <taban> <adet> [min_skor]
 *   Seed'ler splitmix64(taban + i) ile uretilir (tekrarlanabilir).
 *   Kriterlere uyan her seed icin stdout'a bir JSON satiri yazar.
 *
 * Kriterler (UHC/Tombala icin spawn merkezli 2000x2000 kutu = world border):
 *   - Spawn karada ve bir sinifa ait (ova/orman/taiga_kar/col_mesa/savan_cangil/dag)
 *   - Kutuda >= 1 koy (spawn'a Chebyshev <= 850; border kenarina yapisma yok)
 *   - Okyanus orani < %25, >= 3 farkli biyom, nehir var
 *   - Spawn cevresinde (250 blok) agac biyomu var
 * Not: cubiomes koy konumlarinda yanlis pozitif verebilir -> Asama 2 gercek sunucuyla dogrular.
 */
#include "finders.h"
#include "util.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#define BOX   1000   /* border yaricapi */
#define VMAX   850   /* yapi icin en fazla uzaklik (Chebyshev) */

static uint64_t splitmix64(uint64_t *x) {
    uint64_t z = (*x += 0x9E3779B97F4A7C15ULL);
    z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
    z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
    return z ^ (z >> 31);
}

static const char *classify(int b) {
    switch (b) {
    case plains: case sunflower_plains: case meadow:
        return "ova";
    case forest: case birch_forest: case old_growth_birch_forest: case dark_forest:
    case flower_forest: case cherry_grove:
        return "orman";
    case taiga: case snowy_taiga: case old_growth_pine_taiga: case old_growth_spruce_taiga:
    case snowy_plains:
        return "taiga_kar";
    case desert: case badlands: case eroded_badlands: case wooded_badlands:
        return "col_mesa";
    case savanna: case savanna_plateau: case windswept_savanna: case jungle:
    case sparse_jungle: case bamboo_jungle:
        return "savan_cangil";
    case windswept_hills: case windswept_forest: case windswept_gravelly_hills:
    case grove: case snowy_slopes:
        return "dag";
    default:
        return NULL;
    }
}

static int isTreeBiome(int b) {
    switch (b) {
    case forest: case birch_forest: case old_growth_birch_forest: case dark_forest:
    case flower_forest: case cherry_grove: case taiga: case snowy_taiga:
    case old_growth_pine_taiga: case old_growth_spruce_taiga: case jungle:
    case sparse_jungle: case bamboo_jungle: case savanna: case windswept_forest:
    case grove: case swamp: case mangrove_swamp: case meadow:
        return 1;
    default:
        return 0;
    }
}

static int cheb(int x, int z, Pos sp) {
    int dx = abs(x - sp.x), dz = abs(z - sp.z);
    return dx > dz ? dx : dz;
}

/* kutu icindeki gecerli yapilari say; ilk maxout tanesini out'a yaz */
static int findStructs(Generator *g, uint64_t seed, int stype, Pos sp, Pos *out, int maxout) {
    StructureConfig sc;
    if (!getStructureConfig(stype, MC_1_21_1, &sc)) return 0;
    int rs = sc.regionSize * 16;
    int x0 = (sp.x - VMAX) / rs - 1, x1 = (sp.x + VMAX) / rs + 1;
    int z0 = (sp.z - VMAX) / rs - 1, z1 = (sp.z + VMAX) / rs + 1;
    int n = 0;
    for (int rz = z0; rz <= z1; rz++)
        for (int rx = x0; rx <= x1; rx++) {
            Pos p;
            if (!getStructurePos(stype, MC_1_21_1, seed, rx, rz, &p)) continue;
            if (cheb(p.x, p.z, sp) > VMAX) continue;
            if (!isViableStructurePos(stype, g, p.x, p.z, 0)) continue;
            if (n < maxout && out) out[n] = p;
            n++;
        }
    return n;
}

int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr, "kullanim: scan <taban> <adet> [min_skor]\n"); return 1; }
    uint64_t base = strtoull(argv[1], NULL, 10);
    long count = atol(argv[2]);
    double minScore = argc > 3 ? atof(argv[3]) : 0.0;

    Generator g;
    setupGenerator(&g, MC_1_21_1, 0);
    uint64_t state = base;

    for (long i = 0; i < count; i++) {
        uint64_t seed = splitmix64(&state);
        applySeed(&g, DIM_OVERWORLD, seed);

        Pos sp = getSpawn(&g);
        int sb = getBiomeAt(&g, 1, sp.x, 256, sp.z);    /* y=256: yuzey biyomu (magara biyomlari hari) */
        const char *cls = classify(sb);
        if (!cls) continue;

        /* koy: zorunlu */
        Pos vill[8];
        int nv = findStructs(&g, seed, Village, sp, vill, 8);
        if (nv < 1) continue;

        /* biyom izgarasi: 2000x2000 kutu, 64 blok adim */
        int ocean = 0, total = 0, river = 0, mush = 0;
        unsigned char seen[256]; memset(seen, 0, sizeof seen);
        int distinct = 0;
        for (int dz = -BOX; dz <= BOX; dz += 64)
            for (int dx = -BOX; dx <= BOX; dx += 64) {
                int b = getBiomeAt(&g, 1, sp.x + dx, 256, sp.z + dz);
                total++;
                if (isOceanic(b)) ocean++;
                if (b == river || b == frozen_river) river++;
                if (b == mushroom_fields) mush++;
                int k = b & 255;
                if (!seen[k]) { seen[k] = 1; distinct++; }
            }
        double ofrac = (double)ocean / total;
        if (ofrac >= 0.25 || distinct < 3 || river < 1 || mush > 0) continue;

        /* agac: spawn cevresi 250 blok, 50 blok adim */
        int trees = 0;
        for (int dz = -250; dz <= 250; dz += 50)
            for (int dx = -250; dx <= 250; dx += 50)
                if (isTreeBiome(getBiomeAt(&g, 1, sp.x + dx, 256, sp.z + dz))) trees++;
        if (trees < 4) continue;

        /* diger yapilar (puanlama) */
        int portals = findStructs(&g, seed, Ruined_Portal, sp, NULL, 0);
        int other = findStructs(&g, seed, Mineshaft, sp, NULL, 0)
                  + findStructs(&g, seed, Outpost, sp, NULL, 0)
                  + findStructs(&g, seed, Desert_Pyramid, sp, NULL, 0)
                  + findStructs(&g, seed, Jungle_Pyramid, sp, NULL, 0)
                  + findStructs(&g, seed, Igloo, sp, NULL, 0)
                  + findStructs(&g, seed, Swamp_Hut, sp, NULL, 0);

        /* yon dengesi: koylerin kapladigi ceyrek sayisi */
        int quad = 0, qmask = 0;
        for (int k = 0; k < nv && k < 8; k++) {
            int q = (vill[k].x >= sp.x ? 1 : 0) | (vill[k].z >= sp.z ? 2 : 0);
            if (!(qmask & (1 << q))) { qmask |= 1 << q; quad++; }
        }
        double nearest = 1e9;
        for (int k = 0; k < nv && k < 8; k++) {
            double d = hypot(vill[k].x - sp.x, vill[k].z - sp.z);
            if (d < nearest) nearest = d;
        }

        double score = 10.0 * (nv > 4 ? 4 : nv) + 4.0 * (portals > 2 ? 2 : portals)
                     + 3.0 * (other > 4 ? 4 : other) + 2.0 * (distinct > 10 ? 10 : distinct)
                     + 3.0 * quad + (river > 0 ? 5 : 0) + (trees > 20 ? 6 : trees * 0.3)
                     - 40.0 * ofrac - nearest / 100.0;
        if (score < minScore) continue;

        printf("{\"seed\":%lld,\"spawn\":[%d,%d],\"biome\":\"%s\",\"class\":\"%s\","
               "\"villages\":[", (long long)seed, sp.x, sp.z, biome2str(MC_1_21_1, sb), cls);
        for (int k = 0; k < nv && k < 8; k++) printf("%s[%d,%d]", k ? "," : "", vill[k].x, vill[k].z);
        printf("],\"n_villages\":%d,\"nearest_village\":%.0f,\"portals\":%d,\"other_structs\":%d,"
               "\"ocean\":%.3f,\"biomes\":%d,\"river\":%d,\"trees\":%d,\"quadrants\":%d,\"score\":%.1f}\n",
               nv, nearest, portals, other, ofrac, distinct, river, trees, quad, score);
        fflush(stdout);
    }
    return 0;
}
