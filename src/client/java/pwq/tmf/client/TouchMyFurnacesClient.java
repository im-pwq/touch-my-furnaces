package pwq.tmf.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

public class TouchMyFurnacesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		pwq.tmf.Blacklist.load();
		UseBlockCallback.EVENT.register(FurnaceAutomation::onUseBlock);
		ClientTickEvents.END_CLIENT_TICK.register(FurnaceAutomation::onEndTick);
	}
}
