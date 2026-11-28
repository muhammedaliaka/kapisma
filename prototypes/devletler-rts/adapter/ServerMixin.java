package kapisma.rts.mixin;

import java.util.function.BooleanSupplier;
import kapisma.rts.RtsAutonomy;
import kapisma.rts.RtsContext;
import kapisma.rts.RtsTerrain;
import kapisma.rts.RtsTerritory;
import kapisma.rts.RtsWood;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class ServerMixin {
    @Unique private int krts$automationClock;
    @Unique private final RtsTerrain krts$terrain = new RtsTerrain();
    @Unique private final RtsWood krts$wood = new RtsWood();

    @Inject(method = "method_3748", at = @At("HEAD"), remap = false)
    private void rts$tick(BooleanSupplier timeRemaining, CallbackInfo ci) {
        var server = (MinecraftServer) (Object) this;
        RtsContext.heartbeat(server);
        if (++krts$automationClock == 20) {
            krts$automationClock = 0;
            krts$terrain.processRequests(server);
            RtsTerritory.update(server);
            RtsAutonomy.update(server, krts$wood);
        }
    }
}
