package tr.kapisma.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import tr.kapisma.Kapisma;
import tr.kapisma.Msg;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

/** Tombala'nın geçici boyutları ve yalnızca maç içi, güvenli portal güzergâhları. */
final class BingoPortals {
	private static final int RETURN_RADIUS = 150;
	private static final int MAX_RETURN_CHUNKS = 4;
	private static final TicketType<ChunkPos> RETURN_TICKET = TicketType.create("kapisma_bingo_return", Comparator.comparingLong(ChunkPos::toLong));
	private static final TicketType<UUID> SCALE_TICKET = TicketType.create("kapisma_bingo_scaled_portal", Comparator.<UUID>naturalOrder());
	private record PendingScale(ServerLevel level, ChunkPos chunk, int lastTouch) {}
	private final Map<UUID, PendingScale> pendingScales = new HashMap<>();
	private static final ChunkPos NETHER_ENTRY = new ChunkPos(0, 0);
	private static final ChunkPos END_ENTRY = new ChunkPos(6, 0);
	private final GameWorld overworld;
	private final List<BlockPos> returnCandidates;
	private final Set<ChunkPos> returnPins = new LinkedHashSet<>();
	private int distantReturns, platformReturns;
	private record ReturnChoice(BlockPos position, int candidates, boolean platform) {}
	private final Map<UUID, Set<ResourceKey<Level>>> visits = new HashMap<>();
	private final Map<ServerLevel, Set<BlockPos>> protectedBlocks = new HashMap<>();
	private RuntimeWorldHandle nether;
	private RuntimeWorldHandle end;
	private boolean overworldPinned;
	private BlockPos netherArrival, overworldNetherArrival, endArrival, overworldEndArrival;

	BingoPortals(MinecraftServer server, GameWorld overworld, GameWorld.Options options, List<BlockPos> landings,
			boolean useNether, boolean useEnd, int netherRadius) {
		this.overworld = overworld;
		returnCandidates = landings;
		try {
			if (useNether) {
				nether = GameWorld.openDimension(server, overworld.seed, Level.NETHER, BuiltinDimensionTypes.NETHER, options);
				GameWorld.setBorder(nether.asWorld(), 0, 0, netherRadius);
				pin(nether.asWorld(), NETHER_ENTRY, true);
				pinReturnChunks();
			}
			if (useEnd) {
				end = GameWorld.openDimension(server, overworld.seed, Level.END, BuiltinDimensionTypes.END, options);
				GameWorld.setBorder(end.asWorld(), 0, 0, Math.max(200, overworld.borderRadius()));
				pin(end.asWorld(), END_ENTRY, true);
			}
			overworld.force(0, 0, true);
			overworldPinned = true;
		} catch (RuntimeException ex) {
			close();
			throw ex;
		}
	}

	private void pinReturnChunks() {
		List<ChunkPos> remaining = new ArrayList<>(returnCandidates.stream()
			.filter(pos -> withinReturnZone(pos.getX(), pos.getZ()) && overworld.level.getWorldBorder().isWithinBounds(pos))
			.map(ChunkPos::new).distinct().toList());
		while (!remaining.isEmpty() && returnPins.size() < MAX_RETURN_CHUNKS) {
			ChunkPos chosen;
			if (returnPins.isEmpty()) {
				chosen = remaining.stream().min(Comparator.comparingLong(pos -> (long) pos.x * pos.x + (long) pos.z * pos.z)).orElseThrow();
			} else {
				chosen = remaining.stream().max(Comparator.comparingLong(pos -> returnPins.stream()
					.mapToLong(pin -> (long) (pin.x - pos.x) * (pin.x - pos.x) + (long) (pin.z - pos.z) * (pin.z - pos.z))
					.min().orElse(0))).orElseThrow();
			}
			remaining.remove(chosen);
			overworld.level.getChunkSource().addRegionTicket(RETURN_TICKET, chosen, 1, chosen);
			returnPins.add(chosen);
		}
		Kapisma.LOG.info("Tombala Nether dönüş hazırlığı: {} yüzey adayı, {} sabit chunk", returnCandidates.size(), returnPins.size());
	}

	boolean prepare() {
		if (!overworld.loaded(0, 0)) return false;
		for (ChunkPos pos : returnPins) if (!loaded(overworld.level, pos)) return false;
		if (nether != null && netherArrival == null) {
			ServerLevel level = nether.asWorld();
			if (!loaded(level, NETHER_ENTRY)) return false;
			int y = 62;
			for (int h = 40; h <= 104; h++) {
				if (BingoSurvey.landingSafe(level, new BlockPos(8, h, 8))) { y = h; break; }
			}
			netherArrival = netherGate(level, 8, y, 8);
			overworldNetherArrival = netherGate(overworld.level, 8, surface(overworld.level, 8, 8), 8);
		}
		if (end != null && endArrival == null) {
			ServerLevel level = end.asWorld();
			if (!loaded(level, END_ENTRY) || !loaded(overworld.level, new ChunkPos(-1, -1))
				|| !loaded(overworld.level, new ChunkPos(-1, 0)) || !loaded(overworld.level, new ChunkPos(0, -1))
				|| !loaded(level, new ChunkPos(6, -1))) return false;
			int y = surface(overworld.level, 0, 0);
			overworldEndArrival = endGate(overworld.level, 0, y, 0);
			int ey = Math.clamp(surface(level, 104, 4), 52, 92);
			endGate(level, 104, ey, 4);
			endArrival = new BlockPos(104, ey, 0);
		}
		return true;
	}

	void announce() {
		if (netherArrival != null) Msg.broadcast(overworld.level.getServer(), "Maçın Nether portalı X 8, Z 10 konumunda.");
		if (endArrival != null) Msg.broadcast(overworld.level.getServer(), "Maçın End portalı X 0, Z 0 konumunda.");
	}

	boolean contains(Level level) {
		return (nether != null && nether.asWorld() == level) || (end != null && end.asWorld() == level);
	}

	boolean permits(Level level, boolean endPortal) {
		if (endPortal) return endArrival != null && (level == overworld.level || level == end.asWorld());
		return netherArrival != null && (level == overworld.level || level == nether.asWorld());
	}

	boolean visited(ServerPlayer player, ResourceKey<Level> zone) {
		return visits.getOrDefault(player.getUUID(), Set.of()).contains(zone);
	}

	boolean protectedAt(Level level, BlockPos pos) {
		return protectedBlocks.getOrDefault(level, Set.of()).contains(pos);
	}

	/**
	 * Keep the exact mapped destination loaded before vanilla begins its portal timer.
	 * No synchronous world generation; one bounded ticket per player.
	 */
	boolean ready(ServerLevel source, ServerPlayer player, boolean endPortal) {
		if (!permits(source, endPortal)) return false;
		if (endPortal) { releaseScale(player.getUUID()); return true; }
		boolean departing = source == overworld.level;
		ServerLevel destination = departing ? nether.asWorld() : overworld.level;
		double scale = DimensionType.getTeleportationScale(source.dimensionType(), destination.dimensionType());
		int x = Mth.floor(player.getX() * scale), z = Mth.floor(player.getZ() * scale);
		if (!destination.getWorldBorder().isWithinBounds(x + 0.5, z + 0.5)
				|| !departing && !withinReturnZone(x, z)) {
			releaseScale(player.getUUID());
			return true; // distant return or invalid coordinate uses the existing safe path
		}
		UUID id = player.getUUID();
		ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
		PendingScale old = pendingScales.get(id);
		if (old == null || old.level() != destination || !old.chunk().equals(chunk)) {
			releaseScale(id);
			destination.getChunkSource().addRegionTicket(SCALE_TICKET, chunk, 1, id);
			Kapisma.LOG.info("Tombala ölçekli portal hazırlığı: {} ({},{}) yüklü={}", destination.dimension().location(), x, z, loaded(destination, chunk));
		}
		pendingScales.put(id, new PendingScale(destination, chunk, source.getServer().getTickCount()));
		return loaded(destination, chunk);
	}

	private void releaseScale(UUID id) {
		PendingScale old = pendingScales.remove(id);
		if (old != null) old.level().getChunkSource().removeRegionTicket(SCALE_TICKET, old.chunk(), 1, id);
	}

	void tick() {
		int now = overworld.level.getServer().getTickCount();
		for (UUID id : new ArrayList<>(pendingScales.keySet())) {
			if (now - pendingScales.get(id).lastTouch() > 200 || overworld.level.getServer().getPlayerList().getPlayer(id) == null)
				releaseScale(id);
		}
	}

	DimensionTransition transition(ServerLevel source, Entity entity, boolean endPortal) {
		if (!(entity instanceof ServerPlayer p) || !permits(source, endPortal)) return null;
		boolean departing = source == overworld.level;
		ServerLevel destination = departing ? (endPortal ? end.asWorld() : nether.asWorld()) : overworld.level;
		BlockPos landing;
		boolean distantNetherReturn = false;
		ReturnChoice returnChoice = null;
		if (endPortal) {
			landing = departing ? endArrival : overworldEndArrival;
		} else {
			double scale = DimensionType.getTeleportationScale(source.dimensionType(), destination.dimensionType());
			int x = Mth.floor(entity.getX() * scale), z = Mth.floor(entity.getZ() * scale);
			distantNetherReturn = !departing && (!withinReturnZone(x, z)
				|| !destination.getWorldBorder().isWithinBounds(x + 0.5, z + 0.5));
			if (distantNetherReturn) {
				returnChoice = randomReturn(p);
				landing = returnChoice.position();
			} else {
				// A valid scaled coordinate must not silently become the center just because
				// its chunk is unloaded. The portal touch hook prepares it asynchronously.
				if (destination.getWorldBorder().isWithinBounds(x + 0.5, z + 0.5)
						&& !loaded(destination, new ChunkPos(x >> 4, z >> 4))) return null;
				landing = scaledLanding(destination, x, z, entity.getBlockY(), departing);
				Kapisma.LOG.info("Tombala Nether ölçekli geçiş: hedef=({},{}), güvenli={}", x, z, landing != null);
			}
		}
		BlockPos fallback = endPortal ? departing ? endArrival : overworldEndArrival : departing ? netherArrival : overworldNetherArrival;
		if (landing == null || !destination.getWorldBorder().isWithinBounds(landing) || !BingoSurvey.landingSafe(destination, landing)) {
			landing = fallback;
			if (returnChoice != null) returnChoice = new ReturnChoice(landing, returnChoice.candidates(), true);
		}
		if (!destination.getWorldBorder().isWithinBounds(landing) || !BingoSurvey.landingSafe(destination, landing)) return null;
		var zone = endPortal ? Level.END : Level.NETHER;
		boolean announceReturn = distantNetherReturn;
		ReturnChoice recordedReturn = returnChoice;
		BlockPos actualLanding = landing;
		return new DimensionTransition(destination, Vec3.atBottomCenterOf(landing), Vec3.ZERO, entity.getYRot(), entity.getXRot(),
			DimensionTransition.PLACE_PORTAL_TICKET.then(arrived -> {
				releaseScale(p.getUUID());
				if (departing && visits.computeIfAbsent(p.getUUID(), id -> new HashSet<>()).add(zone)
					&& arrived instanceof ServerPlayer actual && Kapisma.GM != null && Kapisma.GM.current instanceof BingoGame bingo)
					bingo.portalCompleted(this, actual, zone);
				if (announceReturn) {
					distantReturns++;
					if (recordedReturn.platform()) platformReturns++;
					Kapisma.LOG.info("Tombala Nether uzak dönüş: {} aday={} platform={} toplam={}/{}",
						actualLanding.toShortString(), recordedReturn.candidates(), recordedReturn.platform(), platformReturns, distantReturns);
					Msg.tell(p, "Uzak Nether portalı seni Overworld'deki güvenli dönüş bölgesine getirdi.");
				}
			}));
	}

	private BlockPos scaledLanding(ServerLevel level, int x, int z, int preferredY, boolean toNether) {
		if (!level.getWorldBorder().isWithinBounds(x + 0.5, z + 0.5) || (!toNether && !withinReturnZone(x, z))) return null;
		if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return null;
		if (!toNether) {
			BlockPos feet = new BlockPos(x, surface(level, x, z), z);
			return BingoSurvey.landingSafe(level, feet) ? feet : null;
		}
		int y = Math.clamp(preferredY, 28, 112);
		for (int delta = 0; delta <= 28; delta++) {
			for (int candidate : new int[] {y + delta, y - delta}) {
				if (candidate < 28 || candidate > 112) continue;
				BlockPos feet = new BlockPos(x, candidate, z);
				if (BingoSurvey.landingSafe(level, feet)) return feet;
			}
		}
		return null;
	}

	private ReturnChoice randomReturn(ServerPlayer p) {
		List<BlockPos> choices = returnCandidates.stream().filter(pos -> withinReturnZone(pos.getX(), pos.getZ()))
			.filter(pos -> overworld.level.getWorldBorder().isWithinBounds(pos))
			.filter(pos -> BingoSurvey.landingSafe(overworld.level, pos)).toList();
		if (!choices.isEmpty()) return new ReturnChoice(choices.get(p.getRandom().nextInt(choices.size())), choices.size(), false);
		int x = p.getRandom().nextInt(3) - 1;
		return new ReturnChoice(overworldNetherArrival.offset(x, 0, 0), 0, true);
	}

	private static boolean withinReturnZone(int x, int z) {
		return x >= -RETURN_RADIUS && x < RETURN_RADIUS && z >= -RETURN_RADIUS && z < RETURN_RADIUS;
	}

	private BlockPos netherGate(ServerLevel level, int x, int y, int z) {
		pad(level, x, y, z, 3);
		for (int dx = -2; dx <= 1; dx++) for (int dy = -1; dy <= 3; dy++) {
			BlockPos pos = new BlockPos(x + dx, y + dy, z + 2);
			if (dx == -2 || dx == 1 || dy == -1 || dy == 3) placeFixed(level, pos, Blocks.OBSIDIAN.defaultBlockState());
		}
		for (int dx = -1; dx <= 0; dx++) for (int dy = 0; dy <= 2; dy++) {
			placeFixed(level, new BlockPos(x + dx, y + dy, z + 2),
				Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
		}
		return new BlockPos(x, y, z);
	}

	private BlockPos endGate(ServerLevel level, int x, int y, int z) {
		pad(level, x, y, z, 4);
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			for (int offset = -1; offset <= 1; offset++) {
				BlockPos frame = new BlockPos(x, y, z).relative(facing, 2).relative(facing.getClockWise(), offset);
				placeFixed(level, frame, Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, facing.getOpposite())
					.setValue(EndPortalFrameBlock.HAS_EYE, true));
			}
		}
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
			placeFixed(level, new BlockPos(x + dx, y, z + dz), Blocks.END_PORTAL.defaultBlockState());
		return new BlockPos(x, y, z - 4);
	}

	private void pad(ServerLevel level, int x, int y, int z, int radius) {
		for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
			BlockPos pos = new BlockPos(x + dx, y, z + dz);
			placeFixed(level, pos.below(), Blocks.OBSIDIAN.defaultBlockState());
			for (int dy = 0; dy <= 4; dy++) level.setBlock(pos.above(dy), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private void placeFixed(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		protectedBlocks.computeIfAbsent(level, key -> new HashSet<>()).add(pos);
	}

	private static int surface(ServerLevel level, int x, int z) {
		return Math.clamp(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 6);
	}

	private static boolean loaded(ServerLevel level, ChunkPos pos) {
		return level.getChunkSource().getChunkNow(pos.x, pos.z) != null;
	}

	private static void pin(ServerLevel level, ChunkPos pos, boolean add) {
		if (add) level.getChunkSource().addRegionTicket(TicketType.FORCED, pos, 1, pos);
		else level.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, 1, pos);
	}

	void close() {
		for (UUID id : new ArrayList<>(pendingScales.keySet())) releaseScale(id);
		for (ChunkPos pos : returnPins) overworld.level.getChunkSource().removeRegionTicket(RETURN_TICKET, pos, 1, pos);
		returnPins.clear();
		if (overworldPinned) {
			overworld.force(0, 0, false);
			overworldPinned = false;
		}
		if (nether != null) { pin(nether.asWorld(), NETHER_ENTRY, false); nether.delete(); nether = null; }
		if (end != null) { pin(end.asWorld(), END_ENTRY, false); end.delete(); end = null; }
	}
}
