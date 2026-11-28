"""Read-only Anvil comparison: logical block/biome data, independent of palette order."""
from pathlib import Path
import hashlib,struct,zlib,gzip,json,sys
from collections import Counter
BASE=Path(__file__).resolve().parent
RUN=BASE/"runtime-proof"
class NBT:
    def __init__(self,data):self.data=data;self.i=0
    def take(self,n):
        assert n>=0 and self.i+n<=len(self.data)
        b=self.data[self.i:self.i+n];self.i+=n;return b
    def n(self,f):return struct.unpack(">"+f,self.take(struct.calcsize(">"+f)))[0]
    def text(self):return self.take(self.n("H")).decode("utf-8",errors="replace")
    def value(self,t):
        if t in (1,2,3,4,5,6):return self.n({1:"b",2:"h",3:"i",4:"q",5:"f",6:"d"}[t])
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
def chunk(world,x,z):
    path=world/"region"/f"r.{x//32}.{z//32}.mca"
    with path.open("rb") as f:
        f.seek(4*((x%32)+32*(z%32)));header=f.read(4)
        offset=int.from_bytes(header[:3],"big")*4096
        assert offset>=8192 and header[3]>0,(world,x,z)
        f.seek(offset);length=int.from_bytes(f.read(4),"big");method=f.read(1)[0]
        assert 1<length<=header[3]*4096-4 and method in (1,2,3),(length,method)
        packed=f.read(length-1)
    data={1:gzip.decompress,2:zlib.decompress,3:lambda b:b}[method](packed)
    n=NBT(data);assert n.n("B")==10;n.text();root=n.value(10)
    assert root["xPos"]==x and root["zPos"]==z and root["Status"]=="minecraft:full",(world,x,z,root.get("Status"))
    return root
def unpack(container,count,min_bits):
    palette=container["palette"]
    assert palette
    labels=[json.dumps(p,sort_keys=True,separators=(",",":")) for p in palette]
    if len(palette)==1:return [labels[0]]*count
    bits=max(min_bits,(len(palette)-1).bit_length())
    per=64//bits;mask=(1<<bits)-1
    words=struct.unpack(">"+str(len(container["data"])//8)+"Q",container["data"])
    assert len(words)==(count+per-1)//per,(len(words),count,bits)
    return [labels[(words[i//per]>>(i%per*bits))&mask] for i in range(count)]
def terrain(c):
    sections={}
    for s in c["sections"]:
        if "block_states" in s and "biomes" in s:
            sections[s["Y"]]=(unpack(s["block_states"],4096,4),unpack(s["biomes"],64,1))
    return sections
def hash_terrain(s):
    h=hashlib.sha256()
    for y,(blocks,biomes) in sorted(s.items()):
        h.update(json.dumps([y,blocks,biomes],separators=(",",":")).encode())
    return h.hexdigest()
names={"frozen-base-repeat":("parity-frozen-base","parity-frozen-base-repeat"),"frozen-noisium":("parity-frozen-base","parity-frozen-noisium"),"frozen-memory-vs-worldgen":("parity-frozen-memory","parity-frozen-worldgen"),"original":("parity-base","parity-worldgen"),"frozen-memory":("parity-frozen-base","parity-frozen-memory"),"frozen-worldgen":("parity-frozen-base","parity-frozen-worldgen")}
mode=sys.argv[1] if len(sys.argv)>1 else "original"
a_name,b_name=names[mode];base=RUN/("optimization-"+a_name);opt=RUN/("optimization-"+b_name)
rows=[];blocks=biomes=0;diff_blocks=diff_biomes=0;diff_heightmaps=0;pairs=Counter();biome_pairs=Counter()
for x in range(64,67):
    for z in range(64,67):
        a,b=chunk(base,x,z),chunk(opt,x,z)
        sa,sb=terrain(a),terrain(b)
        assert set(sa)==set(sb) and len(sa)==24,(x,z,list(sa),list(sb))
        changed=changed_biomes=0
        for y in sa:
            for u,v in zip(sa[y][0],sb[y][0]):
                if u!=v:pairs[(u,v)]+=1;changed+=1
            for u,v in zip(sa[y][1],sb[y][1]):
                if u!=v:biome_pairs[(u,v)]+=1;changed_biomes+=1
            blocks+=4096;biomes+=64
        heights=a["Heightmaps"]==b["Heightmaps"]
        diff_heightmaps+=not heights;diff_blocks+=changed;diff_biomes+=changed_biomes
        rows.append({"chunk":[x,z],"sections":len(sa),"baseline_sha256":hash_terrain(sa),"optimized_sha256":hash_terrain(sb),"different_blocks":changed,"different_biomes":changed_biomes,"heightmaps_equal":heights})
        print("OBS",x,z,"different blocks",changed,"different biomes",changed_biomes,"heightmaps_equal",heights,flush=True)
result={"mode":mode,"chunks":len(rows),"block_states":blocks,"biome_cells":biomes,"different_blocks":diff_blocks,"different_biomes":diff_biomes,"different_heightmaps":diff_heightmaps,"pairs":[{"base":a,"optimized":b,"count":n} for (a,b),n in pairs.most_common()],"biome_pairs":[{"base":a,"optimized":b,"count":n} for (a,b),n in biome_pairs.most_common()],"rows":rows}
with (BASE/("terrain_parity.json" if mode=="original" else "terrain_parity_"+mode+".json")).open("x",encoding="utf-8") as f:json.dump(result,f,indent=2)
print("RESULT",json.dumps({k:v for k,v in result.items() if k!="rows"},ensure_ascii=True),flush=True)
if diff_blocks or diff_biomes or diff_heightmaps:raise SystemExit(2)
print("PASS terrain parity")