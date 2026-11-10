package pwq.tmf;


import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;

public final class FurnaceActions {
	private static final int INPUT = 0, FUEL = 1;

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
            TouchMyFurnaces.LOGGER.info("The slot is occupied."); // сообщение игроку допилим позже
            return;
        }
            if (!player.getAbilities().instabuild) held.shrink(moved);
            furnace.setChanged();
        }

	private static final int OUTPUT = 2;

public static void takeOutput(Player player, AbstractFurnaceBlockEntity furnace) {
	// 1. переплавленное, всегда первым
	if (takeSlot(player, furnace, OUTPUT)) {
		if (player instanceof ServerPlayer sp) {
			furnace.awardUsedRecipesAndPopExperience(sp);
		}
		return;
	}
	// исключение для лавового ведра: выход, ведро, вход
	if (furnace.getItem(FUEL).is(Items.BUCKET)) {
		takeSlot(player, furnace, FUEL);
		return;
	}
	// обычный порядок: выход, вход, топливо
	if (takeSlot(player, furnace, INPUT)) return;
	takeSlot(player, furnace, FUEL);
}

private static boolean takeSlot(Player player, AbstractFurnaceBlockEntity f, int slot) {
	ItemStack stack = f.getItem(slot);
	if (stack.isEmpty()) return false;

	ItemStack taking = stack.copy();
	player.getInventory().add(taking); // в taking остаётся то, что не влезло
	int moved = stack.getCount() - taking.getCount();
	if (moved == 0) return false;

	stack.shrink(moved);
	f.setItem(slot, stack);
	f.setChanged();
	return true;
}

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
