package tr.kapisma.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import tr.kapisma.Msg;

/**
 * Canli pusula: oyuna ozel pusula esyasi. Hedef listesini oyun belirler (takim arkadasi, rakip, merkez...);
 * sag tik hedefi sirayla degistirir (Shift + sag tik: geri). Ibre sik sik (her oyunun kendi araliginda) yenilenir,
 * pusula elde tutulurken aksiyon cubugunda hedef adi ve yon oku canli yazar.
 */
public final class Compass {
	public static final String TAG = "kapisma_compass";

	public record Spot(BlockPos pos, ResourceKey<Level> dim, String playerName) {
		public Spot(BlockPos pos, ResourceKey<Level> dim) {
			this(pos, dim, null);
		}
	}

	public interface Finder {
		/** holder icin hedefin simdiki konumu; yoksa (cevrimdisi, olu...) null. */
		Spot find(ServerPlayer holder);
	}

	/** id: secimi hatirlamak icin kararli anahtar; label: oyuncuya gorunen ad. */
	public record Opt(String id, String label, Finder finder) {
		public String displayLabel(Spot spot) {
			return spot != null && spot.playerName() != null ? spot.playerName() : label;
		}
	}

	private final Map<UUID, String> sel = new HashMap<>();

	public static boolean isCompass(ItemStack s) {
		if (!s.is(Items.COMPASS)) return false;
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		return d != null && d.contains(TAG) && d.copyTag().getBoolean(TAG);
	}

	public static ItemStack item() {
		ItemStack s = new ItemStack(Items.COMPASS);
		s.set(DataComponents.CUSTOM_NAME, Component.literal("Pusula").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GOLD).withBold(true)));
		s.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal("Sağ tık: sıradaki hedef").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.YELLOW)),
			Component.literal("Shift + sağ tık: önceki hedef").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.YELLOW)),
			Component.literal("Elinde tutunca hedefin adı ve yönü görünür.").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GRAY)))));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(TAG, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.empty(), false));
		return s;
	}

	public static boolean has(ServerPlayer p) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) if (isCompass(inv.getItem(i))) return true;
		return false;
	}

	private static boolean holding(ServerPlayer p) {
		return isCompass(p.getMainHandItem()) || isCompass(p.getOffhandItem());
	}

	private int indexOf(ServerPlayer p, List<Opt> opts) {
		String id = sel.get(p.getUUID());
		for (int i = 0; i < opts.size(); i++) if (opts.get(i).id().equals(id)) return i;
		return 0;
	}

	/** Oyuncunun simdiki secili hedefi (liste bossa null). */
	public Opt selected(ServerPlayer p, List<Opt> opts) {
		return opts.isEmpty() ? null : opts.get(indexOf(p, opts));
	}

	/** Hedefi dir (+1 sonraki, -1 onceki) kadar kaydirir. */
	public void cycle(ServerPlayer p, List<Opt> opts, int dir) {
		if (opts.isEmpty()) {
			Msg.tell(p, "Bu modda pusulanın hedefi yok.");
			return;
		}
		int n = Math.floorMod(indexOf(p, opts) + dir, opts.size());
		Opt next = opts.get(n);
		sel.put(p.getUUID(), next.id());
		Msg.sound(p, SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.4f);
		Msg.actionbar(p, Component.literal("Pusula hedefi: ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(next.displayLabel(next.finder().find(p))).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
			.append(Component.literal("  (" + (n + 1) + "/" + opts.size() + ")").withStyle(ChatFormatting.DARK_GRAY)));
	}

	/** Pusulalarin ibresini gunceller; showText: elinde tutanlara canli yazi. */
	public void update(Collection<ServerPlayer> players, Function<ServerPlayer, List<Opt>> optsOf, boolean showText) {
		for (ServerPlayer p : players) {
			if (p.isSpectator()) continue;
			var inv = p.getInventory();
			List<Integer> slots = null;
			for (int i = 0; i < inv.getContainerSize(); i++) if (isCompass(inv.getItem(i))) {
				if (slots == null) slots = new ArrayList<>();
				slots.add(i);
			}
			if (slots == null) continue;
			List<Opt> opts = optsOf.apply(p);
			Opt o = opts.isEmpty() ? null : opts.get(indexOf(p, opts));
			Spot sp = o == null ? null : o.finder().find(p);
			var tr = new LodestoneTracker(sp == null ? Optional.empty() : Optional.of(GlobalPos.of(sp.dim(), sp.pos())), false);
			for (int i : slots) {
				ItemStack s = inv.getItem(i);
				if (!tr.equals(s.get(DataComponents.LODESTONE_TRACKER))) s.set(DataComponents.LODESTONE_TRACKER, tr);
			}
			if (showText && holding(p)) Msg.actionbar(p, text(p, o, sp, opts.size()));
		}
	}

	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private static MutableComponent text(ServerPlayer p, Opt o, Spot sp, int count) {
		MutableComponent c = Component.literal("◆ ").withStyle(ChatFormatting.GOLD);
		if (o == null) return c.append(Component.literal("Pusulanın hedefi yok").withStyle(ChatFormatting.GRAY));
		c.append(Component.literal(o.displayLabel(sp)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (sp == null) {
			c.append(Component.literal("   bulunamadı").withStyle(ChatFormatting.RED));
		} else if (sp.dim() != p.level().dimension()) {
			c.append(Component.literal("   başka boyutta").withStyle(ChatFormatting.RED));
		} else {
			double dx = sp.pos().getX() + 0.5 - p.getX(), dz = sp.pos().getZ() + 0.5 - p.getZ();
			double target = Math.toDegrees(Math.atan2(-dx, dz));
			double rel = Math.floorMod((long) Math.round(target - p.getYRot()), 360L);
			String arrow = ARROWS[(int) Math.round(rel / 45.0) % 8];
			double dy = sp.pos().getY() - p.getY();
			String vert = dy > 8 ? " ▲" : dy < -8 ? " ▼" : "";
			c.append(Component.literal("   " + arrow + vert).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
		}
		if (count > 1) c.append(Component.literal("   sağ tık: sonraki").withStyle(ChatFormatting.DARK_GRAY));
		return c;
	}
}
