package tr.kapisma.game;

import java.util.Arrays;

/** Pure quota policy. Count first, then tier deviation, then diversity. */
final class BingoDifficulty {
	private BingoDifficulty() {}
	static int[] quotas(int difficulty, int count) {
		int[] weights = difficulty == 1 ? new int[]{12,9,4} : difficulty == 3 ? new int[]{5,10,10} : new int[]{8,10,7};
		int[] out = new int[3];
		for (int i=0;i<3;i++) out[i] = weights[i]*count/25;
		int missing=count-Arrays.stream(out).sum();
		boolean[] used=new boolean[3];
		while(missing-- > 0) {
			int best=-1;
			for(int i=0;i<3;i++) if(!used[i] && (best<0 || weights[i]*count%25>weights[best]*count%25)) best=i;
			out[best]++; used[best]=true;
		}
		return out;
	}
	static int error(int[] want,int[] actual) {
		int sum=0;
		for(int i=0;i<3;i++) sum+=Math.abs(want[i]-actual[i]);
		return sum;
	}
	static boolean better(int need,int[] want,int[] candidate,int[] current,int candidatePenalty,int currentPenalty) {
		int a=Arrays.stream(candidate).sum(), b=Arrays.stream(current).sum();
		if(a>need) return false;
		if(a!=b) return a>b;
		int ae=error(want,candidate),be=error(want,current);
		if(ae!=be) return ae<be;
		return candidatePenalty<currentPenalty;
	}
}
