package tr.kapisma.mixin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tr.kapisma.Kapisma;
import tr.kapisma.game.NativeArenaGame;
@Mixin(AbstractContainerMenu.class)
public abstract class ArenaContainerEquipmentMixin {
    @Inject(method="clicked(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V",at=@At("HEAD"),cancellable=true)
    private void kapisma$denyTransfer(int slot,int button,ClickType type,Player player,CallbackInfo ci){
        if(player instanceof ServerPlayer p&&Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame arena&&arena.locksEquipment(p)){
            // Lock non-personal containers and intentional item tossing, before any stack mutation.
            if((Object)this!=p.inventoryMenu||type==ClickType.THROW||type==ClickType.QUICK_CRAFT||slot==-999||slot>=0&&slot<5){
                p.containerMenu.broadcastFullState();ci.cancel();
            }
        }
    }
}