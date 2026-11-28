"""Generate a source-only integration overlay from current KOD sources. Never writes original files."""
from pathlib import Path
import hashlib,json,difflib
BASE=Path(__file__).resolve().parent
ROOT=BASE.parents[1]
SOURCE=ROOT/"mod/kapisma/src/main/java"
OVERLAY=BASE/"overlay"
FILES={}
def read(rel):
    path=SOURCE/rel
    raw=path.read_bytes()
    FILES[rel]=(raw,raw.decode("utf-8").replace("\r\n","\n"))
    return FILES[rel][1]
def replace(s,a,b,expected=1):
    if s.count(a)!=expected:raise RuntimeError(f"anchor count {s.count(a)} expected {expected}: {a!r}")
    return s.replace(a,b)
def save(rel,s):
    assert (SOURCE/rel).read_bytes()==FILES[rel][0],"KOD changed concurrently"
    path=OVERLAY/rel;path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(s,encoding="utf-8",newline="\n")
    patches.append("".join(difflib.unified_diff(FILES[rel][1].splitlines(keepends=True),s.splitlines(keepends=True),fromfile="a/"+rel,tofile="b/"+rel)))
    manifest[rel]={"base_sha256":hashlib.sha256(FILES[rel][0]).hexdigest(),"overlay_sha256":hashlib.sha256(path.read_bytes()).hexdigest()}
patches=[];manifest={}
rel="tr/kapisma/game/GameManager.java";s=read(rel)
s=replace(s,'if (!seeds.selectable(settings.seedClass))','if (!NativeArenaGame.isNew(settings.game) && !seeds.selectable(settings.seedClass))')
s=replace(s,'case "KATIL" -> new ManhuntGame(this, true);','case "KATIL" -> new ManhuntGame(this, true);\n\t\t\tcase RotatingOutpostGame.ID -> new RotatingOutpostGame(this);\n\t\t\tcase "KERVAN_BASKINI" -> new NativeArenaGame(this, NativeArenaGame.Mode.CARAVAN);\n\t\t\tcase "YASAYAN_HARITA" -> new NativeArenaGame(this, NativeArenaGame.Mode.CLUES);\n\t\t\tcase "INSA_ET_SAVUN" -> new NativeArenaGame(this, NativeArenaGame.Mode.BUILD);\n\t\t\tcase "KIT_IHALESI" -> new NativeArenaGame(this, NativeArenaGame.Mode.AUCTION);\n\t\t\tcase "ALCAK_IRTIFA" -> new NativeArenaGame(this, NativeArenaGame.Mode.FLIGHT);\n\t\t\tcase "KIRIK_TELSIZ" -> new NativeArenaGame(this, NativeArenaGame.Mode.RADIO);')
s=replace(s,'if (ps.isEmpty()) return "Oyuncu yok.";','if (ps.isEmpty()) return "Oyuncu yok.";\n\t\tif (NativeArenaGame.isNew(settings.game) && !NativeArenaGame.enabled()) return "Deneysel arenalar devre dışı.";\n\t\tif (NativeArenaGame.isNew(settings.game) && (ps.size() < 2 || ps.size() > 8)) return "Arena oyunları için 2–8 oyuncu gerekli.";')
s=replace(s,'g.stop("Hata: " + e.getMessage());','g.stop("Hata: " + e.getMessage());\n\t\t\tif ((g instanceof RotatingOutpostGame arena && arena.cleanupPending()) || (g instanceof NativeArenaGame n && n.cleanupPending())) current = g;')
s=replace(s,'if (err != null) return err;','if (err != null) {\n\t\t\tif ((g instanceof RotatingOutpostGame arena && arena.cleanupPending()) || (g instanceof NativeArenaGame n && n.cleanupPending())) current = g;\n\t\t\treturn err;\n\t\t}')
s=replace(s,'current.stop(reason);','current.stop(reason);\n\t\t\tif ((current instanceof RotatingOutpostGame arena && arena.cleanupPending()) || (current instanceof NativeArenaGame n && n.cleanupPending())) return;')
s=replace(s,'if (ps.isEmpty()) return "Oyuncu yok.";','if (ps.isEmpty()) return "Oyuncu yok.";\n\t\ttry { for (var p : ps) if (ArenaRecovery.unresolved(server, p.getUUID())) return "Önce arena dönüş kaydı tamamlanmalı."; } catch (java.io.IOException ex) { return "Arena dönüş günlüğü okunamadı."; }')
s=replace(s,'if (p != null && (current == null || !current.contains(p.getUUID()))) Lobby.send(server, p);','''if (p != null && (current == null || !current.contains(p.getUUID()))) {
                        if (ArenaRecovery.recoverOrLobby(server, p)) {}
                        else p.connection.disconnect(net.minecraft.network.chat.Component.literal("Arena dönüşü doğrulanamadı; hosta bildir."));
                    }''')
s=replace(s,'Lobby.send(server, p);\n\t\t\t\tOriginService.reset(server, p);','if (!ArenaRecovery.recoverOrLobby(server, p)) p.connection.disconnect(net.minecraft.network.chat.Component.literal("Arena dönüşü doğrulanamadı; hosta bildir."));\n\t\t\t\telse OriginService.reset(server, p);')
save(rel,s)
rel="tr/kapisma/config/Rules.java";s=read(rel)
s=replace(s,'\tpublic static List<RuleDef> of(String game) {','''\tpublic static final List<RuleDef> DONEN_KARAKOL = List.of(
        RuleDef.num("rotationSeconds", "Karakol süresi (sn)", "Aktif bölgenin değişim süresi.", 30, 10, 120, 5, Items.CLOCK),
        RuleDef.num("scoreGoal", "Kazanma puanı", "Bir takımın kazanmak için gereken puanı.", 40, 5, 200, 5, Items.GOLD_BLOCK),
        RuleDef.flag("teamVoice", "Takım ses kanalı", "Takım ses grubu; ses izolasyonu kabulü tamamlanmadan kapalı tutulur.", false, Items.NOTE_BLOCK)
    );
    public static final List<RuleDef> ARENA = List.of(RuleDef.flag("teamVoice", "Takım ses kanalı", "Deneysel ses koruması doğrulanana kadar kapalı.", false, Items.NOTE_BLOCK));

\tpublic static List<RuleDef> of(String game) {''')
s=replace(s,'case "ESYA" -> ESYA;','case "ESYA" -> ESYA;\n\t\t\tcase "DONEN_KARAKOL" -> DONEN_KARAKOL;\n\t\t\tcase "KERVAN_BASKINI", "YASAYAN_HARITA", "INSA_ET_SAVUN", "KIT_IHALESI", "ALCAK_IRTIFA", "KIRIK_TELSIZ" -> ARENA;')
save(rel,s)
rel="tr/kapisma/config/Settings.java";s=read(rel)
s=replace(s,'originMode.put("ESYA", "OFF");','originMode.put("ESYA", "OFF");\n\t\toriginMode.put("DONEN_KARAKOL", "OFF");\n\t\toriginMode.put("KERVAN_BASKINI", "OFF");\n\t\toriginMode.put("YASAYAN_HARITA", "OFF");\n\t\toriginMode.put("INSA_ET_SAVUN", "OFF");\n\t\toriginMode.put("KIT_IHALESI", "OFF");\n\t\toriginMode.put("ALCAK_IRTIFA", "OFF");\n\t\toriginMode.put("KIRIK_TELSIZ", "OFF");')
s=replace(s,'s.originMode.putIfAbsent("ESYA", "OFF");','s.originMode.putIfAbsent("ESYA", "OFF");\n\t\t\t\t\ts.originMode.putIfAbsent("DONEN_KARAKOL", "OFF");\n\t\t\t\t\ts.originMode.putIfAbsent("KERVAN_BASKINI", "OFF");\n\t\t\t\t\ts.originMode.putIfAbsent("YASAYAN_HARITA", "OFF");\n\t\t\t\t\ts.originMode.putIfAbsent("INSA_ET_SAVUN", "OFF");\n\t\t\t\t\ts.originMode.putIfAbsent("KIT_IHALESI", "OFF");\n\t\t\t\t\ts.originMode.putIfAbsent("ALCAK_IRTIFA", "OFF");\n\t\t\t\t\ts.originMode.putIfAbsent("KIRIK_TELSIZ", "OFF");')
save(rel,s)
rel="tr/kapisma/ui/MenuService.java";s=read(rel)
s=replace(s,'case "KATIL" -> "Katil Kim";','case "KATIL" -> "Katil Kim";\n\t\t\tcase "DONEN_KARAKOL" -> "Dönen Karakol";\n\t\t\tcase "KERVAN_BASKINI" -> "Kervan Baskını";\n\t\t\tcase "YASAYAN_HARITA" -> "Yaşayan Harita";\n\t\t\tcase "INSA_ET_SAVUN" -> "İnşa Et, Savun";\n\t\t\tcase "KIT_IHALESI" -> "Kit İhalesi";\n\t\t\tcase "ALCAK_IRTIFA" -> "Alçak İrtifa";\n\t\t\tcase "KIRIK_TELSIZ" -> "Kırık Telsiz";')
s=replace(s,'String[] games = {"UHC", "BINGO", "ESYA", "MANHUNT", "KATIL"};','String[] games = {"UHC", "BINGO", "ESYA", "MANHUNT", "KATIL", "DONEN_KARAKOL", "KERVAN_BASKINI", "YASAYAN_HARITA", "INSA_ET_SAVUN", "KIT_IHALESI", "ALCAK_IRTIFA", "KIRIK_TELSIZ"};')
s=replace(s,'Items.CHEST, Items.COMPASS, Items.IRON_SWORD};','Items.CHEST, Items.COMPASS, Items.IRON_SWORD, Items.GOLD_BLOCK, Items.HAY_BLOCK, Items.MAP, Items.OAK_PLANKS, Items.GOLD_INGOT, Items.ELYTRA, Items.NOTE_BLOCK};')
s=replace(s,'gizli katil masumları avlar. Sesli sohbet."};','gizli katil masumları avlar. Sesli sohbet.",\n\t\t\t"İki takım, sırayla etkin üç karakol. Çekişmeli alan puan vermez.", "Kervanı koru veya durdur.", "Takımının sıradaki ipucunu bul.", "İnşa ve hücum rolleri değişir.", "Gizli jeton teklifleri, ardından PvP.", "Dört sıralı Elytra geçidi.", "Özel sinyal parçalarını birleştir."};')
s=replace(s,'int[] slots = {9, 11, 13, 15, 17};','int[] slots = {9, 11, 13, 15, 17, 18, 19, 20, 21, 22, 23, 24};')
s=replace(s,'String err = mgr.start();\n\t\t\t\t\tgui.close();','boolean outpost = tr.kapisma.game.NativeArenaGame.isNew(mgr.settings.game);\n\t\t\t\t\tif (outpost) gui.close();\n\t\t\t\t\tString err = mgr.start();\n\t\t\t\t\tif (!outpost) gui.close();')
s=replace(s,'final String g = games[i];','final String g = games[i];\n\t\t\tif (tr.kapisma.game.NativeArenaGame.isNew(g) && !tr.kapisma.game.NativeArenaGame.enabled()) continue;')
save(rel,s)
rel="tr/kapisma/Cmds.java";s=read(rel)
s=replace(s,'"UHC", "BINGO", "ESYA", "MANHUNT", "KATIL"','"UHC", "BINGO", "ESYA", "MANHUNT", "KATIL", "DONEN_KARAKOL", "KERVAN_BASKINI", "YASAYAN_HARITA", "INSA_ET_SAVUN", "KIT_IHALESI", "ALCAK_IRTIFA", "KIRIK_TELSIZ"',3)
s=replace(s,'UHC|BINGO|ESYA|MANHUNT|KATIL>','UHC|BINGO|ESYA|MANHUNT|KATIL|DONEN_KARAKOL|KERVAN_BASKINI|YASAYAN_HARITA|INSA_ET_SAVUN|KIT_IHALESI|ALCAK_IRTIFA|KIRIK_TELSIZ>',3)
s=replace(s,'Geçerli: UHC, BINGO, ESYA, MANHUNT, KATIL','Geçerli: UHC, BINGO, ESYA, MANHUNT, KATIL, DONEN_KARAKOL, KERVAN_BASKINI, YASAYAN_HARITA, INSA_ET_SAVUN, KIT_IHALESI, ALCAK_IRTIFA, KIRIK_TELSIZ')
s=replace(s,'gm().settings.game = g;','if (tr.kapisma.game.NativeArenaGame.isNew(g) && !tr.kapisma.game.NativeArenaGame.enabled()) return say(c, "Deneysel arenalar devre dışı.");\n\t\t\t\t\tgm().settings.game = g;')
save(rel,s)
rel="tr/kapisma/Kapisma.java";s=read(rel)
s=replace(s,'''net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) ->
			!countdownPlayer(player) && (GM == null || !(GM.current instanceof tr.kapisma.game.BingoGame game) || !game.protectedPortal(world, pos)));''','''net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (player instanceof ServerPlayer sp && GM != null && GM.current instanceof tr.kapisma.game.NativeArenaGame arena && arena.arenaLevel(world)) {
                arena.breakBlock(sp, net.minecraft.world.InteractionHand.MAIN_HAND, pos);
                return false;
            }
            return !countdownPlayer(player) && (GM == null || !(GM.current instanceof tr.kapisma.game.BingoGame game) || !game.protectedPortal(world, pos));
        });''')
s=replace(s,'''net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
			!world.isClientSide && countdownPlayer(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);''','''net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClientSide && player instanceof ServerPlayer sp && GM != null && GM.current instanceof tr.kapisma.game.NativeArenaGame arena)
                return arena.useBlock(sp, hand, hit);
            return !world.isClientSide && countdownPlayer(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS;
        });
        tr.kapisma.game.ArenaCommands.register();''')
save(rel,s)
rel="tr/kapisma/voice/VoicePolicy.java";s=read(rel)
s=replace(s,'boolean deepVoice) {','boolean deepVoice, Object epoch, boolean radioOnly) {')
s=replace(s,'\tVoicePolicy {','''\tVoicePolicy(Set<UUID> participants, Set<UUID> spectators, boolean killer, boolean voice, boolean deepVoice) {
        this(participants, spectators, killer, voice, deepVoice, new Object(), false);
    }
    VoicePolicy {''')
s=replace(s,'participants = Set.copyOf(participants);','java.util.Objects.requireNonNull(epoch);\n\t\tparticipants = Set.copyOf(participants);')
save(rel,s)
rel="tr/kapisma/voice/KapismaVoice.java";s=read(rel)
s=replace(s,'private static volatile VoicePolicy policy = VoicePolicy.LOBBY;','private static volatile VoicePolicy policy = VoicePolicy.LOBBY;\n    private static Game epochGame;\n    private static Object epochToken = VoicePolicy.LOBBY.epoch();')
s=replace(s,'if (g == null || g.isClosed()) { policy = VoicePolicy.LOBBY; return; }','''Game active = g == null || g.isClosed() ? null : g;
        if (active != epochGame) { epochGame = active; epochToken = new Object(); }
        VoiceRouteScope.publishEpoch(epochToken);
        if (active == null) { policy = new VoicePolicy(java.util.Set.of(), java.util.Set.of(), false, true, false, epochToken, false); return; }''')
s=replace(s,'killer && gm.settings.flag("KATIL", "deepVoice"));','killer && gm.settings.flag("KATIL", "deepVoice"), epochToken, "KIRIK_TELSIZ".equals(g.id()));')
old='''\tprivate void isolateSpectator(SoundPacketEvent<?> event) {
\t\tvar sender = event.getSenderConnection();
\t\tvar receiver = event.getReceiverConnection();
\t\tVoicePolicy snapshot = policy;
\t\tif (sender != null && receiver != null && sender.getPlayer() != null && receiver.getPlayer() != null
\t\t\t&& !snapshot.permits(sender.getPlayer().getUuid(), receiver.getPlayer().getUuid())) event.cancel();
\t}'''
new='''\tprivate void isolateSpectator(SoundPacketEvent<?> event) {
        var sender = event.getSenderConnection();
        var receiver = event.getReceiverConnection();
        UUID from = sender == null || sender.getPlayer() == null ? null : sender.getPlayer().getUuid();
        UUID to = receiver == null || receiver.getPlayer() == null ? null : receiver.getPlayer().getUuid();
        VoicePolicy snapshot = policy;
        if (!Boolean.getBoolean("kapisma.experimentalAnalogVoice") || !SoundPacketEvent.SOURCE_PLUGIN.equals(event.getSource())) {
            if (snapshot.radioOnly() && !VoiceRouteScope.permitsOrdinaryRadio(snapshot.participants(), snapshot.spectators(), from, to)) event.cancel();
            else if (from != null && to != null && !snapshot.permits(from, to)) event.cancel();
            return;
        }
        VoiceRouteScope.Route route = null;
        if (event.getPacket() instanceof de.maxhenkel.voicechat.api.packets.SoundPacket packet && to != null) {
            var key = new VoiceRouteScope.Key(packet.getSender(), packet.getChannelId(), packet.getSequenceNumber());
            if (event instanceof LocationalSoundPacketEvent)
                route = VoiceRouteScope.current(to, key, VoiceRouteScope.Kind.ANALOG);
            else if (event instanceof StaticSoundPacketEvent)
                route = VoiceRouteScope.current(to, key, VoiceRouteScope.Kind.SPECTATOR_RELAY);
        }
        if (!(snapshot.radioOnly() ? VoiceRouteScope.permitsRadioPlugin(snapshot.participants(), snapshot.spectators(), from, to, route)\n                : VoiceRouteScope.permitsPlugin(snapshot.participants(), snapshot.spectators(), from, to, route))
                || (route != null && (route.epoch() != snapshot.epoch() || !snapshot.micEnabled(route.from()) || !snapshot.micEnabled(route.carrier())))) event.cancel();
    }'''
s=replace(s,old,new)
s=replace(s,'if (c != null && c.isConnected()) api.sendStaticSoundPacketTo(c, packet);','''if (c != null && c.isConnected()) {
                var key = new VoiceRouteScope.Key(packet.getSender(), packet.getChannelId(), packet.getSequenceNumber());
                VoiceRouteScope.send(VoiceRouteScope.Kind.SPECTATOR_RELAY, id, other, key,
                    () -> api.sendStaticSoundPacketTo(c, packet));
            }''')
save(rel,s)
# Resource overlay is separate from the original KOD resource directory.
res=ROOT/"mod/kapisma/src/main/resources/fabric.mod.json"
raw=res.read_bytes();original=raw.decode("utf-8").replace("\r\n","\n")
updated=replace(original,'"mixins": ["kapisma.mixins.json"]','"mixins": ["kapisma.mixins.json", "kapisma.analog-voice.mixins.json", "kapisma.arena.mixins.json"]')
resources=BASE/"overlay-resources";resources.mkdir(exist_ok=True)
(resources/"fabric.mod.json").write_text(updated,encoding="utf-8",newline="\n")
config={"required":True,"minVersion":"0.8","package":"tr.kapisma.mixin","compatibilityLevel":"JAVA_21",
        "plugin":"tr.kapisma.mixin.AnalogVoiceMixinGate","mixins":["AnalogVoiceScopeMixin","AnalogMicrophoneScopeMixin"],"injectors":{"defaultRequire":1}}
(resources/"kapisma.analog-voice.mixins.json").write_text(json.dumps(config,indent=2)+"\n",encoding="utf-8")
(resources/"kapisma.arena.mixins.json").write_text(json.dumps({"required":True,"minVersion":"0.8","package":"tr.kapisma.mixin","compatibilityLevel":"JAVA_21","mixins":["ArenaTeleportMixin","ArenaPlayerEquipmentMixin","ArenaContainerEquipmentMixin","ArenaItemPickupMixin","ArenaRecipeEquipmentMixin","ArenaArrowPickupMixin"],"injectors":{"defaultRequire":1}},indent=2)+"\n",encoding="utf-8")
assert res.read_bytes()==raw
manifest["resources/fabric.mod.json"]={"base_sha256":hashlib.sha256(raw).hexdigest(),
    "overlay_sha256":hashlib.sha256((resources/"fabric.mod.json").read_bytes()).hexdigest()}
patches.append("".join(difflib.unified_diff(original.splitlines(keepends=True),updated.splitlines(keepends=True),
    fromfile="a/resources/fabric.mod.json",tofile="b/resources/fabric.mod.json")))
for rel,(raw,_) in FILES.items():assert (SOURCE/rel).read_bytes()==raw,rel
(BASE/"integration.patch").write_text("".join(patches),encoding="utf-8",newline="\n")
(BASE/"overlay-manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
print("PASS staged",len(manifest),"hooks; original KOD sources unchanged")