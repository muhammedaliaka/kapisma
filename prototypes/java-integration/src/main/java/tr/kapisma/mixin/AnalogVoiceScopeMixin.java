package tr.kapisma.mixin;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tr.kapisma.voice.VoiceRouteScope;

/** Exact Analog 1.2.2+1.21 bytecode targets. The optional config gate checks the mod version. */
@Pseudo
@Mixin(targets="dev.mrturtle.analog.util.RadioUtil",remap=false)
public abstract class AnalogVoiceScopeMixin {
    @Redirect(method="transmitOnChannel",at=@At(value="INVOKE",
        target="Lde/maxhenkel/voicechat/api/VoicechatServerApi;sendLocationalSoundPacketTo(Lde/maxhenkel/voicechat/api/VoicechatConnection;Lde/maxhenkel/voicechat/api/packets/LocationalSoundPacket;)V",
        remap=false),require=1,remap=false)
    private static void kapisma$playerRadio(VoicechatServerApi senderApi,VoicechatConnection receiver,LocationalSoundPacket packet,
            VoicechatServerApi api,MicrophonePacket mic,ServerPlayer origin,int channel){
        kapisma$forward(senderApi,receiver,packet,origin);
    }
    @Redirect(method={"transmitOnChannel","transmitOnNearbyTransmitters"},at=@At(value="INVOKE",
        target="Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V",
        remap=false),require=2,expect=2,allow=2,remap=false)
    private static void kapisma$guardQueuedRadio(MinecraftServer server,Runnable task){
        server.execute(VoiceRouteScope.guard(task));
    }
    @Redirect(method="lambda$transmitOnChannel$2",at=@At(value="INVOKE",
        target="Lde/maxhenkel/voicechat/api/VoicechatServerApi;sendLocationalSoundPacketTo(Lde/maxhenkel/voicechat/api/VoicechatConnection;Lde/maxhenkel/voicechat/api/packets/LocationalSoundPacket;)V",
        remap=false),require=1,remap=false)
    private static void kapisma$fixedRadio(VoicechatServerApi senderApi,VoicechatConnection receiver,LocationalSoundPacket packet,
            ServerLevel level,int channel,VoicechatServerApi api,ServerPlayer origin,MicrophonePacket mic,byte[] data){
        kapisma$forward(senderApi,receiver,packet,origin);
    }
    private static void kapisma$forward(VoicechatServerApi api,VoicechatConnection receiver,LocationalSoundPacket packet,ServerPlayer origin){
        if(receiver==null||receiver.getPlayer()==null||origin==null||packet==null)return;
        var key=new VoiceRouteScope.Key(packet.getSender(),packet.getChannelId(),packet.getSequenceNumber());
        VoiceRouteScope.sendAnalog(origin.getUUID(),receiver.getPlayer().getUuid(),key,
            ()->api.sendLocationalSoundPacketTo(receiver,packet));
    }
}