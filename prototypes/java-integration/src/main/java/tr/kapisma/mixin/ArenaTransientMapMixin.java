package tr.kapisma.mixin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tr.kapisma.game.CaravanTransientMaps;

@Mixin(ServerLevel.class)
public abstract class ArenaTransientMapMixin {
    @Inject(method="getMapData",at=@At("HEAD"),cancellable=true)
    private void kapisma$activeCaravanMap(MapId id,CallbackInfoReturnable<MapItemSavedData> cir){
        var data=CaravanTransientMaps.lookup((ServerLevel)(Object)this,id);
        if(data!=null)cir.setReturnValue(data);
    }
}
