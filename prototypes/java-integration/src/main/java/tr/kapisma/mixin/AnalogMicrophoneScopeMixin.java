package tr.kapisma.mixin;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tr.kapisma.voice.VoiceRouteScope;

/** Preserve the actual authenticated speaker across Analog's nearby-radio relay and queue. */
@Pseudo
@Mixin(targets="dev.mrturtle.analog.AnalogPlugin",remap=false)
public abstract class AnalogMicrophoneScopeMixin {
    @Redirect(method="onMicrophonePacket",at=@At(value="INVOKE",
        target="Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V",remap=false),
        require=1,remap=false)
    private void kapisma$source(MinecraftServer server,Runnable task,MicrophonePacketEvent event){
        var connection=event.getSenderConnection();
        if(connection==null||connection.getPlayer()==null)return;
        server.execute(VoiceRouteScope.guardMicrophone(connection.getPlayer().getUuid(),task));
    }
}
