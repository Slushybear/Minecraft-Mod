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
 * Saves the menu to the config/daedalus folder:
 * <pre>
 * client.json      the theme and which profile is active, shared by every profile
 * Default.json     one file per profile: each module's enabled state, keybind and settings
 * Dungeons.json
 * Legit.json
 * </pre>
 * A profile file looks like
 * <pre>
 * { "Freelook": { "enabled": true, "keybind": "key.keyboard.f", "settings": { "Invert": false } } }
 * </pre>
 * Changes are saved about a second after they happen (see {@link #markDirty}), and again when the menu closes and
 * when the game quits. The open-menu key is saved by Minecraft in options.txt, not here.
 */
public final class DaedalusConfig {
    public static final List<String> PROFILES = List.of("Default", "Dungeons", "Legit");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("daedalus");
    private static final Path CLIENT_FILE = DIR.resolve("client.json");
    /** Where everything was saved before profiles got their own files. Read once, then renamed. */
    private static final Path OLD_FILE = FabricLoader.getInstance().getConfigDir().resolve("daedalus.json");
    /** How long to wait after the last change before saving. */
    private static final int SAVE_DELAY_TICKS = 20;

    /** Module states for profiles that are not active right now. The active one lives in the modules themselves. */
    private static final Map<String, JsonObject> SAVED_PROFILES = new HashMap<>();
    private static String activeProfile = PROFILES.getFirst();
    /** Ticks until a pending save, or -1 when nothing changed. */
    private static int saveIn = -1;

    private DaedalusConfig() {}

    public static String activeProfile() {
        return activeProfile;
    }

    public static void load() {
        if (Files.exists(OLD_FILE) && !Files.exists(CLIENT_FILE)) {
            migrateOldFile();
        } else {
            JsonObject client = read(CLIENT_FILE);
            if (client != null) {
                if (client.has("theme") && client.get("theme").isJsonObject()) {
                    Theme.fromJson(client.getAsJsonObject("theme"));
                }
                if (client.has("profile") && PROFILES.contains(client.get("profile").getAsString())) {
                    activeProfile = client.get("profile").getAsString();
                }
            }
            for (String name : PROFILES) {
                JsonObject profile = read(profileFile(name));
                if (profile != null) {
                    SAVED_PROFILES.put(name, profile);
                }
            }
        }
        applyProfile(SAVED_PROFILES.get(activeProfile));
    }

    /** Something changed. It will be saved shortly, so a burst of changes is written once. */
    public static void markDirty() {
        saveIn = SAVE_DELAY_TICKS;
    }

    /** Called every client tick. Saves once the delay after the last change has passed. */
    public static void tick() {
        if (saveIn > 0) {
            saveIn--;
        } else if (saveIn == 0) {
            save();
        }
    }

    public static void save() {
        saveIn = -1;
        SAVED_PROFILES.put(activeProfile, captureModules());
        JsonObject client = new JsonObject();
        client.add("theme", Theme.toJson());
        client.addProperty("profile", activeProfile);
        write(CLIENT_FILE, client);
        for (String name : PROFILES) {
            if (SAVED_PROFILES.containsKey(name)) {
                write(profileFile(name), SAVED_PROFILES.get(name));
            }
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

    private static Path profileFile(String name) {
        return DIR.resolve(name + ".json");
    }

    /** Reads the old single config/daedalus.json, then renames it so it is not read again. */
    private static void migrateOldFile() {
        JsonObject root = read(OLD_FILE);
        if (root != null) {
            if (root.has("theme") && root.get("theme").isJsonObject()) {
                Theme.fromJson(root.getAsJsonObject("theme"));
            }
            if (root.has("profiles") && root.get("profiles").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("profiles").entrySet()) {
                    if (PROFILES.contains(entry.getKey()) && entry.getValue().isJsonObject()) {
                        SAVED_PROFILES.put(entry.getKey(), entry.getValue().getAsJsonObject());
                    }
                }
            }
            if (root.has("profile") && PROFILES.contains(root.get("profile").getAsString())) {
                activeProfile = root.get("profile").getAsString();
            }
        }
        try {
            Files.move(OLD_FILE, OLD_FILE.resolveSibling("daedalus.json.old"), StandardCopyOption.REPLACE_EXISTING);
            DaedalusClient.LOGGER.info("Moved settings from {} into {}", OLD_FILE, DIR);
        } catch (IOException e) {
            DaedalusClient.LOGGER.error("Could not rename {}", OLD_FILE, e);
        }
        markDirty();
    }

    /** Reads a JSON object from a file. Returns null if the file is missing; a broken file is moved aside. */
    private static JsonObject read(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            // Gson throws unchecked exceptions for broken JSON or wrong types, hence RuntimeException.
            DaedalusClient.LOGGER.error("Could not read {}, moving it aside and using defaults", file, e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                DaedalusClient.LOGGER.error("Could not move {} aside", file, moveError);
            }
            return null;
        }
    }

    /** Writes to a temp file first, then swaps it in, so a crash mid-write cannot leave a half-written file. */
    private static void write(Path file, JsonObject json) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            DaedalusClient.LOGGER.error("Could not save {}", file, e);
        }
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
}
