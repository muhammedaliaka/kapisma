import oshi.SystemInfo;
class OshiProbe {
    public static void main(String[] args) {
        var hw=new SystemInfo().getHardware();
        int cpus=hw.getProcessor().getLogicalProcessorCount();
        long mem=hw.getMemory().getTotal();
        if(cpus<=0||mem<=0)throw new AssertionError("invalid telemetry");
        System.out.println("OSHI_PROBE_OK logical_cpus="+cpus+" total_bytes="+mem);
    }
}
