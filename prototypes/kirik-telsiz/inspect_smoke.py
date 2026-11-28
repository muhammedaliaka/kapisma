"""Read-only NBT inspection of original MOD smoke-world metadata."""
import gzip,struct
from pathlib import Path
from verify import LAB
class NBT:
    def __init__(self,data): self.data=data;self.i=0
    def take(self,n):
        b=self.data[self.i:self.i+n];self.i+=n;assert len(b)==n;return b
    def n(self,f): return struct.unpack(">"+f,self.take(struct.calcsize(">"+f)))[0]
    def text(self): return self.take(self.n("H")).decode("utf-8",errors="replace")
    def value(self,t):
        if t in (1,2,3,4,5,6): return self.n({1:"b",2:"h",3:"i",4:"q",5:"f",6:"d"}[t])
        if t==7:return self.take(self.n("i"))
        if t==8:return self.text()
        if t==9:
            k=self.n("B");return [self.value(k) for _ in range(self.n("i"))]
        if t==10:
            d={}
            while (k:=self.n("B")):
                key=self.text();d[key]=self.value(k)
            return d
        if t in (11,12):return self.take(self.n("i")*(4 if t==11 else 8))
        raise ValueError(t)
def read(path):
    n=NBT(gzip.decompress(path.read_bytes()));assert n.n("B")==10;n.text();return n.value(10)
if __name__=="__main__":
    d=read(LAB/"smoke-world/level.dat")["Data"]
    for k in ("LevelName","WorldGenSettings","DataPacks","Difficulty","GameType","allowCommands","hardcore","GameRules"):
        print(k,repr(d.get(k))[:6000])
