package tr.kapisma.voice;

import java.util.*;

public final class VoicePolicyTest {
	private static void check(boolean result, String label) {
		if (!result) throw new AssertionError(label);
		System.out.println("PASS " + label);
	}
	public static void main(String[] args) {
		UUID live=UUID.randomUUID(), dead=UUID.randomUUID(), dead2=UUID.randomUUID(), lobby=UUID.randomUUID();
		Set<UUID> participants=new HashSet<>(Set.of(live,dead,dead2));
		Set<UUID> spectators=new HashSet<>(Set.of(dead,dead2));
		VoicePolicy p=new VoicePolicy(participants,spectators,false,true,false);
		check(!p.permits(live,dead),"live to spectator blocked");
		check(!p.permits(dead,live),"spectator to live blocked");
		check(p.permits(dead,dead2),"spectator to spectator allowed");
		check(!p.permits(lobby,dead)&&!p.permits(dead,lobby),"lobby isolation both directions");
		check(p.permits(lobby,live),"lobby and live proximity preserved");
		spectators.clear();participants.clear();
		check(p.spectator(dead)&&p.participants().contains(live),"immutable snapshot");
		VoicePolicy muted=new VoicePolicy(Set.of(live,dead),Set.of(dead),true,false,false);
		check(!muted.micEnabled(live)&&!muted.micEnabled(dead)&&muted.micEnabled(lobby),"killer voice disabled only participants");
		VoicePolicy deep=new VoicePolicy(Set.of(live,dead),Set.of(dead),true,true,true);
		check(deep.deepen(live)&&deep.deepen(dead)&&!deep.deepen(lobby),"deep voice uniform participants");
		check(VoicePolicy.LOBBY.permits(dead,live)&&VoicePolicy.LOBBY.micEnabled(dead),"lobby reset");
		System.out.println("VOICE POLICY 9/9 PASS");
	}
}
