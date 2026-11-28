package tr.kapisma.mixin;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Version gate avoids loading optional Analog or voice API when absent. Unknown versions fail closed at packet policy. */
public final class AnalogVoiceMixinGate implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage){}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){
        return Boolean.getBoolean("kapisma.experimentalAnalogVoice")&&supported();
    }
    public static boolean supported(){
        var loader=FabricLoader.getInstance();
        return loader.isModLoaded("voicechat")&&loader.getModContainer("analog")
            .map(c->"1.2.2+1.21".equals(c.getMetadata().getVersion().getFriendlyString())).orElse(false);
    }
    public void acceptTargets(Set<String> mine,Set<String> other){}
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}