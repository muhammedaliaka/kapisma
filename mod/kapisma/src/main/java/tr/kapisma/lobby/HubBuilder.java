package tr.kapisma.lobby;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Lobinin ana platformu: bos dunyada yuzen, aydinlik, dolu bir meydan.
 * Merkez: havuz + cesme; cevre: cicek yataklari, 4 yonde patika, agaclar, banklar, lambalar.
 * Doguda parkur kapisi, kuzeyde parkur, batida kapisma puan panosu, guneyde bilgi panosu.
 * Hicbir yerde gorunmez/gecilmez blok yoktur; kenar aciktir (dusen platforma geri alinir).
 */
final class HubBuilder {
	private HubBuilder() {}

	static final int Y = Lobby.BASE_Y;
	static final int R = 16;

	private static ServerLevel w;

	private static void set(int x, int y, int z, BlockState s) {
		w.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static void set(int x, int y, int z, Block b) {
		set(x, y, z, b.defaultBlockState());
	}

	private static int hash(int x, int z) {
		int h = x * 73856093 ^ z * 19349663;
		h ^= h >>> 13;
		return Math.floorMod(h * 0x5bd1e995, 1000);
	}

	private static final Block[] FLOWERS = {
		Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER,
		Blocks.ALLIUM, Blocks.RED_TULIP, Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.LILY_OF_THE_VALLEY};

	static void build(ServerLevel level) {
		w = level;
		for (int cx = -3; cx <= 2; cx++) for (int cz = -3; cz <= 2; cz++) level.getChunk(cx, cz);

		// ---- zemin ----
		for (int x = -R - 1; x <= R + 1; x++) {
			for (int z = -R - 1; z <= R + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > R + 0.5) continue;
				boolean axisPath = (Math.abs(x) <= 1 || Math.abs(z) <= 1) && d > 3.5;
				int h = hash(x, z);
				if (d <= 2.5) {                                    // havuz
					set(x, Y, z, Blocks.WATER);
				} else if (d <= 3.5) {                             // havuz kenari
					set(x, Y, z, Blocks.SMOOTH_QUARTZ);
					set(x, Y + 1, z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
				} else if (axisPath) {                             // 4 yonde patika
					set(x, Y, z, h % 3 == 0 ? Blocks.POLISHED_ANDESITE : h % 3 == 1 ? Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE);
				} else if (d <= 6.5) {                             // cicek yatagi
					set(x, Y, z, Blocks.GRASS_BLOCK);
					if (h < 330) set(x, Y + 1, z, FLOWERS[h % FLOWERS.length]);
				} else if (d <= 8.5) {                             // halka yol
					set(x, Y, z, h % 4 == 0 ? Blocks.POLISHED_DIORITE : Blocks.QUARTZ_BRICKS);
				} else if (d <= 14) {                              // cimen
					set(x, Y, z, Blocks.GRASS_BLOCK);
					if (h < 120) set(x, Y + 1, z, FLOWERS[h % FLOWERS.length]);
					else if (h < 300) set(x, Y + 1, z, Blocks.SHORT_GRASS);
				} else {                                           // kenar seridi
					boolean lamp = d > 15 && d < 16 && Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(z, x))), 30) <= 3;
					set(x, Y, z, lamp ? Blocks.SEA_LANTERN : Blocks.WHITE_CONCRETE);
				}
				// alt yuzey: kademeli ters koni (acik renkli)
				for (int dy = 1; dy <= 7; dy++) {
					double rr = R + 0.5 - dy * 2.3;
					if (d <= rr) {
						Block under = dy <= 1 ? Blocks.QUARTZ_BLOCK : dy <= 3 ? Blocks.CALCITE : dy <= 5 ? Blocks.WHITE_TERRACOTTA : Blocks.SMOOTH_QUARTZ;
						set(x, Y - dy, z, under);
					}
				}
			}
		}

		// ---- cesme ----
		set(0, Y, 0, Blocks.QUARTZ_PILLAR);
		for (int yy = 1; yy <= 2; yy++) set(0, Y + yy, 0, Blocks.QUARTZ_PILLAR.defaultBlockState());
		set(0, Y + 3, 0, Blocks.SEA_LANTERN);

		// ---- agaclar (kiraz) ----
		for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) tree(sx * 9, sz * 9);

		// ---- banklar (merkeze bakar) ----
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				Direction face = sz > 0 ? Direction.NORTH : Direction.SOUTH;
				for (int dx = -1; dx <= 1; dx++) {
					set(sx * 6 + dx, Y + 1, sz * 6, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, face));
				}
				set(sx * 6 - 2, Y + 1, sz * 6, Blocks.SPRUCE_FENCE);
				set(sx * 6 + 2, Y + 1, sz * 6, Blocks.SPRUCE_FENCE);
			}
		}

		// ---- lamba direkleri ----
		for (int k = 0; k < 8; k++) {
			double a = Math.toRadians(22.5 + k * 45);
			int x = (int) Math.round(Math.cos(a) * 12.5), z = (int) Math.round(Math.sin(a) * 12.5);
			set(x, Y + 1, z, Blocks.OAK_FENCE);
			set(x, Y + 2, z, Blocks.OAK_FENCE);
			set(x, Y + 3, z, Blocks.OAK_FENCE);
			set(x, Y + 4, z, Blocks.LANTERN);
		}

		// ---- parkur kapisi (dogu) + baslangic koprusu ----
		for (int zz = -1; zz <= 1; zz++) set(17, Y, zz, Blocks.LIME_CONCRETE);
		for (int yy = 1; yy <= 4; yy++) { set(14, Y + yy, -2, Blocks.QUARTZ_PILLAR); set(14, Y + yy, 2, Blocks.QUARTZ_PILLAR); }
		for (int zz = -2; zz <= 2; zz++) set(14, Y + 5, zz, Blocks.CHISELED_QUARTZ_BLOCK);
		set(14, Y + 6, 0, Blocks.SEA_LANTERN);
		Lobby.sign(w, new BlockPos(13, Y + 1, 2), 4, "PARKUR", "Kolay → Çok Zor", "4 bölüm", "Doğuya yürü");
		Boards.put(w, Boards.TAG_INFO + "_pk", 14.5, Y + 7.2, 0.5,
			Component.literal("PARKUR").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
				.append(Component.literal("\nKolay · Orta · Zor · Çok Zor\nBaşlamak için yeşil platformda dur").withStyle(ChatFormatting.WHITE)), 1.3f);

		// ---- puan panosu duvarlari ----
		wall(0, -13, true);      // kuzey: parkur puanlari
		wall(-13, 0, false);     // bati: kapisma puanlari
		wall(0, 14, true);       // guney: bilgi
		Boards.put(w, Boards.TAG_INFO, 0.5, Y + 7.0, 0.5,
			Component.literal("KAPIŞMA LOBİSİ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal("\nOyun menüsü: Nether Yıldızı\nYardım: /yardim").withStyle(ChatFormatting.YELLOW)), 1.5f);
		Boards.put(w, Boards.TAG_INFO + "_s", 0.5, Y + 5.0, 13.2,
			Component.literal("NASIL OYNANIR").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
				.append(Component.literal("\nHost oyunu ve kuralları seçer\nNether Yıldızı → Oyun Menüsü\nBeklerken parkur oyna, puan kazan!").withStyle(ChatFormatting.WHITE)), 1.2f);
	}

	private static void wall(int cx, int cz, boolean alongX) {
		for (int o = -2; o <= 2; o++) {
			int x = alongX ? cx + o : cx, z = alongX ? cz : cz + o;
			for (int yy = 1; yy <= 3; yy++) set(x, Y + yy, z, yy == 3 ? Blocks.QUARTZ_SLAB : Blocks.STONE_BRICKS);
		}
	}

	private static void tree(int x, int z) {
		set(x, Y + 1, z, Blocks.AIR);
		for (int yy = 1; yy <= 4; yy++) set(x, Y + yy, z, Blocks.CHERRY_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
		BlockState leaf = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 3; dy <= 6; dy++) {
					double dd = dx * dx + dz * dz + (dy - 4.5) * (dy - 4.5) * 1.6;
					if (dd > 5.2) continue;
					if (dx == 0 && dz == 0 && dy <= 4) continue;
					if (w.getBlockState(new BlockPos(x + dx, Y + dy, z + dz)).isAir()) set(x + dx, Y + dy, z + dz, leaf);
				}
			}
		}
	}
}
