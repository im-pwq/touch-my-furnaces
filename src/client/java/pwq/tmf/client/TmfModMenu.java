package pwq.tmf.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.minecraft.network.chat.Component;
import pwq.tmf.Blacklist;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TmfModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			Blacklist.Data defaults = Blacklist.defaults();
			return YetAnotherConfigLib.createBuilder()
				.title(Component.translatable("tmf.config.title"))
				.category(ConfigCategory.createBuilder()
					.name(Component.translatable("tmf.config.category"))
					.tooltip(Component.translatable("tmf.config.category.desc"))
					.group(stringList("blacklist", defaults.blocked,
						Blacklist::getBlocked, Blacklist::setBlocked))
					.group(stringList("whitelist", defaults.allowed,
						Blacklist::getAllowed, Blacklist::setAllowed))
					.build())
				.save(Blacklist::save)
				.build()
				.generateScreen(parent);
		};
	}

	private static ListOption<String> stringList(String key, List<String> def,
		Supplier<List<String>> getter, Consumer<List<String>> setter) {
		return ListOption.<String>createBuilder()
			.name(Component.translatable("tmf.config." + key))
			.description(OptionDescription.of(
				Component.translatable("tmf.config." + key + ".desc")))
			.binding(def, getter, setter)
			.controller(StringControllerBuilder::create)
			.initial("")
			.insertEntriesAtEnd(true)
			.build();
	}
}
