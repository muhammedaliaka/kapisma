package tr.kapisma.mixin;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tr.kapisma.Kapisma;
import tr.kapisma.game.NativeArenaGame;
/** A server teleport must invalidate swept flight-gate history, even if the displacement is small. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ArenaTeleportMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="teleport(DDDFFLjava/util/Set;)V",at=@At("HEAD"))
    private void kapisma$arenaTeleport(double x,double y,double z,float yaw,float pitch,Set<RelativeMovement> flags,CallbackInfo ci){
        if(Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame arena)
            arena.onServerTeleport(player.getUUID());
    }
    @Inject(method="handlePlayerInput",at=@At("TAIL"))
    private void kapisma$caravanInput(net.minecraft.network.protocol.game.ServerboundPlayerInputPacket packet,CallbackInfo ci){
        if(Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame arena)
            arena.caravanInput(player,packet.getZza(),packet.getXxa(),packet.isShiftKeyDown());
    }
}
