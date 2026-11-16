#!/usr/bin/env python3
"""
Kapisma bilgi kutuphanesi: Minecraft 1.21.1 sunucu jar'indaki resmi veriden (loot tablolari, biyom
ozellikleri, yaratik doğusları, yapilar) "bu esya nerede bulunur" bilgisini cikarir.

Cikti: mod/kapisma/src/main/resources/data/kapisma/knowledge.json
  biomes:      biyom -> {blocks: dogal olusan bloklar, mobs: doğabilen yaratiklar}
  blockDrops:  blok -> dusen esyalar
  mobDrops:    yaratik -> dusen esyalar
  structureLoot: yapi -> sandik esyalari (yapi kimligi onekleri ile eslesir)
  fishing:     balik tutma esyalari
  general:     her overworld biyomunda bulunan sabit bloklar (tas, toprak, çakıl...)

Kullanim: python mcknow.py   (once knowledge/raw altina jar'dan data/ cikarilmis olmali, bkz. --extract)
"""
import argparse, json, os, re, sys, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "knowledge", "raw", "data", "minecraft")
OUT = os.path.join(ROOT, "mod", "kapisma", "src", "main", "resources", "data", "kapisma", "knowledge.json")
JAR = os.path.join(ROOT, "server", "versions", "1.21.1", "server-1.21.1.jar")


def extract():
    z = zipfile.ZipFile(JAR)
    base = os.path.join(ROOT, "knowledge", "raw")
    n = 0
    for i in z.namelist():
        if i.startswith("data/") and not i.endswith("/"):
            z.extract(i, base)
            n += 1
    print("cikarildi:", n)


def load(*parts):
    with open(os.path.join(RAW, *parts), encoding="utf-8") as f:
        return json.load(f)


def strip(x):
    return x[len("minecraft:"):] if x.startswith("minecraft:") else x


def listdir(*parts):
    p = os.path.join(RAW, *parts)
    return sorted(os.listdir(p)) if os.path.isdir(p) else []


def walk_files(*parts):
    base = os.path.join(RAW, *parts)
    for d, _, fs in os.walk(base):
        for f in sorted(fs):
            if f.endswith(".json"):
                yield os.path.relpath(os.path.join(d, f), base).replace("\\", "/")[:-5], os.path.join(d, f)


# ------------------------------------------------------------ biyom etiketleri
def resolve_tag(kind, name, seen=None):
    """tags/worldgen/biome/<name>.json -> biyom kimlikleri kumesi"""
    seen = seen or set()
    if name in seen:
        return set()
    seen.add(name)
    p = os.path.join(RAW, "tags", kind, *name.split("/")) + ".json"
    out = set()
    if not os.path.exists(p):
        return out
    for v in json.load(open(p, encoding="utf-8"))["values"]:
        if isinstance(v, dict):
            v = v["id"]
        if v.startswith("#"):
            out |= resolve_tag(kind, strip(v[1:]), seen)
        else:
            out.add(strip(v))
    return out


# ------------------------------------------------------------ ozellik -> bloklar
FEATURE_CACHE = {}


def feature_blocks(fid, depth=0):
    fid = strip(fid)
    if fid in FEATURE_CACHE:
        return FEATURE_CACHE[fid]
    FEATURE_CACHE[fid] = set()
    blocks = set()
    for kind in ("placed_feature", "configured_feature"):
        p = os.path.join(RAW, "worldgen", kind, fid + ".json")
        if os.path.exists(p):
            collect(json.load(open(p, encoding="utf-8")), blocks)
    FEATURE_CACHE[fid] = blocks
    return blocks


TYPE_BLOCKS = {
    "kelp": ["kelp", "kelp_plant"], "bamboo": ["bamboo"], "vines": ["vine"], "sea_pickle": ["sea_pickle"],
    "coral_tree": ["tube_coral", "brain_coral", "bubble_coral", "fire_coral", "horn_coral", "tube_coral_block", "brain_coral_block",
                   "bubble_coral_block", "fire_coral_block", "horn_coral_block"],
    "coral_mushroom": ["tube_coral_block", "brain_coral_block", "bubble_coral_block", "fire_coral_block", "horn_coral_block"],
    "coral_claw": ["tube_coral_block", "brain_coral_block", "bubble_coral_block", "fire_coral_block", "horn_coral_block"],
    "seagrass": ["seagrass", "tall_seagrass"], "large_dripstone": ["dripstone_block", "pointed_dripstone"],
    "dripstone_cluster": ["dripstone_block", "pointed_dripstone"], "pointed_dripstone": ["pointed_dripstone", "dripstone_block"],
    "monster_room": ["spawner", "chest", "cobblestone", "mossy_cobblestone", "cobweb"], 
    "desert_well": ["sandstone", "sandstone_slab", "sandstone_stairs"], "fossil": ["bone_block", "coal_ore"],
    "iceberg": ["packed_ice", "blue_ice", "snow_block"], "forest_rock": ["mossy_cobblestone"], "cocoa": ["cocoa"],
    "beehive": ["bee_nest"], "glow_lichen": ["glow_lichen"], "sculk_patch": ["sculk", "sculk_catalyst", "sculk_vein"],
    "spring_feature": [],
}


def collect(o, blocks):
    if isinstance(o, dict):
        t = o.get("type")
        if isinstance(t, str) and strip(t) in TYPE_BLOCKS:
            blocks.update(TYPE_BLOCKS[strip(t)])
        if isinstance(o.get("block"), str) and o["block"].startswith("minecraft:"):
            blocks.add(strip(o["block"]))
        if "Name" in o and isinstance(o["Name"], str):
            blocks.add(strip(o["Name"]))
        for k, v in o.items():
            if k in ("feature", "default") and isinstance(v, str):
                blocks |= feature_blocks(v)
            elif k == "features" and isinstance(v, list):
                for e in v:
                    if isinstance(e, str):
                        blocks |= feature_blocks(e)
                    else:
                        collect(e, blocks)
            else:
                collect(v, blocks)
    elif isinstance(o, list):
        for e in o:
            collect(e, blocks)


def loot_items(o, out):
    """Loot tablosunda gecen tum item adlari (kosullardan bagimsiz: silk touch/makas dahil)."""
    if isinstance(o, dict):
        if o.get("type") == "minecraft:item" and isinstance(o.get("name"), str):
            out.add(strip(o["name"]))
        for v in o.values():
            loot_items(v, out)
    elif isinstance(o, list):
        for e in o:
            loot_items(e, out)


def loot_file(path):
    out = set()
    loot_items(json.load(open(path, encoding="utf-8")), out)
    return sorted(out)


def main():
    overworld = resolve_tag("worldgen/biome", "is_overworld")
    print("overworld biyom:", len(overworld))

    # --- biyomlar
    biomes = {}
    for b in sorted(overworld):
        data = load("worldgen", "biome", b + ".json")
        blocks, mobs = set(), set()
        for step in data["features"]:
            for f in step:
                blocks |= feature_blocks(f)
        for cat, lst in data["spawners"].items():
            for e in lst:
                mobs.add(strip(e["type"]))
        biomes[b] = {"blocks": sorted(blocks), "mobs": sorted(mobs)}

    # --- yuzey (ozellik olmayan) bloklari: etiket/ad ile
    def add(blist, *names):
        for b in blist:
            biomes[b]["blocks"] = sorted(set(biomes[b]["blocks"]) | set(names))
    tag = lambda n: resolve_tag("worldgen/biome", n) & overworld
    add(tag("is_beach") | tag("is_ocean") | tag("is_river") | {"desert"}, "sand")
    add({"desert"}, "sandstone", "cut_sandstone", "chiseled_sandstone", "smooth_sandstone")
    add(tag("is_badlands"), "red_sand", "terracotta", "red_sandstone", "orange_terracotta", "white_terracotta", "yellow_terracotta",
        "brown_terracotta", "red_terracotta", "light_gray_terracotta")
    add({b for b in overworld if re.search(r"snowy|frozen|ice_spikes|grove|jagged|frozen_peaks|snowy_slopes", b)}, "snow_block", "snow", "powder_snow")
    add({"ice_spikes", "frozen_ocean", "deep_frozen_ocean", "frozen_river", "frozen_peaks"}, "ice", "packed_ice")
    add({"ice_spikes"}, "blue_ice")
    add({"deep_frozen_ocean"}, "blue_ice")
    add({"mushroom_fields"}, "mycelium")
    add({"mangrove_swamp"}, "mud", "mangrove_log", "mangrove_roots", "muddy_mangrove_roots")
    add({b for b in overworld if "taiga" in b and "old_growth" in b}, "podzol", "coarse_dirt")
    add({"stony_shore", "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "stony_peaks", "jagged_peaks", "frozen_peaks"}, "stone", "gravel")
    add({b for b in overworld if "ocean" in b or b == "river"}, "gravel", "clay", "water")
    add({b for b in overworld if "deep_dark" in b}, "sculk", "sculk_vein", "sculk_sensor", "sculk_catalyst", "sculk_shrieker", "reinforced_deepslate")

    # yapilarda dogal olusan bloklar (oyuncu bunlari yapi bulunca alir)
    structure_blocks = {
        "minecraft:ocean_monument": ["prismarine", "prismarine_bricks", "dark_prismarine", "sea_lantern", "sponge", "wet_sponge", "gold_block"],
        "minecraft:mineshaft": ["rail", "powered_rail", "oak_planks", "oak_fence", "cobweb", "torch", "chest_minecart", "oak_log"],
        "minecraft:village_plains": ["hay_block", "bell", "oak_planks", "cobblestone", "glass_pane", "bookshelf", "composter", "barrel", "smoker",
                                     "blast_furnace", "grindstone", "lectern", "loom", "stonecutter", "fletching_table", "smithing_table",
                                     "cartography_table", "brewing_stand", "cauldron", "white_bed", "red_bed", "lantern", "torch"],
        "minecraft:desert_pyramid": ["tnt", "sandstone", "chiseled_sandstone", "orange_terracotta", "blue_terracotta", "stone_pressure_plate"],
        "minecraft:jungle_pyramid": ["mossy_cobblestone", "tripwire_hook", "dispenser", "lever", "sticky_piston", "redstone_wire"],
        "minecraft:igloo": ["white_carpet", "red_bed", "furnace", "crafting_table", "snow_block"],
        "minecraft:woodland_mansion": ["dark_oak_planks", "cobblestone", "white_wool", "red_carpet", "chest"],
        "minecraft:pillager_outpost": ["dark_oak_planks", "cobblestone", "white_banner", "chest", "cake"],
        "minecraft:ruined_portal": ["obsidian", "crying_obsidian", "netherrack", "gold_block", "magma_block", "iron_nugget", "chest"],
        "minecraft:shipwreck": ["oak_planks", "spruce_planks", "dark_oak_planks", "oak_log", "chest"],
    }
    general = ["stone", "dirt", "grass_block", "gravel", "deepslate", "cobblestone", "bedrock", "water", "lava", "coarse_dirt", "tuff",
               "granite", "diorite", "andesite", "coal_ore", "iron_ore", "copper_ore", "gold_ore", "redstone_ore", "lapis_ore",
               "diamond_ore", "emerald_ore_placeholder"]
    general = [g for g in general if not g.endswith("_placeholder")]

    # --- blok dususleri
    block_drops = {}
    for rel, path in walk_files("loot_table", "blocks"):
        items = loot_file(path)
        if items:
            block_drops["minecraft:" + rel] = ["minecraft:" + i for i in items]
    mob_drops = {}
    for rel, path in walk_files("loot_table", "entities"):
        items = loot_file(path)
        if items:
            mob_drops["minecraft:" + rel] = ["minecraft:" + i for i in items]
    # koyun renkleri: entities/sheep/<renk>.json -> yaratik "sheep"
    sheep = set()
    for k in list(mob_drops):
        if k.startswith("minecraft:sheep/"):
            sheep |= set(mob_drops.pop(k))
    mob_drops["minecraft:sheep"] = sorted(sheep | set(mob_drops.get("minecraft:sheep", [])))

    # --- yapi sandiklari
    struct_loot = {}
    prefix_map = [
        ("abandoned_mineshaft", ["mineshaft"]), ("ancient_city", ["ancient_city"]), ("buried_treasure", ["buried_treasure"]),
        ("desert_pyramid", ["desert_pyramid"]), ("igloo_chest", ["igloo"]), ("jungle_temple", ["jungle_pyramid"]),
        ("pillager_outpost", ["pillager_outpost"]), ("ruined_portal", ["ruined_portal"]), ("shipwreck", ["shipwreck"]),
        ("stronghold", ["stronghold"]), ("underwater_ruin", ["ocean_ruin_cold", "ocean_ruin_warm"]),
        ("woodland_mansion", ["mansion"]), ("village/", ["village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga"]),
        ("trial_chambers", ["trial_chambers"]),
    ]
    for rel, path in walk_files("loot_table", "chests"):
        for pre, sids in prefix_map:
            if rel.startswith(pre) or ("/" + pre) in rel:
                for sid in sids:
                    struct_loot.setdefault("minecraft:" + sid, set()).update("minecraft:" + i for i in loot_file(path))
    struct_loot = {k: sorted(v) for k, v in struct_loot.items()}
    # zindan: monster_room ozelligi biyom bloklarinda 'spawner' olarak gorunur -> sandik esyalari "dungeon" sahte yapisi
    dungeon = set()
    for rel, path in walk_files("loot_table", "chests"):
        if rel == "simple_dungeon":
            dungeon.update("minecraft:" + i for i in loot_file(path))
    struct_loot["kapisma:dungeon"] = sorted(dungeon)

    # --- balik tutma
    fishing = set()
    for rel, path in walk_files("loot_table", "gameplay"):
        if rel.startswith("fishing"):
            fishing.update("minecraft:" + i for i in loot_file(path))

    out = dict(version="1.21.1", biomes=biomes, general=["minecraft:" + g for g in general],
               blockDrops=block_drops, mobDrops=mob_drops, structureLoot=struct_loot,
               fishing=sorted(fishing), structureBlocks={k: ["minecraft:" + b for b in v] for k, v in structure_blocks.items()})
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    json.dump(out, open(OUT, "w", encoding="utf-8"), separators=(",", ":"))
    print("yazildi:", OUT, os.path.getsize(OUT) // 1024, "KB")
    print("ornek diamond biyomlari:", sum(1 for b in biomes.values() if "diamond_ore" in b["blocks"]))
    print("ornek cocoa biyomlari:", [b for b, v in biomes.items() if "cocoa" in v["blocks"]])
    print("ornek cactus:", [b for b, v in biomes.items() if "cactus" in v["blocks"]])


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--extract", action="store_true")
    a = ap.parse_args()
    if a.extract:
        extract()
    main()
