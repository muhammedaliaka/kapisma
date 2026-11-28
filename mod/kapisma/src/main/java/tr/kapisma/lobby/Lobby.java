package tr.kapisma.lobby;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import tr.kapisma.Kapisma;

/**
 * Bekleme lobisi: ana dunyadan ayri, bos (void) bir Fantasy dunyasinda yuzen aydinlik platform.
 * Altinda dunya gorunmez; yalnizca platform, parkur ve gokyuzu vardir. Ana dunya yalnizca Manhunt'ta kullanilir.
 */
public final class Lobby {
	private Lobby() {}

	public static final int BASE_Y = 100;
	public static final String MENU_TAG = "kapisma_menu";
	public static final double[] HUB_X_BOARD_PARKOUR = {0.5, BASE_Y + 5.0, -12.2};
	public static final double[] HUB_X_BOARD_GAME = {-12.2, BASE_Y + 5.0, 0.5};

	public static ServerLevel LEVEL;
	private static RuntimeWorldHandle handle;
	public static Scores scores;
	public static volatile boolean boardsDirty;

	public static double[] spawn() {
		return new double[] {0.5, BASE_Y + 1, 11.5};
	}

	public static boolean inLobby(ServerPlayer p) {
		return LEVEL != null && p.level() == LEVEL;
	}

	public static boolean isLobbyLevel(ServerLevel l) {
		return LEVEL != null && l == LEVEL;
	}

	/** Sunucu acilisinda lobi dunyasi + platform + parkur + panolar kurulur. */
	public static void ensureBuilt(MinecraftServer server) {
		scores = Scores.load();
		Parkour.load();
		RuntimeWorldConfig cfg = new RuntimeWorldConfig()
			.setDimensionType(BuiltinDimensionTypes.OVERWORLD)
			.setGenerator(new VoidChunkGenerator(server))
			.setDifficulty(Difficulty.PEACEFUL)
			.setMirrorOverworldGameRules(false)
			.setMirrorOverworldDifficulty(false)
			.setShouldTickTime(false)
			.setTimeOfDay(6000)
			.setGameRule(GameRules.RULE_DOMOBSPAWNING, false)
			.setGameRule(GameRules.RULE_DAYLIGHT, false)
			.setGameRule(GameRules.RULE_WEATHER_CYCLE, false)
			.setGameRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS, false)
			.setGameRule(GameRules.RULE_RANDOMTICKING, 0)
			.setGameRule(GameRules.RULE_SPAWN_CHUNK_RADIUS, 0)
			.setGameRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN, true);
		handle = Fantasy.get(server).openTemporaryWorld(cfg);
		LEVEL = handle.asWorld();
		double[] sp = spawn();
		LEVEL.setDefaultSpawnPos(BlockPos.containing(sp[0], sp[1], sp[2]), 180f);
		long t0 = System.currentTimeMillis();
		HubBuilder.build(LEVEL);
		Parkour.build(LEVEL);
		// Meydan her zaman yuklu kalsin (panolar ve varliklar icin); parkur oyuncu gelince yuklenir.
		for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) LEVEL.setChunkForced(cx, cz, true);
		Boards.refresh(LEVEL, scores);
		Kapisma.LOG.info("Lobi kuruldu ({} ms): {} parkur platformu", System.currentTimeMillis() - t0, Parkour.PLATS.size());
	}

	/** Ana dunya normal kurallarda kalsin (Manhunt orada oynanir). */
	public static void applyNormalRules(MinecraftServer s) {
		s.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, s);
		s.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, s);
		s.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(0, s);
		s.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, s);
		s.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, s);
	}

	public static ItemStack menuItem() {
		ItemStack st = new ItemStack(Items.NETHER_STAR);
		st.set(DataComponents.CUSTOM_NAME, Component.literal("Oyun Menüsü").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA).withBold(true)));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MENU_TAG, true);
		st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return st;
	}

	public static boolean isMenuItem(ItemStack st) {
		CustomData d = st.get(DataComponents.CUSTOM_DATA);
		return d != null && d.copyTag().getBoolean(MENU_TAG);
	}

	/** Oyuncuyu temizleyip lobiye gonderir. */
	public static void send(MinecraftServer server, ServerPlayer p) {
		Parkour.forget(p);
		resetPlayer(p);
		p.setGameMode(GameType.ADVENTURE);
		if (LEVEL == null) {
			p.teleportTo(server.overworld(), 0.5, 200, 0.5, 0f, 0f);
		} else {
			double[] sp = spawn();
			p.teleportTo(LEVEL, sp[0], sp[1], sp[2], 180f, 0f);
		}
		p.getInventory().setItem(4, menuItem());
	}

	/** Envanteri bozmadan meydan baslangic noktasina alir. */
	public static void toHub(ServerPlayer p) {
		double[] sp = spawn();
		p.teleportTo(LEVEL, sp[0], sp[1], sp[2], 180f, 0f);
		p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		p.hurtMarked = true;
		p.fallDistance = 0;
	}

	public static void resetPlayer(ServerPlayer p) {
		// Close the previous screen before clearing: its carried stack may be
		// returned to the inventory during closeContainer().
		p.closeContainer();
		p.containerMenu.setCarried(ItemStack.EMPTY);
		p.getInventory().clearContent();
		p.getEnderChestInventory().clearContent();
		p.stopRiding();
		p.setCamera(p);
		p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		p.hurtMarked = true;
		p.removeAllEffects();
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(20f);
		p.setExperienceLevels(0);
		p.setExperiencePoints(0);
		p.totalExperience = 0;
		p.setTicksFrozen(0);
		p.setAirSupply(p.getMaxAirSupply());
		p.clearFire();
		p.fallDistance = 0;
		p.setInvulnerable(false);
	}

	/** Ayakta duran tabela (yazi degistirilemez). */
	static void sign(ServerLevel l, BlockPos pos, int rotation, String... lines) {
		l.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
		l.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation), 3);
		if (l.getBlockEntity(pos) instanceof SignBlockEntity sbe) {
			SignText t = new SignText();
			for (int i = 0; i < 4 && i < lines.length; i++) t = t.setMessage(i, Component.literal(lines[i]));
			sbe.setText(t, true);
			sbe.setWaxed(true);
			sbe.setChanged();
		}
	}

	/** Her sunucu tikinde cagrilir. */
	public static void tick(MinecraftServer server) {
		if (LEVEL == null) return;
		long now = server.getTickCount();
		if (now % 20 == 0) LobbyBoard.tick(server);
		if (LEVEL.players().isEmpty()) {                 // bos lobide parkur/pano isi yok
			if (boardsDirty && now % 20 == 0) { boardsDirty = false; Boards.refresh(LEVEL, scores); }
			return;
		}
		if (now % 4 == 0) {
			Parkour.tick(server, LEVEL);
			for (ServerPlayer p : LEVEL.players()) {
				if (p.getY() < BASE_Y - 14) {            // platformdan dustu (parkur disinda): meydana geri
					if (!Parkour.running(p)) toHub(p);
				}
			}
		}
		if (now % 40 == 0) {
			for (ServerPlayer p : LEVEL.players()) {
				if (p.getFoodData().getFoodLevel() < 20) p.getFoodData().setFoodLevel(20);
				if (p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());
			}
		}
		if (boardsDirty && now % 20 == 0) {
			boardsDirty = false;
			Boards.refresh(LEVEL, scores);
		}
	}
}
