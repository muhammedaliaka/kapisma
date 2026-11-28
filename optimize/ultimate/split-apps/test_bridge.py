import socket,sys
from setup_bridge import adb,check,BASE
def request(port,key,op):
    with socket.create_connection(("127.0.0.1",port),timeout=5) as s:
        s.settimeout(15)
        s.sendall(("ULTRA1 "+key+" "+op+"\n").encode("ascii"))
        data=b""
        while True:
            b=s.recv(4096)
            if not b:return data.decode("utf-8")
            data+=b
            if len(data)>131072:raise RuntimeError("response too big")
def main():
    check()
    keys=BASE/"build/bridge-keys"
    daily=(keys/"daily.token").read_text().strip()
    server=(keys/"server.token").read_text().strip()
    port=int(adb("forward","tcp:0","tcp:39761"))
    try:
        cases=[("invalid token","0"*64,"PING","ERR\nAuthentication failed"),("cleaner ping",daily,"PING","OK\nbridge=ready"),("server status",server,"STATUS","OK\nserver_mode=inactive"),("cleaner cannot enter",daily,"ENTER","ERR\nOperation forbidden"),("server cannot daily",server,"DAILY","ERR\nOperation forbidden"),("unknown command",server,"ANYTHING","ERR\nunsupported operation")]
        for label,key,op,expected in cases:
            r=request(port,key,op)
            if not r.startswith(expected):raise RuntimeError(label+" failed: "+r[:200])
            print("PASS",label)
    finally:
        adb("forward","--remove","tcp:"+str(port))
if __name__=="__main__":main()
