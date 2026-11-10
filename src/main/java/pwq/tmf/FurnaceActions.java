package pwq.tmf;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;

public final class FurnaceActions {
	private static final int INPUT = 0, FUEL = 1, OUTPUT = 2;

	private FurnaceActions() {}

	public static boolean accepts(AbstractFurnaceBlockEntity f, ItemStack held) {
		if (Blacklist.isBlocked(held)) return false;
		return isSmeltable(f, held) || f.canPlaceItem(FUEL, held);
	}

	private static boolean isSmeltable(AbstractFurnaceBlockEntity f, ItemStack held) {
		if (!(f.getLevel() instanceof ServerLevel level)) return false;
		if (f instanceof BlastFurnaceBlockEntity) return hasRecipe(RecipeType.BLASTING, held, level);
		if (f instanceof SmokerBlockEntity) return hasRecipe(RecipeType.SMOKING, held, level);
		return hasRecipe(RecipeType.SMELTING, held, level);
	}

	private static <T extends Recipe<SingleRecipeInput>> boolean hasRecipe(RecipeType<T> type, ItemStack held, ServerLevel level) {
		return level.recipeAccess().getRecipeFor(type, new SingleRecipeInput(held), level).isPresent();
	}

	public static void useWithItem(Player player, AbstractFurnaceBlockEntity furnace, ItemStack held) {
		int slot = isSmeltable(furnace, held) ? INPUT : FUEL;
		int moved = insert(furnace, slot, held);
						if (moved == 0) {
			actionBar(player, Component.translatable("tmf.actionbar.slot_occupied")
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		ItemStack movedStack = held.copyWithCount(moved);
		if (!player.getAbilities().instabuild) held.shrink(moved);
		furnace.setChanged();
		feedback(furnace, player, movedStack, true);
	}

	public static void takeOutput(Player player, AbstractFurnaceBlockEntity furnace) {
		// 1. переплавленное, всегда первым
		ItemStack out = takeSlot(player, furnace, OUTPUT);
		if (!out.isEmpty()) {
			if (player instanceof ServerPlayer sp) {
				furnace.awardUsedRecipesAndPopExperience(sp);
			}
			feedback(furnace, player, out, false);
			return;
		}
		// исключение для лавового ведра: выход, ведро, вход
		if (furnace.getItem(FUEL).is(Items.BUCKET)) {
			report(player, furnace, takeSlot(player, furnace, FUEL));
			return;
		}
		// обычный порядок: выход, вход, топливо
		if (!report(player, furnace, takeSlot(player, furnace, INPUT))) {
			report(player, furnace, takeSlot(player, furnace, FUEL));
		}
	}

	private static boolean report(Player player, AbstractFurnaceBlockEntity f, ItemStack got) {
		if (got.isEmpty()) return false;
		feedback(f, player, got, false);
		return true;
	}
	private static void actionBar(Player player, Component msg) { if (player instanceof ServerPlayer sp) { sp.sendSystemMessage(msg, true); } else { player.sendOverlayMessage(msg); } }

	private static ItemStack takeSlot(Player player, AbstractFurnaceBlockEntity f, int slot) {
		ItemStack stack = f.getItem(slot);
		if (stack.isEmpty()) return ItemStack.EMPTY;

		ItemStack taking = stack.copy();
		player.getInventory().add(taking); // в taking остаётся то, что не влезло
		int moved = stack.getCount() - taking.getCount();
		if (moved == 0) return ItemStack.EMPTY;

		ItemStack movedStack = stack.copyWithCount(moved);
		stack.shrink(moved);
		f.setItem(slot, stack);
		f.setChanged();
		return movedStack;
	}

	private static void feedback(AbstractFurnaceBlockEntity f, Player player, ItemStack stack, boolean into) {
		if (f.getLevel() != null) {
			f.getLevel().playSound(null, f.getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS,
				into ? 0.5f : 0.8f,
				into ? 0.75f : 1.35f);
		}
		Component msg = Component .translatable(into ? "tmf.actionbar.in" : "tmf.actionbar.out", stack.getCount(), stack.getHoverName()) .withStyle(into ? ChatFormatting.GREEN : ChatFormatting.YELLOW); actionBar(player, msg);

	private static int insert(AbstractFurnaceBlockEntity f, int slot, ItemStack held) {
		ItemStack cur = f.getItem(slot);
		if (cur.isEmpty()) {
			int n = Math.min(held.getCount(), held.getMaxStackSize());
			f.setItem(slot, held.copyWithCount(n));
			return n;
		}
		if (!ItemStack.isSameItemSameComponents(cur, held)) return 0;
		int n = Math.min(held.getCount(), cur.getMaxStackSize() - cur.getCount());
		if (n <= 0) return 0;
		cur.grow(n);
		return n;
	}
}
