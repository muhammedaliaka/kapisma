"""Datapack rules for negotiated diplomacy. The server adapter governs HYW combat."""

from itertools import combinations
import json
from economy import MAX_INSTABILITY


PAIRS = list(combinations(range(1, 9), 2))
AGREEMENTS = (
    ("peace", 2, 10, "truce", "ateşkes", 3, "truce_seconds"),
    ("trade", None, 0, "commerce", "ticaret anlaşması", None, "trade_seconds"),
    ("pact", 0, 30, "nonaggression", "saldırmazlık paktı", 5, "nonaggression_seconds"),
)
COMMANDS = (("krts_war", "declare"), *((f"krts_{action}", action) for action, *_ in AGREEMENTS))
TRIGGERS = (*(objective for objective, _ in COMMANDS), "krts_tech")
DEFAULTS = {"war_cost": 4, "war_notice_seconds": 15, "minimum_war_seconds": 30,
            "truce_seconds": 45, "trade_seconds": 60, "nonaggression_seconds": 100,
            "offer_seconds": 30, "logistics_cost": 8, "archery_cost": 7,
            "medicine_cost": 9, "doctrine_cost": 9}
LIMITS = {name: (1, 60) if name.endswith("_cost") else (5, 600) for name in DEFAULTS}
PAIR_FIELDS = ("pact", "clock", "offer", "offer_t", "trade", "trade_offer", "trade_t")
TECHNOLOGIES = (
    ("log", "Lojistik", "logistics_cost", None, "Her gelir döngüsünde 1 ek altın."),
    ("bow", "Okçuluk", "archery_cost", None, "Yeni okçu üretimi açılır."),
    ("medicine", "Saha Tıbbı", "medicine_cost", "log", "Erzak teslimi 6 can yeniler."),
    ("doctrine", "Talim Doktrini", "doctrine_cost", "bow", "Talim süresi 15 saniyeye iner."),
)


def generate(put, rules, *, natural_match=False):
    war_cost = rules["war_cost"]
    notice = rules["war_notice_seconds"]
    offer_seconds = rules["offer_seconds"]
    labels = {key: label for key, label, *_ in TECHNOLOGIES}
    technologies = TECHNOLOGIES[:2] if natural_match else TECHNOLOGIES
    put("dip/init",
        'scoreboard players reset * krts_identity',
        *[f'scoreboard players set #{prefix}{a}{b} krts_ctrl 0'
          for a, b in PAIRS for prefix in PAIR_FIELDS],
        *[f'scoreboard players set #{prefix}{slot} krts_ctrl 0'
          for slot in range(1, 9) for prefix in labels],
        *(['function krts:ally/init'] if natural_match else []))

    put("dip/clock", *[f'execute if score #slot krts_ctrl matches {b}.. run function krts:dip/clock/{a}{b}' for a, b in PAIRS],
        *(['function krts:ally/clock'] if natural_match else []))
    put("dip/income", *[f'execute if score #trade{a}{b} krts_ctrl matches 1.. if entity @a[scores={{krts_slot={slot}}},gamemode=!spectator] run function krts:earn/{slot}'
                        for a, b in PAIRS for slot in (a, b)],
        *[f'execute if score #log{slot} krts_ctrl matches 1 if entity @a[scores={{krts_slot={slot}}},gamemode=!spectator] run function krts:earn/{slot}' for slot in range(1, 9)])
    put("dip/player", *[f'execute if score @s krts_slot matches {slot} run function krts:dip/player/{slot}' for slot in range(1, 9)],
        *[f'scoreboard players set @s {objective} 0\nscoreboard players enable @s {objective}' for objective in TRIGGERS],
        *(['function krts:ally/player'] if natural_match else []))
    for a, b in PAIRS:
        key = f"{a}{b}"
        pact, clock, offer, offer_time, trade, trade_offer, trade_time = (f"#{prefix}{key}" for prefix in PAIR_FIELDS)
        put(f"dip/clock/{key}",
            f'execute unless score #a{a} krts_ctrl matches 1 run return run function krts:dip/clear/{key}',
            f'execute unless score #a{b} krts_ctrl matches 1 run return run function krts:dip/clear/{key}',
            f'execute if score {pact} krts_ctrl matches 4 run function krts:dip/legacy_trade/{key}',
            f'execute if score {offer} krts_ctrl matches 21..28 run function krts:dip/legacy_offer/{key}',
            *[command for pending, timer in ((offer, offer_time), (trade_offer, trade_time)) for command in (
                f'execute if score {timer} krts_ctrl matches 1.. run scoreboard players remove {timer} krts_ctrl 1',
                f'execute if score {timer} krts_ctrl matches 0 if score {pending} krts_ctrl matches 1.. run scoreboard players set {pending} krts_ctrl 0')],
            f'execute if score {trade} krts_ctrl matches 1 run say [RTS] Devlet {a} ile Devlet {b} arasındaki ticaretin süresi doldu.',
            f'execute if score {trade} krts_ctrl matches 1.. run scoreboard players remove {trade} krts_ctrl 1',
            f'execute if score {clock} krts_ctrl matches 1.. run scoreboard players remove {clock} krts_ctrl 1',
            f'execute unless score {clock} krts_ctrl matches 0 run return 0',
            f'execute if score {pact} krts_ctrl matches 1 run return run function krts:dip/war/{key}',
            f'execute if score {pact} krts_ctrl matches 3..5 run return run function krts:dip/expire/{key}')
        put(f"dip/clear/{key}", *[f'execute if score #{prefix}{key} krts_ctrl matches 1.. run scoreboard players set #{prefix}{key} krts_ctrl 0'
                                  for prefix in PAIR_FIELDS])
        put(f"dip/legacy_trade/{key}", f'scoreboard players operation {trade} krts_ctrl = {clock} krts_ctrl',
            f'scoreboard players set {pact} krts_ctrl 0', f'scoreboard players set {clock} krts_ctrl 0')
        put(f"dip/legacy_offer/{key}", f'scoreboard players operation {trade_offer} krts_ctrl = {offer} krts_ctrl',
            f'scoreboard players remove {trade_offer} krts_ctrl 20',
            f'scoreboard players operation {trade_time} krts_ctrl = {offer_time} krts_ctrl',
            f'scoreboard players set {offer} krts_ctrl 0', f'scoreboard players set {offer_time} krts_ctrl 0')
        put(f"dip/war/{key}", f'scoreboard players set {pact} krts_ctrl 2',
            f'scoreboard players set {clock} krts_ctrl {rules["minimum_war_seconds"]}',
            *([f'scoreboard players add #allyWar{key} krts_ctrl 1'] if natural_match else []),
            f'say [RTS] Devlet {a} ve Devlet {b} artık savaşta.')
        put(f"dip/expire/{key}", f'scoreboard players set {pact} krts_ctrl 0',
            f'say [RTS] Devlet {a} ile Devlet {b} arasındaki anlaşmanın süresi doldu.')
        for action, _, _, result, label, state, duration_key in AGREEMENTS:
            duration = rules[duration_key]
            commerce = action == "trade"
            put(f"dip/{result}/{key}", *([f'scoreboard players set {trade} krts_ctrl {duration}'] if commerce else [
                f'scoreboard players set {pact} krts_ctrl {state}', f'scoreboard players set {clock} krts_ctrl {duration}']),
                f'scoreboard players set {trade_offer if commerce else offer} krts_ctrl 0',
                f'scoreboard players set {trade_time if commerce else offer_time} krts_ctrl 0',
                f'say [RTS] Devlet {a} ve Devlet {b} {duration} saniyelik {label} yaptı.')
        for source, target in ((a, b), (b, a)):
            suffix = f"{source}_{target}"
            eligible = (
                f'execute unless score #a{target} krts_ctrl matches 1 run return run tellraw @s "[RTS] Bu devlet elendi."',
                f'execute unless entity @a[scores={{krts_slot={target}}},gamemode=!spectator] run return run tellraw @s "[RTS] Bu devletin oyuncusu çevrimiçi değil."',
            )
            put(f"dip/declare/{suffix}", *eligible,
                f'execute if score #bank_trial krts_ctrl matches 1 if score #unstable{source} krts_ctrl matches {MAX_INSTABILITY}.. run return run tellraw @s "[RTS] İstikrarsızlık en yüksek düzeyde. Yeni savaş açılamaz."',
                *([f'execute if score #allied{key} krts_ctrl matches 1 run return run tellraw @s "[RTS] Bu devlet müttefikin. İttifakı bozma seçeneğini kullan."'] if natural_match else []),
                f'execute unless score {pact} krts_ctrl matches 0 run return run tellraw @s "[RTS] Mevcut savaş veya anlaşma sona ermeden yeni ilan verilemez."',
                f'execute if score {trade} krts_ctrl matches 1.. run return run tellraw @s "[RTS] Ticaret anlaşması sona ermeden savaş ilan edilemez."',
                f'execute unless score #g{source} krts_ctrl matches {war_cost}.. run return run tellraw @s "[RTS] Savaş ilanı {war_cost} altın gerektirir."',
                f'scoreboard players remove #g{source} krts_ctrl {war_cost}',
                f'function krts:dip/notice/{suffix}')
            put(f"dip/notice/{suffix}",
                f'scoreboard players set {pact} krts_ctrl 1', f'scoreboard players set {clock} krts_ctrl {notice}',
                *[f'scoreboard players set {pending} krts_ctrl 0' for pending in (offer, offer_time, trade_offer, trade_time)],
                f'say [RTS] Devlet {source} savaş ilan etti. Devlet {target} ile savaş {notice} saniye sonra başlar.')
            for action, required, offset, result, label, _, _ in AGREEMENTS:
                if action == "trade":
                    pending, timer = trade_offer, trade_time
                    requirements = (
                        f'execute if score {pact} krts_ctrl matches 1..2 run return run tellraw @s "[RTS] Savaş bildirimi veya savaş sürerken ticaret yapılamaz."',
                        f'execute if score {trade} krts_ctrl matches 1.. run return run tellraw @s "[RTS] Bu devletle zaten ticaret yapıyorsun."',
                    )
                else:
                    pending, timer = offer, offer_time
                    requirements = (
                        f'execute unless score {pact} krts_ctrl matches {required} run return run tellraw @s "[RTS] Devletlerin durumu bu anlaşmaya uygun değil."',
                        f'execute unless score {clock} krts_ctrl matches 0 run return run tellraw @s "[RTS] Zorunlu bekleme süresi henüz dolmadı."',
                    )
                put(f"dip/{action}/{suffix}", *eligible, *requirements,
                    f'execute if score {pending} krts_ctrl matches {offset+target} if score {timer} krts_ctrl matches 1.. run return run function krts:dip/{result}/{key}',
                    f'execute if score @s krts_{action} matches ..-1 run return run tellraw @s "[RTS] Bu teklif artık geçerli değil."',
                    f'scoreboard players set {pending} krts_ctrl {offset+source}', f'scoreboard players set {timer} krts_ctrl {offer_seconds}',
                    f'tellraw @a[scores={{krts_slot={target}}}] {{"text":"Devlet {source} {label} önerdi · {offer_seconds} sn. [Kabul et]","color":"gold","clickEvent":{{"action":"run_command","value":"/trigger krts_{action} set -{source}"}}}}',
                    f'tellraw @s "[RTS] Devlet {target} için {label} teklifi gönderildi."')

    put("dip/help", 'tellraw @s "[RTS] Diplomasi · Devlet seç:"',
        *[f'execute if score @s krts_slot matches {slot} run function krts:dip/menu/{slot}' for slot in range(1, 9)],
        'tellraw @s {"text":"[Teknoloji]","color":"gold","clickEvent":{"action":"run_command","value":"/trigger krts_tech set 5"}}')
    for slot in range(1, 9):
        put(f"dip/menu/{slot}", *[f'execute if score #slot krts_ctrl matches {target}.. if score #a{target} krts_ctrl matches 1 run function krts:dip/menu/{slot}_{target}'
                                  for target in range(1, 9) if target != slot])
        for target in range(1, 9):
            if target == slot:
                continue
            key = f"{min(slot, target)}{max(slot, target)}"
            def button(label, action):
                return {"text": f"[{label}] ", "color": "gold",
                        "clickEvent": {"action": "run_command", "value": f"/trigger krts_{action} set {target}"}}
            states = {
                0: ("Tarafsız", [button("Saldırmazlık", "pact")]),
                1: ("Savaş bildirimi", []),
                2: ("Savaş", [button("Ateşkes", "peace")]),
                3: ("Ateşkes", []),
                4: ("Ticaret", []),
                5: ("Saldırmazlık", []),
            }
            messages = []
            for state, (label, buttons) in states.items():
                components = [{"text": f"Devlet {target} · {label} "}]
                if state:
                    components += [{"score": {"name": f"#clock{key}", "objective": "krts_ctrl"}}, {"text": " sn "}]
                state_check = f'execute if score #pact{key} krts_ctrl matches {state}'
                if natural_match and state == 0:
                    state_check += f' unless score #allied{key} krts_ctrl matches 1'
                if state in (0, 3, 5):
                    available = [button("Ticaret", "trade"), *buttons]
                    if state == 0:
                        available.insert(0, button(f"Savaş ilan et · {war_cost} altın", "war"))
                    commerce_time = [{"text": "· Ticaret "},
                                     {"score": {"name": f"#trade{key}", "objective": "krts_ctrl"}}, {"text": " sn "}]
                    messages += [
                        f'{state_check} unless score #trade{key} krts_ctrl matches 1.. run tellraw @s '
                        + json.dumps(components + available, ensure_ascii=False),
                        f'{state_check} if score #trade{key} krts_ctrl matches 1.. run tellraw @s '
                        + json.dumps(components + commerce_time + buttons, ensure_ascii=False),
                    ]
                else:
                    messages.append(f'{state_check} run tellraw @s ' + json.dumps(components + buttons, ensure_ascii=False))
            put(f"dip/menu/{slot}_{target}", *messages,
                *([f'function krts:ally/menu/{slot}_{target}'] if natural_match else []))
        put(f"dip/player/{slot}",
            *[f'execute if score @s {goal} matches {value} run function krts:dip/{action}/{slot}_{target}'
              for target in range(1, 9) if target != slot
              for goal, action in COMMANDS for value in ((target,) if action == "declare" else (target, -target))],
            *[f'execute if score @s krts_tech matches {i} run function krts:dip/tech/{key}{slot}'
              for i, (key, *_rest) in enumerate(technologies, 1)],
            f'execute if score @s krts_tech matches 5 run function krts:dip/tech/menu/{slot}')
        tech_menu = []
        for index, (key, label, cost, prerequisite, effect) in enumerate(technologies, 1):
            price = rules[cost]
            owns = f'score #{key}{slot} krts_ctrl matches 1'
            requirement = f'score #{prerequisite}{slot} krts_ctrl matches 1'
            detail = {"action": "show_text", "contents": {"text": effect}}
            bought = {"text": f"{label} · Açıldı", "color": "green", "hoverEvent": detail}
            button = {"text": f"[{label} · {price} altın]", "color": "gold", "hoverEvent": detail,
                      "clickEvent": {"action": "run_command", "value": f"/trigger krts_tech set {index}"}}
            tech_menu.append(f'execute if {owns} run tellraw @s ' + json.dumps(bought, ensure_ascii=False))
            available = f'execute unless {owns}'
            if prerequisite:
                locked = {"text": f"{label} · Önce {labels[prerequisite]}", "color": "gray", "hoverEvent": detail}
                tech_menu.append(f'{available} unless {requirement} run tellraw @s ' + json.dumps(locked, ensure_ascii=False))
                available += f' if {requirement}'
            tech_menu.append(f'{available} run tellraw @s ' + json.dumps(button, ensure_ascii=False))
            requirement_message = (
                f'execute unless {requirement} run return run tellraw @s "[RTS] Önce {labels[prerequisite]} araştır."',
            ) if prerequisite else ()
            put(f"dip/tech/{key}{slot}",
                f'execute if {owns} run return run tellraw @s "[RTS] {label} zaten açıldı."',
                *requirement_message,
                f'execute unless score #g{slot} krts_ctrl matches {price}.. run return run tellraw @s "[RTS] {label} için altın yetersiz."',
                f'scoreboard players remove #g{slot} krts_ctrl {price}', f'scoreboard players set #{key}{slot} krts_ctrl 1',
                f'tellraw @s "[RTS] {label} tamamlandı."')
        put(f"dip/tech/menu/{slot}", *tech_menu)
