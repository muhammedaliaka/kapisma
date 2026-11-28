package tr.kapisma.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tr.kapisma.Kapisma;
import tr.kapisma.game.ManhuntGame;

@Mixin(LivingEntity.class)
public abstract class KillerCombatMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float kapisma$killerStrength(float amount, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer p) || !source.is(DamageTypes.PLAYER_ATTACK)
			|| Kapisma.GM == null || !(Kapisma.GM.current instanceof ManhuntGame game)) return amount;
		return amount + game.killerDamageBonus(p);
	}

	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void kapisma$killerResistance(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if (!((Object) this instanceof ServerPlayer p) || Kapisma.GM == null
			|| !(Kapisma.GM.current instanceof ManhuntGame game)
			|| source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE)) return;
		float factor = game.killerDamageFactor(p);
		if (factor < 1) cir.setReturnValue(cir.getReturnValueF() * factor);
	}
}
