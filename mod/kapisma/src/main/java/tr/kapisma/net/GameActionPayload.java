package tr.kapisma.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GameActionPayload(int action) implements CustomPacketPayload {
	public static final int TOGGLE_KILLER = 0, SPECTATE = 1;
	public static final Type<GameActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kapisma", "game_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GameActionPayload> CODEC =
		CustomPacketPayload.codec((payload, buffer) -> buffer.writeVarInt(payload.action),
			buffer -> new GameActionPayload(buffer.readVarInt()));

	@Override
	public Type<GameActionPayload> type() { return TYPE; }
}
