package tr.kapisma.game;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Bounded, conservative evidence of a walkable cave, not a promise of a route from spawn. */
final class CaveReachability {
	private static final int MAX_NODES = 192;
	private static final int MIN_NODES = 24;
	private static final int MIN_SPAN = 8;

	private CaveReachability() {}

	static boolean search(BlockPos origin, Map<BlockPos, Boolean> cache, int[] budget, long deadline,
			Predicate<BlockPos> stand, Predicate<BlockPos> sky, Predicate<BlockPos> clear) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		BlockPos first = origin.immutable();
		queue.add(first);
		seen.add(first);
		int minX = first.getX(), maxX = minX, minZ = first.getZ(), maxZ = minZ;
		int visited = 0;
		while (!queue.isEmpty() && visited < MAX_NODES && budget[0] > 0 && System.nanoTime() < deadline) {
			BlockPos pos = queue.removeFirst();
			visited++;
			budget[0]--;
			minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
			minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
			if (Boolean.TRUE.equals(cache.get(pos)) || sky.test(pos)
					|| visited >= MIN_NODES && Math.max(maxX - minX, maxZ - minZ) >= MIN_SPAN) {
				for (BlockPos found : seen) cache.put(found, true);
				return true;
			}
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos adjacent = pos.relative(direction);
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos next = adjacent.offset(0, dy, 0);
					if (seen.contains(next) || Boolean.FALSE.equals(cache.get(next)) || !stand.test(next)) continue;
					// Jump up / step down requires a third free block on the lower side.
					if (dy > 0 && !clear.test(pos.above(2)) || dy < 0 && !clear.test(next.above(2))) continue;
					seen.add(next);
					queue.addLast(next);
				}
			}
		}
		// A budget cutoff is unknown, not negative evidence; don't poison future probes.
		if (queue.isEmpty()) for (BlockPos found : seen) cache.put(found, false);
		return false;
	}
}
