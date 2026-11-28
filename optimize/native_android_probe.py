"""Bounded Windows native client test over an owned USB ADB loopback forward.
No account credentials, public server, fabricated ACK or other process termination.
Requires the matching on-device isolated native harness already launched.
"""
import argparse,ctypes,hashlib,importlib.util,json,os,re,subprocess,time
from pathlib import Path

HERE=Path(__file__).resolve().parent
ADB=Path(os.environ.get("LOCALAPPDATA",""))/"Android/Sdk/platform-tools/adb.exe"
# KOD-owned adapter of the reviewed MOD wrapper, with matching Origins registry JAR.
WRAPPER=HERE/"native_client_adapter.py"
WRAPPER_HASH="00884e950e07edabb34d88c1cb10df0aedb6962d3709ff94d097cb667689f706"
NAMES=tuple("KodNativeQA"+str(i) for i in range(1,5))
PORT=25587
SERIAL="SCTB19202412160567"
class MemoryStatus(ctypes.Structure):
    _fields_=[("length",ctypes.c_ulong),("load",ctypes.c_ulong)]+[(k,ctypes.c_ulonglong) for k in ("total","available","page_total","page_available","virtual_total","virtual_available","extended")]
def available_mib():
    s=MemoryStatus();s.length=ctypes.sizeof(s)
    if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(s)):raise RuntimeError("memory status unavailable")
    return s.available//1048576
def adb(*args,check=True):
    p=subprocess.run([str(ADB),"-s",SERIAL,*args],capture_output=True,text=True,encoding="utf-8",errors="replace",timeout=12)
    if check and p.returncode:raise RuntimeError("ADB command failed: "+str(args[0]))
    return p
def server_login(log,name):
    return bool(re.search(r"(?im)\b"+re.escape(name)+r"\[(?:/[^\]]+|IP hidden)\] logged in with entity id \d+",log)) and name.lower()+" joined the game" in log.lower()
def socket_owned(pid,netstat_text):
    for line in netstat_text.splitlines():
        cols=line.split()
        if len(cols)>=5 and cols[0]=="TCP" and cols[1].startswith("127.0.0.1:") and cols[2]=="127.0.0.1:"+str(PORT) and cols[3]=="ESTABLISHED" and cols[-1]==str(pid):return True
    return False
def native_join_verified(log,name,pid,client_log,netstat_text):
    return server_login(log,name) and "[CHAT]" in client_log and "Registry remapping failed!" not in client_log and socket_owned(pid,netstat_text)
def main():
    p=argparse.ArgumentParser();p.add_argument("run_id")
    p.add_argument("--candidate-sha",choices=("0e2326996ed44829a736cd3aa7517d3bcf5d00ea473bdee999949261ec1f5843","9708483619c3dab78ff3a536d170a8c5c28c42564ba99045ef634dc4ff880463"),default="0e2326996ed44829a736cd3aa7517d3bcf5d00ea473bdee999949261ec1f5843")
    a=p.parse_args()
    if not re.fullmatch(r"kapisma-ultra-test-20261126-r[0-9]+",a.run_id):raise SystemExit("invalid run id")
    stage="/data/local/tmp/"+a.run_id
    root=HERE/("native-"+a.run_id);root.mkdir(exist_ok=False)
    clients=[];forward=False;signal_allowed=False;complete=False
    def emit(kind,**kw):
        row=dict(event=kind,epoch=time.time(),**kw)
        with (root/"host.jsonl").open("a",encoding="utf-8") as f:f.write(json.dumps(row)+"\n")
        print(json.dumps(row),flush=True)
    def readlog():
        return adb("shell","cat "+stage+"/stress.log",check=False).stdout
    def alive():
        if available_mib()<3072:raise RuntimeError("host available RAM below 3 GiB")
        if any(c.p.poll() is not None for c in clients):raise RuntimeError("native client exited")
    def wait_for(predicate,seconds,phase):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            alive()
            if phase.startswith("join "):
                for c in clients:
                    if any("Registry remapping failed!" in l or "Client disconnected with reason:" in l for l in c.lines()):
                        raise RuntimeError("client connection/registry rejected: "+c.name)
            log=readlog()
            if "HARNESS_ABORT" in log or re.search(r"(?m)^exit=[1-9]",log):raise RuntimeError("server test aborted")
            if predicate(log):return log
            time.sleep(2)
        raise RuntimeError(phase+" timeout")
    def signal(name):
        if name not in ("joined","walk","abort"):raise ValueError("invalid signal")
        adb("shell","touch "+stage+"/native."+name)
    try:
        if hashlib.sha256(WRAPPER.read_bytes()).hexdigest()!=WRAPPER_HASH:raise RuntimeError("KOD client adapter changed; re-audit required")
        # Manifest and live ready marker are required before setting a signal or forwarding.
        manifest=dict(l.split("=",1) for l in adb("shell","cat "+stage+"/experiment.txt").stdout.splitlines() if "=" in l)
        if manifest.get("native_mode")!="1" or manifest.get("candidate_sha256")!=a.candidate_sha:raise RuntimeError("wrong Android test manifest")
        emit("candidate_verified",sha256=a.candidate_sha,xray_empty_skip=manifest.get("xray_empty_skip","0"))
        signal_allowed=True
        if any(adb("shell","test -f "+stage+"/native."+n,check=False).returncode==0 for n in ("joined","walk","abort")):raise RuntimeError("stale signals")
        wait_for(lambda l:"HARNESS_NATIVE_READY players=4" in l,240,"server ready")
        if available_mib()<10240:raise RuntimeError("need at least 10 GiB free before four-client launch")
        existing=adb("forward","--list").stdout
        if any("tcp:"+str(PORT) in l.split() for l in existing.splitlines()):raise RuntimeError("port forward already occupied")
        adb("forward","--no-rebind","tcp:"+str(PORT),"tcp:25577");forward=True
        if not any(l.split()==[SERIAL,"tcp:"+str(PORT),"tcp:25577"] for l in adb("forward","--list").stdout.splitlines()):raise RuntimeError("forward verification failed")
        # Verify the actual listener is restricted to loopback, not a public port.
        net=subprocess.run(["netstat","-ano","-p","tcp"],capture_output=True,text=True,timeout=10,check=True).stdout
        listeners=[l.split() for l in net.splitlines() if l.split() and l.split()[0]=="TCP" and len(l.split())>=4 and l.split()[1].endswith(":"+str(PORT)) and l.split()[3] in ("LISTENING","DINLENIYOR")]
        if not listeners or any(l[1] not in ("127.0.0.1:"+str(PORT),"[::1]:"+str(PORT)) for l in listeners):raise RuntimeError("forward listener not verified loopback")
        emit("forward_verified",port=PORT,available_mib=available_mib())
        spec=importlib.util.spec_from_file_location("kod_native_wrapper",WRAPPER);mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
        emit("client_adapter_verified",sha256=WRAPPER_HASH,origins_sha256="c7e06f8fd49a279d566c3e778ff0c71bbb371f1c4358ab9b2190d1e3b6c36d33")
        for i,name in enumerate(NAMES):
            alive()
            if available_mib()<5120:raise RuntimeError("insufficient host headroom for next client")
            c=mod.NativeClient(name=name,port=PORT,game=root/("profile-"+str(i+1)),log=root/("client-"+str(i+1)+".log"),walk=True,yaw=-90 if manifest.get("native_route")=="1" else (180,-90,0,90)[i],ticks=2400,heap_mib=1500)
            clients.append(c);emit("client_started",name=name,pid=c.p.pid,available_mib=available_mib())
            def joined(log):
                if not server_login(log,name):return False
                net=subprocess.run(["netstat","-ano","-p","tcp"],capture_output=True,text=True,timeout=10,check=True).stdout
                return native_join_verified(log,name,c.p.pid,"\n".join(c.lines()),net)
            wait_for(joined,90,"join "+name)
            emit("client_joined",name=name,pid=c.p.pid,available_mib=available_mib(),socket_owned=True,client_chat=True)
        signal("joined")
        wait_for(lambda l:"[Server] KAPISMA_NATIVE_WALK_READY" in l,600 if manifest.get("native_route")=="1" else 300,"UHC start")
        for c in clients:c.start_walk()
        signal("walk");emit("walk_started",clients=4)
        end=time.monotonic()+(360 if manifest.get("native_route")=="1" else 160)
        while time.monotonic()<end:
            log=readlog()
            if "HARNESS_NATIVE_COMPLETE" in log and re.search(r"(?m)^exit=0\s*$",log):
                complete=True;break
            alive()
            if "HARNESS_ABORT" in log or re.search(r"(?m)^exit=[1-9]",log):raise RuntimeError("server test aborted")
            emit("walk_monitor",available_mib=available_mib(),samples=[len(c.positions()) for c in clients])
            time.sleep(5)
        if not complete:raise RuntimeError("walk completion timeout")
        emit("server_complete",position_samples=[len(c.positions()) for c in clients])
    except Exception as e:
        emit("failure",reason=str(e))
        if signal_allowed:
            try:signal("abort")
            except Exception:emit("abort_signal_failed")
    finally:
        for c in clients:
            try:c.close();emit("client_closed",name=c.name,pid=c.p.pid,exit=c.p.poll())
            except Exception as e:emit("client_close_failed",name=c.name,reason=str(e));complete=False
        if forward:
            try:
                adb("forward","--remove","tcp:"+str(PORT))
                if any("tcp:"+str(PORT) in l.split() for l in adb("forward","--list").stdout.splitlines()):raise RuntimeError("forward still present")
                emit("forward_removed",port=PORT)
            except Exception as e:emit("forward_remove_failed",reason=str(e));complete=False
        emit("host_complete",diagnostic_complete=complete)
    return 0 if complete else 1
if __name__=="__main__":raise SystemExit(main())
