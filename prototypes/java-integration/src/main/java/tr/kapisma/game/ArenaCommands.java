package tr.kapisma.game;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import tr.kapisma.Kapisma;

/** Player identity is always obtained from the command source, never command arguments. */
public final class ArenaCommands {
    private ArenaCommands(){}
    public static void register(){
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player,world,hand,target,hit)->{
            if(!world.isClientSide&&player instanceof net.minecraft.server.level.ServerPlayer p&&Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame game)
                return game.caravanUseEntity(p,target);
            return net.minecraft.world.InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player,world,hand,target,hit)->{
            if(!world.isClientSide&&Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame game&&game.caravanProtects(target))
                return net.minecraft.world.InteractionResult.FAIL;
            return net.minecraft.world.InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player,world,hand)->{
            if(!world.isClientSide&&player instanceof net.minecraft.server.level.ServerPlayer p&&Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame game){
                var result=game.caravanUseHook(p,hand);if(result!=null)return result;
            }
            return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
        });
        CommandRegistrationCallback.EVENT.register((d,registry,env)->d.register(Commands.literal("arena")
            .then(Commands.literal("proof").requires(src->src.getEntity()==null&&src.hasPermission(4)&&Boolean.getBoolean("kapisma.arenaProof"))
                .executes(c->proof(c.getSource())))
            .then(Commands.literal("kervan")
                .then(Commands.literal("yardim").executes(c->run(c.getSource(),"caravan_help",0)))
                .then(Commands.literal("guvenli").executes(c->run(c.getSource(),"caravan_safe",0)))
                .then(Commands.literal("riskli").executes(c->run(c.getSource(),"caravan_risky",0)))
                .then(Commands.literal("birak").executes(c->run(c.getSource(),"caravan_drop",0)))
                .then(Commands.literal("teslim").executes(c->run(c.getSource(),"caravan_deliver",0)))
                .then(Commands.literal("kanca")
                    .then(Commands.literal("otomatik").executes(c->climb(c.getSource(),false)))
                    .then(Commands.literal("manuel").executes(c->climb(c.getSource(),true)))))
            .then(Commands.literal("ipucu").executes(c->run(c.getSource(),"hint",0)))
            .then(Commands.literal("bul").executes(c->run(c.getSource(),"find",0)))
            .then(Commands.literal("kod").then(Commands.argument("deger",IntegerArgumentType.integer(1000,9999))
                .executes(c->run(c.getSource(),"code",IntegerArgumentType.getInteger(c,"deger")))))
            .then(Commands.literal("teklif").then(Commands.argument("jeton",IntegerArgumentType.integer(-1,100))
                .executes(c->run(c.getSource(),"bid",IntegerArgumentType.getInteger(c,"jeton")))))
            .then(Commands.literal("magaza").executes(c->siege(c.getSource(),"shop",0,0)))
            .then(Commands.literal("katalog").executes(c->siege(c.getSource(),"catalog",0,0)))
            .then(Commands.literal("al").then(Commands.argument("urun",IntegerArgumentType.integer(1,4096)).then(Commands.argument("adet",IntegerArgumentType.integer(1,1000)).executes(c->siege(c.getSource(),"buy",IntegerArgumentType.getInteger(c,"urun"),IntegerArgumentType.getInteger(c,"adet"))))))
            .then(Commands.literal("sat").then(Commands.argument("urun",IntegerArgumentType.integer(1,4096)).then(Commands.argument("adet",IntegerArgumentType.integer(1,1000)).executes(c->siege(c.getSource(),"sell",IntegerArgumentType.getInteger(c,"urun"),IntegerArgumentType.getInteger(c,"adet"))))))
            .then(Commands.literal("kit").then(Commands.argument("secim",IntegerArgumentType.integer(1,5)).executes(c->siege(c.getSource(),"kit",IntegerArgumentType.getInteger(c,"secim"),0))))
            .then(Commands.literal("bakiye").executes(c->run(c.getSource(),"balance",0)))));
    }
    private static int climb(CommandSourceStack source,boolean manual){
        var gm=Kapisma.GM;var p=source.getPlayer();
        if(gm==null||gm.current!=null||!(p==null?source.hasPermission(4):gm.canAdmin(p))){
            source.sendFailure(Component.literal("Yalnız lobide host/yönetici değiştirebilir."));return 0;
        }
        gm.settings.set("KERVAN_BASKINI","manualClimb",manual?1:0);gm.settings.save();
        source.sendSuccess(()->Component.literal("Kervan kanca modu: "+(manual?"manuel":"otomatik")),false);return 1;
    }
    private static int siege(CommandSourceStack source,String action,int product,int count){
        var p=source.getPlayer();
        if(p!=null&&NativeArenaGame.enabled()&&Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame game&&game.siegeCommand(p,action,product,count))return 1;
        source.sendFailure(Component.literal("İşlem reddedildi."));return 0;
    }
    private static int proof(CommandSourceStack source){
        var g=Kapisma.GM==null?null:Kapisma.GM.current;
        String s=g instanceof NativeArenaGame arena?arena.proofState():g instanceof RotatingOutpostGame outpost?outpost.proofState():"none";
        source.sendSuccess(()->Component.literal("ARENA_PROOF "+s),false);return 1;
    }
    private static int run(CommandSourceStack source,String action,int value){
        var p=source.getPlayer();
        if(p!=null&&NativeArenaGame.enabled()&&Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame game&&game.command(p,action,value)){
            if(action.equals("bid"))p.sendSystemMessage(Component.literal(value==-1?"Teklif geri çekme isteği alındı.":"Gizli teklif isteği alındı."));
            return 1;
        }
        source.sendFailure(Component.literal("Bu işlem şu anda kullanılamaz."));
        return 0;
    }
}