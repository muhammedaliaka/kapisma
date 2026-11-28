package tr.kapisma.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Katil Kim: istemci modu (kapisma-hud) tum oyuncu skin'lerini ayni skin ile degistirir. Istemcideki tr.kapismahud.DisguisePayload ile ayni bayt duzeni. */
public final class DisguisePayload implements CustomPacketPayload {
	public static final Type<DisguisePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kapisma", "disguise_v2"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DisguisePayload> CODEC = CustomPacketPayload.codec(DisguisePayload::write, DisguisePayload::read);

	public final boolean on;

	public DisguisePayload(boolean on) {
		this.on = on;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void write(RegistryFriendlyByteBuf b) {
		b.writeByte(1);
		b.writeBoolean(on);
	}

	private static DisguisePayload read(RegistryFriendlyByteBuf b) {
		throw new UnsupportedOperationException("sunucu sadece gonderir");
	}
}
