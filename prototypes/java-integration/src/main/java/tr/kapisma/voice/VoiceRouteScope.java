package tr.kapisma.voice;
import java.util.*;

/** Trusted server-side forwarding provenance, scoped to a single synchronous API send. */
public final class VoiceRouteScope {
    public enum Kind { ANALOG, SPECTATOR_RELAY }
    public record Key(UUID packetSender,UUID channel,long sequence){}
    public record Route(Kind kind,UUID from,UUID carrier,UUID to,Key key,Object epoch){
        public Route{Objects.requireNonNull(kind);Objects.requireNonNull(from);Objects.requireNonNull(carrier);Objects.requireNonNull(to);Objects.requireNonNull(key);Objects.requireNonNull(epoch);}
    }
    private static final ThreadLocal<Route> CURRENT=new ThreadLocal<>();
    private record Microphone(UUID source,Object epoch){}
    private static final ThreadLocal<Microphone> MIC=new ThreadLocal<>();
    private static volatile Object epoch=new Object();
    public static Object epoch(){return epoch;}
    public static void publishEpoch(Object next){epoch=Objects.requireNonNull(next);}
    public static boolean isCurrent(Object captured){return epoch==captured;}
    private VoiceRouteScope(){}
    private static void withMic(Microphone mic,Runnable task){
        Microphone old=MIC.get();
        if(mic==null)MIC.remove();else MIC.set(mic);
        try{task.run();}finally{if(old==null)MIC.remove();else MIC.set(old);}
    }
    public static Runnable guardMicrophone(UUID source,Runnable task){
        Microphone mic=new Microphone(Objects.requireNonNull(source),epoch);
        return ()->{if(isCurrent(mic.epoch()))withMic(mic,task);};
    }
    public static Runnable guard(Runnable task){
        Object captured=epoch;Microphone mic=MIC.get();
        return ()->{if(isCurrent(captured)&&(mic==null||mic.epoch()==captured))withMic(mic,task);};
    }
    public static void sendAnalog(UUID carrier,UUID to,Key key,Runnable action){
        Microphone mic=MIC.get();
        if(mic!=null&&!isCurrent(mic.epoch()))return;
        UUID source=mic==null?carrier:mic.source();
        if(!source.equals(key.packetSender()))return;
        send(Kind.ANALOG,source,carrier,to,key,action);
    }
    public static void send(Kind kind,UUID from,UUID to,Key key,Runnable action){
        send(kind,from,from,to,key,action);
    }
    private static void send(Kind kind,UUID from,UUID carrier,UUID to,Key key,Runnable action){
        Objects.requireNonNull(action);
        Route old=CURRENT.get();
        CURRENT.set(new Route(kind,from,carrier,to,key,epoch));
        try{action.run();}finally{if(old==null)CURRENT.remove();else CURRENT.set(old);}
    }
    public static Route current(UUID receiver,Key key,Kind expected){
        Route r=CURRENT.get();
        if(r==null||r.kind()!=expected||!r.to().equals(receiver)||!r.key().equals(key))return null;
        return r;
    }
    /** In the radio puzzle, ordinary voice never connects a live participant; text stays available. */
    public static boolean permitsOrdinaryRadio(Set<UUID> participants,Set<UUID> spectators,UUID from,UUID to){
        if(from==null||to==null)return false;
        boolean a=participants.contains(from),b=participants.contains(to);
        return !a&&!b || a&&b&&spectators.contains(from)&&spectators.contains(to);
    }
    public static boolean permitsRadioPlugin(Set<UUID> participants,Set<UUID> spectators,UUID sender,UUID receiver,Route route){
        if(receiver==null)return false;
        if(route==null){
            // No ordinary plugin packets into the puzzle, even with a known sender.
            return !participants.contains(receiver)&&sender!=null&&!participants.contains(sender);
        }
        return permitsPlugin(participants,spectators,sender,receiver,route);
    }
    /** Preserve existing normal proximity policy. Plugin-origin audio must prove its route during matches. */
    public static boolean permitsPlugin(Set<UUID> participants,Set<UUID> spectators,UUID sender,UUID receiver,Route route){
        if(receiver==null)return false;
        if(route!=null){
            if(!receiver.equals(route.to())||(sender!=null&&!sender.equals(route.from())))return false;
            if(route.kind()==Kind.SPECTATOR_RELAY)
                return participants.contains(route.from())&&participants.contains(receiver)&&spectators.contains(route.from())&&spectators.contains(receiver);
            return participants.contains(route.from())==participants.contains(receiver)&&
                participants.contains(route.carrier())==participants.contains(route.from())&&
                !spectators.contains(route.carrier())&&!spectators.contains(route.from())&&!spectators.contains(receiver);
        }
        if(participants.isEmpty())return true;
        // Unknown origin during a match: never infer identity from plugin-created packet sender UUID.
        return sender!=null&&participants.contains(sender)==participants.contains(receiver)&&
            spectators.contains(sender)==spectators.contains(receiver);
    }
}