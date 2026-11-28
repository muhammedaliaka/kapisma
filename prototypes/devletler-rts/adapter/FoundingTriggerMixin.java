package kapisma.rts.mixin;

import kapisma.rts.RtsTerrain;
import net.minecraft.class_2168;
import net.minecraft.class_266;
import net.minecraft.class_3153;
import net.minecraft.class_3222;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_3153.class)
public class FoundingTriggerMixin {
    @Inject(method = "method_13820", at = @At("RETURN"), remap = false)
    private static void rts$processRegionChoice(class_2168 source, class_3222 player,
                                               class_266 objective, int value, CallbackInfoReturnable<Integer> result) {
        if (objective.method_1113().equals("krts_found")) RtsTerrain.processRegionChoice(player, value);
    }
}
