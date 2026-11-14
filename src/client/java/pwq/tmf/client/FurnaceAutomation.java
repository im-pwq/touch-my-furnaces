package pwq.tmf.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import pwq.tmf.Blacklist;

/**
 * Client-only "invisible GUI" automation.
 *
 * <p>Shift + right-click on a furnace opens the real {@link AbstractFurnaceMenu} on the server,
 * hides the screen (see {@code GuiMixin}) and quick-moves the wanted slots. It never touches the
 * block entity directly: every move is a normal {@link ContainerInput#QUICK_MOVE} click, exactly
 * like a player shift-clicking in the GUI.
 */
public final class FurnaceAutomation {
	private static final int OPEN_TIMEOUT_TICKS = 20;
	/** How many ticks to wait after the menu appears before trusting its slot contents. */
	private static final int CONTENT_DELAY_TICKS = 2;

	private static final int INPUT = 0;
	private static final int FUEL = 1;
	private static final int OUTPUT = 2;

	private enum Phase { IDLE, OPENING, CLICKING }

	private static Phase phase = Phase.IDLE;
	private static boolean suppressScreen;
	private static boolean extracting;
	private static int waitTicks;
	private static int clickIndex;
	private static int[] clicks;

	private FurnaceAutomation() {}

	/** Registered on {@code UseBlockCallback.EVENT} (client side). */
	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
		if (!(player instanceof LocalPlayer local) || !local.isShiftKeyDown()) return InteractionResult.PASS;
		if (!(level.getBlockEntity(hit.getBlockPos()) instanceof AbstractFurnaceBlockEntity furnace)) {
			return InteractionResult.PASS;
		}
		// Already busy: swallow the click so vanilla does not place a block mid-automation.
		if (phase != Phase.IDLE) return InteractionResult.FAIL;

		ItemStack held = local.getMainHandItem();
		if (held.isEmpty()) {
			start(local, hit, true);
			return InteractionResult.FAIL;
		}
		if (!accepts(level, furnace, held)) return InteractionResult.PASS;
		start(local, hit, false);
		return InteractionResult.FAIL;
	}

	private static void start(LocalPlayer player, BlockHitResult hit, boolean extract) {
		extracting = extract;
		phase = Phase.OPENING;
		suppressScreen = true;
		waitTicks = 0;
		clickIndex = 0;
		clicks = null;
		openFurnace(player, hit);
	}

	/**
	 * Vanilla {@code ServerPlayerGameMode.useItemOn} skips opening the block when the player sneaks
	 * with a non-empty hand. We tell the server "not sneaking" for a single packet, send the use
	 * packet, then restore the real input. One connection handles packets in order, so the server
	 * sees: not-sneaking -&gt; use -&gt; sneaking.
	 */
	private static void openFurnace(LocalPlayer player, BlockHitResult hit) {
		ClientPacketListener connection = player.connection;
		Input real = player.input.keyPresses;
		Input noShift = new Input(real.forward(), real.backward(), real.left(), real.right(),
			real.jump(), false, real.sprint());
		connection.send(new ServerboundPlayerInputPacket(noShift));
		connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 0));
		connection.send(new ServerboundPlayerInputPacket(real));
	}

	public static void onEndTick(Minecraft client) {
		if (phase == Phase.IDLE) return;
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			reset();
			return;
		}
		switch (phase) {
			case OPENING -> tickOpening(player);
			case CLICKING -> tickClicking(client, player);
			default -> { }
		}
	}

	private static void tickOpening(LocalPlayer player) {
		if (player.containerMenu instanceof AbstractFurnaceMenu menu && menu.containerId != 0) {
			phase = Phase.CLICKING;
			waitTicks = CONTENT_DELAY_TICKS;
			clicks = null;
			clickIndex = 0;
			return;
		}
		if (++waitTicks > OPEN_TIMEOUT_TICKS) reset();
	}

	private static void tickClicking(Minecraft client, LocalPlayer player) {
		if (!(player.containerMenu instanceof AbstractFurnaceMenu menu) || menu.containerId == 0) {
			reset();
			return;
		}
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		if (clicks == null) {
			clicks = extracting ? planExtract(menu) : planInsert(menu, player);
		}
		if (clickIndex >= clicks.length) {
			player.closeContainer();
			reset();
			return;
		}
		int slot = clicks[clickIndex++];
		if (slot >= 0 && slot < menu.slots.size()) {
			client.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
		}
	}

	/** Slot of the selected hotbar stack inside the furnace menu (one click per tick). */
	private static int[] planInsert(AbstractFurnaceMenu menu, LocalPlayer player) {
		int selected = player.getInventory().getSelectedSlot();
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.getSlot(i);
			if (slot.container == player.getInventory() && slot.getContainerSlot() == selected) {
				return new int[] { i };
			}
		}
		return new int[0];
	}

	/** Mirrors {@code FurnaceActions.takeOutput}: output, then the bucket exception, then input, then fuel. */
	private static int[] planExtract(AbstractFurnaceMenu menu) {
		if (!menu.getSlot(OUTPUT).getItem().isEmpty()) return new int[] { OUTPUT };
		if (menu.getSlot(FUEL).getItem().is(Items.BUCKET)) return new int[] { FUEL };
		if (!menu.getSlot(INPUT).getItem().isEmpty()) return new int[] { INPUT };
		return new int[] { FUEL };
	}

	/** Client-side twin of {@code FurnaceActions.accepts}: blacklist + smeltable/fuel. */
	private static boolean accepts(Level level, AbstractFurnaceBlockEntity furnace, ItemStack held) {
		if (Blacklist.isBlocked(held)) return false;
		if (held.has(DataComponents.COOKING_FUEL)) return true;
		if (!(level instanceof ClientLevel client)) return false;
		ResourceKey<RecipePropertySet> key = furnace instanceof BlastFurnaceBlockEntity
			? RecipePropertySet.BLAST_FURNACE_INPUT
			: furnace instanceof SmokerBlockEntity
				? RecipePropertySet.SMOKER_INPUT
				: RecipePropertySet.FURNACE_INPUT;
		RecipePropertySet accepted = client.recipeAccess().propertySet(key);
		return accepted != null && accepted.test(held);
	}

	/** Called from the mixin: keep our furnace screen hidden while the automation runs. */
	public static boolean shouldSuppressScreen(Screen screen) {
		return suppressScreen && screen instanceof MenuAccess<?> access
			&& access.getMenu() instanceof AbstractFurnaceMenu;
	}

	private static void reset() {
		phase = Phase.IDLE;
		suppressScreen = false;
		extracting = false;
		clicks = null;
		clickIndex = 0;
		waitTicks = 0;
	}
}
