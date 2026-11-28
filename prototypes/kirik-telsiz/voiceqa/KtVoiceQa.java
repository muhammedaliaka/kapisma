package kt.voiceqa;
import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;

public final class KtVoiceQa implements VoicechatPlugin {
    private volatile boolean guard;
    private volatile String stage="idle";
    private volatile boolean running;
    private long nextSequence;
    private static final UUID BOT=UUID.nameUUIDFromBytes("OfflinePlayer:ktqa1".getBytes(StandardCharsets.UTF_8));
    private static final UUID A=UUID.nameUUIDFromBytes("OfflinePlayer:KtNativeQA1".getBytes(StandardCharsets.UTF_8));
    private static final UUID B=UUID.nameUUIDFromBytes("OfflinePlayer:KtNativeQA2".getBytes(StandardCharsets.UTF_8));
    public String getPluginId(){return "kt_voiceqa";}
    public void registerEvents(EventRegistration r){
        if ("server".equals(System.getProperty("kt.voiceqa.mode"))) {
            r.registerEvent(VoicechatServerStartedEvent.class,e->start(e.getVoicechat()));
            r.registerEvent(VoicechatServerStoppedEvent.class,e->running=false);
            r.registerEvent(LocationalSoundPacketEvent.class,this::inspect,100);
            r.registerEvent(MicrophonePacketEvent.class,e->{
                if(e.getSenderConnection()!=null && BOT.equals(e.getSenderConnection().getPlayer().getUuid())) {
                    System.out.println("KTQA_MIC "+stage);
                    if(stage.startsWith("direct") && e.getPacket().getOpusEncodedData().length>0) direct(e);
                }
            });
        } else if ("client".equals(System.getProperty("kt.voiceqa.mode"))) {
            r.registerEvent(ClientVoicechatConnectionEvent.class,e->System.out.println("KTQA_VOICE_CONNECTED "+e.isConnected()));
            r.registerEvent(ClientReceiveSoundEvent.LocationalSound.class,this::received);
        }
    }
    private void inspect(LocationalSoundPacketEvent e){
        if(!"plugin".equals(e.getSource()) || e.getReceiverConnection()==null)return;
        UUID id=e.getReceiverConnection().getPlayer().getUuid();
        if(!A.equals(id)&&!B.equals(id))return;
        boolean nullSender=e.getSenderConnection()==null;
        boolean spectator=false;
        try {
            Object player=e.getReceiverConnection().getPlayer().getPlayer();
            spectator=(boolean)player.getClass().getMethod("method_7325").invoke(player);
        } catch(Exception ex){System.out.println("KTQA_ERROR spectator state "+ex);return;}
        boolean cancelled=guard && nullSender && spectator && e.cancel();
        System.out.println("KTQA_PACKET "+stage+" receiver="+(A.equals(id)?"A":"B")+" nullSender="+nullSender+" spectator="+spectator+" cancelled="+cancelled);
    }
    private void direct(MicrophonePacketEvent e){
        try {
            Object sender=e.getSenderConnection().getPlayer().getPlayer();
            Object server=sender.getClass().getMethod("method_5682").invoke(sender);
            VoicechatServerApi api=e.getVoicechat();
            MicrophonePacket packet=e.getPacket();
            ((Executor)server).execute(()->{
                try {
                    Class<?> radio=Class.forName("dev.mrturtle.analog.util.RadioUtil");
                    Method receive=null, transmit=null;
                    for(Method m:radio.getMethods()){
                        if(m.getName().equals("isReceivingChannel"))receive=m;
                        if(m.getName().equals("transmitOnChannel"))transmit=m;
                    }
                    Object receiver=api.getConnectionOf(A).getPlayer().getPlayer();
                    boolean tuned=(boolean)receive.invoke(null,receiver,7);
                    System.out.println("KTQA_TUNED "+stage+" "+tuned);
                    transmit.invoke(null,api,packet,sender,7);
                } catch(Exception ex){System.out.println("KTQA_ERROR direct "+ex);ex.printStackTrace();}
            });
        }catch(Exception ex){System.out.println("KTQA_ERROR schedule "+ex);}
    }
    private void received(ClientReceiveSoundEvent e){
        short[] pcm=e.getRawAudio();
        long energy=0;
        for(short v:pcm)energy+=Math.abs((int)v);
        if(pcm.length>0 && energy/pcm.length>300)System.out.println("KTQA_PCM count="+pcm.length+" mean="+energy/pcm.length);
    }
    private void start(VoicechatServerApi api){
        String request=System.getProperty("kt.voiceqa.request");
        if(request==null)return;
        running=true;
        Thread thread=new Thread(()->{
            String last="";
            while(running){
                try {
                    String cmd=Files.readString(Path.of(request)).trim();
                    if(!cmd.equals(last)&& (cmd.equals("red")||cmd.equals("green")||cmd.equals("wrongchannel")||cmd.equals("directred")||cmd.equals("directgreen")||cmd.equals("directwrong")||cmd.equals("postred"))){
                        last=cmd;stage=cmd;guard=cmd.endsWith("green");
                        send(api);
                        System.out.println("KTQA_FINISHED "+stage);
                    }
                    Thread.sleep(100);
                }catch(Exception ex){System.out.println("KTQA_ERROR "+ex);return;}
            }
        },"kt-voiceqa");thread.setDaemon(true);thread.start();
    }
    private void send(VoicechatServerApi api)throws Exception{
        VoicechatConnection c=api.getConnectionOf(BOT);
        if(c==null)throw new IllegalStateException("QA bot unavailable");
        AudioSender sender=api.createAudioSender(c);
        if(!api.registerAudioSender(sender))throw new IllegalStateException("synthetic sender registration failed");
        sender.sequenceNumber(nextSequence);
        OpusEncoder enc=api.createEncoder();
        try {
            for(int frame=0;frame<40;frame++){
                short[] samples=new short[960];
                for(int i=0;i<samples.length;i++)samples[i]=(short)(2400*Math.sin(2*Math.PI*600*(frame*960+i)/48000.0));
                if(!sender.send(enc.encode(samples)))throw new IllegalStateException("send rejected");
                nextSequence++;
                Thread.sleep(20);
            }
            // Analog ignores empty microphone frames, so preserve the sequence across phases.
            System.out.println("KTQA_NEXT_SEQUENCE "+nextSequence);
        }finally{enc.close();api.unregisterAudioSender(sender);}
    }
}