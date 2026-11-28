"""Read pinned TSA content and classify conservatively; no runtime safety claim."""
from pathlib import Path
import json,zipfile,hashlib,collections
BASE=Path(__file__).resolve().parent
manifest=json.loads((BASE/"D37_CONTENT_CANDIDATES.json").read_text())
m=next(x for x in manifest["mods"] if x["slug"]=="tsa-decorations")
jar=BASE/"external-mod-candidates"/m["name"]
assert jar.stat().st_size==m["size"] and hashlib.sha512(jar.read_bytes()).hexdigest()==m["sha512"]
out=BASE/"D39_TSA_INVENTORY.json"
assert not out.exists()
rows=[]; metadata=[]; ids=set()
with zipfile.ZipFile(jar) as z:
    names=set(z.namelist())
    for name in sorted(names):
        if not(name.startswith("data/") and "/filament/" in name and name.endswith(".json")):continue
        d=json.loads(z.read(name))
        if "/filament/decoration/" not in name:
            metadata.append({"path":name,"id":d.get("id") if isinstance(d,dict) else None});continue
        assert isinstance(d,dict) and d["id"] not in ids
        ids.add(d["id"])
        behaviour=d.get("behaviour",{})
        assert isinstance(behaviour,dict)
        blocks=d.get("blocks")
        compact=blocks==[{"origin":[0,0,0],"size":[1,1,1]}]
        model=d["model"]; namespace,modelpath=model.split(":",1)
        asset=f"assets/{namespace}/models/{modelpath}.json"
        exists=asset in names
        unknown=sorted(set(d)-{"id","group","model","properties","blocks","behaviour","vanillaItem"})
        if unknown:kind="review_unknown_fields"
        elif behaviour:kind="interactive"
        elif not blocks:kind="no_explicit_footprint"
        elif compact and exists:kind="compact_noninteractive_candidate"
        else:kind="noncompact_or_unverified"
        rows.append({"id":d["id"],"path":name,"model":model,"model_asset":asset,"model_exists":exists,"blocks":blocks,"behaviour":sorted(behaviour),"category":kind,"unknown_fields":unknown,"properties":d.get("properties",{})})
counts=collections.Counter(r["category"] for r in rows)
behaviours=collections.Counter(k for r in rows for k in r["behaviour"])
shortlist_ids={"tsa:stone_brick_pile","tsa:darkoak_table","tsa:spruce_table","tsa:small_darkoak_table","tsa:small_spruce_table"}
shortlist=[r["id"] for r in rows if r["category"]=="compact_noninteractive_candidate" and r["id"] in shortlist_ids]
assert len(rows)==237 and len(metadata)==2 and len(ids)==237
assert len(shortlist)==len(shortlist_ids),shortlist
data={"status":"static review only, no production approval","sha512":m["sha512"],"decoration_count":len(rows),"other_definitions":metadata,"categories":dict(counts),"behaviour_counts":dict(behaviours),"shortlist":shortlist,"definitions":rows}
out.write_text(json.dumps(data,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
print("DECORATIONS",len(rows),"OTHER",len(metadata),"CATEGORIES",counts,"BEHAVIOURS",behaviours,"SHORTLIST",shortlist,flush=True)
print("SHA256",hashlib.sha256(out.read_bytes()).hexdigest(),flush=True)
