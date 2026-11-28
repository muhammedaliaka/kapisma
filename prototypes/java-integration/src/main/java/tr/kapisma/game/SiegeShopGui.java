package tr.kapisma.game;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.GuiHelpers;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import tr.kapisma.Kapisma;
import tr.kapisma.Msg;

/** Virtual shop icons never become inventory items. Actual transactions use the shared ledger. */
public final class SiegeShopGui extends SimpleGui {
    private final NativeArenaGame game;
    private final ServerPlayer actor;
    private int amount=1,lastBalance=-1;
    private long nextClick;
    private String lastStock="";
    private static final int[] AMOUNTS={1,8,16,64};
    private static final String[] LABELS={"Meşe tahtası","Kırık taş","Taş tuğla","Demir blok","Obsidyen","Cam","Tuğla","Düzgün taş"};
    SiegeShopGui(NativeArenaGame game,ServerPlayer actor){
        super(MenuType.GENERIC_9x3,actor,false);this.game=game;this.actor=actor;
        setTitle(Component.literal("Kuşatma Mağazası"));setLockPlayerInventory(true);refresh();
    }
    public static boolean active(ServerPlayer actor){
        return GuiHelpers.getCurrentGui(actor) instanceof SiegeShopGui g&&g.actor==actor&&g.isOpen()&&Kapisma.GM!=null&&Kapisma.GM.current==g.game;
    }
    private boolean live(){return isOpen()&&GuiHelpers.getCurrentGui(actor)==this&&game.siegeShopReady(actor);}
    private static Component label(String s,ChatFormatting color){return Component.literal(s).withStyle(st->st.withItalic(false).withColor(color));}
    private String stockKey(){var s=new StringBuilder();for(int i=1;i<=SiegeShop.PRODUCTS.size();i++)s.append(game.siegeStock(actor,i)).append(',');return s.toString();}
    private void refresh(){
        int balance=game.siegeBalance(actor);lastBalance=balance;lastStock=stockKey();
        for(int i=0;i<SiegeShop.PRODUCTS.size();i++){
            int id=i+1;var product=SiegeShop.PRODUCTS.get(i);
            int owned=game.siegeStock(actor,id),price=product.price()*amount;
            setSlot(i,new GuiElementBuilder(product.item()).setName(label(LABELS[i]+" · "+product.price()+" kredi",ChatFormatting.WHITE))
                .addLoreLine(label("Sol tık: "+amount+" al · "+price+" kredi",balance>=price?ChatFormatting.GREEN:ChatFormatting.RED))
                .addLoreLine(label("Sağ tık: "+amount+" sat · "+price+" kredi",owned>=amount?ChatFormatting.GREEN:ChatFormatting.GRAY))
                .addLoreLine(label("Satın alınmış stok: "+owned,ChatFormatting.AQUA))
                .setCallback((idx,type,action)->{
                    if(!live()||(type!=ClickType.MOUSE_LEFT&&type!=ClickType.MOUSE_RIGHT))return;
                    long now=actor.getServer().getTickCount();if(now<nextClick)return;nextClick=now+2;
                    boolean ok=game.siegeCommand(actor,type==ClickType.MOUSE_LEFT?"buy":"sell",id,amount);
                    if(!ok)Msg.tell(actor,"Alışveriş reddedildi: kredi, stok veya envanter.");
                    refresh();
                }));
        }
        for(int i=0;i<AMOUNTS.length;i++){
            int n=AMOUNTS[i];
            setSlot(10+i,new GuiElementBuilder(Items.PAPER,n).setName(label("Adet: "+n,amount==n?ChatFormatting.GREEN:ChatFormatting.GRAY)).glow(amount==n)
                .setCallback((idx,type,action)->{if(live()&&type==ClickType.MOUSE_LEFT){amount=n;refresh();}}));
        }
        setSlot(18,new GuiElementBuilder(Items.GOLD_INGOT).setName(label("Takım kredisi: "+balance,ChatFormatting.GOLD)));
        String[] kits={"Kuşatmacı","Savaşçı","Okçu","Sızmacı","Destekçi"};
        var icons=new net.minecraft.world.item.Item[]{Items.IRON_AXE,Items.IRON_SWORD,Items.BOW,Items.CROSSBOW,Items.SHIELD};
        for(int i=0;i<kits.length;i++){
            int id=i+1;setSlot(19+i,new GuiElementBuilder(icons[i]).setName(label(kits[i],ChatFormatting.AQUA))
                .addLoreLine(label("İnşa aşamasında sol tıkla seç",ChatFormatting.GRAY))
                .glow(game.siegeSelectedKit(actor)==id).setCallback((idx,type,action)->{
                    if(live()&&type==ClickType.MOUSE_LEFT){if(!game.siegeCommand(actor,"kit",id,0))Msg.tell(actor,"Kit yalnızca inşa aşamasında seçilir.");refresh();}
                }));
        }
        setSlot(26,new GuiElementBuilder(Items.BARRIER).setName(label("Kapat",ChatFormatting.RED))
            .setCallback((idx,type,action)->{if(live()&&type==ClickType.MOUSE_LEFT)close();}));
    }
    @Override public void onTick(){
        if(!live()){close();return;}
        if(game.siegeBalance(actor)!=lastBalance||!stockKey().equals(lastStock))refresh();
    }
}