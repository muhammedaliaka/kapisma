package tr.kapisma.game;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Suppress identical snapshots only within the same connection and refresh window. */
final class HudSendGate<T> {
    private record Sent<T>(Object connection,T value,long tick) {}
    private final Map<UUID,Sent<T>> sent=new HashMap<>();
    private final int heartbeat;
    HudSendGate(int heartbeat){if(heartbeat<1)throw new IllegalArgumentException("heartbeat");this.heartbeat=heartbeat;}
    boolean shouldSend(UUID id,Object connection,T snapshot,long tick){
        Objects.requireNonNull(connection);Objects.requireNonNull(snapshot);
        var prior=sent.get(id);
        return prior==null||prior.connection()!=connection||!prior.value().equals(snapshot)
            ||tick<prior.tick()||tick-prior.tick()>=heartbeat;
    }
    void markSent(UUID id,Object connection,T snapshot,long tick){
        sent.put(id,new Sent<>(Objects.requireNonNull(connection),Objects.requireNonNull(snapshot),tick));
    }
    void forget(UUID id){sent.remove(id);}
    void clear(){sent.clear();}
}
