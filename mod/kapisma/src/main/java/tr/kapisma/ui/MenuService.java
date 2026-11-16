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
			case "KATIL" -> "Katil Kim";
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

	private void buildMain(SimpleGui gui, ServerPlayer p) {
		Settings st = mgr.settings;
		for (int i = 0; i < 54; i++) gui.clearSlot(i);
		String[] games = {"UHC", "BINGO", "MANHUNT", "KATIL"};
		Item[] icons = {Items.GOLDEN_APPLE, Items.FILLED_MAP, Items.COMPASS, Items.IRON_SWORD};
		String[] descs = {"Doğal can yok, daralan sınır, son takım kazanır.", "5x5 eşya kartı, çizgileri ilk tamamlayan kazanır.", "Runner Ejderha'yı öldürmeye, hunter'lar runner'ı avlamaya çalışır.",
			"Herkes aynı skin'de, 200x200 alanda rastgele doğar. Masumlar katil hepsini öldürmeden Ejderha'yı öldürür; gizli katil masumları avlar. Sesli sohbet."};
		int[] slots = {10, 12, 14, 16};
		for (int i = 0; i < 4; i++) {
			final String g = games[i];
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
		gui.setSlot(28, new GuiElementBuilder(Items.PLAYER_HEAD)
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
					String err = mgr.start();
					gui.close();
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
		ServerPlayer h = mgr.host();
		gui.setSlot(45, new GuiElementBuilder(Items.OAK_SIGN)
			.setName(name("Host: " + (h != null ? h.getGameProfile().getName() : "yok"), ChatFormatting.GOLD))
			.addLoreLine(lore("Ayarları host değiştirir.")));
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
