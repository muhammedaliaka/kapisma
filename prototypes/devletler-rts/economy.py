"""Neutral lending, public relief and capital repair for the RTS economy."""

import json

TRIGGER = "krts_bank"
PRINCIPAL = 8
TOTAL_PAYMENT = 10
INSTALLMENT = 2
PERIOD = 72
RESERVE = 2
MAX_INSTABILITY = 6
TREASURY_LIMIT = 60
REPAIR_COST = 2
REPAIR_HEALTH = 4
BANK_GUARD = "if score #bank_trial krts_ctrl matches 1 if score #strict krts_ctrl matches 1"


def record_income(slot, amount):
    income = f"#debt_income{slot} krts_ctrl"
    add = (f"add {income} {amount}" if isinstance(amount, int)
           else f"operation {income} += {amount} krts_ctrl")
    return (f"execute {BANK_GUARD} if score #debt{slot} krts_ctrl matches 1.. "
            f"run scoreboard players {add}")


def generate(put, buttons, capital_health, *, natural_match=False):
    slots = range(1, 9)
    fields = ("debt", "due", "debt_t", "debt_income", "unstable")
    constants = ('scoreboard players set #bank_two krts_ctrl 2',
                 f'scoreboard players set #bank_max_hp krts_ctrl {capital_health * 1000}')
    put("bank/load", *constants,
        *[f'scoreboard players add #{field}{slot} krts_ctrl 0' for slot in slots for field in fields])
    put("bank/init", *constants,
        *[f'scoreboard players set #{field}{slot} krts_ctrl 0' for slot in slots for field in fields])
    put("bank/clock", *[f'execute if score #a{slot} krts_ctrl matches 1 if score #debt{slot} krts_ctrl matches 1.. '
                       f'run function krts:bank/clock/{slot}' for slot in slots])
    put("bank/player",
        *[f'execute if score @s krts_slot matches {slot} if score #a{slot} krts_ctrl matches 1 run function krts:bank/player/{slot}'
          for slot in slots],
        f'scoreboard players set @s {TRIGGER} 0', f'scoreboard players enable @s {TRIGGER}')
    put("bank/food_position", '$return run execute positioned $(x) -60 $(z) run function krts:bank/food')
    put("bank/food",
        'execute unless data block ~-8 ~ ~2 Items[{Slot:0b,id:"minecraft:bread"}] run return 0',
        'execute store result score #bank_food krts_ctrl run data get block ~-8 ~ ~2 Items[{Slot:0b}].count',
        'execute unless score #bank_food krts_ctrl matches 1.. run return 0',
        'scoreboard players remove #bank_food krts_ctrl 1',
        'execute if score #bank_food krts_ctrl matches 0 run data remove block ~-8 ~ ~2 Items[{Slot:0b}]',
        'execute if score #bank_food krts_ctrl matches 1.. store result block ~-8 ~ ~2 Items[{Slot:0b}].count int 1 run scoreboard players get #bank_food krts_ctrl',
        'return 1')

    for slot in slots:
        def score(field):
            return f"#{field}{slot} krts_ctrl"

        gold, debt, due, timer, income, stability = (score(field) for field in ("g", *fields))
        capital = f"@e[tag=krts.capital,tag=krts.s{slot},limit=1]"
        put(f"earn/{slot}",
            f'execute unless score {gold} matches ..{TREASURY_LIMIT - 1} run return 0',
            f'scoreboard players add {gold} 1',
            record_income(slot, 1))
        put(f"bank/player/{slot}",
            *[f'execute if score @s {TRIGGER} matches {i} run function krts:bank/{action}/{slot}'
              for i, action in enumerate(("borrow", "early_payment", "relief", "menu", "repair"), 1)])
        put(f"bank/menu/{slot}",
            'tellraw @s ' + _summary(slot),
            f'tellraw @s {buttons({f"Kredi al · {PRINCIPAL} altın, toplam {TOTAL_PAYMENT}": "/trigger krts_bank set 1",
                                  "Erken ödeme": "/trigger krts_bank set 2",
                                  "Halka erzak dağıt": "/trigger krts_bank set 3"})}',
            f'execute if score #capital_mode krts_ctrl matches 1 run tellraw @s '
            f'{buttons({f"Belediyeyi onar · {REPAIR_COST} altın": "/trigger krts_bank set 5"})}')
        put(f"bank/borrow/{slot}",
            f'execute if score {debt} matches 1.. run return run tellraw @s "[RTS] Bir açık kredin var."',
            f'execute if score #left krts_ctrl matches ..{PERIOD} run return run tellraw @s "[RTS] Maçta ilk taksit için yeterli süre kalmadı."',
            f'execute unless score {gold} matches ..{TREASURY_LIMIT - PRINCIPAL} run return run tellraw @s "[RTS] Hazine bu krediye sığmıyor."',
            f'scoreboard players add {gold} {PRINCIPAL}',
            f'scoreboard players set {debt} {TOTAL_PAYMENT}',
            f'scoreboard players set {timer} {PERIOD}',
            f'scoreboard players set {due} 0',
            f'scoreboard players set {income} 0',
            f'tellraw @s "[RTS] {PRINCIPAL} altın alındı. Toplam {TOTAL_PAYMENT} ödenecek. '
            f'Her {PERIOD} saniyede {INSTALLMENT} altın vadeye girer."')
        put(f"bank/clock/{slot}",
            f'scoreboard players remove {timer} 1',
            f'execute if score {timer} matches ..0 run function krts:bank/settle/{slot}')
        put(f"bank/settle/{slot}",
            f'scoreboard players set {timer} {PERIOD}',
            f'scoreboard players add {due} {INSTALLMENT}',
            f'scoreboard players operation {due} < {debt}',
            f'scoreboard players operation #bank_pay krts_ctrl = {income}',
            f'scoreboard players remove #bank_pay krts_ctrl {RESERVE}',
            'scoreboard players operation #bank_pay krts_ctrl /= #bank_two krts_ctrl',
            f'scoreboard players set {income} 0',
            f'scoreboard players operation #bank_pay krts_ctrl < {due}',
            f'function krts:bank/pay/{slot}',
            f'execute if score {due} matches 1.. run function krts:bank/overdue/{slot}',
            f'execute if score {due} matches 1.. run tellraw @a[scores={{krts_slot={slot}}},gamemode=!spectator] '
            + _summary(slot, "[RTS] Vadesi geçen taksit istikrarsızlığı artırdı. "))
        put(f"bank/early_payment/{slot}",
            f'execute unless score {debt} matches 1.. run return run tellraw @s "[RTS] Açık borcun yok."',
            f'execute unless score {gold} matches {RESERVE + 1}.. run return run tellraw @s "[RTS] Temel ihtiyaç payı dışında altın yok."',
            f'scoreboard players set #bank_pay krts_ctrl {INSTALLMENT}',
            f'function krts:bank/pay/{slot}',
            f'function krts:bank/menu/{slot}')
        put(f"bank/pay/{slot}",
            f'scoreboard players operation #bank_spare krts_ctrl = {gold}',
            f'scoreboard players remove #bank_spare krts_ctrl {RESERVE}',
            'scoreboard players operation #bank_pay krts_ctrl < #bank_spare krts_ctrl',
            f'scoreboard players operation #bank_pay krts_ctrl < {debt}',
            'execute unless score #bank_pay krts_ctrl matches 1.. run return 0',
            f'scoreboard players operation {gold} -= #bank_pay krts_ctrl',
            f'scoreboard players operation {debt} -= #bank_pay krts_ctrl',
            f'scoreboard players operation {due} -= #bank_pay krts_ctrl',
            f'execute if score {due} matches ..-1 run scoreboard players set {due} 0',
            f'execute if score {debt} matches 0 run scoreboard players set {income} 0',
            f'execute if score {debt} matches 0 run scoreboard players set {timer} 0')
        put(f"bank/overdue/{slot}",
            f'execute if score {stability} matches 1..{MAX_INSTABILITY - 2} run scoreboard players add {stability} 1',
            f'execute if score {stability} matches ..{MAX_INSTABILITY - 1} run scoreboard players add {stability} 1')
        put(f"bank/relief/{slot}",
            f'execute if score {debt} matches 1.. run return run tellraw @s "[RTS] Halkın istikrarını toparlamadan önce borcu kapat."',
            f'execute unless score {stability} matches 1.. run return run tellraw @s "[RTS] Halk istikrarlı."',
            *([
                f'execute unless score #bread{slot} krts_ctrl matches 1.. run return run tellraw @s "[RTS] Dağıtılacak erzak bulunamadı."',
                f'scoreboard players remove #bread{slot} krts_ctrl 1',
            ] if natural_match else [
                f'execute in minecraft:overworld store result score #bank_relief krts_ctrl run function krts:bank/food_position with storage krts:arena h{slot}',
                'execute unless score #bank_relief krts_ctrl matches 1 run return run tellraw @s "[RTS] Dağıtılacak erzak bulunamadı."',
            ]),
            f'scoreboard players remove {stability} 1',
            'tellraw @s "[RTS] Bir ekmek halka dağıtıldı. İstikrarsızlık 1 azaldı."')
        put(f"bank/repair/{slot}",
            f'execute if score {debt} matches 1.. run return run tellraw @s "[RTS] Onarımdan önce borcu kapat."',
            'execute unless score #capital_mode krts_ctrl matches 1 run return 0',
            f'execute unless score {gold} matches {RESERVE + REPAIR_COST}.. run return run tellraw @s "[RTS] Temel ihtiyaç payı dışında onarım altını yetersiz."',
            f'execute in minecraft:overworld unless entity {capital} run return 0',
            f'execute in minecraft:overworld store result score #bank_hp krts_ctrl run data get entity {capital} Health 1000',
            'execute unless score #bank_hp krts_ctrl matches 1.. run return 0',
            'execute if score #bank_hp krts_ctrl >= #bank_max_hp krts_ctrl run return run tellraw @s "[RTS] Belediyenin canı zaten tam."',
            f'scoreboard players add #bank_hp krts_ctrl {REPAIR_HEALTH * 1000}',
            'scoreboard players operation #bank_hp krts_ctrl < #bank_max_hp krts_ctrl',
            f'execute in minecraft:overworld store result entity {capital} Health double 0.001 run scoreboard players get #bank_hp krts_ctrl',
            f'scoreboard players remove {gold} {REPAIR_COST}',
            f'execute if score {stability} matches 1.. run scoreboard players remove {stability} 1',
            'tellraw @s "[RTS] Belediye onarıldı."')


def _summary(slot, prefix="[RTS] "):
    fields = (("Hazine", "g"), ("Borç", "debt"), ("Vadesi geçmiş", "due"),
              ("Sonraki taksit (sn)", "debt_t"), ("Dönem geliri", "debt_income"), ("İstikrarsızlık", "unstable"))
    components = [{"text": prefix}]
    for i, (label, field) in enumerate(fields):
        components.append({"text": (" · " if i else "") + label + " "})
        components.append({"score": {"name": f"#{field}{slot}", "objective": "krts_ctrl"}})
    components.append({"text": f" · Dönem başına temel ihtiyaç payı {RESERVE}"})
    return json.dumps(components, ensure_ascii=False)
