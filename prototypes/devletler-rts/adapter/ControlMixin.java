package kapisma.rts.mixin;

import kapisma.rts.RtsContext;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "ydmsama.hundred_years_war.main.utils.ServerRelationHelper", remap = false)
public class ControlMixin {
    @Inject(method = "hasControlOver", at = @At("HEAD"), cancellable = true)
    private static void rts$guardCommand(class_1657 player, class_1297 target,
                                         CallbackInfoReturnable<Boolean> result) {
        if (RtsContext.blocksControl(player, target)) result.setReturnValue(false);
    }
}
