package tr.kapisma.game;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.MapColor.Brightness;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Experimental 1.21.1 adapter; opt-in UHC preparation only.
 * Only the server thread may advance it. Caller must not publish the map
 * before completion. The caller must cancel when its arena is closed.
 */
final class IncrementalBiomePreview {
    static final boolean ENABLED = "1".equals(System.getenv("KAPISMA_INCREMENTAL_MAPS"));
    // Expensive vanilla comparison for isolated correctness runs only, never benchmark runs.
    static final boolean PARITY_PROOF = ENABLED && "1".equals(System.getenv("KAPISMA_MAP_PARITY_PROOF"));
    static final int MAX_OPERATIONS = 512;
    static final int MAX_PAINT_OPERATIONS = 4096;
    static final long BUDGET_NS = 8_000_000L;

    private final ServerLevel level;
    private final ItemStack item;
    private final MapItemSavedData data;
    private final MapPreviewWork work;
    private final byte[] originalColors;
    private boolean consumed;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    IncrementalBiomePreview(ServerLevel level, ItemStack item) {
        this.level = Objects.requireNonNull(level);
        this.item = Objects.requireNonNull(item);
        requireServerThread();
        data = Objects.requireNonNull(MapItem.getSavedData(item, level), "map data");
        if (data.dimension != level.dimension()) throw new IllegalArgumentException("map dimension mismatch");
        work = new MapPreviewWork(data.centerX, data.centerZ, data.scale);
        originalColors = PARITY_PROOF ? data.colors.clone() : null;
    }

    private void requireServerThread() {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("preview requires server thread");
    }

    int step(int maxOperations, long budgetNs) {
        requireServerThread();
        if (consumed) throw new IllegalStateException("preview already consumed");
        if (MapItem.getSavedData(item, level) != data) {
            work.cancel();
            throw new IllegalStateException("map data replaced");
        }
        return work.stepPhased(maxOperations, MAX_PAINT_OPERATIONS, budgetNs, System::nanoTime,
            (x,z) -> level.getBiome(pos.set(x, 0, z)).is(BiomeTags.WATER_ON_MAP_OUTLINES),
            (x,z,water,neighbors) -> paint(data,x,z,water,neighbors));
    }

    boolean complete() { return !consumed && work.complete(); }
    ItemStack take() {
        requireServerThread();
        if (!complete() || MapItem.getSavedData(item, level) != data)
            throw new IllegalStateException("preview not ready or map data replaced");
        if (PARITY_PROOF) {
            try {
                verifyVanilla();
            } catch (RuntimeException | Error ex) {
                work.cancel();
                throw ex;
            }
        }
        consumed = true;
        return item;
    }
    void cancel() { requireServerThread(); if (!consumed) work.cancel(); }

    /** Restore the initial pixel buffer before calling vanilla; otherwise untouched
     * transparent pixels could conceal an incremental rendering error. No client has the item yet. */
    private void verifyVanilla() {
        byte[] incremental = data.colors.clone();
        if (originalColors.length != MapPreviewWork.SAMPLE_COUNT || incremental.length != originalColors.length)
            throw new IllegalStateException("unexpected map buffer size");
        System.arraycopy(originalColors, 0, data.colors, 0, originalColors.length);
        MapItem.renderBiomePreviewMap(level, item);
        if (MapItem.getSavedData(item, level) != data || !Arrays.equals(incremental, data.colors))
            throw new IllegalStateException("incremental/vanilla biome preview mismatch");
        tr.kapisma.Kapisma.LOG.info("ULTRA_MAP_PARITY pixels={} scale={} matched=true", incremental.length, data.scale);
    }

    // 1.21.1 preview palette: leave transparent cells untouched, including edges.
    private static void paint(MapItemSavedData data, int x, int z, boolean water, int neighbors) {
        int packed = packedColor(x, z, water, neighbors);
        if (packed >= 0) data.setColor(x, z, (byte) packed);
    }

    // -1 means preserve the existing pixel. Unsigned packed byte otherwise.
    static int packedColor(int x, int z, boolean water, int neighbors) {
        MapColor color;
        Brightness brightness = Brightness.LOWEST;
        if (!water) {
            if (neighbors == 0) return -1;
            color = MapColor.COLOR_BROWN;
            if (neighbors > 3) brightness = Brightness.NORMAL;
        } else {
            color = MapColor.COLOR_ORANGE;
            if (neighbors == 8) {
                if ((z & 1) != 0) return -1;
                int stripe = ((x + (int)(Mth.sin((float)z) * 7.0F)) / 8) % 5;
                brightness = switch (stripe) {
                    case 0, 4 -> Brightness.LOW;
                    case 1, 3 -> Brightness.NORMAL;
                    case 2 -> Brightness.HIGH;
                    default -> Brightness.LOWEST;
                };
            } else if (neighbors >= 6) {
                brightness = Brightness.NORMAL;
            } else if (neighbors >= 2) {
                brightness = Brightness.LOW;
            }
        }
        return Byte.toUnsignedInt(color.getPackedId(brightness));
    }
}
