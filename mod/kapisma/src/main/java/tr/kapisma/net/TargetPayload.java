package tr.kapisma.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Pusula hedefi (mini haritada altin baklava): kapisma:maptarget. Ayri paket: eski HUD modu bunu kaydetmez, ona hic gonderilmez. */
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
		b.writeBoolean(has);
		if (!has) return;
		b.writeInt(x);
		b.writeInt(z);
		b.writeUtf(label);
	}

	private static TargetPayload read(RegistryFriendlyByteBuf b) {
		throw new UnsupportedOperationException("sunucu sadece gonderir");
	}
}
