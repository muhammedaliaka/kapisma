package tr.kapisma.game;

/** Admission cap for NEW treasure chunk tickets, not an eviction or JVM throttle. */
final class ChunkAdmission {
    private int cap=2, healthyPolls;
    int update(int pendingSpawnChunks, double smoothedMspt) {
        if(pendingSpawnChunks<0)throw new IllegalArgumentException("pending chunks");
        int cpu=!Double.isFinite(smoothedMspt)||smoothedMspt<0||smoothedMspt>=50?1:smoothedMspt>=35?2:4;
        int spawn=pendingSpawnChunks>=3?1:pendingSpawnChunks>0?2:4;
        int target=Math.min(cpu,spawn);
        if(target<cap){cap=target;healthyPolls=0;}
        else if(target>cap){
            if(++healthyPolls>=5){cap++;healthyPolls=0;}
        }else healthyPolls=0;
        return cap;
    }
    static int slots(int cap,int outstanding) {
        if(cap<1||cap>4||outstanding<0)throw new IllegalArgumentException("admission state");
        return Math.max(0,cap-outstanding);
    }
}
