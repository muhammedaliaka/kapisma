"""Neutral buyers place time-limited orders in the municipal menu."""

import json
from economy import record_income

TRIGGER = "krts_caravan"
INTERVAL = 30
TRADE_TIME = 45
ORDERS = (
    ("wood", "odun", 4, 3),
    ("stone", "kırık taş", 4, 3),
    ("bread", "erzak", 2, 4),
    ("iron", "demir", 2, 4),
)


def generate(put, buttons, treasury_limit):
    slots = range(1, 9)
    token = 'execute store result storage krts:caravan token int 1 run scoreboard players get #caravanRound krts_ctrl'
    put("caravan/load",
        *[f'scoreboard players add #{field} krts_ctrl 0' for field in ("caravanOffer", "caravanNext", "caravanRound")],
        f'execute unless score #caravanTime krts_ctrl matches 0.. run scoreboard players set #caravanTime krts_ctrl {INTERVAL}',
        token)
    put("caravan/init",
        'scoreboard players set #caravanOffer krts_ctrl 0',
        'scoreboard players set #caravanNext krts_ctrl 0',
        f'scoreboard players set #caravanTime krts_ctrl {INTERVAL}',
        *[f'scoreboard players set #caravanDone{slot} krts_ctrl 0' for slot in slots],
        f'scoreboard players reset * {TRIGGER}')
    put("caravan/clock",
        'scoreboard players remove #caravanTime krts_ctrl 1',
        'execute if score #caravanTime krts_ctrl matches ..0 run function krts:caravan/advance')
    put("caravan/advance",
        'execute if score #caravanOffer krts_ctrl matches 1.. run return run function krts:caravan/depart',
        'scoreboard players add #caravanNext krts_ctrl 1',
        f'execute if score #caravanNext krts_ctrl matches {len(ORDERS) + 1}.. run scoreboard players set #caravanNext krts_ctrl 1',
        'scoreboard players operation #caravanOffer krts_ctrl = #caravanNext krts_ctrl',
        'scoreboard players add #caravanRound krts_ctrl 1',
        token,
        f'scoreboard players set #caravanTime krts_ctrl {TRADE_TIME}',
        'tellraw @a[scores={krts_slot=1..8},gamemode=!spectator] '
        '"[RTS] Yeni kervan siparişi geldi. Ayrıntılar /trigger krts_menu içinde."')
    put("caravan/depart",
        'scoreboard players set #caravanOffer krts_ctrl 0',
        f'scoreboard players set #caravanTime krts_ctrl {INTERVAL}')

    put("caravan/player",
        f'execute if score @s {TRIGGER} matches 1.. run function krts:caravan/accept',
        f'scoreboard players set @s {TRIGGER} 0',
        f'scoreboard players enable @s {TRIGGER}')
    put("caravan/accept",
        'execute unless score #caravanOffer krts_ctrl matches 1.. run return run tellraw @s "[RTS] Kervan ayrıldı."',
        f'execute unless score @s {TRIGGER} = #caravanRound krts_ctrl run return run tellraw @s '
        '"[RTS] Kervan siparişi değişti. Güncel siparişi menüden aç."',
        *[f'execute if score @s krts_slot matches {slot} if score #a{slot} krts_ctrl matches 1 '
          f'run function krts:caravan/accept/{slot}' for slot in slots])
    put("caravan/menu",
        *[f'execute if score @s krts_slot matches {slot} if score #a{slot} krts_ctrl matches 1 '
          f'run function krts:caravan/menu/{slot}' for slot in slots])

    def countdown(label):
        return json.dumps([{"text": label}, {"score": {"name": "#caravanTime", "objective": "krts_ctrl"}},
                           {"text": " sn"}], ensure_ascii=False)

    put("caravan/order",
        *[f'$execute if score #caravanOffer krts_ctrl matches {index} {check} score @s krts_profile matches 3 run tellraw @s '
          + buttons({f"Kervana {amount} {label} teslim et · {payment + bonus} altın": f"/trigger {TRIGGER} set $(token)"})
          for index, (_, label, amount, payment) in enumerate(ORDERS, 1)
          for check, bonus in (("unless", 0), ("if", 1))])

    for slot in slots:
        gold = f"#g{slot} krts_ctrl"
        done = f"#caravanDone{slot} krts_ctrl"
        put(f"caravan/menu/{slot}",
            'execute if score #caravanOffer krts_ctrl matches 0 run return run tellraw @s '
            + countdown("Sonraki kervan · "),
            'tellraw @s ' + countdown("Kervanın ayrılmasına · "),
            f'execute if score {done} = #caravanRound krts_ctrl run return run tellraw @s '
            '"[RTS] Bu kervana siparişini teslim ettin."',
            'function krts:caravan/order with storage krts:caravan')
        put(f"caravan/accept/{slot}",
            f'execute if score {done} = #caravanRound krts_ctrl run return run tellraw @s '
            '"[RTS] Bu kervana siparişini zaten teslim ettin."',
            *[f'execute if score #caravanOffer krts_ctrl matches {index} run function krts:caravan/sell/{slot}/{index}'
              for index in range(1, len(ORDERS) + 1)])
        for index, (stock, label, amount, payment) in enumerate(ORDERS, 1):
            resource = f"#{stock}{slot} krts_ctrl"
            put(f"caravan/sell/{slot}/{index}",
                f'execute unless score {resource} matches {amount}.. run return run tellraw @s '
                f'"[RTS] Sipariş için {amount} {label} gerekir."',
                f'scoreboard players set #caravanPay krts_ctrl {payment}',
                'execute if score @s krts_profile matches 3 run scoreboard players add #caravanPay krts_ctrl 1',
                f'scoreboard players operation #caravanBalance krts_ctrl = {gold}',
                'scoreboard players operation #caravanBalance krts_ctrl += #caravanPay krts_ctrl',
                f'execute unless score #caravanBalance krts_ctrl matches ..{treasury_limit} run return run tellraw @s '
                '"[RTS] Kervan ödemesi hazineye sığmıyor."',
                f'scoreboard players remove {resource} {amount}',
                f'scoreboard players operation {gold} = #caravanBalance krts_ctrl',
                record_income(slot, "#caravanPay"),
                f'scoreboard players operation {done} = #caravanRound krts_ctrl',
                'tellraw @s ' + json.dumps(
                    [{"text": f"[RTS] {amount} {label} teslim edildi. Hazineye "},
                     {"score": {"name": "#caravanPay", "objective": "krts_ctrl"}},
                     {"text": " altın eklendi."}], ensure_ascii=False))
