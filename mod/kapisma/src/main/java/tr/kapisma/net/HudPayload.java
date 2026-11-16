package tr.kapisma.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Istemci modu (kapisma-hud) icin HUD verisi: kapisma:hud. Istemcideki tr.kapismahud.HudPayload ile AYNI bayt duzeni (surum 1).
 * Istemci modu olmayan oyuncuya gonderilmez (ServerPlayNetworking.canSend).
 */
public final class HudPayload implements CustomPacketPayload {
	public static final Type<HudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kapisma", "hud"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HudPayload> CODEC = CustomPacketPayload.codec(HudPayload::write, HudPayload::read);

	public record Cell(String itemId, int ownerRgb, boolean mine, boolean target) {}

	public record Rival(String name, int rgb, int lines, int items) {}

	public boolean active;
	public String title = "";
	public String teamName = "";
	public int teamRgb = 0xFFFFFF;
	public int lines, need, items, timeSec;
	public boolean countdown;
	public final List<Cell> card = new ArrayList<>();
	public final List<Rival> rivals = new ArrayList<>();

	/** HUD'u kapatan bos paket. */
	public static HudPayload off() {
		return new HudPayload();
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void write(RegistryFriendlyByteBuf b) {
		b.writeByte(1);
		b.writeBoolean(active);
		if (!active) return;
		b.writeUtf(title);
		b.writeUtf(teamName);
		b.writeInt(teamRgb);
		b.writeVarInt(lines);
		b.writeVarInt(need);
		b.writeVarInt(items);
		b.writeInt(timeSec);
		b.writeBoolean(countdown);
		b.writeVarInt(card.size());
		for (Cell c : card) {
			b.writeUtf(c.itemId());
			b.writeInt(c.ownerRgb());
			b.writeByte((c.mine() ? 1 : 0) | (c.target() ? 2 : 0));
		}
		b.writeVarInt(rivals.size());
		for (Rival r : rivals) {
			b.writeUtf(r.name());
			b.writeInt(r.rgb());
			b.writeVarInt(r.lines());
			b.writeVarInt(r.items());
		}
	}

	private static HudPayload read(RegistryFriendlyByteBuf b) {
		throw new UnsupportedOperationException("sunucu sadece gonderir");
	}
}
