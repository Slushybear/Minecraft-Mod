package com.slushybear.daedalus.automation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.slushybear.daedalus.DaedalusClient;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;

/** Saves macros to config/daedalus/macros.json. Writes go through a temp file, so a crash cannot leave a half-written file. */
public final class MacroStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("daedalus").resolve("macros.json");

    private MacroStore() {}

    public static List<Macro> load() {
        if (!Files.exists(FILE)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            List<Macro> loaded = GSON.fromJson(reader, new TypeToken<List<Macro>>() {}.getType());
            if (loaded == null) {
                return new ArrayList<>();
            }
            // The file can be edited by hand, so every entry is checked against the caps and the current allowlist.
            return loaded.stream()
                    .filter(MacroStore::isSane)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException | JsonParseException e) {
            DaedalusClient.LOGGER.error("Could not read {}, moving it aside", FILE, e);
            moveAside();
            return new ArrayList<>();
        }
    }

    public static void save(List<Macro> macros) {
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(macros, writer);
            }
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            DaedalusClient.LOGGER.error("Could not save {}", FILE, e);
        }
    }

    private static boolean isSane(Macro macro) {
        return macro != null
                && macro.name() != null
                && macro.steps() != null
                && macro.steps().size() <= Limits.MAX_STEPS
                && macro.steps().stream().allMatch(step -> step != null && isValidStep(step))
                && (macro.intervalTicks() == 0
                    || (macro.intervalTicks() >= Limits.MIN_INTERVAL_TICKS && macro.intervalTicks() <= Limits.MAX_INTERVAL_TICKS))
                && macro.keySlot() >= 0 && macro.keySlot() <= Limits.KEY_SLOTS;
    }

    private static boolean isValidStep(Step step) {
        try {
            DefinitionValidator.checkStep(step, Allowlist.defaults());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
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
