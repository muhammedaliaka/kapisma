#include "finders.h"
#include "util.h"
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
/* Faz 0 sanity: verilen seed icin spawn, biyom ve 1000 blok icindeki koy sayisi (MC 1.21.1) */
int main(int argc, char **argv) {
    Generator g; setupGenerator(&g, MC_1_21_1, 0);
    for (int i = 1; i < argc; i++) {
        uint64_t seed = strtoull(argv[i], NULL, 10);
        applySeed(&g, DIM_OVERWORLD, seed);
        Pos sp = getSpawn(&g);
        int b = getBiomeAt(&g, 1, sp.x, 63, sp.z);
        int villages = 0; double nearest = 1e9; Pos np = {0,0};
        int r = 1000 / 512 + 2;  /* village bolge boyutu 32 chunk = 512 blok */
        for (int rz = (sp.z/512)-r; rz <= (sp.z/512)+r; rz++)
          for (int rx = (sp.x/512)-r; rx <= (sp.x/512)+r; rx++) {
            Pos p;
            if (!getStructurePos(Village, MC_1_21_1, seed, rx, rz, &p)) continue;
            double d = hypot(p.x - sp.x, p.z - sp.z);
            if (d > 1000) continue;
            if (!isViableStructurePos(Village, &g, p.x, p.z, 0)) continue;
            villages++; if (d < nearest) { nearest = d; np = p; }
          }
        printf("seed=%lld spawn=(%d,%d) biyom=%s koy(<=1000)=%d en_yakin=%.0f blok @(%d,%d)\n",
               (long long)seed, sp.x, sp.z, biome2str(MC_1_21_1, b), villages, villages?nearest:-1.0, np.x, np.z);
    }
    return 0;
}
