package kapisma.rts.mixin;

import kapisma.rts.RtsContext;
import net.minecraft.class_1282;
import net.minecraft.class_1297;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"net.minecraft.class_1309", "ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity"}, remap = false)
public class DamageMixin {
    @Inject(method = "method_5643", at = @At("HEAD"), cancellable = true)
    private void rts$damage(class_1282 source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (RtsContext.blocksDamage((class_1297) (Object) this, source)) cir.setReturnValue(false);
    }

    @Inject(method = "method_5643", at = @At("RETURN"))
    private void rts$recordInjury(class_1282 source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) RtsContext.recordInjury((class_1297) (Object) this);
    }
}
