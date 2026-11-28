package kapisma.rts.mixin;

import kapisma.rts.RtsWood;
import kapisma.rts.RtsTerritory;
import net.minecraft.class_239;
import net.minecraft.class_3222;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.main.network.packets.MoveCommandPacket;

@Mixin(value = MoveCommandPacket.class, remap = false)
public abstract class WoodOrderMixin {
    @Shadow @Final private class_239 hitResult;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void rts$directWoodworker(class_3222 player, MoveCommandPacket packet, CallbackInfo callback) {
        var hit = ((WoodOrderMixin) (Object) packet).hitResult;
        if (RtsTerritory.receiveOrder(player, hit) || RtsWood.receiveOrder(player, hit)) callback.cancel();
    }
}
