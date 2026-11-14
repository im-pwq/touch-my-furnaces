package pwq.tmf.client.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pwq.tmf.client.FurnaceAutomation;

/**
 * Hides the furnace screen while {@link FurnaceAutomation} is working.
 *
 * <p>{@code MenuScreens.ScreenConstructor.fromPacket} sets {@code player.containerMenu} first and
 * only then calls {@link Gui#setScreen(Screen)}, so cancelling here keeps the live menu without ever
 * showing the GUI.
 */
@Mixin(Gui.class)
public class GuiMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void tmf$suppressFurnaceScreen(Screen screen, CallbackInfo ci) {
		if (FurnaceAutomation.shouldSuppressScreen(screen)) {
			ci.cancel();
		}
	}
}
