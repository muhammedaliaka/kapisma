package kapisma.rts.mixin;

import kapisma.rts.RtsContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;

@Mixin(value = BaseCombatEntity.class, remap = false)
public class GuardMixin {
    @Inject(method = "shouldSuppressWanderBehavior", at = @At("HEAD"), cancellable = true)
    private void rts$keepUnitAtAssignedPosition(CallbackInfoReturnable<Boolean> callback) {
        var unit = (BaseCombatEntity) (Object) this;
        if (unit.method_5752().contains("krts.unit")
                && (RtsContext.score("krts_ctrl", "#natural_match") == 1
                || RtsContext.score("krts_guarding", unit) == 1
                || RtsContext.score("krts_training", unit) == 1)) {
            callback.setReturnValue(true);
        }
    }
}
