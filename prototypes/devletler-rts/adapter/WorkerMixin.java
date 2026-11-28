package kapisma.rts.mixin;

import net.minecraft.class_1646;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_1646.class)
public class WorkerMixin {
    @Inject(method = "method_5958", at = @At("HEAD"), cancellable = true, remap = false)
    private void rts$useWorkshopJobs(CallbackInfo info) {
        var tags = ((class_1646) (Object) this).method_5752();
        if (tags.contains("krts.worker") || tags.contains("krts.farmer")) info.cancel();
    }
}
