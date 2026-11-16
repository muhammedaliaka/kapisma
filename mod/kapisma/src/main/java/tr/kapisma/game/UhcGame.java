package tr.kapisma.game;

import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import tr.kapisma.Msg;
import tr.kapisma.team.TeamService.GameTeam;

/** UHC: dogal can yok, golden head, cut-clean, PvP suresi, daralan sinir, son takim kazanir. */
public final class UhcGame extends WorldGame {
	public static final String HEAD_TAG = "kapisma_golden_head";

	private static final Map<Item, Item> CUT_CLEAN = Map.ofEntries(
		Map.entry(Items.RAW_IRON, Items.IRON_INGOT), Map.entry(Items.RAW_GOLD, Items.GOLD_INGOT),
		Map.entry(Items.RAW_COPPER, Items.COPPER_INGOT), Map.entry(Items.BEEF, Items.COOKED_BEEF),
		Map.entry(Items.PORKCHOP, Items.COOKED_PORKCHOP), Map.entry(Items.CHICKEN, Items.COOKED_CHICKEN),
		Map.entry(Items.MUTTON, Items.COOKED_MUTTON), Map.entry(Items.RABBIT, Items.COOKED_RABBIT),
		Map.entry(Items.COD, Items.COOKED_COD), Map.entry(Items.SALMON, Items.COOKED_SALMON),
		Map.entry(Items.POTATO, Items.BAKED_POTATO));

	private int pvpTick, shrinkStart, shrinkEnd, limit;
	private SideBoard board;
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private boolean shrinkAnnounced;

	public UhcGame(GameManager mgr) {
		super(mgr);
	}

	@Override
	public String id() { return "UHC"; }

	@Override
	public String title() { return "UHC"; }

	@Override
	protected GameWorld.Options worldOptions() {
		return new GameWorld.Options(st.flag("UHC", "naturalRegen"), st.flag("UHC", "mobSpawn"), !st.flag("UHC", "lockDay"), false);
	}

	@Override
	protected double borderRadius() { return st.num("UHC", "borderStart"); }

	@Override
	protected boolean eliminationMode() { return true; }

	@Override
	protected double scatterRadius() { return Math.min(st.num("UHC", "scatterRadius"), borderRadius() * 0.9); }

	private boolean glowApplied;

	private boolean pvpActive() { return phase == Phase.RUNNING && ticks >= pvpTick; }

	@Override
	protected boolean minimapEnabled() { return st.flag("UHC", "minimap"); }

	@Override
	protected double minimapEndRadius() { return st.num("UHC", "borderEnd"); }

	@Override
	protected void onStart() {
		if (minimapEnabled()) giveWorldMap();
		pvpTick = (int) st.num("UHC", "pvpGraceMin") * 1200;
		shrinkStart = (int) st.num("UHC", "shrinkStartMin") * 1200;
		shrinkEnd = Math.max(shrinkStart + 1200, (int) st.num("UHC", "shrinkEndMin") * 1200);
		limit = (int) st.num("UHC", "timeLimitMin") * 1200;
		if (st.flag("UHC", "sidebar")) board = new SideBoard(server, "kp_uhc", "UHC");
		int buff = (int) st.num("UHC", "earlyBuffSec");
		for (ServerPlayer p : onlinePlayers()) {
			if (buff > 0) {
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, buff * 20, 1, false, false, true));
				p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, buff * 20, 1, false, false, true));
			}
		}
		Msg.broadcast(server, "UHC başladı! PvP " + (pvpTick > 0 ? (pvpTick / 1200) + " dk sonra açılır." : "açık.")
			+ " Sınır ±" + (int) world.borderRadius() + " bloktan " + (int) st.num("UHC", "shrinkStartMin") + ". dakikada daralmaya başlar.");
	}

	@Override
	protected void onRunningTick() {
		if (pvpTick > 0 && ticks == pvpTick) {
			Msg.broadcast(server, "PvP AÇILDI! Kapışma başlasın.");
			for (ServerPlayer p : onlinePlayers()) { Msg.title(p, "PvP AÇIK", "Kapışma başlasın!", 5, 40, 10); Msg.sound(p, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 0.7f, 1f); }
		}
		if (pvpTick > 1200 && ticks == pvpTick - 1200) {
			Msg.broadcast(server, "PvP 1 dakika sonra açılıyor.");
			Msg.soundAll(server, net.minecraft.sounds.SoundEvents.BELL_BLOCK, 1f, 1f);
		}
		if (!shrinkAnnounced && shrinkStart > 1200 && ticks == shrinkStart - 1200) {
			Msg.broadcast(server, "Sınır 1 dakika sonra daralmaya başlıyor!");
			Msg.soundAll(server, net.minecraft.sounds.SoundEvents.BELL_BLOCK, 1f, 0.8f);
		}
		if (!shrinkAnnounced && ticks >= shrinkStart) {
			shrinkAnnounced = true;
			world.shrinkBorder(st.num("UHC", "borderEnd"), (long) (shrinkEnd - shrinkStart) * 50L);
			Msg.broadcast(server, "Sınır daralmaya başladı! Hedef: ±" + (int) st.num("UHC", "borderEnd") + " blok.");
			for (ServerPlayer p : onlinePlayers()) Msg.title(p, "Sınır daralıyor", "Merkeze doğru ilerle!", 5, 50, 15);
			Msg.soundAll(server, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.7f);
		}
		if (!glowApplied && shrinkAnnounced && ticks >= shrinkEnd && st.flag("UHC", "glowFinal")) {
			glowApplied = true;
			for (ServerPlayer p : onlinePlayers()) if (isAlive(p.getUUID())) p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60 * 60 * 20, 0, false, false, false));
			Msg.broadcast(server, "Sınır son boyuta ulaştı: herkes parlıyor! Final kapışması.");
		}
		if (limit > 0 && ticks >= limit) finishByHealth();
		if (ticks % 20 == 0) hud();
	}

	private void hud() {
		int sec = ticks / 20;
		boolean pvpOn = pvpActive();
		String pvp = pvpOn ? "AÇIK" : Msg.mmss((pvpTick - ticks) / 20);
		int alivePlayers = (int) players.stream().filter(this::isAlive).count();
		var wb = world.level.getWorldBorder();
		int radius = (int) world.borderRadius();
		if (board != null) {
			java.util.List<String> lines = new java.util.ArrayList<>();
			lines.add("§7Süre §f" + Msg.mmss(sec));
			lines.add("§7PvP " + (pvpOn ? "§c§lAÇIK" : "§e" + pvp));
			lines.add("§7Sınır §f±" + radius + (shrinkAnnounced ? " §7→ ±" + (int) st.num("UHC", "borderEnd") : ""));
			lines.add("§7Takım §f" + aliveTeams().size() + "  §7Oyuncu §f" + alivePlayers);
			var top = players.stream().filter(id -> kills.getOrDefault(id, 0) > 0)
				.sorted((x, y) -> Integer.compare(kills.getOrDefault(y, 0), kills.getOrDefault(x, 0))).limit(3).toList();
			if (!top.isEmpty()) lines.add("§c§lEN ÇOK ÖLDÜREN");
			for (var id : top) lines.add("§f" + names.getOrDefault(id, "?") + " §c" + kills.getOrDefault(id, 0));
			board.update(lines);
		}
		for (ServerPlayer p : onlinePlayers()) {
			StringBuilder sb = new StringBuilder("⏱ " + Msg.mmss(sec) + "   PvP: " + pvp + "   Sınır: ±" + radius + "   Takım: " + aliveTeams().size());
			if (p.level() == world.level && !p.isSpectator()) {
				sb.append("   Öldürme: ").append(kills.getOrDefault(p.getUUID(), 0));
				double dx = wb.getCenterX() - p.getX(), dz = wb.getCenterZ() - p.getZ();
				int dist = (int) Math.sqrt(dx * dx + dz * dz);
				if (dist > 12) {
					double rel = Math.toDegrees(Math.atan2(-dx, dz)) - p.getYRot();
					int idx = Math.floorMod(Math.round((float) (rel / 45.0)), 8);
					sb.append("   Merkez: ").append(dist).append(' ').append(ARROWS[idx]);
				}
				if (Math.abs(p.getX() - wb.getCenterX()) > wb.getSize() / 2 || Math.abs(p.getZ() - wb.getCenterZ()) > wb.getSize() / 2) sb.append("   §c§lSINIR DIŞI!");
			}
			Msg.actionbar(p, Component.literal(sb.toString()).withStyle(ChatFormatting.WHITE));
		}
	}

	@Override
	protected void onClose() {
		if (board != null) board.close();
		board = null;
	}

	private void finishByHealth() {
		GameTeam best = null;
		float bestHp = -1;
		for (GameTeam t : aliveTeams()) {
			float hp = 0;
			for (var id : t.members) {
				ServerPlayer p = player(id);
				if (p != null && isAlive(id)) hp += p.getHealth();
			}
			if (hp > bestHp) { bestHp = hp; best = t; }
		}
		finish(best, "Süre doldu: en sağlıklı takım");
	}

	@Override
	public boolean allowDamage(LivingEntity e, DamageSource src, float amount) {
		if (!(e instanceof ServerPlayer target) || !contains(target.getUUID())) return true;
		if (phase == Phase.COUNTDOWN) return false;
		if (src.getEntity() instanceof ServerPlayer attacker && attacker != target && contains(attacker.getUUID())) {
			return pvpActive() && st.flag("UHC", "pvp");
		}
		return true;
	}

	@Override
	protected void onEliminated(ServerPlayer p, DamageSource src) {
		GameTeam mine = teamOf(p.getUUID());
		if (mine != null) {
			for (var id : mine.members) {
				ServerPlayer m = id.equals(p.getUUID()) ? null : player(id);
				if (m != null && isAlive(id)) Msg.tell(m, "☠ Takım arkadaşın " + p.getGameProfile().getName() + " öldü. Konum: "
					+ p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ() + " (eşyaları orada)");
			}
		}
		if (!st.flag("UHC", "goldenHead")) return;
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		head.set(DataComponents.PROFILE, new ResolvableProfile(p.getGameProfile()));
		head.set(DataComponents.CUSTOM_NAME, Component.literal(p.getGameProfile().getName() + " Golden Head")
			.withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(HEAD_TAG, true);
		head.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		p.spawnAtLocation(head);
	}

	@Override
	public void onUseItem(ServerPlayer p, ItemStack stack) {
		if (phase != Phase.RUNNING || !contains(p.getUUID())) return;
		CustomData d = stack.get(DataComponents.CUSTOM_DATA);
		if (d == null || !d.copyTag().getBoolean(HEAD_TAG)) return;
		stack.shrink(1);
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 2));
		p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120 * 20, 0));
		Msg.tell(p, "Golden Head yedin!");
	}

	@Override
	public void onEntityLoad(Entity e, ServerLevel level) {
		if (world == null || level != world.level || !st.flag("UHC", "cutClean")) return;
		if (e instanceof ItemEntity ie) {
			Item to = CUT_CLEAN.get(ie.getItem().getItem());
			if (to != null) ie.setItem(new ItemStack(to, ie.getItem().getCount()));
		}
	}
}
