"""Alliances, transfers and explicit calls to join a war in natural Diplomacy."""

from itertools import permutations
import json

from diplomacy import PAIRS
from economy import MAX_INSTABILITY, RESERVE, TREASURY_LIMIT


TRIGGERS = ("krts_ally", "krts_ally_reply", "krts_ally_call", "krts_betray",
            "krts_aid_gold", "krts_aid_food", "krts_aid_iron")
SEEN = "krts_ally_seen"
CALL_SECONDS = 30
BETRAYAL = 3
AID = (
    ("gold", "g", "altın", 2, TREASURY_LIMIT),
    ("food", "bread", "erzak", 1, 8),
    ("iron", "iron", "demir", 1, 8),
)


def key(first, second):
    return f"{min(first, second)}{max(first, second)}"


def button(label, trigger, value):
    return {"text": f"[{label}] ", "color": "gold",
            "clickEvent": {"action": "run_command", "value": f"/trigger {trigger} set {value}"}}


def message(text, *choices):
    return json.dumps([{"text": text}, *choices], ensure_ascii=False)


def generate(put, rules):
    slots = range(1, 9)
    directed = tuple(permutations(slots, 2))
    pair_fields = ("allied", "allyOffer", "allyOfferTime", "allyWar")
    call_fields = ("allyCall", "allyCallTime")
    put("ally/load",
        'scoreboard players add #allySerial krts_ctrl 0',
        'scoreboard players set #allyMinus krts_ctrl -1',
        *[f'scoreboard players add #allyWar{a}{b} krts_ctrl 0' for a, b in PAIRS])
    put("ally/init",
        *[f'scoreboard players set #{field}{a}{b} krts_ctrl 0' for a, b in PAIRS for field in pair_fields],
        *[f'scoreboard players set #{field}{a}{b} krts_ctrl 0' for a, b in directed for field in call_fields],
        f'scoreboard players reset * {SEEN}',
        'data remove storage krts:alliance offers', 'data remove storage krts:alliance calls',
        *[f'scoreboard players reset * {goal}' for goal in TRIGGERS])
    put("ally/clock",
        *[f'execute if score #slot krts_ctrl matches {b}.. run function krts:ally/pair/{a}{b}' for a, b in PAIRS],
        *[f'execute if score #allyCall{a}{b} krts_ctrl matches 1.. run function krts:ally/call/clock '
          f'with storage krts:alliance calls.c{a}_{b}' for a, b in directed])
    put("ally/player",
        *[f'execute if score @s krts_slot matches {slot} if score #a{slot} krts_ctrl matches 1 '
          f'run function krts:ally/player/{slot}' for slot in slots],
        *[f'scoreboard players set @s {goal} 0\nscoreboard players enable @s {goal}' for goal in TRIGGERS])

    for a, b in PAIRS:
        pair = f"{a}{b}"
        put(f"ally/pair/{pair}",
            f'execute unless score #a{a} krts_ctrl matches 1 run return run function krts:ally/clear/{pair}',
            f'execute unless score #a{b} krts_ctrl matches 1 run return run function krts:ally/clear/{pair}',
            f'execute if score #allyOfferTime{pair} krts_ctrl matches 1.. '
            f'run scoreboard players remove #allyOfferTime{pair} krts_ctrl 1',
            f'execute if score #allyOffer{pair} krts_ctrl matches 1.. if score #allyOfferTime{pair} krts_ctrl matches 0 '
            f'run function krts:ally/offer/clear {{pair:{pair}}}')
        put(f"ally/clear/{pair}",
            f'execute if score #allied{pair} krts_ctrl matches 1 run scoreboard players set #allied{pair} krts_ctrl 0',
            f'execute if score #allyOffer{pair} krts_ctrl matches 1.. run function krts:ally/offer/clear {{pair:{pair}}}')

    for source in slots:
        targets = [target for target in slots if target != source]
        put(f"ally/player/{source}",
            f'execute unless score @s krts_ally_reply matches 0 run function krts:ally/reply/{source}',
            f'execute if score @s krts_ally_call matches 11..88 run function krts:ally/request/{source}',
            *[f'execute if score @s {goal} matches {target} run function krts:ally/{action}/{source}_{target}'
              for goal, action in (("krts_ally", "offer"), ("krts_betray", "betray"),
                                   *((f"krts_aid_{aid}", f"transfer/{aid}") for aid, *_ in AID))
              for target in targets])
        put(f"ally/reply/{source}",
            'scoreboard players operation #allyReply krts_ctrl = @s krts_ally_reply',
            'execute if score #allyReply krts_ctrl matches ..-1 run scoreboard players operation #allyReply krts_ctrl *= #allyMinus krts_ctrl',
            *[command for other in targets for command in (
                f'execute if score #allyReply krts_ctrl = #allyOffer{key(source, other)} krts_ctrl '
                f'run return run function krts:ally/offer/reply with storage krts:alliance offers.o{key(source, other)}',
                f'execute if score #allyReply krts_ctrl = #allyCall{other}{source} krts_ctrl '
                f'run return run function krts:ally/call/reply with storage krts:alliance calls.c{other}_{source}')],
            'tellraw @s "[RTS] Bu ittifak teklifi veya çağrı artık geçerli değil."')
        put(f"ally/request/{source}", *[
            f'execute if score @s krts_ally_call matches {10 * target + enemy} run function krts:ally/call/request '
            f'{{caller:{source},responder:{target},enemy:{enemy},pair:{key(source, target)},'
            f'front:{key(source, enemy)},joining:{key(target, enemy)}}}'
            for target in targets for enemy in targets if target != enemy])

    war_cost = rules["war_cost"]
    for source, target in directed:
        pair = key(source, target)
        suffix = f"{source}_{target}"
        pact = f"#pact{pair} krts_ctrl"
        allied = f"#allied{pair} krts_ctrl"
        peer = f"@a[scores={{krts_slot={target}}},gamemode=!spectator]"
        available = (
            f'execute unless score #a{target} krts_ctrl matches 1 run return run tellraw @s "[RTS] Bu devlet elendi."',
            f'execute unless entity {peer} run return run tellraw @s "[RTS] Bu devletin oyuncusu çevrimiçi değil."',
        )
        put(f"ally/menu/{suffix}",
            f'execute unless score {allied} matches 1 unless score {pact} matches 1..2 run tellraw @s '
            + message(f"Devlet {target} ", button("İttifak kur", "krts_ally", target)),
            f'execute if score {allied} matches 1 run tellraw @s '
            + message(f"Devlet {target} · Müttefik ",
                      *(button(f"{amount} {label} gönder", f"krts_aid_{aid}", target)
                        for aid, _, label, amount, _ in AID),
                      button(f"İttifakı boz ve savaş ilan et · {war_cost} altın", "krts_betray", target)),
            f'execute if score #allyOffer{pair} krts_ctrl matches 1.. run function krts:ally/offer/menu '
            f'with storage krts:alliance offers.o{pair}',
            f'execute if score #allyCall{target}{source} krts_ctrl matches 1.. run function krts:ally/call/menu '
            f'with storage krts:alliance calls.c{target}_{source}',
            *[f'execute if score {allied} matches 1 if score #a{enemy} krts_ctrl matches 1 '
              f'if score #pact{key(source, enemy)} krts_ctrl matches 2 run tellraw @s '
              + message(f"Devlet {target} ", button(f"Devlet {enemy} için savaş çağrısı", "krts_ally_call", target * 10 + enemy))
              for enemy in slots if enemy not in (source, target)])
        put(f"ally/offer/{suffix}", *available,
            f'execute if score {allied} matches 1 run return run tellraw @s "[RTS] Zaten müttefiksiniz."',
            f'execute if score {pact} matches 1..2 run return run tellraw @s "[RTS] Savaş sürerken ittifak kurulamaz."',
            f'execute if score #allyOffer{pair} krts_ctrl matches 1.. run return run tellraw @s "[RTS] Bekleyen ittifak teklifi var."',
            'scoreboard players add #allySerial krts_ctrl 1',
            f'scoreboard players operation #allyOffer{pair} krts_ctrl = #allySerial krts_ctrl',
            f'scoreboard players set #allyOfferTime{pair} krts_ctrl {rules["offer_seconds"]}',
            f'data modify storage krts:alliance offers.o{pair} set value {{caller:{source},responder:{target},pair:{pair}}}',
            f'execute store result storage krts:alliance offers.o{pair}.token int 1 run scoreboard players get #allySerial krts_ctrl',
            f'execute as {peer} run function krts:ally/offer/menu with storage krts:alliance offers.o{pair}',
            f'tellraw @s "[RTS] Devlet {target} için ittifak teklifi gönderildi."')
        put(f"ally/betray/{suffix}", *available,
            f'execute unless score {allied} matches 1 run return run tellraw @s "[RTS] Bu devletle ittifakın yok."',
            f'execute if score #unstable{source} krts_ctrl matches {MAX_INSTABILITY}.. run return run tellraw @s "[RTS] İstikrarsızlık en yüksek düzeyde. Yeni savaş açılamaz."',
            f'execute unless score #g{source} krts_ctrl matches {war_cost}.. run return run tellraw @s "[RTS] Savaş ilanı {war_cost} altın gerektirir."',
            f'scoreboard players remove #g{source} krts_ctrl {war_cost}',
            f'function krts:ally/clear/{pair}',
            f'scoreboard players set #trade{pair} krts_ctrl 0',
            f'scoreboard players add #unstable{source} krts_ctrl {BETRAYAL}',
            f'execute if score #unstable{source} krts_ctrl matches {MAX_INSTABILITY + 1}.. run scoreboard players set #unstable{source} krts_ctrl {MAX_INSTABILITY}',
            f'tellraw @s "[RTS] İttifak ihlali. İstikrarsızlık {BETRAYAL} puan arttı."',
            f'function krts:dip/notice/{suffix}')
        for aid, stock, label, amount, limit in AID:
            put(f"ally/transfer/{aid}/{suffix}", *available,
                f'execute unless score {allied} matches 1 run return run tellraw @s "[RTS] Yardım için etkin ittifak gerekir."',
                *([f'execute if score #debt{source} krts_ctrl matches 1.. unless score #g{source} krts_ctrl matches {RESERVE + amount}.. '
                   'run return run tellraw @s "[RTS] Temel ihtiyaç payını korumak için gönderim durdu."'] if aid == "gold" else []),
                f'execute unless score #{stock}{source} krts_ctrl matches {amount}.. run return run tellraw @s "[RTS] Yardım için {amount} {label} gerekir."',
                f'execute unless score #{stock}{target} krts_ctrl matches 0..{limit - amount} run return run tellraw @s "[RTS] Müttefik depoda yeterli yer yok."',
                f'scoreboard players remove #{stock}{source} krts_ctrl {amount}',
                f'scoreboard players add #{stock}{target} krts_ctrl {amount}',
                f'tellraw @s "[RTS] Devlet {target} için {amount} {label} gönderdin."',
                f'tellraw {peer} "[RTS] Devlet {source} sana {amount} {label} gönderdi."')

    generate_offers(put)
    generate_calls(put)


def generate_offers(put):
    put("ally/offer/menu",
        '$execute if score @s krts_slot matches $(responder) run tellraw @s '
        + message("[RTS] Devlet $(caller) ittifak önerdi. ",
                  button("İttifakı kabul et", "krts_ally_reply", "$(token)"),
                  button("Teklifi reddet", "krts_ally_reply", "-$(token)")))
    put("ally/offer/clear",
        '$scoreboard players set #allyOffer$(pair) krts_ctrl 0',
        '$scoreboard players set #allyOfferTime$(pair) krts_ctrl 0',
        '$data remove storage krts:alliance offers.o$(pair)')
    put("ally/offer/reply",
        '$execute unless score @s krts_slot matches $(responder) run return 0',
        '$execute if score #pact$(pair) krts_ctrl matches 1..2 run return run function krts:ally/offer/clear {pair:$(pair)}',
        '$execute unless score #allyOfferTime$(pair) krts_ctrl matches 1.. run return 0',
        '$execute unless score #a$(caller) krts_ctrl matches 1 run return 0',
        '$function krts:ally/offer/clear {pair:$(pair)}',
        'execute if score @s krts_ally_reply matches ..-1 run return run tellraw @s "[RTS] İttifak teklifi reddedildi."',
        '$scoreboard players set #allied$(pair) krts_ctrl 1',
        '$say [RTS] Devlet $(caller) ve Devlet $(responder) ittifak kurdu.')


def generate_calls(put):
    valid = 'function krts:ally/call/valid {caller:$(caller),responder:$(responder),enemy:$(enemy),pair:$(pair),front:$(front),joining:$(joining)}'
    clear = 'function krts:ally/call/clear {caller:$(caller),responder:$(responder)}'
    put("ally/call/valid",
        '$execute unless score #a$(caller) krts_ctrl matches 1 run return 0',
        '$execute unless score #a$(responder) krts_ctrl matches 1 run return 0',
        '$execute unless score #a$(enemy) krts_ctrl matches 1 run return 0',
        '$execute unless score #allied$(pair) krts_ctrl matches 1 run return 0',
        '$execute unless score #pact$(front) krts_ctrl matches 2 run return 0',
        '$execute unless score #pact$(joining) krts_ctrl matches 0 run return 0',
        '$execute if score #trade$(joining) krts_ctrl matches 1.. run return 0',
        '$execute if score #allied$(joining) krts_ctrl matches 1 run return 0',
        f'$execute if score #unstable$(responder) krts_ctrl matches {MAX_INSTABILITY}.. run return 0',
        'return 1')
    put("ally/call/request",
        '$execute unless entity @a[scores={krts_slot=$(responder)},gamemode=!spectator] '
        'run return run tellraw @s "[RTS] Müttefikin çevrimiçi değil."',
        '$execute if score #allyCall$(caller)$(responder) krts_ctrl matches 1.. '
        'run return run tellraw @s "[RTS] Bu müttefike gönderdiğin bir çağrı bekliyor."',
        '$execute store result score #allyValid krts_ctrl run ' + valid,
        'execute unless score #allyValid krts_ctrl matches 1 run return run tellraw @s '
        '"[RTS] Çağrı için etkin savaş, ittifak ve müttefikinin savaşa katılabilmesi gerekir."',
        f'$execute if score seen.$(caller).$(responder).$(enemy) {SEEN} = #allyWar$(front) krts_ctrl '
        'run return run tellraw @s "[RTS] Bu müttefike bu savaş için zaten çağrı gönderdin."',
        'scoreboard players add #allySerial krts_ctrl 1',
        f'$scoreboard players operation seen.$(caller).$(responder).$(enemy) {SEEN} = #allyWar$(front) krts_ctrl',
        '$scoreboard players operation #allyCall$(caller)$(responder) krts_ctrl = #allySerial krts_ctrl',
        f'$scoreboard players set #allyCallTime$(caller)$(responder) krts_ctrl {CALL_SECONDS}',
        '$data modify storage krts:alliance calls.c$(caller)_$(responder) set value '
        '{caller:$(caller),responder:$(responder),enemy:$(enemy),pair:$(pair),front:$(front),joining:$(joining)}',
        '$execute store result storage krts:alliance calls.c$(caller)_$(responder).token int 1 run scoreboard players get #allySerial krts_ctrl',
        '$execute as @a[scores={krts_slot=$(responder)},gamemode=!spectator] '
        'run function krts:ally/call/menu with storage krts:alliance calls.c$(caller)_$(responder)',
        '$tellraw @s "[RTS] Devlet $(responder) için savaş çağrısı gönderildi."')
    put("ally/call/menu",
        '$tellraw @s ' + message("[RTS] Devlet $(caller), Devlet $(enemy) ile savaşına katılmanı istedi. ",
                                  button("Çağrıyı kabul et", "krts_ally_reply", "$(token)"),
                                  button("Çağrıyı reddet · +1 istikrarsızlık", "krts_ally_reply", "-$(token)")))
    put("ally/call/clock",
        '$execute store result score #allyValid krts_ctrl run ' + valid,
        '$execute unless score #allyValid krts_ctrl matches 1 run return run ' + clear,
        '$scoreboard players remove #allyCallTime$(caller)$(responder) krts_ctrl 1',
        '$execute if score #allyCallTime$(caller)$(responder) krts_ctrl matches ..0 run ' + clear)
    put("ally/call/clear",
        '$scoreboard players set #allyCall$(caller)$(responder) krts_ctrl 0',
        '$scoreboard players set #allyCallTime$(caller)$(responder) krts_ctrl 0',
        '$data remove storage krts:alliance calls.c$(caller)_$(responder)')
    put("ally/call/reply",
        '$execute unless score @s krts_slot matches $(responder) run return 0',
        '$execute unless score #allyCallTime$(caller)$(responder) krts_ctrl matches 1.. run return 0',
        '$execute store result score #allyValid krts_ctrl run ' + valid,
        '$execute unless score #allyValid krts_ctrl matches 1 run return run ' + clear,
        '$' + clear,
        '$execute if score @s krts_ally_reply matches ..-1 run return run function krts:ally/call/refuse '
        '{caller:$(caller),responder:$(responder)}',
        '$function krts:dip/notice/$(responder)_$(enemy)')
    put("ally/call/refuse",
        '$scoreboard players add #unstable$(responder) krts_ctrl 1',
        'tellraw @s "[RTS] Müttefikin çağrısını reddettin. İstikrarsızlık 1 arttı."',
        '$tellraw @a[scores={krts_slot=$(caller)},gamemode=!spectator] "[RTS] Devlet $(responder) savaş çağrını reddetti."')
