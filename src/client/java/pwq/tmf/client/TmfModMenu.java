package pwq.tmf.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.minecraft.network.chat.Component;
import pwq.tmf.Blacklist;

public class TmfModMenu implements ModMenuApi {
	@Override
public ConfigScreenFactory<?> getModConfigScreenFactory() {
	return parent -> YetAnotherConfigLib.createBuilder()
			.title(Component.literal("Touch My Furnaces"))
			.category(ConfigCategory.createBuilder()
					.name(Component.literal("Configuration"))
					.group(ListOption.<String>createBuilder()
							.name(Component.literal("Item blacklist"))
							.description(OptionDescription.of(Component.literal(
									"Items ID that are blocked from fast-load into furnace. Example: minecraft:iron_door или *_door")))
							.binding(Blacklist.defaults().blocked, Blacklist::getBlocked, Blacklist::setBlocked)
							.controller(StringControllerBuilder::create)
							.initial("")
							.build())
					.group(ListOption.<String>createBuilder()
							.name(Component.literal("Whitelist"))
							.description(OptionDescription.of(Component.literal(
									"Items ID that are allowed for fast-load into furnace, even if they were blocked in blacklist.")))
							.binding(Blacklist.defaults().allowed, Blacklist::getAllowed, Blacklist::setAllowed)
							.controller(StringControllerBuilder::create)
							.initial("")
							.build())
					.build())
			.save(Blacklist::save)
			.build()
			.generateScreen(parent);
}
}
