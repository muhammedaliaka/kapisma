package tr.kapisma.game;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import tr.kapisma.arena.CaravanMatchRules;

/** Match-scoped transient modifier; preserves all unrelated attribute modifiers.
 * Invoke on server thread for each participating player each tick and at all exit hooks.
 */
public final class CaravanSpeedAdapter {
    private static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("kapisma","caravan_cargo_speed");
    private CaravanSpeedAdapter(){}
    private static void requireThread(ServerPlayer p){
        if(p.getServer()==null||!p.getServer().isSameThread())throw new IllegalStateException("wrong thread");
    }
    public static void sync(ServerPlayer p,CaravanMatchRules rules,boolean inOwnedArena){
        requireThread(p);
        if(!inOwnedArena||rules==null||!rules.carrying(p.getUUID())||!p.isAlive()||p.isSpectator()){
            clear(p);return;
        }
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed==null)return;
        if(p.isPassenger())p.stopRiding();
        double amount=rules.movementMultiplier(p.getUUID())-1;
        var existing=speed.getModifier(ID);
        if(existing!=null&&Double.compare(existing.amount(),amount)==0&&existing.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)return;
        speed.removeModifier(ID);
        speed.addTransientModifier(new AttributeModifier(ID,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public static void clear(ServerPlayer p){
        requireThread(p);
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null)speed.removeModifier(ID);
    }
}
