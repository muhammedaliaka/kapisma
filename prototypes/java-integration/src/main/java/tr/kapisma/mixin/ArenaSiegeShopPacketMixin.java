package tr.kapisma.mixin;
import eu.pb4.sgui.api.GuiHelpers;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tr.kapisma.game.SiegeShopGui;
/** SGUI intercepts before vanilla's container-ID guard; restore it for our shop only. */
@Mixin(value=ServerGamePacketListenerImpl.class,priority=1500)
public abstract class ArenaSiegeShopPacketMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleContainerClick(Lnet/minecraft/network/protocol/game/ServerboundContainerClickPacket;)V",at=@At("HEAD"),cancellable=true)
    private void kapisma$guardShop(ServerboundContainerClickPacket packet,CallbackInfo ci){
        // Vanilla schedules to the server thread; never inspect/mutate GUI from Netty.
        if(player.getServer()==null||!player.getServer().isSameThread())return;
        if(GuiHelpers.getCurrentGui(player) instanceof SiegeShopGui&&
            (packet.getContainerId()!=player.containerMenu.containerId||packet.getSlotNum() < -999||packet.getSlotNum()>=player.containerMenu.slots.size())){
            player.containerMenu.broadcastFullState();ci.cancel();
        }
    }
}
