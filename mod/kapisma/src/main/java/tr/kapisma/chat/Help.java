package tr.kapisma.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import tr.kapisma.Kapisma;

/** /yardim: komut listesi ve kisinin yetkisine gore aciklama. Komutlar tiklanabilir. */
public final class Help {
	private Help() {}

	private static MutableComponent cmd(String cmd, String desc) {
		return Component.literal(cmd).withStyle(s -> s.withColor(ChatFormatting.YELLOW)
			.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd.split(" <|\\[")[0] + " "))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Tıkla: sohbet kutusuna yaz"))))
			.append(Component.literal("  " + desc).withStyle(ChatFormatting.GRAY));
	}

	public static void send(ServerPlayer p) {
		var gm = Kapisma.GM;
		boolean admin = gm != null && gm.canAdmin(p);
		ServerPlayer host = gm != null ? gm.host() : null;
		p.sendSystemMessage(Component.literal("━━ Kapışma komutları ━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		p.sendSystemMessage(Component.literal("Herkes için:").withStyle(ChatFormatting.AQUA));
		p.sendSystemMessage(cmd("/oyun", "menüyü açar (Nether Yıldızı ile de açılır)"));
		p.sendSystemMessage(cmd("/yardim", "bu liste"));
		p.sendSystemMessage(cmd("/takimim renk <renk>", "takım rengini seç (örn. kirmizi, mavi)"));
		p.sendSystemMessage(cmd("/takimim isim <ad>", "takım ismini yaz"));
		p.sendSystemMessage(cmd("/kart", "Tombala kartı ve hedef seçimi (oyun sırasında)"));
		p.sendSystemMessage(cmd("/istatistik [oyuncu]", "son oyunda kim kimi kaç kez öldürdü"));
		p.sendSystemMessage(cmd("/koord", "koordinatını takımına gönderir"));
		p.sendSystemMessage(cmd("/parkur checkpoint|bas|cik", "lobi parkuru: son checkpoint'e dön / baştan başla / çık"));
		p.sendSystemMessage(cmd("/puan", "parkur ve kapışma puanların, ilk 3"));
		p.sendSystemMessage(Component.literal("Sohbet:").withStyle(ChatFormatting.AQUA));
		p.sendSystemMessage(cmd("/tc <mesaj>", "sadece takımına"));
		p.sendSystemMessage(cmd("/herkes <mesaj>", "herkese"));
		p.sendSystemMessage(cmd("/takim <takım> <mesaj>", "seçtiğin takıma (boşluk yerine _ koy)"));
		p.sendSystemMessage(cmd("/ozel <oyuncu> <mesaj>", "tek kişiye"));
		p.sendSystemMessage(Component.literal("Not: Lobide yazdığın herkese gider. Oyun başlayınca normal yazın sadece takımına gider.").withStyle(ChatFormatting.DARK_GRAY));
		p.sendSystemMessage(Component.literal(admin ? "Host/op komutları (yetkin var):" : "Host/op komutları (yetkin YOK, host: " + (host != null ? host.getGameProfile().getName() : "yok") + "):")
			.withStyle(admin ? ChatFormatting.GREEN : ChatFormatting.RED));
		p.sendSystemMessage(cmd("/kapisma start | stop", "oyunu başlat / durdur"));
		p.sendSystemMessage(cmd("/kapisma game <UHC|BINGO|MANHUNT|KATIL>", "oyun modu"));
		p.sendSystemMessage(cmd("/kapisma team <solo|duo>", "takım modu"));
		p.sendSystemMessage(cmd("/kapisma origins <random|select|off>", "ırk modu"));
		p.sendSystemMessage(cmd("/kapisma ban|unban <ırk>", "ırk yasakla / serbest bırak"));
		p.sendSystemMessage(cmd("/kapisma seedclass <sınıf>", "seed sınıfı"));
		p.sendSystemMessage(cmd("/kapisma rules <oyun>", "kuralları listeler"));
		p.sendSystemMessage(cmd("/kapisma rule <oyun> <kural> <değer>", "kural değiştirir"));
		p.sendSystemMessage(cmd("/kapisma runner <oyuncu|temizle>", "Manhunt runner seçimi"));
		p.sendSystemMessage(cmd("/kapisma host <oyuncu>", "hostluğu devret"));
		p.sendSystemMessage(Component.literal("/gamemode, /give, /tp gibi Minecraft komutları sadece op (yönetici) içindir.").withStyle(ChatFormatting.DARK_GRAY));
	}
}
