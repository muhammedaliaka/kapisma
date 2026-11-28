import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;

class JnaNativeProbe {
    public static void main(String[] args) {
        long pid=ProcessHandle.current().pid();
        try (NativeLibrary process=NativeLibrary.getProcess()) {
            int actual=process.getFunction("getpid").invokeInt(new Object[0]);
            if(actual!=pid||Native.POINTER_SIZE!=8)throw new AssertionError("native call mismatch");
            System.out.println("JNA_PROBE_OK version="+Native.VERSION+" pointer_size="+Native.POINTER_SIZE+" pid_match=true");
        }
    }
}
