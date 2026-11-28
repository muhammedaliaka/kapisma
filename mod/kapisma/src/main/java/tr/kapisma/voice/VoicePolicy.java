package tr.kapisma.voice;

import java.util.Set;
import java.util.UUID;

/** Immutable snapshot, safe to read from the voice networking thread. */
record VoicePolicy(Set<UUID> participants, Set<UUID> spectators, boolean killer, boolean voice, boolean deepVoice) {
	static final VoicePolicy LOBBY = new VoicePolicy(Set.of(), Set.of(), false, true, false);
	VoicePolicy {
		participants = Set.copyOf(participants);
		spectators = Set.copyOf(spectators);
	}
	boolean spectator(UUID id) { return spectators.contains(id); }
	boolean permits(UUID from, UUID to) {
		return spectator(from) == spectator(to);
	}
	boolean micEnabled(UUID id) { return !killer || !participants.contains(id) || voice; }
	boolean deepen(UUID id) { return killer && participants.contains(id) && deepVoice; }
}
