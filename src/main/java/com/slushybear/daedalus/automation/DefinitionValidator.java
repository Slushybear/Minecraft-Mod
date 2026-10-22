package com.slushybear.daedalus.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Turns player input into macros and steps. Every failure throws IllegalArgumentException with a message for the player. */
public final class DefinitionValidator {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1," + Limits.MAX_NAME_LENGTH + "}");

    private DefinitionValidator() {}

    /** Creates an empty macro. Interval 0 means manual only. */
    public static Macro newMacro(String name, int intervalTicks, Allowlist allowlist, List<Macro> existing) {
        if (existing.size() >= allowlist.maxMacros()) {
            throw new IllegalArgumentException("You already have " + existing.size() + " macros (max " + allowlist.maxMacros() + ")");
        }
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Name must be 1-" + Limits.MAX_NAME_LENGTH + " letters, digits, _ or -");
        }
        if (existing.stream().anyMatch(m -> m.name().equalsIgnoreCase(name))) {
            throw new IllegalArgumentException("A macro called '" + name + "' already exists");
        }
        if (intervalTicks != 0 && (intervalTicks < allowlist.minIntervalTicks() || intervalTicks > allowlist.maxIntervalTicks())) {
            throw new IllegalArgumentException("Interval must be 0 (manual) or "
                    + allowlist.minIntervalTicks() / 20 + "-" + allowlist.maxIntervalTicks() / 20 + " seconds");
        }
        return new Macro(name, List.of(), intervalTicks, 0, false);
    }

    /** Parses a line such as "HOLD FORWARD 20" and appends it to the macro. */
    public static Macro addStep(Macro macro, String line, Allowlist allowlist) {
        if (macro.steps().size() >= allowlist.maxSteps()) {
            throw new IllegalArgumentException("'" + macro.name() + "' is full (max " + allowlist.maxSteps() + " steps)");
        }
        List<Step> steps = new ArrayList<>(macro.steps());
        steps.add(parseStep(line, allowlist));
        return macro.withSteps(steps);
    }

    public static Step parseStep(String line, Allowlist allowlist) {
        String[] parts = line.trim().split("\\s+", 2);
        ActionType type = ActionType.byId(parts[0])
                .orElseThrow(() -> new IllegalArgumentException("Unknown action '" + parts[0] + "'. Options: " + actionNames()));
        String rest = parts.length > 1 ? parts[1].trim() : "";

        Step step = switch (type) {
            case WAIT, SWAP_SLOT -> new Step(type, "", parseInt(rest, type));
            case HOLD -> {
                String[] args = splitArgs(rest, 2, type);
                yield new Step(type, args[0], parseInt(args[1], type));
            }
            case CHAT -> new Step(type, rest, 0);
            case HARVEST_CROPS, REPLANT_CROPS -> {
                String[] args = splitArgs(rest, 2, type);
                yield new Step(type, args[0], parseInt(args[1], type));
            }
            case GOTO -> {
                String[] args = splitArgs(rest, 3, type);
                yield new Step(type,
                        parseInt(args[0], type) + " " + parseInt(args[1], type) + " " + parseInt(args[2], type),
                        0);
            }
            case MINE -> {
                String[] args = splitArgs(rest, 2, type);
                yield new Step(type, args[0].toUpperCase(Locale.ROOT), parseInt(args[1], type));
            }
        };

        checkStep(step, allowlist);
        return step;
    }

    /** Checks an already-built step. Used by the parser, and by the store when it loads a file. */
    public static void checkStep(Step step, Allowlist allowlist) {
        if (!allowlist.actions().contains(step.type())) {
            throw new IllegalArgumentException(step.type() + " is not available on this server");
        }
        switch (step.type()) {
            case WAIT -> requireRange(step.amount(), 1, Limits.MAX_WAIT_TICKS, "ticks");
            case SWAP_SLOT -> requireRange(step.amount(), 1, 9, "slot");
            case HOLD -> {
                HeldKey.byId(step.target())
                        .filter(allowlist.holdKeys()::contains)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown or disallowed key '" + step.target() + "'"));
                requireRange(step.amount(), 1, Limits.MAX_HOLD_TICKS, "ticks");
            }
            case CHAT -> {
                if (!allowlist.chatEnabled()) {
                    throw new IllegalArgumentException("Chat steps are turned off on this server");
                }
                if (step.target().isEmpty() || step.target().length() > Limits.MAX_CHAT_LENGTH) {
                    throw new IllegalArgumentException("Chat text must be 1-" + Limits.MAX_CHAT_LENGTH + " characters");
                }
                if (step.target().startsWith("/")) {
                    throw new IllegalArgumentException("Chat steps cannot send commands");
                }
            }
            case HARVEST_CROPS, REPLANT_CROPS -> {
                CropType.byId(step.target())
                        .filter(allowlist.crops()::contains)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown or disallowed crop '" + step.target() + "'"));
                requireRange(step.amount(), 1, allowlist.maxRadius(), "radius");
            }
            case GOTO -> {
                String[] coords = step.target().split(" ");
                if (coords.length != 3) {
                    throw new IllegalArgumentException("GOTO needs x y z");
                }
                for (String coord : coords) {
                    requireRange(parseInt(coord, step.type()), -30000000, 30000000, "coordinate");
                }
            }
            case MINE -> {
                MineTarget.byId(step.target())
                        .filter(allowlist.mineTargets()::contains)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown or disallowed block '" + step.target() + "'"));
                requireRange(step.amount(), 1, Limits.MAX_MINE_RADIUS, "radius");
            }
        }
    }

    private static String[] splitArgs(String rest, int expected, ActionType type) {
        String[] args = rest.isEmpty() ? new String[0] : rest.split("\\s+", expected);
        if (args.length != expected) {
            throw new IllegalArgumentException(type + " needs " + expected + " arguments");
        }
        return args;
    }

    private static int parseInt(String text, ActionType type) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(type + " needs a whole number, got '" + text + "'");
        }
    }

    private static void requireRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1)
                    + " must be " + min + "-" + max);
        }
    }

    private static String actionNames() {
        StringBuilder names = new StringBuilder();
        for (ActionType type : ActionType.values()) {
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(type.name());
        }
        return names.toString();
    }
}
