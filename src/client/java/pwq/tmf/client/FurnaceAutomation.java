package pwq.tmf.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
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
 * hides the screen (see {@code GuiMixin}) and moves the wanted slots. It never touches the block
 * entity directly: every move is an ordinary container click.
 *
 * <p>Insertion is a single {@link ContainerInput#QUICK_MOVE} of the held hotbar stack (vanilla
 * routes it to the input or fuel slot). Extraction fills the <em>hotbar first</em>, then the main
 * inventory: the output slot uses vanilla {@code QUICK_MOVE} (its slot range is already iterated
 * in reverse, i.e. hotbar first), while the bucket/input/fuel slot is moved by hand with normal
 * {@link ContainerInput#PICKUP} clicks, exactly like a player moving a stack to a chosen slot.
 */
public final class FurnaceAutomation {
	private static final int OPEN_TIMEOUT_TICKS = 20;
	/** How many ticks to wait after the menu appears before trusting its slot contents. */
	private static final int CONTENT_DELAY_TICKS = 2;

	private static final int INPUT = 0;
	private static final int FUEL = 1;
	private static final int OUTPUT = 2;

	/** Player inventory inside the furnace menu: main 3..29, hotbar 30..38. */
	private static final int MAIN_START = 3;
	private static final int MAIN_END = 30;
	private static final int HOTBAR_START = 30;
	private static final int HOTBAR_END = 39;

	private enum Phase { IDLE, OPENING, MOVING }

	private static Phase phase = Phase.IDLE;
	private static boolean suppressScreen;
	private static boolean extracting;
	private static int waitTicks;
	/** Slot contents captured right before the first move, used to build the feedback diff. */
	private static ItemStack[] before;
	private static boolean sourceResolved;
	private static int extractSource = -1;
	private static boolean done;

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
			case MOVING -> tickMoving(client, player);
			default -> { }
		}
	}

	private static void tickOpening(LocalPlayer player) {
		if (player.containerMenu instanceof AbstractFurnaceMenu menu && menu.containerId != 0) {
			phase = Phase.MOVING;
			waitTicks = CONTENT_DELAY_TICKS;
			return;
		}
		if (++waitTicks > OPEN_TIMEOUT_TICKS) reset();
	}

	private static void tickMoving(Minecraft client, LocalPlayer player) {
		if (!(player.containerMenu instanceof AbstractFurnaceMenu menu) || menu.containerId == 0) {
			reset();
			return;
		}
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		if (before == null) before = snapshot(menu);
		if (done) {
			finish(menu, player);
			return;
		}
		if (extracting) {
			extractStep(client, menu, player);
		} else {
			insertStep(client, menu, player);
		}
	}

	/** Insertion: quick-move the selected hotbar stack; vanilla routes it to input or fuel. */
	private static void insertStep(Minecraft client, AbstractFurnaceMenu menu, LocalPlayer player) {
		int slot = selectedHotbarSlot(menu, player);
		if (slot >= 0) {
			click(client, menu, player, slot, ContainerInput.QUICK_MOVE, 0);
		}
		done = true;
	}

	/**
	 * Extraction: the output slot first, then the bucket exception, then input, then fuel (the
	 * original take order). Output rides vanilla {@code QUICK_MOVE}; the rest is moved by hand so
	 * it lands in the hotbar before the main inventory.
	 */
	private static void extractStep(Minecraft client, AbstractFurnaceMenu menu, LocalPlayer player) {
		if (!sourceResolved) {
			extractSource = pickExtractSource(menu);
			sourceResolved = true;
		}
		if (extractSource < 0) {
			done = true;
			return;
		}
		if (extractSource == OUTPUT) {
			if (!menu.getSlot(OUTPUT).getItem().isEmpty()) {
				click(client, menu, player, OUTPUT, ContainerInput.QUICK_MOVE, 0);
			}
			done = true;
			return;
		}
		moveHotbarFirst(client, menu, player, extractSource);
	}

	/**
	 * Picks the source stack up with a normal left-click and drops it into the first accepting
	 * inventory slot, hotbar (30..38) before main (3..29). When nothing more fits, the remainder
	 * goes back to the source slot, so the cursor is never left holding items.
	 */
	private static void moveHotbarFirst(Minecraft client, AbstractFurnaceMenu menu, LocalPlayer player, int source) {
		ItemStack carried = menu.getCarried();
		if (carried.isEmpty()) {
			ItemStack src = menu.getSlot(source).getItem();
			if (src.isEmpty()) {
				done = true;
				return;
			}
			int target = findTarget(menu, src);
			if (target < 0) {
				done = true;
				return;
			}
			click(client, menu, player, source, ContainerInput.PICKUP, 0);
			return;
		}
		int target = findTarget(menu, carried);
		if (target < 0) {
			// No room left anywhere: put the remainder back and stop.
			click(client, menu, player, source, ContainerInput.PICKUP, 0);
			done = true;
			return;
		}
		click(client, menu, player, target, ContainerInput.PICKUP, 0);
	}

	/** First hotbar slot, then main inventory slot, that can take the stack. */
	private static int findTarget(AbstractFurnaceMenu menu, ItemStack stack) {
		for (int i = HOTBAR_START; i < HOTBAR_END; i++) {
			if (canAccept(menu.getSlot(i), stack)) return i;
		}
		for (int i = MAIN_START; i < MAIN_END; i++) {
			if (canAccept(menu.getSlot(i), stack)) return i;
		}
		return -1;
	}

	private static boolean canAccept(Slot slot, ItemStack stack) {
		ItemStack current = slot.getItem();
		if (current.isEmpty()) return slot.mayPlace(stack);
		if (!ItemStack.isSameItemSameComponents(current, stack)) return false;
		return current.getCount() < slot.getMaxStackSize(current);
	}

	private static void click(Minecraft client, AbstractFurnaceMenu menu, LocalPlayer player, int slot,
			ContainerInput input, int button) {
		client.gameMode.handleContainerInput(menu.containerId, slot, button, input, player);
	}

	private static void finish(AbstractFurnaceMenu menu, LocalPlayer player) {
		report(menu, player);
		player.closeContainer();
		reset();
	}

	/**
	 * Feedback, counted from the player's inventory side: extraction is what entered it, insertion
	 * is what left it. Keys stay {@code tmf.actionbar.in} / {@code tmf.actionbar.out}, now read
	 * from the inventory's point of view (in = into the inventory, out = out of it).
	 */
	private static void report(AbstractFurnaceMenu menu, LocalPlayer player) {
		int moved = 0;
		ItemStack sample = ItemStack.EMPTY;
		for (int i = MAIN_START; i < menu.slots.size(); i++) {
			int delta = menu.getSlot(i).getItem().getCount() - before[i].getCount();
			if (extracting ? delta > 0 : delta < 0) {
				moved += Math.abs(delta);
				sample = extracting ? menu.getSlot(i).getItem() : before[i];
			}
		}
		if (moved <= 0) {
			// Nothing moved: only the insert path has something useful to say.
			if (!extracting) {
				actionBar(player, Component.translatable("tmf.actionbar.slot_occupied")
					.withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		playPickup(!extracting);
		actionBar(player, Component
			.translatable(extracting ? "tmf.actionbar.in" : "tmf.actionbar.out", moved, sample.getHoverName())
			.withStyle(extracting ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
	}

	private static ItemStack[] snapshot(AbstractFurnaceMenu menu) {
		ItemStack[] items = new ItemStack[menu.slots.size()];
		for (int i = 0; i < items.length; i++) {
			items[i] = menu.getSlot(i).getItem().copy();
		}
		return items;
	}

	private static void actionBar(LocalPlayer player, Component message) {
		player.sendOverlayMessage(message);
	}

	/** Client-side pickup blip on the UI channel, so the in-game "UI" volume slider controls it. */
	private static void playPickup(boolean inserting) {
		float volume = inserting ? 0.5f : 0.8f;
		float pitch = inserting ? 0.75f : 1.35f;
		Minecraft.getInstance().getSoundManager()
			.play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, pitch, volume));
	}

	/** Slot of the selected hotbar stack inside the furnace menu. */
	private static int selectedHotbarSlot(AbstractFurnaceMenu menu, LocalPlayer player) {
		int selected = player.getInventory().getSelectedSlot();
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.getSlot(i);
			if (slot.container == player.getInventory() && slot.getContainerSlot() == selected) {
				return i;
			}
		}
		return -1;
	}

	/** Output, then the bucket exception, then input, then fuel (the original take order). */
	private static int pickExtractSource(AbstractFurnaceMenu menu) {
		if (!menu.getSlot(OUTPUT).getItem().isEmpty()) return OUTPUT;
		if (menu.getSlot(FUEL).getItem().is(Items.BUCKET)) return FUEL;
		if (!menu.getSlot(INPUT).getItem().isEmpty()) return INPUT;
		return menu.getSlot(FUEL).getItem().isEmpty() ? -1 : FUEL;
	}

	/** Blacklist + smeltable/fuel check, evaluated on the client. */
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
		waitTicks = 0;
		before = null;
		sourceResolved = false;
		extractSource = -1;
		done = false;
	}
}
