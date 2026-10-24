package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.DaedalusClient;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.helpers.MessageFormatter;

/**
 * The pathfinder's own log, for working out what went wrong on a walk. It goes to {@code logs/daedalus-pathfinder.log}
 * in the game folder, one line per event with a timestamp and a level, and the previous session's file is kept as
 * {@code daedalus-pathfinder.old.log}. INFO and above also go to the game's own log, as before.
 *
 * <p>DEBUG lines (each move as it starts, sprint and hop changes, rejected moves with the reason, speed and braking
 * numbers) are only written while debug logging is on, which is a setting on the Pathfinder module. Nothing here logs
 * every tick: callers log when something changes.
 *
 * <p>Messages use {} placeholders, like the game's logger.
 */
public final class PathLog {
    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String FILE_NAME = "daedalus-pathfinder.log";
    private static final String OLD_FILE_NAME = "daedalus-pathfinder.old.log";

    private static volatile boolean debug;
    private static Path file;
    private static BufferedWriter writer;
    private static boolean broken;

    private PathLog() {}

    public static boolean debugEnabled() {
        return debug;
    }

    public static void setDebug(boolean on) {
        if (debug == on) {
            return;
        }
        debug = on;
        info("Debug logging {}", on ? "on" : "off");
    }

    public static void debug(String message, Object... args) {
        if (debug) {
            write(Level.DEBUG, format(message, args), null);
        }
    }

    public static void info(String message, Object... args) {
        String text = format(message, args);
        DaedalusClient.LOGGER.info("[Pathfinder] {}", text);
        write(Level.INFO, text, null);
    }

    public static void warn(String message, Object... args) {
        String text = format(message, args);
        DaedalusClient.LOGGER.warn("[Pathfinder] {}", text);
        write(Level.WARN, text, null);
    }

    public static void error(String message, Throwable error, Object... args) {
        String text = format(message, args);
        DaedalusClient.LOGGER.error("[Pathfinder] {}", text, error);
        write(Level.ERROR, text, error);
    }

    /** Where the log is written, for telling the player. */
    public static Path file() {
        return logDir().resolve(FILE_NAME);
    }

    /** One log line, as written to the file. Visible for tests. */
    static String line(LocalDateTime time, Level level, String text) {
        return TIME.format(time) + " [" + level + "] " + text;
    }

    static String format(String message, Object... args) {
        return args.length == 0 ? message : MessageFormatter.arrayFormat(message, args).getMessage();
    }

    private static synchronized void write(Level level, String text, Throwable error) {
        if (broken) {
            return;
        }
        try {
            if (writer == null) {
                open();
            }
            writer.write(line(LocalDateTime.now(), level, text));
            writer.newLine();
            if (error != null) {
                StringWriter trace = new StringWriter();
                error.printStackTrace(new PrintWriter(trace));
                writer.write(trace.toString());
            }
            writer.flush(); // a crash right after is exactly when the last lines matter
        } catch (IOException | RuntimeException e) {
            broken = true; // never let logging break a walk
            DaedalusClient.LOGGER.warn("Could not write the pathfinder log", e);
        }
    }

    private static void open() throws IOException {
        Path dir = logDir();
        Files.createDirectories(dir);
        file = dir.resolve(FILE_NAME);
        if (Files.exists(file)) {
            Files.move(file, dir.resolve(OLD_FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
        }
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static Path logDir() {
        return FabricLoader.getInstance().getGameDir().resolve("logs");
    }
}
