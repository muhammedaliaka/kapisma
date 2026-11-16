package tr.kapisma;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import tr.kapisma.config.RuleDef;
import tr.kapisma.config.Rules;
import tr.kapisma.game.GameManager;
import tr.kapisma.origin.OriginService;

/** /kapisma ... ve /oyun komutlari. Ayar komutlari host veya op icin. */
public final class Cmds {
	private Cmds() {}

	private static GameManager gm() {
		return Kapisma.GM;
	}

	private static boolean admin(CommandContext<CommandSourceStack> c) {
		var src = c.getSource();
		if (src.getEntity() instanceof ServerPlayer p) return gm().canAdmin(p);
		return true;            // konsol
	}

	private static int deny(CommandContext<CommandSourceStack> c) {
		ServerPlayer h = gm().host();
		c.getSource().sendFailure(Component.literal("Bu komut sadece host veya op içindir. Şu an host: "
			+ (h != null ? h.getGameProfile().getName() : "yok") + ". Host sana devredebilir (menü > Host devret). Tüm komutlar için: /yardim"));
		return 0;
	}

	private static int usage(CommandContext<CommandSourceStack> c, String text) {
		c.getSource().sendFailure(Component.literal("Eksik/yanlış kullanım. Doğrusu: " + text));
		return 0;
	}

	private static int parkour(CommandContext<CommandSourceStack> c, String what) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		if (!tr.kapisma.lobby.Lobby.inLobby(p)) {
			c.getSource().sendFailure(Component.literal("Parkur sadece lobide kullanılır."));
			return 0;
		}
		tr.kapisma.lobby.Parkour.action(p, what);
		return 1;
	}

	private static int scores(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		var sc = tr.kapisma.lobby.Lobby.scores;
		var e = sc.players.get(p.getUUID().toString());
		p.sendSystemMessage(Component.literal("━━ Puanların ━━").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
		if (e == null) {
			p.sendSystemMessage(Component.literal("Henüz puanın yok: parkur oyna ya da bir kapışmaya katıl.").withStyle(net.minecraft.ChatFormatting.GRAY));
		} else {
			p.sendSystemMessage(Component.literal("Parkur: " + e.parkourPoints + " puan, " + e.parkourFinishes + " kez bitirme"
				+ (e.bestTimeSec >= 0 ? ", en iyi süre " + Msg.mmss(e.bestTimeSec) : "")).withStyle(net.minecraft.ChatFormatting.GREEN));
			p.sendSystemMessage(Component.literal("Kapışma: " + e.gamePoints + " puan, " + e.wins + " galibiyet, " + e.kills + " öldürme, " + e.games + " oyun").withStyle(net.minecraft.ChatFormatting.RED));
		}
		int i = 1;
		p.sendSystemMessage(Component.literal("Parkur ilk 3:").withStyle(net.minecraft.ChatFormatting.AQUA));
		for (var t : sc.topParkour(3)) p.sendSystemMessage(Component.literal((i++) + ". " + t.name + " " + t.parkourPoints).withStyle(net.minecraft.ChatFormatting.WHITE));
		i = 1;
		p.sendSystemMessage(Component.literal("Kapışma ilk 3:").withStyle(net.minecraft.ChatFormatting.AQUA));
		for (var t : sc.topGames(3)) p.sendSystemMessage(Component.literal((i++) + ". " + t.name + " " + t.gamePoints).withStyle(net.minecraft.ChatFormatting.WHITE));
		return 1;
	}

	private static int say(CommandContext<CommandSourceStack> c, String s) {
		c.getSource().sendSuccess(() -> Msg.info(s), false);
		return 1;
	}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		var root = Commands.literal("kapisma")
			.executes(Cmds::help)
			.then(Commands.literal("help").executes(Cmds::help))
			.then(Commands.literal("ping").executes(c -> say(c, "Kapışma çalışıyor")))
			.then(Commands.literal("menu").executes(Cmds::menu))
			.then(Commands.literal("debug").requires(src -> src.hasPermission(2))
				.then(Commands.literal("xray")
					.then(Commands.literal("audit").executes(c -> { String r = tr.kapisma.xray.Xray.audit(c.getSource().getPlayerOrException()); Kapisma.LOG.info("xray audit: {}", r); return say(c, r); }))
					.then(Commands.literal("simulate").executes(c -> {
						String r = tr.kapisma.xray.Xray.simulate(c.getSource().getPlayerOrException());
						Kapisma.LOG.info("xray simulate: {}", r);
						return say(c, r);
					}))
					.then(Commands.literal("tpcave").executes(c -> {
						ServerPlayer pl = c.getSource().getPlayerOrException();
						var cave = tr.kapisma.xray.Xray.findCave(pl);
						if (cave == null) return say(c, "Bu chunk'ta gizli mağara yok.");
						pl.teleportTo(cave.getX() + 0.5, cave.getY(), cave.getZ() + 0.5);
						Kapisma.LOG.info("xray tpcave: {}", cave.toShortString());
						return say(c, "Mağaraya ışınlandın: " + cave.toShortString());
					}))
					.then(Commands.literal("status").executes(c -> { String r = tr.kapisma.xray.Xray.status(); Kapisma.LOG.info("{}", r); return say(c, r); }))
					.then(Commands.literal("force").then(Commands.argument("mod", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1, 2)).executes(c -> {
						int m = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "mod");
						tr.kapisma.xray.Xray.forceMode = m < 0 ? null : m;
						return say(c, "xray zorla: " + (m < 0 ? "kapalı (oyuna bağlı)" : m));
					}))))
				.then(Commands.literal("seedscan").executes(c -> say(c, tr.kapisma.seed.SeedScan.running() ? "Tarama sürüyor: " + tr.kapisma.seed.SeedScan.progress() : tr.kapisma.seed.SeedScan.start(1000)))
					.then(Commands.argument("yaricap", com.mojang.brigadier.arguments.IntegerArgumentType.integer(200, 2000)).executes(c ->
						say(c, tr.kapisma.seed.SeedScan.start(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "yaricap"))))))
				.then(Commands.literal("givecard")
					.then(Commands.argument("oyuncu", EntityArgument.player())
						.then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 25)).executes(c -> {
							if (!(gm().current instanceof tr.kapisma.game.BingoGame b)) return say(c, "Tombala çalışmıyor.");
							int n = b.debugGive(EntityArgument.getPlayer(c, "oyuncu"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n"));
							return say(c, n + " kart eşyası verildi.");
						})))))
			.then(Commands.literal("newworld").executes(c -> {
				if (!admin(c)) return deny(c);
				gm().requestNewWorld();
				return 1;
			}))
			.then(Commands.literal("status").executes(Cmds::status))
			.then(Commands.literal("start").executes(c -> {
				if (!admin(c)) return deny(c);
				String err = gm().start();
				if (err != null) { c.getSource().sendFailure(Component.literal(err)); return 0; }
				return 1;
			}))
			.then(Commands.literal("stop").executes(c -> {
				if (!admin(c)) return deny(c);
				gm().stop("Komutla durduruldu");
				return say(c, "Oyun durduruldu.");
			}))
			.then(Commands.literal("host").executes(c -> usage(c, "/kapisma host <oyuncu>")).then(Commands.argument("oyuncu", EntityArgument.player()).executes(c -> {
				if (!admin(c)) return deny(c);
				ServerPlayer p = EntityArgument.getPlayer(c, "oyuncu");
				gm().setHost(p);
				return say(c, "Yeni host: " + p.getGameProfile().getName());
			})))
			.then(Commands.literal("game").executes(c -> usage(c, "/kapisma game <UHC|BINGO|MANHUNT|KATIL>")).then(Commands.argument("oyun", StringArgumentType.word())
				.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[] {"UHC", "BINGO", "MANHUNT", "KATIL"}, b))
				.executes(c -> {
					if (!admin(c)) return deny(c);
					String g = StringArgumentType.getString(c, "oyun").toUpperCase();
					if (!g.equals("UHC") && !g.equals("BINGO") && !g.equals("MANHUNT") && !g.equals("KATIL")) return say(c, "Geçerli: UHC, BINGO, MANHUNT, KATIL");
					gm().settings.game = g;
					gm().settings.save();
					return say(c, "Oyun modu: " + g);
				})))
			.then(Commands.literal("team").executes(c -> usage(c, "/kapisma team <solo|duo>")).then(Commands.argument("mod", StringArgumentType.word())
				.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[] {"solo", "duo"}, b))
				.executes(c -> {
					if (!admin(c)) return deny(c);
					gm().settings.teamMode = StringArgumentType.getString(c, "mod").equalsIgnoreCase("duo") ? "DUO" : "SOLO";
					gm().settings.save();
					return say(c, "Takım modu: " + gm().settings.teamMode);
				})))
			.then(Commands.literal("seedclass").executes(c -> usage(c, "/kapisma seedclass <sınıf|random>")).then(Commands.argument("sinif", StringArgumentType.word())
				.suggests((c, b) -> {
					var all = new java.util.ArrayList<>(gm().seeds.classKeys());
					all.add("random");
					return net.minecraft.commands.SharedSuggestionProvider.suggest(all, b);
				})
				.executes(c -> {
					if (!admin(c)) return deny(c);
					gm().settings.seedClass = StringArgumentType.getString(c, "sinif");
					gm().settings.save();
					return say(c, "Seed sınıfı: " + gm().settings.seedClass);
				})))
			.then(Commands.literal("origins").executes(c -> usage(c, "/kapisma origins <random|select|off>")).then(Commands.argument("mod", StringArgumentType.word())
				.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[] {"random", "select", "off"}, b))
				.executes(c -> {
					if (!admin(c)) return deny(c);
					var s = gm().settings;
					s.originMode.put(s.game, StringArgumentType.getString(c, "mod").toUpperCase());
					s.save();
					return say(c, s.game + " ırk modu: " + s.originMode.get(s.game));
				})))
			.then(Commands.literal("ban").executes(c -> usage(c, "/kapisma ban <ırk>")).then(Commands.argument("irk", StringArgumentType.word())
				.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(OriginService.ALL, b))
				.executes(c -> {
					if (!admin(c)) return deny(c);
					String o = StringArgumentType.getString(c, "irk").toLowerCase();
					if (!OriginService.ALL.contains(o)) return say(c, "Bilinmeyen ırk.");
					if (!gm().settings.bannedOrigins.contains(o)) gm().settings.bannedOrigins.add(o);
					gm().settings.save();
					return say(c, o + " yasaklandı.");
				})))
			.then(Commands.literal("unban").executes(c -> usage(c, "/kapisma unban <ırk>")).then(Commands.argument("irk", StringArgumentType.word())
				.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(OriginService.ALL, b))
				.executes(c -> {
					if (!admin(c)) return deny(c);
					gm().settings.bannedOrigins.remove(StringArgumentType.getString(c, "irk").toLowerCase());
					gm().settings.save();
					return say(c, "Yasak kaldırıldı.");
				})))
			.then(Commands.literal("rule").executes(c -> usage(c, "/kapisma rule <UHC|BINGO|MANHUNT|KATIL> <kural> <değer>  (liste: /kapisma rules <oyun>)"))
				.then(Commands.argument("oyun", StringArgumentType.word())
					.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[] {"UHC", "BINGO", "MANHUNT", "KATIL"}, b))
					.then(Commands.argument("kural", StringArgumentType.word())
						.suggests((c, b) -> {
							var keys = Rules.of(StringArgumentType.getString(c, "oyun").toUpperCase()).stream().map(RuleDef::key).toList();
							return net.minecraft.commands.SharedSuggestionProvider.suggest(keys, b);
						})
						.then(Commands.argument("deger", DoubleArgumentType.doubleArg())
							.executes(Cmds::rule)))))
			.then(Commands.literal("runner").executes(c -> usage(c, "/kapisma runner <oyuncu|temizle>")).then(Commands.argument("oyuncu", StringArgumentType.word())
				.suggests((c, b) -> {
					var names = new java.util.ArrayList<String>(java.util.List.of(c.getSource().getServer().getPlayerList().getPlayerNamesArray()));
					names.add("temizle");
					return net.minecraft.commands.SharedSuggestionProvider.suggest(names, b);
				})
				.executes(c -> {
					if (!admin(c)) return deny(c);
					String n = StringArgumentType.getString(c, "oyuncu");
					var st = gm().settings;
					if (n.equalsIgnoreCase("temizle")) st.runnerNames.clear();
					else if (!st.runnerNames.removeIf(x -> x.equalsIgnoreCase(n))) st.runnerNames.add(n);
					st.save();
					return say(c, st.runnerNames.isEmpty() ? "Runner seçimi: rastgele" : "Runner'lar: " + String.join(", ", st.runnerNames));
				})))
			.then(Commands.literal("rules").executes(c -> usage(c, "/kapisma rules <UHC|BINGO|MANHUNT|KATIL>")).then(Commands.argument("oyun", StringArgumentType.word()).executes(Cmds::rules)));
		d.register(root);
		d.register(Commands.literal("oyun").executes(Cmds::menu));
		d.register(Commands.literal("yardim").executes(Cmds::help));
		d.register(Commands.literal("parkur")
			.executes(c -> usage(c, "/parkur checkpoint | bas | cik"))
			.then(Commands.literal("checkpoint").executes(c -> parkour(c, "checkpoint")))
			.then(Commands.literal("bas").executes(c -> parkour(c, "bas")))
			.then(Commands.literal("cik").executes(c -> parkour(c, "cik"))));
		d.register(Commands.literal("puan").executes(Cmds::scores));
		d.register(Commands.literal("istatistik")
			.executes(c -> tr.kapisma.game.Stats.send(c.getSource().getPlayerOrException(), statGame(), null))
			.then(Commands.argument("oyuncu", StringArgumentType.word())
				.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(tr.kapisma.game.Stats.playerNames(statGame()), b))
				.executes(c -> tr.kapisma.game.Stats.send(c.getSource().getPlayerOrException(), statGame(), StringArgumentType.getString(c, "oyuncu")))));
		d.register(Commands.literal("tc").then(Commands.argument("mesaj", StringArgumentType.greedyString()).executes(c ->
			tr.kapisma.chat.ChatService.team(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "mesaj")))));
		d.register(Commands.literal("herkes").then(Commands.argument("mesaj", StringArgumentType.greedyString()).executes(c ->
			tr.kapisma.chat.ChatService.all(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "mesaj")))));
		d.register(Commands.literal("ozel").then(Commands.argument("oyuncu", EntityArgument.player())
			.then(Commands.argument("mesaj", StringArgumentType.greedyString()).executes(c ->
				tr.kapisma.chat.ChatService.whisper(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "oyuncu"), StringArgumentType.getString(c, "mesaj"))))));
		d.register(Commands.literal("takim")
			.executes(c -> usage(c, "/takim <takım_adı> <mesaj>  (boşluk yerine _ koy; örn. /takim Kırmızı_Takım selam)"))
			.then(Commands.argument("takim", StringArgumentType.word())
				.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(tr.kapisma.chat.ChatService.teamTokens(), b))
				.executes(c -> usage(c, "/takim <takım_adı> <mesaj>"))
				.then(Commands.argument("mesaj", StringArgumentType.greedyString()).executes(c ->
					tr.kapisma.chat.ChatService.toTeam(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "takim"), StringArgumentType.getString(c, "mesaj"))))));
		d.register(Commands.literal("koord").executes(c -> {
			ServerPlayer p = c.getSource().getPlayerOrException();
			String dim = p.level().dimension().location().getPath();
			return tr.kapisma.chat.ChatService.team(p, String.format("Koordinatım: %d %d %d (%s)", p.getBlockX(), p.getBlockY(), p.getBlockZ(), dim));
		}));
		d.register(Commands.literal("takimim")
			.executes(c -> usage(c, "/takimim renk <renk> | /takimim isim <ad> | /takimim sifirla  (veya menü > Takımım)"))
			.then(Commands.literal("renk").executes(c -> usage(c, "/takimim renk <kirmizi|mavi|yesil|sari|pembe|turkuaz|turuncu|koyu_yesil|mor|beyaz>"))
				.then(Commands.argument("renk", StringArgumentType.word())
					.suggests((c, b) -> {
						var l = new java.util.ArrayList<String>();
						for (String n : tr.kapisma.team.TeamService.COLOR_NAMES) l.add(ascii(n));
						return net.minecraft.commands.SharedSuggestionProvider.suggest(l, b);
					})
					.executes(c -> {
						ServerPlayer p = c.getSource().getPlayerOrException();
						String want = ascii(StringArgumentType.getString(c, "renk"));
						var cols = tr.kapisma.team.TeamService.COLORS;
						for (int i = 0; i < cols.length; i++) {
							if (!ascii(tr.kapisma.team.TeamService.COLOR_NAMES[i]).equals(want)) continue;
							for (var e : gm().teamPrefs.entrySet()) {
								if (cols[i] == e.getValue().color && !e.getKey().equals(p.getUUID())) {
									Msg.tell(p, "Bu renk başka bir oyuncu tarafından seçilmiş.");
									return 0;
								}
							}
							gm().pref(p.getUUID()).color = cols[i];
							Msg.tell(p, "Takım rengin: " + tr.kapisma.team.TeamService.COLOR_NAMES[i]);
							return 1;
						}
						return usage(c, "/takimim renk <kirmizi|mavi|yesil|sari|pembe|turkuaz|turuncu|koyu_yesil|mor|beyaz>");
					})))
			.then(Commands.literal("isim").executes(c -> usage(c, "/takimim isim <ad>  (en çok 16 harf)"))
				.then(Commands.argument("ad", StringArgumentType.greedyString()).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					String n = StringArgumentType.getString(c, "ad").trim();
					if (n.length() > 16) n = n.substring(0, 16);
					gm().pref(p.getUUID()).name = n.isEmpty() ? null : n;
					Msg.tell(p, "Takım ismin: " + (n.isEmpty() ? "otomatik" : n));
					return 1;
				})))
			.then(Commands.literal("sifirla").executes(c -> {
				ServerPlayer p = c.getSource().getPlayerOrException();
				gm().pref(p.getUUID()).color = null;
				gm().pref(p.getUUID()).name = null;
				Msg.tell(p, "Takım rengi ve ismi otomatik.");
				return 1;
			})));
		d.register(Commands.literal("kart").executes(c -> {
			ServerPlayer p = c.getSource().getPlayerOrException();
			if (gm().current instanceof tr.kapisma.game.BingoGame b) b.openCard(p);
			else say(c, "Şu an Tombala oynanmıyor.");
			return 1;
		}));
	}

	/** Calisan oyun yoksa son biten oyun. */
	private static tr.kapisma.game.Game statGame() {
		return gm().current != null ? gm().current : gm().last;
	}

	private static String ascii(String t) {
		return t.toLowerCase(new java.util.Locale("tr")).replace('ı', 'i').replace('ş', 's').replace('ç', 'c').replace('ğ', 'g')
			.replace('ü', 'u').replace('ö', 'o').replace(' ', '_');
	}

	private static int help(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		if (c.getSource().getEntity() instanceof ServerPlayer p) tr.kapisma.chat.Help.send(p);
		else say(c, "Komutlar: /yardim oyuncular içindir; konsolda /kapisma status kullan.");
		return 1;
	}

	private static int menu(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		gm().menus.openMain(p);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c) {
		var g = gm();
		var s = g.settings;
		ServerPlayer h = g.host();
		StringBuilder sb = new StringBuilder();
		sb.append("Host: ").append(h != null ? h.getGameProfile().getName() : "yok")
			.append(" | Mod: ").append(s.game).append(" | Takım: ").append(s.teamMode)
			.append(" | Irk: ").append(s.originMode.get(s.game)).append(" | Seed sınıfı: ").append(s.seedClass)
			.append(" | Yasaklı: ").append(s.bannedOrigins)
			.append(" | Havuz: ").append(g.seeds.total())
			.append(" | Oyun: ").append(g.current == null ? "yok (lobi)" : g.current.id() + " " + g.current.phase);
		return say(c, sb.toString());
	}

	private static int rules(CommandContext<CommandSourceStack> c) {
		String game = StringArgumentType.getString(c, "oyun").toUpperCase();
		StringBuilder sb = new StringBuilder(game + " kuralları: ");
		for (RuleDef r : Rules.of(game)) sb.append(r.key()).append('=').append(gm().settings.num(game, r.key())).append("  ");
		return say(c, sb.toString());
	}

	private static int rule(CommandContext<CommandSourceStack> c) {
		if (!admin(c)) return deny(c);
		String game = StringArgumentType.getString(c, "oyun").toUpperCase();
		String key = StringArgumentType.getString(c, "kural");
		double v = DoubleArgumentType.getDouble(c, "deger");
		for (RuleDef r : Rules.of(game)) {
			if (r.key().equals(key)) {
				gm().settings.set(game, key, Math.max(r.min(), Math.min(r.max(), v)));
				gm().settings.save();
				return say(c, game + "." + key + " = " + gm().settings.num(game, key));
			}
		}
		c.getSource().sendFailure(Component.literal("Bilinmeyen kural: " + key));
		return 0;
	}
}
