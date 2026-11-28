package tr.kapisma.game;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Resumable two-pass map work. Caller owns thread confinement and publication.
 * No Minecraft calls, async execution, or partially published inventory items.
 */
final class MapPreviewWork {
    static final int WIDTH = 128;
    static final int SAMPLE_COUNT = WIDTH * WIDTH;
    static final int INNER = WIDTH - 2;
    static final int PAINT_COUNT = INNER * INNER;

    @FunctionalInterface interface Sample { boolean waterAt(int blockX, int blockZ); }
    @FunctionalInterface interface Paint { void pixel(int x, int z, boolean water, int waterNeighbors); }

    private final int originX, originZ, spacing;
    private final boolean[] water = new boolean[SAMPLE_COUNT];
    private int sampled, painted;
    private boolean cancelled, running;

    MapPreviewWork(int centerX, int centerZ, int scale) {
        if (scale < 0 || scale > 4) throw new IllegalArgumentException("map scale");
        spacing = 1 << scale;
        // Vanilla 1.21.1 uses integer truncation toward zero, not floorDiv.
        originX = Math.multiplyExact(Math.subtractExact(centerX / spacing, 64), spacing);
        originZ = Math.multiplyExact(Math.subtractExact(centerZ / spacing, 64), spacing);
        Math.addExact(originX, 127 * spacing);
        Math.addExact(originZ, 127 * spacing);
    }

    boolean complete() { return !cancelled && sampled == SAMPLE_COUNT && painted == PAINT_COUNT; }

    /** Phase-specific operation caps; no phase transition within the same slice.
     * Both stages keep the same soft elapsed budget. */
    int stepPhased(int sampleCap, int paintCap, long budgetNs, LongSupplier clock, Sample sample, Paint paint) {
        if (sampleCap <= 0 || paintCap <= 0) throw new IllegalArgumentException("phase cap");
        int cap = sampled < SAMPLE_COUNT ? Math.min(sampleCap, SAMPLE_COUNT - sampled) : paintCap;
        return step(cap, budgetNs, clock, sample, paint);
    }
    boolean cancelled() { return cancelled; }
    int sampled() { return sampled; }
    int painted() { return painted; }
    void cancel() { cancelled = true; }

    /**
     * At most maxOperations, with an elapsed-time check before each operation
     * after the first. One expensive lookup can overrun the soft time budget.
     * A callback failure invalidates the job; it is never retried or published.
     */
    int step(int maxOperations, long budgetNs, LongSupplier clock, Sample sample, Paint paint) {
        if (maxOperations <= 0 || budgetNs <= 0) throw new IllegalArgumentException("work budget");
        Objects.requireNonNull(clock);
        Objects.requireNonNull(sample);
        Objects.requireNonNull(paint);
        if (running) throw new IllegalStateException("reentrant preview");
        if (cancelled || complete()) return 0;
        running = true;
        int done = 0;
        try {
            long start = clock.getAsLong();
            while (!cancelled && !complete() && done < maxOperations) {
                if (done > 0 && clock.getAsLong() - start >= budgetNs) break;
                if (sampled < SAMPLE_COUNT) {
                    int x = sampled % WIDTH, z = sampled / WIDTH;
                    water[sampled] = sample.waterAt(originX + x * spacing, originZ + z * spacing);
                    sampled++;
                } else {
                    // Keep vanilla painting order: x outer, z inner.
                    int x = 1 + painted / INNER, z = 1 + painted % INNER;
                    int n = 0;
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if ((dx != 0 || dz != 0) && water[(z + dz) * WIDTH + x + dx]) n++;
                        }
                    }
                    paint.pixel(x, z, water[z * WIDTH + x], n);
                    painted++;
                }
                done++;
            }
            return done;
        } catch (RuntimeException | Error ex) {
            cancelled = true;
            throw ex;
        } finally {
            running = false;
        }
    }
}
