package tr.kapisma.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import tr.kapisma.Kapisma;

/** /yardim: komutlar konuya gore gruplu, ne ise yaradigi acik yazili. Komutlar tiklanabilir; oyun varsa o moda ozel ipucu cikar. */
public final class Help {
	private Help() {}

	private static MutableComponent cmd(String cmd, String desc) {
		return Component.literal(cmd).withStyle(s -> s.withColor(ChatFormatting.YELLOW)
			.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd.split(" <|\\[| \\(")[0] + " "))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Tıkla: sohbet kutusuna yaz"))))
			.append(Component.literal("  " + desc).withStyle(ChatFormatting.GRAY));
	}

	private static void head(ServerPlayer p, String text) {
		p.sendSystemMessage(Component.literal("▌ " + text).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
	}

	/** Oyun calisiyorsa o moda ozel kisa kullanim ipucu. */
	private static String modeTip(String game) {
		return switch (game) {
			case "UHC" -> "UHC: PvP başta kapalı, süre bitince açılır. Sınır daralır, merkeze ilerle. Pusula: rakip, takım arkadaşı, sınır merkezi, başlangıç noktan.";
			case "BINGO" -> "Tombala: 9. slottaki kâğıda sağ tık = kart (/kart). Eşyayı ilk alan takım sayar. Pusula: rakip, takım arkadaşı, başlangıç noktası.";
			case "ESYA" -> "Eşya Yarışı. /gorev gizli eşyanı gösterir. /atla ile kazandığın atlama hakkını kullanabilirsin.";
			case "MANHUNT" -> "Manhunt: hunter pusulası runner'ı gösterir; sağ tık = en yakın / en uzak / belirli runner / takım arkadaşı.";
			case "KATIL" -> "Katil Kim: pusula en yakın / en uzak oyuncuyu gösterir (isim yazmaz). Sağ tık hedefi değiştirir.";
			default -> null;
		};
	}

	public static void send(ServerPlayer p) {
		var gm = Kapisma.GM;
		boolean admin = gm != null && gm.canAdmin(p);
		ServerPlayer host = gm != null ? gm.host() : null;
		p.sendSystemMessage(Component.literal("━━ Kapışma yardım ━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		p.sendSystemMessage(Component.literal("Komut bilmek şart değil: Nether Yıldızı'na sağ tık (ya da /oyun) menüyü açar.").withStyle(ChatFormatting.WHITE));
		if (gm != null && gm.current != null && !gm.current.isClosed()) {
			String tip = modeTip(gm.current.id());
			if (tip != null) p.sendSystemMessage(Component.literal("» " + tip).withStyle(ChatFormatting.GREEN));
		}

		head(p, "Oyun sırasında");
		p.sendSystemMessage(cmd("/pusula [sonraki|onceki]", "pusulanın hedefini değiştirir (pusulaya sağ tık ile aynı; Shift+sağ tık = geri)"));
		p.sendSystemMessage(cmd("/kart", "Tombala kartını açar (kâğıda sağ tık ile aynı)"));
		p.sendSystemMessage(cmd("/izle", "izleyici pusulası menüsünü açar"));
		p.sendSystemMessage(cmd("/koord", "bulunduğun yeri takımına yazar"));
		p.sendSystemMessage(cmd("/istatistik [oyuncu]", "son oyunda kim kimi kaç kez öldürdü / nasıl öldü"));

		head(p, "Takımım");
		p.sendSystemMessage(cmd("/takimim renk <renk>", "takım rengini seç (kırmızı, mavi, yeşil...)"));
		p.sendSystemMessage(cmd("/takimim isim <ad>", "takımına isim ver"));
		p.sendSystemMessage(cmd("/takimim sifirla", "renk ve ismi otomatik yap"));

		head(p, "Lobi");
		p.sendSystemMessage(cmd("/parkur checkpoint|bas|cik", "parkurda son checkpoint'e dön / baştan başla / parkurdan çık"));
		p.sendSystemMessage(cmd("/puan", "parkur ve kapışma puanların, ilk 3"));

		head(p, "Sohbet");
		p.sendSystemMessage(cmd("/tc <mesaj>", "sadece takımına"));
		p.sendSystemMessage(cmd("/herkes <mesaj>", "herkese"));
		p.sendSystemMessage(cmd("/takim <takım> <mesaj>", "seçtiğin takıma (boşluk yerine _ koy)"));
		p.sendSystemMessage(cmd("/ozel <oyuncu> <mesaj>", "tek kişiye"));
		p.sendSystemMessage(Component.literal("Lobide yazdığın herkese gider; oyun başlayınca normal yazdığın sadece takımına gider.").withStyle(ChatFormatting.DARK_GRAY));

		head(p, "Ekran (Kapışma HUD modu varsa)");
		p.sendSystemMessage(cmd("/hud", "sol üstteki kartın ve sağ üstteki haritanın boyutu/açık-kapalı ayarı (H tuşu: hepsini gizle)"));
		p.sendSystemMessage(cmd("/hud boyut <0.4-1.5|otomatik>", "küçük ekranda HUD'u küçült"));

		p.sendSystemMessage(Component.literal(admin ? "▌ Host / yönetici (yetkin var)" : "▌ Host / yönetici (yetkin YOK, host: " + (host != null ? host.getGameProfile().getName() : "yok") + ")")
			.withStyle(admin ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
		p.sendSystemMessage(cmd("/kapisma start | stop", "oyunu başlat / durdur"));
		p.sendSystemMessage(cmd("/kapisma game <UHC|BINGO|ESYA|MANHUNT|KATIL>", "hangi oyunun oynanacağını seç"));
		p.sendSystemMessage(cmd("/kapisma team <solo|duo>", "herkes tek mi, ikili takım mı"));
		p.sendSystemMessage(cmd("/kapisma origins <random|select|off>", "ırk: rastgele / herkes seçsin / kapalı"));
		p.sendSystemMessage(cmd("/kapisma ban|unban <ırk>", "bir ırkı yasakla / serbest bırak"));
		p.sendSystemMessage(cmd("/kapisma seedclass <sınıf>", "dünya (seed) sınıfı"));
		p.sendSystemMessage(cmd("/kapisma rules <oyun>", "oyunun kurallarını ve değerlerini listeler"));
		p.sendSystemMessage(cmd("/kapisma rule <oyun> <kural> <değer>", "bir kuralı değiştirir (değer sayı: 1 açık, 0 kapalı)"));
		p.sendSystemMessage(cmd("/kapisma runner <oyuncu|temizle>", "Manhunt'ta runner seç"));
		p.sendSystemMessage(cmd("/kapisma host <oyuncu>", "hostluğu başkasına devret"));
		p.sendSystemMessage(Component.literal("/gamemode, /give, /tp gibi Minecraft komutları sadece op (yönetici) içindir.").withStyle(ChatFormatting.DARK_GRAY));
	}
}
