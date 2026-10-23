package com.slushybear.daedalus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slushybear.daedalus.DaedalusClient;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.ModuleRegistry;
import com.slushybear.daedalus.module.setting.Setting;
import com.slushybear.daedalus.ui.Theme;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Saves the menu to config/daedalus.json. The theme is shared; each profile keeps its own module states
 * (enabled, keybind, settings). The open-menu key is saved by Minecraft in options.txt, not here.
 *
 * File layout:
 * <pre>
 * { "theme": {...}, "profile": "Default",
 *   "profiles": { "Default": { "Freelook": { "enabled": true, "keybind": "key.keyboard.f", "settings": { "Invert": false } } } } }
 * </pre>
 */
public final class DaedalusConfig {
    public static final List<String> PROFILES = List.of("Default", "Dungeons", "Legit");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("daedalus.json");

    /** Module states for profiles that are not active right now. The active one lives in the modules themselves. */
    private static final Map<String, JsonObject> SAVED_PROFILES = new HashMap<>();
    private static String activeProfile = PROFILES.getFirst();

    private DaedalusConfig() {}

    public static String activeProfile() {
        return activeProfile;
    }

    public static String nextProfile() {
        return PROFILES.get((PROFILES.indexOf(activeProfile) + 1) % PROFILES.size());
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("theme")) {
                    Theme.fromJson(root.getAsJsonObject("theme"));
                }
                if (root.has("profiles")) {
                    for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("profiles").entrySet()) {
                        if (PROFILES.contains(entry.getKey()) && entry.getValue().isJsonObject()) {
                            SAVED_PROFILES.put(entry.getKey(), entry.getValue().getAsJsonObject());
                        }
                    }
                }
                if (root.has("profile") && PROFILES.contains(root.get("profile").getAsString())) {
                    activeProfile = root.get("profile").getAsString();
                }
            } catch (IOException | RuntimeException e) {
                // Gson throws unchecked exceptions for broken JSON or wrong types, hence RuntimeException.
                DaedalusClient.LOGGER.error("Could not read {}, moving it aside and using defaults", FILE, e);
                moveAside();
                Theme.reset();
                SAVED_PROFILES.clear();
            }
        }
        applyProfile(SAVED_PROFILES.get(activeProfile));
    }

    public static void save() {
        SAVED_PROFILES.put(activeProfile, captureModules());

        JsonObject profiles = new JsonObject();
        for (String name : PROFILES) {
            if (SAVED_PROFILES.containsKey(name)) {
                profiles.add(name, SAVED_PROFILES.get(name));
            }
        }
        JsonObject root = new JsonObject();
        root.add("theme", Theme.toJson());
        root.addProperty("profile", activeProfile);
        root.add("profiles", profiles);

        // Write to a temp file first, then swap it in, so a crash mid-write cannot leave a half-written config.
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            DaedalusClient.LOGGER.error("Could not save {}", FILE, e);
        }
    }

    /** Stores the current module states under the current profile, then loads the other profile's states. */
    public static void switchProfile(String name) {
        if (!PROFILES.contains(name) || name.equals(activeProfile)) {
            return;
        }
        SAVED_PROFILES.put(activeProfile, captureModules());
        activeProfile = name;
        applyProfile(SAVED_PROFILES.get(name));
        save();
    }

    private static JsonObject captureModules() {
        JsonObject profile = new JsonObject();
        for (Module module : ModuleRegistry.all()) {
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.settings()) {
                settings.add(setting.name(), setting.toJson());
            }
            JsonObject json = new JsonObject();
            json.addProperty("enabled", module.enabled());
            json.addProperty("keybind", module.keybind());
            json.add("settings", settings);
            profile.add(module.name(), json);
        }
        return profile;
    }

    /** Resets every module, then applies whatever the profile saved. A profile never used before ends up all defaults. */
    private static void applyProfile(JsonObject profile) {
        for (Module module : ModuleRegistry.all()) {
            module.resetAll();
            if (profile == null || !profile.has(module.name()) || !profile.get(module.name()).isJsonObject()) {
                continue;
            }
            JsonObject json = profile.getAsJsonObject(module.name());
            try {
                if (json.has("enabled")) {
                    module.setEnabled(json.get("enabled").getAsBoolean());
                }
                if (json.has("keybind")) {
                    module.setKeybind(json.get("keybind").getAsString());
                }
            } catch (RuntimeException e) {
                DaedalusClient.LOGGER.warn("Ignoring bad saved state for {}", module.name());
            }
            JsonObject settings = json.has("settings") && json.get("settings").isJsonObject()
                    ? json.getAsJsonObject("settings") : new JsonObject();
            for (Setting<?> setting : module.settings()) {
                if (!settings.has(setting.name())) {
                    continue;
                }
                try {
                    setting.fromJson(settings.get(setting.name()));
                } catch (RuntimeException e) {
                    DaedalusClient.LOGGER.warn("Ignoring bad saved value for {} > {}", module.name(), setting.name());
                }
            }
        }
    }

    private static void moveAside() {
        try {
            Files.move(FILE, FILE.resolveSibling(FILE.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            DaedalusClient.LOGGER.error("Could not move {} aside", FILE, e);
        }
    }
}
