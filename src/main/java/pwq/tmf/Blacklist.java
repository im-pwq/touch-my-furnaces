package pwq.tmf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Blacklist {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("touch-my-furnaces.json");
	private static final String DEFAULTS = "/assets/touch-my-furnaces/default_blacklist.json";

	public static class Data {
		public List<String> blocked = new ArrayList<>();
		public List<String> allowed = new ArrayList<>();
	}

	private static Data data = new Data();
	private static List<Pattern> blockedPatterns = List.of();
	private static List<Pattern> allowedPatterns = List.of();

	/** Дефолты из jar, их же использует кнопка сброса в экране конфига. */
	public static Data defaults() {
		try (var in = Blacklist.class.getResourceAsStream(DEFAULTS)) {
			if (in != null) {
				Data d = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Data.class);
				if (d != null) return normalize(d);
			}
		} catch (Exception e) {
			TouchMyFurnaces.LOGGER.error("Cannot find default_blacklist.json.", e);
		}
		return new Data();
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				Data read = GSON.fromJson(Files.readString(FILE), Data.class);
				data = read != null ? normalize(read) : defaults();
			} else {
				data = defaults();
				save();
			}
		} catch (Exception e) {
			TouchMyFurnaces.LOGGER.error("Unable to read mod config. Loading defaults...", e);
			data = defaults();
		}
		compile();
	}

	public static void save() {
		try {
			Files.writeString(FILE, GSON.toJson(data));
		} catch (Exception e) {
			TouchMyFurnaces.LOGGER.error("Cannot save the config.", e);
		}
		compile();
	}

	public static List<String> getBlocked() { return data.blocked; }
	public static void setBlocked(List<String> list) { data.blocked = new ArrayList<>(list); }
	public static List<String> getAllowed() { return data.allowed; }
	public static void setAllowed(List<String> list) { data.allowed = new ArrayList<>(list); }

	public static boolean isBlocked(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		for (Pattern p : allowedPatterns) {
			if (p.matcher(id).matches()) return false;
		}
		for (Pattern p : blockedPatterns) {
			if (p.matcher(id).matches()) return true;
		}
		return false;
	}

	private static Data normalize(Data d) {
		if (d.blocked == null) d.blocked = new ArrayList<>();
		if (d.allowed == null) d.allowed = new ArrayList<>();
		return d;
	}

	private static void compile() {
		blockedPatterns = compileAll(data.blocked);
		allowedPatterns = compileAll(data.allowed);
	}

	private static List<Pattern> compileAll(List<String> masks) {
		List<Pattern> list = new ArrayList<>();
		for (String mask : masks) {
			if (mask == null || mask.isBlank()) continue;
			StringBuilder sb = new StringBuilder();
			for (char c : mask.trim().toLowerCase().toCharArray()) {
				if (c == '*') sb.append(".*");
				else sb.append(Pattern.quote(String.valueOf(c)));
			}
			list.add(Pattern.compile(sb.toString()));
		}
		return list;
	}
}
