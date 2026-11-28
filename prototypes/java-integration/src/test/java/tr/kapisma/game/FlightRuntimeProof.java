package tr.kapisma.game;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.AABB;
import tr.kapisma.Kapisma;
/** QA-only console fixture. Real server player and vanilla item-use/motion methods, no fake ledger results. */
public final class FlightRuntimeProof implements ModInitializer {
    public void onInitialize(){
        if(!Boolean.getBoolean("kapisma.arenaProof"))throw new IllegalStateException("QA flag required");
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("arena-flight-proof")
            .requires(s->s.getEntity()==null&&s.hasPermission(4))
            .then(Commands.argument("actor",EntityArgument.player())
                .then(Commands.literal("glide").executes(c->run(EntityArgument.getPlayer(c,"actor"),"glide")))
                .then(Commands.literal("rocket").executes(c->run(EntityArgument.getPlayer(c,"actor"),"rocket")))
                .then(Commands.literal("snapshot").executes(c->run(EntityArgument.getPlayer(c,"actor"),"snapshot"))))));
    }
    private static int run(ServerPlayer p,String op){
        if(!(Kapisma.GM.current instanceof NativeArenaGame g)||!g.controlsFlightRocket(p)||g.phase!=Game.Phase.RUNNING)
            throw new IllegalStateException("wrong QA arena");
        if(op.equals("glide"))p.startFallFlying();
        if(op.equals("rocket")){
            var s=p.getItemInHand(InteractionHand.MAIN_HAND);
            if(!s.is(Items.FIREWORK_ROCKET))throw new IllegalStateException("missing rocket");
            var result=s.use(p.level(),p,InteractionHand.MAIN_HAND);
            System.out.println("FLIGHTQA_USE "+p.getScoreboardName()+" "+result.getResult()+" count="+s.getCount());
        }
        var v=p.getDeltaMovement();
        int rockets=p.serverLevel().getEntitiesOfClass(FireworkRocketEntity.class,new AABB(-100,p.level().getMinBuildHeight(),-100,100,p.level().getMaxBuildHeight(),100)).size();
        System.out.printf(java.util.Locale.ROOT,"FLIGHTQA_STATE %s %s x=%.5f y=%.5f z=%.5f vx=%.5f vy=%.5f vz=%.5f glide=%s ground=%s rockets=%d count=%d%n",
            p.getScoreboardName(),op,p.getX(),p.getY(),p.getZ(),v.x,v.y,v.z,p.isFallFlying(),p.onGround(),rockets,p.getInventory().getItem(0).getCount());
        return 1;
    }
}
