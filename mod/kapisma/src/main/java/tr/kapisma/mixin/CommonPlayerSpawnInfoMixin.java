package tr.kapisma.mixin;

import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tr.kapisma.guard.AntiCheat;

/** Giris/yeniden dogma paketindeki "hashed seed" gercek yerine uydurma gonderilir: hile istemcisi seed'i dogrulayamaz. */
@Mixin(CommonPlayerSpawnInfo.class)
public abstract class CommonPlayerSpawnInfoMixin {
	@Redirect(method = "write", at = @At(value = "FIELD", target = "Lnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;seed:J", opcode = Opcodes.GETFIELD))
	private long kapisma$fakeSeed(CommonPlayerSpawnInfo self) {
		return AntiCheat.fakeHashedSeed(self.seed());
	}
}
