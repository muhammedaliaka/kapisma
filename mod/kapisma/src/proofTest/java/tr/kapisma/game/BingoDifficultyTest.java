package tr.kapisma.game;
import java.util.Arrays;
public final class BingoDifficultyTest {
	private static void check(boolean b,String label){if(!b)throw new AssertionError(label);System.out.println("PASS "+label);}
	public static void main(String[] args){
		boolean valid=true;
		for(int d=1;d<=3;d++)for(int n=0;n<=25;n++){int[] q=BingoDifficulty.quotas(d,n);valid &= Arrays.stream(q).sum()==n && Arrays.stream(q).allMatch(i->i>=0);}
		check(valid,"all 78 quota combinations sum exactly");
		check(Arrays.equals(BingoDifficulty.quotas(1,25),new int[]{12,9,4}),"easy full quotas");
		check(Arrays.equals(BingoDifficulty.quotas(2,25),new int[]{8,10,7}),"medium full quotas");
		check(Arrays.equals(BingoDifficulty.quotas(3,25),new int[]{5,10,10}),"hard full quotas");
		int[] q={5,10,10};
		check(BingoDifficulty.better(25,q,new int[]{15,5,5},new int[]{5,10,9},8,0),"complete beats incomplete");
		check(BingoDifficulty.better(25,q,q,new int[]{15,5,5},1,0),"correct difficulty beats off-target");
		check(BingoDifficulty.better(25,q,q,q,0,1),"diversity tie break");
		check(!BingoDifficulty.better(25,q,new int[]{5,10,11},q,0,0),"oversize rejected");
		check(!BingoDifficulty.better(25,q,new int[]{5,10,9},q,0,3),"never replace full by incomplete");
		System.out.println("BINGO DIFFICULTY 9/9 PASS");
	}
}
