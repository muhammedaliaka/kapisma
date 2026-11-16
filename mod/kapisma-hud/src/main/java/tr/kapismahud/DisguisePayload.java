package tr.kapismahud;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Katil Kim: skin gizleme acik/kapali (kapisma:disguise). Sunucudaki tr.kapisma.net.DisguisePayload ile ayni bayt duzeni. */
public final class DisguisePayload implements CustomPacketPayload {
	public static final Type<DisguisePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kapisma", "disguise"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DisguisePayload> CODEC = CustomPacketPayload.codec(DisguisePayload::write, DisguisePayload::read);

	public boolean on;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void write(RegistryFriendlyByteBuf b) {
		throw new UnsupportedOperationException("istemci sadece okur");
	}

	private static DisguisePayload read(RegistryFriendlyByteBuf b) {
		DisguisePayload p = new DisguisePayload();
		int version = b.readByte();
		p.on = b.readBoolean() && version == 1;
		return p;
	}
}
