package tr.kapisma.ui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import tr.kapisma.Msg;
import tr.kapisma.config.RuleDef;
import tr.kapisma.config.Rules;
import tr.kapisma.config.Settings;
import tr.kapisma.game.GameManager;
import tr.kapisma.game.WorldGame;
import tr.kapisma.origin.OriginService;

/** Sunucu tarafli sandik menuleri (istemciye mod gerekmez). */
public final class MenuService {
	private final GameManager mgr;

	public MenuService(GameManager mgr) {
		this.mgr = mgr;
	}

	private static Component name(String s, ChatFormatting... f) {
		return Component.literal(s).withStyle(st -> st.withItalic(false).applyFormats(f));
	}

	private static Component lore(String s) {
		return Component.literal(s).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY));
	}

	public static String gameLabel(String g) {
		return switch (g) {
			case "UHC" -> "UHC";
			case "BINGO" -> "Tombala (Bingo)";
			case "ESYA" -> "Eşya Yarışı";
			case "KATIL" -> "Katil Kim";
			case "DONEN_KARAKOL" -> "Dönen Karakol";
			case "KERVAN_BASKINI" -> "Kervan Baskını";
			case "YASAYAN_HARITA" -> "Yaşayan Harita";
			case "INSA_ET_SAVUN" -> "İnşa Et, Savun";
			case "KIT_IHALESI" -> "Kit İhalesi";
			case "ALCAK_IRTIFA" -> "Alçak İrtifa";
			case "KIRIK_TELSIZ" -> "Kırık Telsiz";
			default -> "Manhunt";
		};
	}

	public static String originModeLabel(String m) {
		return switch (m) {
			case "RANDOM" -> "Rastgele";
			case "SELECT" -> "Seçmeli";
			default -> "Kapalı (herkes Human)";
		};
	}

	private boolean admin(ServerPlayer p) {
		if (mgr.canAdmin(p)) return true;
		Msg.tell(p, "Ayarları sadece host değiştirebilir.");
		return false;
	}

	// ================= ANA MENU =================
	public void openMain(ServerPlayer p) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, p, false);
		gui.setTitle(Component.literal("Kapışma"));
		buildMain(gui, p);
		gui.open();
	}

	public void openSpectators(ServerPlayer p, WorldGame game) {
		openSpectators(p, game, 0);
	}

	private void openSpectators(ServerPlayer p, WorldGame game, int requestedPage) {
		if (mgr.current != game || game.isClosed() || !p.isSpectator() || !game.contains(p.getUUID())) return;
		var live = game.players.stream().filter(id -> !game.eliminated.contains(id)).toList();
		var dead = game.players.stream().filter(game.eliminated::contains).toList();
		int pages = Math.max(1, (Math.max(live.size(), dead.size()) + 16) / 17);
		int page = Math.max(0, Math.min(requestedPage, pages - 1));
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, p, false);
		gui.setTitle(Component.literal("İzleyici Pusulası · " + (page + 1) + "/" + pages));
		gui.setSlot(0, new GuiElementBuilder(Items.LIME_DYE).setName(name("Hayatta (" + live.size() + ")", ChatFormatting.GREEN)));
		gui.setSlot(18, new GuiElementBuilder(Items.GRAY_DYE).setName(name("Elenenler (" + dead.size() + ")", ChatFormatting.GRAY)));
		for (int i = 0; i < 17; i++) {
			int index = page * 17 + i;
			if (index < live.size()) spectatorEntry(gui, p, game, live.get(index), false, i + 1);
			if (index < dead.size()) spectatorEntry(gui, p, game, dead.get(index), true, i + 19);
		}
		if (page > 0) gui.setSlot(45, new GuiElementBuilder(Items.ARROW).setName(name("Önceki sayfa", ChatFormatting.YELLOW))
			.setCallback((idx, type, action) -> { gui.close(); openSpectators(p, game, page - 1); }));
		gui.setSlot(49, new GuiElementBuilder(Items.COMPASS).setName(name("Yenile", ChatFormatting.AQUA))
			.setCallback((idx, type, action) -> { gui.close(); openSpectators(p, game, page); }));
		if (page + 1 < pages) gui.setSlot(53, new GuiElementBuilder(Items.ARROW).setName(name("Sonraki sayfa", ChatFormatting.YELLOW))
			.setCallback((idx, type, action) -> { gui.close(); openSpectators(p, game, page + 1); }));
		gui.open();
	}

	private void spectatorEntry(SimpleGui gui, ServerPlayer p, WorldGame game, java.util.UUID id, boolean eliminated, int slot) {
		boolean visit = game.spectatorCanVisit(id);
		var entry = new GuiElementBuilder(eliminated ? Items.SKELETON_SKULL : Items.PLAYER_HEAD)
			.setName(name(game.names.getOrDefault(id, "Oyuncu"), eliminated ? ChatFormatting.GRAY : ChatFormatting.GREEN))
			.addLoreLine(lore(visit ? eliminated ? "Tıkla: elendiği yere git" : "Tıkla: oyuncunun yanına git"
				: eliminated ? "Elenme konumu yok" : "İzlemeye uygun değil"));
		if (visit) entry.setCallback((idx, type, action) -> {
			gui.close();
			if (!game.visitSpectatorTarget(p, id)) Msg.tell(p, "Bu oyuncunun izlenebilecek konumu artık yok.");
		});
		gui.setSlot(slot, entry);
	}

	private void buildMain(SimpleGui gui, ServerPlayer p) {
		Settings st = mgr.settings;
		for (int i = 0; i < 54; i++) gui.clearSlot(i);
		String[] games = {"UHC", "BINGO", "ESYA", "MANHUNT", "KATIL", "DONEN_KARAKOL", "KERVAN_BASKINI", "YASAYAN_HARITA", "INSA_ET_SAVUN", "KIT_IHALESI", "ALCAK_IRTIFA", "KIRIK_TELSIZ"};
		Item[] icons = {Items.GOLDEN_APPLE, Items.FILLED_MAP, Items.CHEST, Items.COMPASS, Items.IRON_SWORD, Items.GOLD_BLOCK, Items.HAY_BLOCK, Items.MAP, Items.OAK_PLANKS, Items.GOLD_INGOT, Items.ELYTRA, Items.NOTE_BLOCK};
		String[] descs = {"Doğal can yok, daralan sınır, son takım kazanır.", "5x5 eşya kartı, çizgileri ilk tamamlayan kazanır.",
			"Her tur sana özel gizli eşya, 5 can ve seriyle kazanılan atlama hakları.", "Runner Ejderha'yı öldürmeye, hunter'lar runner'ı avlamaya çalışır.",
			"Herkes aynı skin'de, 200x200 alanda rastgele doğar. Masumlar katil hepsini öldürmeden Ejderha'yı öldürür; gizli katil masumları avlar. Sesli sohbet.",
			"İki takım, sırayla etkin üç karakol. Çekişmeli alan puan vermez.", "Kervanı koru veya durdur.", "Takımının sıradaki ipucunu bul.", "İnşa ve hücum rolleri değişir.", "Gizli jeton teklifleri, ardından PvP.", "Dört sıralı Elytra geçidi.", "Özel sinyal parçalarını birleştir."};
		int[] slots = {9, 11, 13, 15, 17, 18, 19, 20, 21, 22, 23, 24};
		for (int i = 0; i < games.length; i++) {
			final String g = games[i];
			if (tr.kapisma.game.NativeArenaGame.isNew(g) && !tr.kapisma.game.NativeArenaGame.enabled()) continue;
			boolean sel = st.game.equals(g);
			gui.setSlot(slots[i], new GuiElementBuilder(icons[i])
				.setName(name(gameLabel(g) + (sel ? "  ✔" : ""), sel ? ChatFormatting.GREEN : ChatFormatting.WHITE, ChatFormatting.BOLD))
				.addLoreLine(lore(descs[i]))
				.glow(sel)
				.setCallback((idx, type, action) -> {
					if (!admin(p)) return;
					st.game = g;
					st.save();
					buildMain(gui, p);
				}));
		}
		if ("UHC".equals(st.game) || "BINGO".equals(st.game)) gui.setSlot(28, new GuiElementBuilder(Items.PLAYER_HEAD)
			.setName(name("Takım modu: " + ("DUO".equals(st.teamMode) ? "Duo (2'li)" : "Solo"), ChatFormatting.AQUA))
			.addLoreLine(lore("Tıkla: Solo / Duo değiştir"))
			.addLoreLine(lore("Manhunt'ta roller ayrı belirlenir."))
			.setCallback((idx, type, action) -> {
				if (!admin(p)) return;
				st.teamMode = "DUO".equals(st.teamMode) ? "SOLO" : "DUO";
				st.save();
				buildMain(gui, p);
			}));
		gui.setSlot(30, new GuiElementBuilder(Items.COMPARATOR)
			.setName(name("Ayarlar", ChatFormatting.YELLOW, ChatFormatting.BOLD))
			.addLoreLine(lore("Kurallar, ırk modu, yasaklı ırklar, seed sınıfı, host devri"))
			.setCallback((idx, type, action) -> openSettings(p)));
		gui.setSlot(32, new GuiElementBuilder(Items.KNOWLEDGE_BOOK)
			.setName(name("Irklar ve özellikleri", ChatFormatting.LIGHT_PURPLE))
			.setCallback((idx, type, action) -> openOriginInfo(p)));
		gui.setSlot(37, new GuiElementBuilder(Items.OAK_SIGN)
			.setName(name("Oyuncular ve takımlar", ChatFormatting.AQUA))
			.setCallback((idx, type, action) -> openTeams(p)));
		boolean running = mgr.current != null && !mgr.current.isClosed();
		gui.setSlot(49, running
			? new GuiElementBuilder(Items.RED_CONCRETE).setName(name("OYUNU DURDUR", ChatFormatting.RED, ChatFormatting.BOLD))
				.setCallback((idx, type, action) -> {
					if (!admin(p)) return;
					mgr.stop("Host durdurdu");
					gui.close();
				})
			: new GuiElementBuilder(Items.LIME_CONCRETE).setName(name("BAŞLAT: " + gameLabel(st.game), ChatFormatting.GREEN, ChatFormatting.BOLD))
				.addLoreLine(lore(mgr.server.getPlayerList().getPlayerCount() + " oyuncu katılacak"))
				.setCallback((idx, type, action) -> {
					if (!admin(p)) return;
					boolean outpost = tr.kapisma.game.NativeArenaGame.isNew(mgr.settings.game);
					if (outpost) gui.close();
					String err = mgr.start();
					if (!outpost) gui.close();
					if (err != null) p.sendSystemMessage(Msg.error(err));
				}));
		if ("MANHUNT".equals(st.game)) {
			gui.setSlot(39, new GuiElementBuilder(Items.LEATHER_BOOTS)
				.setName(name("Runner seç", ChatFormatting.GREEN))
				.addLoreLine(lore(st.runnerNames.isEmpty() ? "Seçili yok: rastgele" : "Seçili: " + String.join(", ", st.runnerNames)))
				.addLoreLine(lore("Tıkla: oyunculardan runner seç"))
				.setCallback((idx, type, action) -> { if (admin(p)) openRunnerPick(p); }));
		}
		gui.setSlot(43, new GuiElementBuilder(Items.WHITE_BANNER)
			.setName(name("Takımım: renk ve isim", ChatFormatting.AQUA))
			.addLoreLine(lore(teamPrefSummary(p)))
			.addLoreLine(lore("Tıkla: rengini ve takım ismini seç"))
			.setCallback((idx, type, action) -> openTeamPick(p)));
		gui.setSlot(41, new GuiElementBuilder(Items.WRITABLE_BOOK)
			.setName(name("Komutlar ve yardım", ChatFormatting.AQUA))
			.addLoreLine(lore("Tıkla: ne işe yaradığı yazılı komut listesi (sohbette)"))
			.addLoreLine(lore("Pusula: elindeyken sağ tık = hedef değiştir"))
			.setCallback((idx, type, action) -> { gui.close(); tr.kapisma.chat.Help.send(p); }));
		ServerPlayer h = mgr.host();
		gui.setSlot(45, new GuiElementBuilder(Items.OAK_SIGN)
			.setName(name("Host: " + (h != null ? h.getGameProfile().getName() : "yok"), ChatFormatting.GOLD))
			.addLoreLine(lore("Ayarları host değiştirir.")));
	}

	private void openTeams(ServerPlayer p) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, p, false);
		gui.setTitle(Component.literal("Oyuncular ve takımlar"));
		var game = mgr.current;
		int slot = 0;
		if (game != null && !"KATIL".equals(game.id())) {
			for (var team : game.teams) {
				if (team.members.isEmpty()) continue;
				var entry = new GuiElementBuilder(Items.WHITE_BANNER).setName(team.display());
				for (var id : team.members) entry.addLoreLine(lore(game.names.get(id)));
				gui.setSlot(slot++, entry);
			}
		} else {
			for (ServerPlayer player : mgr.server.getPlayerList().getPlayers()) {
				var entry = new GuiElementBuilder(Items.PLAYER_HEAD).setName(name(player.getGameProfile().getName(), ChatFormatting.WHITE));
				entry.addLoreLine(lore(game == null ? teamPrefSummary(player) : "Rol gizli"));
				gui.setSlot(slot++, entry);
			}
		}
		gui.setSlot(26, new GuiElementBuilder(Items.ARROW).setName(name("Geri", ChatFormatting.WHITE)).setCallback((i, t, a) -> openMain(p)));
		gui.open();
	}

	private void openOriginInfo(ServerPlayer p) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, p, false);
		gui.setTitle(Component.literal("Irklar ve özellikleri"));
		for (int i = 0; i < OriginService.ALL.size(); i++) {
			String id = OriginService.ALL.get(i);
			var entry = new GuiElementBuilder(OriginService.ICONS.get(id)).setName(name(OriginService.label(id), ChatFormatting.GOLD));
			OriginService.FEATURES.get(id).forEach(line -> entry.addLoreLine(lore(line)));
			if (mgr.settings.bannedOrigins.contains(id)) entry.addLoreLine(name("Bu sunucuda yasaklı", ChatFormatting.RED));
			gui.setSlot(i, entry);
		}
		gui.setSlot(26, new GuiElementBuilder(Items.ARROW).setName(name("Geri", ChatFormatting.WHITE)).setCallback((i, t, a) -> openMain(p)));
		gui.open();
	}

	// ================= AYARLAR =================
	public void openSettings(ServerPlayer p) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, p, false);
		gui.setTitle(Component.literal("Ayarlar"));
		buildSettings(gui, p);
		gui.open();
	}

	private void buildSettings(SimpleGui gui, ServerPlayer p) {
		Settings st = mgr.settings;
		for (int i = 0; i < 27; i++) gui.clearSlot(i);
		String om = st.originMode.getOrDefault(st.game, "RANDOM");
		gui.setSlot(10, new GuiElementBuilder(Items.NAUTILUS_SHELL)
			.setName(name("Irk modu (" + gameLabel(st.game) + "): " + originModeLabel(om), ChatFormatting.LIGHT_PURPLE))
			.addLoreLine(lore("Tıkla: Rastgele → Seçmeli → Kapalı"))
			.addLoreLine(lore("Yasaklı ırklar: " + (st.bannedOrigins.isEmpty() ? "yok" : String.join(", ", st.bannedOrigins))))
			.setCallback((idx, type, action) -> {
				if (!admin(p)) return;
				String next = switch (om) { case "RANDOM" -> "SELECT"; case "SELECT" -> "OFF"; default -> "RANDOM"; };
				st.originMode.put(st.game, next);
				st.save();
				buildSettings(gui, p);
			}));
		int n = "random".equals(st.seedClass) ? mgr.seeds.total() : mgr.seeds.size(st.seedClass);
		gui.setSlot(12, new GuiElementBuilder(Items.GRASS_BLOCK)
			.setName(name("Seed sınıfı: " + mgr.seeds.displayName(st.seedClass), ChatFormatting.GREEN))
			.addLoreLine(lore(n + " doğrulanmış seed"))
			.addLoreLine(lore("Tıkla: sıradaki sınıf"))
			.setCallback((idx, type, action) -> {
				if (!admin(p)) return;
				List<String> keys = new ArrayList<>(List.of("random"));
				keys.addAll(mgr.seeds.classKeys());
				st.seedClass = keys.get((Math.max(0, keys.indexOf(st.seedClass)) + 1) % keys.size());
				st.save();
				buildSettings(gui, p);
			}));
		gui.setSlot(14, new GuiElementBuilder(Items.WRITABLE_BOOK)
			.setName(name(gameLabel(st.game) + " kuralları", ChatFormatting.YELLOW))
			.addLoreLine(lore("Tıkla: kuralları düzenle"))
			.setCallback((idx, type, action) -> openRules(p, st.game)));
		gui.setSlot(16, new GuiElementBuilder(Items.BARRIER)
			.setName(name("Yasaklı ırklar", ChatFormatting.RED))
			.addLoreLine(lore("Tıkla: ırk havuzunu düzenle"))
			.setCallback((idx, type, action) -> openBans(p)));
		gui.setSlot(22, new GuiElementBuilder(Items.GOLDEN_HELMET)
			.setName(name("Host devret", ChatFormatting.GOLD))
			.addLoreLine(lore("Tıkla: hostluğu başka oyuncuya ver"))
			.setCallback((idx, type, action) -> { if (admin(p)) openHostPick(p); }));
		gui.setSlot(26, new GuiElementBuilder(Items.ARROW).setName(name("Geri", ChatFormatting.WHITE))
			.setCallback((idx, type, action) -> openMain(p)));
	}

	// ================= KURALLAR =================
	public void openRules(ServerPlayer p, String game) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, p, false);
		gui.setTitle(Component.literal(gameLabel(game) + " kuralları"));
		buildRules(gui, p, game);
		gui.open();
	}

	private void buildRules(SimpleGui gui, ServerPlayer p, String game) {
		Settings st = mgr.settings;
		for (int i = 0; i < 54; i++) gui.clearSlot(i);
		List<RuleDef> defs = Rules.of(game);
		for (int i = 0; i < defs.size() && i < 45; i++) {
			RuleDef d = defs.get(i);
			double v = st.num(game, d.key());
			String shown = d.bool() ? (v >= 0.5 ? "AÇIK" : "KAPALI") : (v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v));
			GuiElementBuilder b = new GuiElementBuilder(d.icon())
				.setName(name(d.label() + ": " + shown, d.bool() ? (v >= 0.5 ? ChatFormatting.GREEN : ChatFormatting.RED) : ChatFormatting.YELLOW))
				.addLoreLine(lore(d.desc()))
				.addLoreLine(lore(d.bool() ? "Tıkla: aç/kapat" : "Sol tık: +" + (long) d.step() + "   Sağ tık: -" + (long) d.step()))
				.hideDefaultTooltip();
			b.setCallback((idx, type, action) -> {
				if (!admin(p)) return;
				double cur = st.num(game, d.key());
				double next;
				if (d.bool()) next = cur >= 0.5 ? 0 : 1;
				else next = Math.max(d.min(), Math.min(d.max(), cur + (type.isRight ? -d.step() : d.step())));
				st.set(game, d.key(), next);
				st.save();
				buildRules(gui, p, game);
			});
			gui.setSlot(i, b);
		}
		gui.setSlot(48, new GuiElementBuilder(Items.TNT).setName(name("Varsayılana dön", ChatFormatting.RED))
			.setCallback((idx, type, action) -> {
				if (!admin(p)) return;
				st.reset(game);
				st.save();
				buildRules(gui, p, game);
			}));
		gui.setSlot(49, new GuiElementBuilder(Items.ARROW).setName(name("Geri", ChatFormatting.WHITE))
			.setCallback((idx, type, action) -> openSettings(p)));
	}

	// ================= YASAKLI IRKLAR =================
	public void openBans(ServerPlayer p) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x4, p, false);
		gui.setTitle(Component.literal("Yasaklı ırklar"));
		buildBans(gui, p);
		gui.open();
	}

	private void buildBans(SimpleGui gui, ServerPlayer p) {
		Settings st = mgr.settings;
		for (int i = 0; i < 36; i++) gui.clearSlot(i);
		int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};   // 15 irk
		for (int i = 0; i < OriginService.ALL.size(); i++) {
			String o = OriginService.ALL.get(i);
			boolean banned = st.bannedOrigins.contains(o);
			gui.setSlot(slots[i], new GuiElementBuilder(OriginService.ICONS.get(o))
				.setName(name(OriginService.label(o) + (banned ? " - YASAKLI" : " - serbest"), banned ? ChatFormatting.RED : ChatFormatting.GREEN))
				.addLoreLine(lore("Tıkla: yasakla / serbest bırak"))
				.glow(!banned)
				.setCallback((idx, type, action) -> {
					if (!admin(p)) return;
					if (banned) st.bannedOrigins.remove(o); else st.bannedOrigins.add(o);
					st.save();
					buildBans(gui, p);
				}));
		}
		gui.setSlot(30, new GuiElementBuilder(Items.PAPER)
			.setName(name("Herkese farklı ırk: " + (st.uniqueOrigins ? "AÇIK" : "KAPALI"), st.uniqueOrigins ? ChatFormatting.GREEN : ChatFormatting.RED))
			.addLoreLine(lore("Rastgele dağıtımda tekrar önlenir."))
			.setCallback((idx, type, action) -> {
				if (!admin(p)) return;
				st.uniqueOrigins = !st.uniqueOrigins;
				st.save();
				buildBans(gui, p);
			}));
		gui.setSlot(22, new GuiElementBuilder(Items.ARROW).setName(name("Geri", ChatFormatting.WHITE))
			.setCallback((idx, type, action) -> openSettings(p)));
	}

	// ================= TAKIM RENGI / ISMI =================
	private static final Item[] COLOR_ITEMS = {
		Items.RED_WOOL, Items.BLUE_WOOL, Items.LIME_WOOL, Items.YELLOW_WOOL, Items.PINK_WOOL,
		Items.LIGHT_BLUE_WOOL, Items.ORANGE_WOOL, Items.GREEN_WOOL, Items.PURPLE_WOOL, Items.WHITE_WOOL};

	private String teamPrefSummary(ServerPlayer p) {
		var pr = mgr.teamPrefs.get(p.getUUID());
		if (pr == null || (pr.color == null && (pr.name == null || pr.name.isBlank()))) return "Seçili yok: otomatik renk ve isim";
		return "Renk: " + (pr.color != null ? tr.kapisma.team.TeamService.colorName(pr.color) : "otomatik")
			+ " | İsim: " + (pr.name != null && !pr.name.isBlank() ? pr.name : "otomatik");
	}

	public void openTeamPick(ServerPlayer p) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, p, false);
		gui.setTitle(Component.literal("Takımım: renk ve isim"));
		buildTeamPick(gui, p);
		gui.open();
	}

	private void buildTeamPick(SimpleGui gui, ServerPlayer p) {
		var prefs = mgr.teamPrefs;
		var mine = mgr.pref(p.getUUID());
		for (int i = 0; i < 27; i++) gui.clearSlot(i);
		var colors = tr.kapisma.team.TeamService.COLORS;
		int[] slots = {1, 2, 3, 4, 5, 6, 7, 10, 12, 14};
		for (int i = 0; i < colors.length; i++) {
			final var col = colors[i];
			String takenBy = null;
			for (var e : prefs.entrySet()) {
				if (col == e.getValue().color && !e.getKey().equals(p.getUUID())) {
					ServerPlayer o = mgr.server.getPlayerList().getPlayer(e.getKey());
					takenBy = o != null ? o.getGameProfile().getName() : "başka oyuncu";
				}
			}
			boolean sel = col == mine.color;
			final String tb = takenBy;
			GuiElementBuilder b = new GuiElementBuilder(COLOR_ITEMS[i])
				.setName(name(tr.kapisma.team.TeamService.COLOR_NAMES[i] + (sel ? "  ✔" : ""), col, ChatFormatting.BOLD))
				.addLoreLine(lore(tb != null ? "Alınmış: " + tb : sel ? "Seçili (tekrar tıkla: kaldır)" : "Tıkla: bu rengi seç"))
				.glow(sel);
			b.setCallback((idx, type, action) -> {
				if (tb != null) { Msg.tell(p, "Bu renk " + tb + " tarafından seçilmiş."); return; }
				mine.color = sel ? null : col;
				buildTeamPick(gui, p);
			});
			gui.setSlot(slots[i], b);
		}
		gui.setSlot(16, new GuiElementBuilder(Items.NAME_TAG)
			.setName(name("Takım ismi: " + (mine.name != null && !mine.name.isBlank() ? mine.name : "otomatik"), ChatFormatting.GOLD))
			.addLoreLine(lore("Tıkla: ismi yaz (en çok 16 harf)"))
			.setCallback((idx, type, action) -> openNameInput(p)));
		gui.setSlot(21, new GuiElementBuilder(Items.TNT).setName(name("Sıfırla (otomatik)", ChatFormatting.RED))
			.setCallback((idx, type, action) -> {
				mine.color = null;
				mine.name = null;
				buildTeamPick(gui, p);
			}));
		gui.setSlot(22, new GuiElementBuilder(Items.ARROW).setName(name("Geri", ChatFormatting.WHITE))
			.setCallback((idx, type, action) -> openMain(p)));
		gui.setTitle(Component.literal("Takımım: renk ve isim"));
	}

	private void openNameInput(ServerPlayer p) {
		var gui = new eu.pb4.sgui.api.gui.AnvilInputGui(p, false);
		gui.setTitle(Component.literal("Takım ismi yaz"));
		gui.setDefaultInputValue(mgr.pref(p.getUUID()).name != null ? mgr.pref(p.getUUID()).name : "");
		gui.setSlot(2, new GuiElementBuilder(Items.LIME_DYE).setName(name("Onayla", ChatFormatting.GREEN))
			.setCallback((idx, type, action) -> {
				String in = gui.getInput() == null ? "" : gui.getInput().trim();
				if (in.length() > 16) in = in.substring(0, 16);
				mgr.pref(p.getUUID()).name = in.isEmpty() ? null : in;
				Msg.tell(p, in.isEmpty() ? "Takım ismi otomatik." : "Takım ismin: " + in);
				openTeamPick(p);
			}));
		gui.open();
	}

	// ================= OYUNCU SECIMLERI =================
	private GuiElementBuilder head(ServerPlayer t) {
		GuiElementBuilder b = new GuiElementBuilder(Items.PLAYER_HEAD);
		try {
			b.setSkullOwner(t.getGameProfile(), mgr.server);
		} catch (Throwable ignored) {
			// offline-mode: ozel kafa yuklenemezse varsayilan kafa
		}
		return b;
	}

	public void openRunnerPick(ServerPlayer p) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, p, false);
		gui.setTitle(Component.literal("Runner seç"));
		buildRunnerPick(gui, p);
		gui.open();
	}

	private void buildRunnerPick(SimpleGui gui, ServerPlayer p) {
		Settings st = mgr.settings;
		for (int i = 0; i < 27; i++) gui.clearSlot(i);
		List<ServerPlayer> online = new ArrayList<>(mgr.server.getPlayerList().getPlayers());
		for (int i = 0; i < online.size() && i < 18; i++) {
			ServerPlayer t = online.get(i);
			String nm = t.getGameProfile().getName();
			boolean sel = st.runnerNames.stream().anyMatch(x -> x.equalsIgnoreCase(nm));
			gui.setSlot(i, head(t)
				.setName(name(nm + (sel ? "  - RUNNER" : "  - hunter"), sel ? ChatFormatting.GREEN : ChatFormatting.RED))
				.addLoreLine(lore("Tıkla: runner yap / çıkar"))
				.glow(sel)
				.setCallback((idx, type, action) -> {
					if (!admin(p)) return;
					if (sel) st.runnerNames.removeIf(x -> x.equalsIgnoreCase(nm)); else st.runnerNames.add(nm);
					st.save();
					buildRunnerPick(gui, p);
				}));
		}
		gui.setSlot(21, new GuiElementBuilder(Items.TNT).setName(name("Seçimi temizle (rastgele)", ChatFormatting.RED))
			.setCallback((idx, type, action) -> {
				if (!admin(p)) return;
				st.runnerNames.clear();
				st.save();
				buildRunnerPick(gui, p);
			}));
		gui.setSlot(22, new GuiElementBuilder(Items.ARROW).setName(name("Geri", ChatFormatting.WHITE))
			.setCallback((idx, type, action) -> openMain(p)));
	}

	public void openHostPick(ServerPlayer p) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, p, false);
		gui.setTitle(Component.literal("Host devret"));
		List<ServerPlayer> online = new ArrayList<>(mgr.server.getPlayerList().getPlayers());
		for (int i = 0; i < online.size() && i < 18; i++) {
			ServerPlayer t = online.get(i);
			gui.setSlot(i, head(t)
				.setName(name(t.getGameProfile().getName(), ChatFormatting.GOLD))
				.addLoreLine(lore("Tıkla: host yap"))
				.setCallback((idx, type, action) -> {
					if (!admin(p)) return;
					mgr.setHost(t);
					gui.close();
				}));
		}
		gui.setSlot(22, new GuiElementBuilder(Items.ARROW).setName(name("Geri", ChatFormatting.WHITE))
			.setCallback((idx, type, action) -> openMain(p)));
		gui.open();
	}

	// ================= IRK SECIMI (oyun basinda) =================
	public void openOriginPick(ServerPlayer p, List<String> pool, Consumer<String> onPick) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x4, p, false);
		gui.setTitle(Component.literal("Irkını seç"));
		int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
		for (int i = 0; i < pool.size() && i < slots.length; i++) {
			String o = pool.get(i);
			gui.setSlot(slots[i], new GuiElementBuilder(OriginService.ICONS.get(o))
				.setName(name(OriginService.label(o), ChatFormatting.GOLD, ChatFormatting.BOLD))
				.addLoreLine(lore("Tıkla: bu ırkı seç"))
				.setCallback((idx, type, action) -> {
					onPick.accept(o);
					Msg.tell(p, "Irkın: " + OriginService.label(o) + ". Diğer oyuncular bekleniyor.");
					gui.close();
				}));
		}
		gui.open();
	}
}
