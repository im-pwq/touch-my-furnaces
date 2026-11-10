package pwq.tmf.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import pwq.tmf.FurnaceActions;

public class TouchMyFurnacesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		pwq.tmf.Blacklist.load();
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			if (!(level.getBlockEntity(hit.getBlockPos()) instanceof AbstractFurnaceBlockEntity furnace)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getMainHandItem();
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS; // решение за сервером
			}
			if (held.isEmpty()) {
				FurnaceActions.takeOutput(player, furnace);
				return InteractionResult.SUCCESS; // с пустой рукой в присяди GUI не открываем
			}
			if (!FurnaceActions.accepts(furnace, held)) {
				return InteractionResult.PASS; // ваниль поставит блок
			}
			FurnaceActions.useWithItem(player, furnace, held);
			return InteractionResult.SUCCESS;
		});
	}
}
