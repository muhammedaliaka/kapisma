package kapisma.rts.mixin;

import java.util.UUID;
import kapisma.rts.RtsContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ydmsama.hundred_years_war.main.utils.RelationSystem.RelationType;

@Mixin(targets = "ydmsama.hundred_years_war.main.utils.RelationSystem", remap = false)
public class RelationMixin {
    @Inject(method = "getRelation", at = @At("HEAD"), cancellable = true)
    private static void rts$relation(UUID source, UUID target, CallbackInfoReturnable<RelationType> cir) {
        RelationType relation = RtsContext.relationship(source, target);
        if (relation != null) cir.setReturnValue(relation);
    }
}
