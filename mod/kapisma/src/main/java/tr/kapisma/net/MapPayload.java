package tr.kapisma.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Istemci modu (kapisma-hud) icin mini harita verisi: kapisma:minimap. Istemcideki tr.kapismahud.MapPayload ile AYNI bayt duzeni (surum 2).
 * Arazi yok: sadece sinir karesi (baslangic / simdiki / son), oyuncunun kendisi ve takim arkadaslari.
 */
public final class MapPayload implements CustomPacketPayload {
	public static final Type<MapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kapisma", "minimap"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MapPayload> CODEC = CustomPacketPayload.codec(MapPayload::write, MapPayload::read);

	public record Mate(String name, int rgb, int x, int z) {}

	public boolean active;
	public double centerX, centerZ;
	public float startRadius, curRadius, endRadius;   // endRadius <= 0: daralma yok
	public int selfX, selfZ;
	public float selfYaw;
	public final List<Mate> mates = new ArrayList<>();
	public boolean hasTarget;          // pusula hedefi
	public int targetX, targetZ;
	public String targetLabel = "";

	public static MapPayload off() {
		return new MapPayload();
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void write(RegistryFriendlyByteBuf b) {
		b.writeByte(2);
		b.writeBoolean(active);
		if (!active) return;
		b.writeDouble(centerX);
		b.writeDouble(centerZ);
		b.writeFloat(startRadius);
		b.writeFloat(curRadius);
		b.writeFloat(endRadius);
		b.writeInt(selfX);
		b.writeInt(selfZ);
		b.writeFloat(selfYaw);
		b.writeVarInt(mates.size());
		for (Mate m : mates) {
			b.writeUtf(m.name());
			b.writeInt(m.rgb());
			b.writeInt(m.x());
			b.writeInt(m.z());
		}
		b.writeBoolean(hasTarget);
		if (hasTarget) {
			b.writeInt(targetX);
			b.writeInt(targetZ);
			b.writeUtf(targetLabel);
		}
	}

	private static MapPayload read(RegistryFriendlyByteBuf b) {
		throw new UnsupportedOperationException("sunucu sadece gonderir");
	}
}
