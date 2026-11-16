package tr.kapismahud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucudan gelen HUD verisi (kapisma:hud). Sunucu modundaki tr.kapisma.net.HudPayload ile AYNI bayt duzeni.
 * Surum 1: byte version, bool active, [title, teamName, teamRgb, lines, need, items, timeSec, countdown, card[], rivals[]]
 */
public final class HudPayload implements CustomPacketPayload {
	public static final Type<HudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kapisma", "hud"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HudPayload> CODEC = CustomPacketPayload.codec(HudPayload::write, HudPayload::read);

	public static final class Cell {
		public String itemId;
		public int ownerRgb = -1;       // -1: alinmadi
		public boolean mine;            // benim takimim aldi
		public boolean target;          // hedef isaretli
	}

	public static final class Rival {
		public String name;
		public int rgb;
		public int lines;
		public int items;
	}

	public boolean active;
	public String title = "";
	public String teamName = "";
	public int teamRgb = 0xFFFFFF;
	public int lines, need, items, timeSec;
	public boolean countdown;
	public final List<Cell> card = new ArrayList<>();
	public final List<Rival> rivals = new ArrayList<>();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void write(RegistryFriendlyByteBuf b) {
		throw new UnsupportedOperationException("istemci sadece okur");
	}

	private static HudPayload read(RegistryFriendlyByteBuf b) {
		HudPayload p = new HudPayload();
		int version = b.readByte();
		p.active = b.readBoolean();
		if (!p.active || version != 1) { p.active = false; return p; }
		p.title = b.readUtf();
		p.teamName = b.readUtf();
		p.teamRgb = b.readInt();
		p.lines = b.readVarInt();
		p.need = b.readVarInt();
		p.items = b.readVarInt();
		p.timeSec = b.readInt();
		p.countdown = b.readBoolean();
		int n = b.readVarInt();
		for (int i = 0; i < n; i++) {
			Cell c = new Cell();
			c.itemId = b.readUtf();
			c.ownerRgb = b.readInt();
			int flags = b.readByte();
			c.mine = (flags & 1) != 0;
			c.target = (flags & 2) != 0;
			p.card.add(c);
		}
		int r = b.readVarInt();
		for (int i = 0; i < r; i++) {
			Rival v = new Rival();
			v.name = b.readUtf();
			v.rgb = b.readInt();
			v.lines = b.readVarInt();
			v.items = b.readVarInt();
			p.rivals.add(v);
		}
		return p;
	}
}
