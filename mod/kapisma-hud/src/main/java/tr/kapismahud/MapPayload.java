package tr.kapismahud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sunucudan gelen mini harita verisi (kapisma:minimap). Sunucudaki tr.kapisma.net.MapPayload ile AYNI bayt duzeni (surum 1). */
public final class MapPayload implements CustomPacketPayload {
	public static final Type<MapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kapisma", "minimap"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MapPayload> CODEC = CustomPacketPayload.codec(MapPayload::write, MapPayload::read);

	public static final class Mate {
		public String name;
		public int rgb;
		public int x, z;
	}

	public boolean active;
	public double centerX, centerZ;
	public float startRadius, curRadius, endRadius;
	public int selfX, selfZ;
	public float selfYaw;
	public final List<Mate> mates = new ArrayList<>();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void write(RegistryFriendlyByteBuf b) {
		throw new UnsupportedOperationException("istemci sadece okur");
	}

	private static MapPayload read(RegistryFriendlyByteBuf b) {
		MapPayload p = new MapPayload();
		int version = b.readByte();
		p.active = b.readBoolean();
		if (!p.active || version != 1) { p.active = false; return p; }
		p.centerX = b.readDouble();
		p.centerZ = b.readDouble();
		p.startRadius = b.readFloat();
		p.curRadius = b.readFloat();
		p.endRadius = b.readFloat();
		p.selfX = b.readInt();
		p.selfZ = b.readInt();
		p.selfYaw = b.readFloat();
		int n = b.readVarInt();
		for (int i = 0; i < n; i++) {
			Mate m = new Mate();
			m.name = b.readUtf();
			m.rgb = b.readInt();
			m.x = b.readInt();
			m.z = b.readInt();
			p.mates.add(m);
		}
		return p;
	}
}
