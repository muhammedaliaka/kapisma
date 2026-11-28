package tr.kapisma.voice;
import java.lang.reflect.*;
import java.util.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Test-only entrypoint installed only by verify_runtime.py, never in production resources.
 * Generates a synthetic sine; no real microphone or recording. */
public final class VoiceRuntimeProof implements ModInitializer,VoicechatPlugin {
    private static volatile VoicechatServerApi api;
    private static Job job;
    private static long sequence;
    private record Job(ServerPlayer origin,int channel,OpusEncoder encoder,int remaining,String mode){}
    public String getPluginId(){return "kapisma_voice_proof";}
    public void registerEvents(EventRegistration reg){
        reg.registerEvent(VoicechatServerStartedEvent.class,e->{api=e.getVoicechat();System.out.println("ARENA_VOICE_API_READY");});
        reg.registerEvent(VoicechatServerStoppedEvent.class,e->api=null);
        reg.registerEvent(LocationalSoundPacketEvent.class,e->{
            if(!SoundPacketEvent.SOURCE_PLUGIN.equals(e.getSource())||e.getReceiverConnection()==null)return;
            System.out.println("ARENA_VOICE_PACKET receiver="+e.getReceiverConnection().getPlayer().getUuid()+" nullSender="+(e.getSenderConnection()==null)+" cancelled="+e.isCancelled());
        },-1000);
    }
    public void onInitialize(){
        if(!Boolean.getBoolean("kapisma.arenaProof"))throw new IllegalStateException("QA flag required");
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("arena-voice-proof")
            .requires(s->s.getEntity()==null&&s.hasPermission(4))
            .then(Commands.argument("source",EntityArgument.player()).then(Commands.argument("channel",IntegerArgumentType.integer(0,999))
                 .executes(c->begin(EntityArgument.getPlayer(c,"source"),IntegerArgumentType.getInteger(c,"channel"),"channel"))
                .then(Commands.literal("nearby").executes(c->begin(EntityArgument.getPlayer(c,"source"),IntegerArgumentType.getInteger(c,"channel"),"nearby")))
                .then(Commands.literal("mic").executes(c->begin(EntityArgument.getPlayer(c,"source"),IntegerArgumentType.getInteger(c,"channel"),"mic")))))));
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("arena-voice-fixture")
            .requires(s->s.getEntity()==null&&s.hasPermission(4))
            .then(Commands.argument("anchor",EntityArgument.player())
                .then(Commands.literal("receiver").executes(c->fixture(EntityArgument.getPlayer(c,"anchor"),"receiver")))
                .then(Commands.literal("transmitter").executes(c->fixture(EntityArgument.getPlayer(c,"anchor"),"transmitter"))))));

        ServerTickEvents.END_SERVER_TICK.register(VoiceRuntimeProof::tick);
    }
    private static int fixture(ServerPlayer p,String type){
        try{
            var level=p.serverLevel();var pos=p.blockPosition().offset(0,0,2);
            if(!level.getBlockState(pos).isAir())throw new IllegalStateException("fixture location occupied "+pos);
            var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("analog:"+type));
            if(block==Blocks.AIR||!level.setBlockAndUpdate(pos,block.defaultBlockState()))throw new IllegalStateException("fixture placement failed");
            block.setPlacedBy(level,pos,level.getBlockState(pos),p,new ItemStack(block));
            var entity=Objects.requireNonNull(level.getBlockEntity(pos));
            entity.getClass().getField("enabled").setBoolean(entity,true);
            entity.getClass().getField("channel").setInt(entity,7);entity.setChanged();
            var radio=Class.forName("dev.mrturtle.analog.util.RadioUtil");
            Object state=radio.getMethod("getGlobalRadioState",net.minecraft.server.level.ServerLevel.class).invoke(null,level);
            var positions=(List<?>)state.getClass().getMethod(type.equals("receiver")?"getReceivers":"getTransmitters").invoke(state);
            if(!positions.contains(pos))throw new IllegalStateException("fixture not registered");
            System.out.println("ARENA_VOICE_FIXTURE "+type+" "+pos);return 1;
        }catch(Exception ex){throw new IllegalStateException("fixture",ex);}
    }
    private static int begin(ServerPlayer p,int channel,String mode){
        if(job!=null||api==null)throw new IllegalStateException("voice proof unavailable");
        job=new Job(p,channel,api.createEncoder(),40,mode);
        return 1;
    }
    private static void tick(MinecraftServer server){
        if(job==null)return;
        var j=job;
        try{
            var radio=Class.forName("dev.mrturtle.analog.util.RadioUtil");
            var mic=Class.forName("de.maxhenkel.voicechat.voice.common.MicPacket");
            var impl=Class.forName("de.maxhenkel.voicechat.plugins.impl.packets.MicrophonePacketImpl");
            Method transmit=j.mode().equals("nearby")?radio.getMethod("transmitOnNearbyTransmitters",VoicechatServerApi.class,MicrophonePacket.class,ServerPlayer.class):radio.getMethod("transmitOnChannel",VoicechatServerApi.class,MicrophonePacket.class,ServerPlayer.class,int.class);
            Object voiceServer=null;Method micEntry=null;
            if(j.mode().equals("mic")){
                Object events=Class.forName("de.maxhenkel.voicechat.Voicechat").getField("SERVER").get(null);
                voiceServer=events.getClass().getMethod("getServer").invoke(events);
                micEntry=voiceServer.getClass().getMethod("onMicPacket",UUID.class,mic);
            }
            int count=Math.min(j.remaining(),2);
            for(int f=0;f<count;f++){
                short[] pcm=new short[960];
                for(int i=0;i<pcm.length;i++)pcm[i]=(short)(2400*Math.sin(2*Math.PI*600*(sequence*960+i)/48000.0));
                var packet=mic.getConstructor(byte[].class,boolean.class,long.class).newInstance(j.encoder().encode(pcm),false,sequence++);
                var wrapper=impl.getConstructor(mic,UUID.class).newInstance(packet,j.origin().getUUID());
                if(micEntry!=null)micEntry.invoke(voiceServer,j.origin().getUUID(),packet);
                else if(j.mode().equals("nearby"))transmit.invoke(null,api,wrapper,j.origin());
                else transmit.invoke(null,api,wrapper,j.origin(),j.channel());
            }
            if(j.remaining()==count){j.encoder().close();job=null;System.out.println("ARENA_VOICE_PROOF_DONE");}
            else job=new Job(j.origin(),j.channel(),j.encoder(),j.remaining()-count,j.mode());
        }catch(Throwable ex){
            j.encoder().close();job=null;System.out.println("ARENA_VOICE_PROOF_ERROR "+ex);ex.printStackTrace();
        }
    }
}