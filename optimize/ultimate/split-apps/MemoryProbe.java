package tr.kapisma.ultra.probe;
import java.util.Random;
import java.util.Locale;
public final class MemoryProbe {
 static volatile long sink;
 static final int N=4*1024*1024; // 32 MiB per long array
 static void copy(long[] a,long[] b,int rounds){
   long t=System.nanoTime();
   for(int k=0;k<rounds;k++){System.arraycopy(a,0,b,0,a.length);sink^=b[(k*8191)&(N-1)];}
   double sec=(System.nanoTime()-t)/1e9;
   System.out.printf(Locale.US,"copy_payload_GiB_s=%.3f rounds=%d seconds=%.6f%n",(double)N*8*rounds/1073741824.0/sec,rounds,sec);
 }
 static void read(long[] a,int rounds){
   long t=System.nanoTime(),sum=0;
   for(int k=0;k<rounds;k++){for(int i=0;i<N;i+=4){sum+=a[i]+a[i+1]+a[i+2]+a[i+3];}}
   double sec=(System.nanoTime()-t)/1e9;sink=sum;
   System.out.printf(Locale.US,"sequential_read_GiB_s=%.3f rounds=%d seconds=%.6f%n",(double)N*8*rounds/1073741824.0/sec,rounds,sec);
 }
 static void chase(int[] next,int n,int steps){
   int x=0;for(int i=0;i<100000;i++)x=next[x];
   long t=System.nanoTime();for(int i=0;i<steps;i++)x=next[x];long elapsed=System.nanoTime()-t;sink=x;
   System.out.printf(Locale.US,"dependent_latency_ns=%.3f working_set_MiB=%.2f steps=%d%n",(double)elapsed/steps,(double)n*4/1048576.0,steps);
 }
 public static void main(String[] args){
   long[] a=new long[N],b=new long[N]; Random r=new Random(31);
   for(int i=0;i<N;i++){a[i]=r.nextLong();b[i]=~a[i];}
   System.arraycopy(a,0,b,0,N);read(a,1);
   for(int k=0;k<3;k++){copy(a,b,6);read(a,4);}
   int n=4*1024*1024;int[] order=new int[n],next=new int[n];
   for(int i=0;i<n;i++)order[i]=i;
   for(int i=n-1;i>0;i--){int j=r.nextInt(i+1),v=order[i];order[i]=order[j];order[j]=v;}
   for(int i=0;i<n;i++)next[order[i]]=order[(i+1)%n];
   for(int k=0;k<3;k++)chase(next,n,2000000);
   System.out.println("probe_complete sink="+sink);
 }
}
