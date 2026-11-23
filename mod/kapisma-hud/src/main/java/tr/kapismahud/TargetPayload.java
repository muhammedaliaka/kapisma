package tr.kapismahud;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Pusula hedefi (kapisma:maptarget); sunucudaki tr.kapisma.net.TargetPayload ile ayni bayt duzeni. */
public final class TargetPayload implements CustomPacketPayload {
	public static final Type<TargetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kapisma", "maptarget"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TargetPayload> CODEC = CustomPacketPayload.codec(TargetPayload::write, TargetPayload::read);

	public boolean has;
	public int x, z;
	public String label = "";

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void write(RegistryFriendlyByteBuf b) {
		throw new UnsupportedOperationException("istemci sadece okur");
	}

	private static TargetPayload read(RegistryFriendlyByteBuf b) {
		TargetPayload p = new TargetPayload();
		p.has = b.readBoolean();
		if (p.has) {
			p.x = b.readInt();
			p.z = b.readInt();
			p.label = b.readUtf();
		}
		return p;
	}
}
