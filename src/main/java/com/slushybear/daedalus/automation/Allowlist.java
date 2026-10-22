package com.slushybear.daedalus.automation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.EnumSet;
import java.util.Set;

/** Which actions, crops, keys, blocks and limits are currently allowed. Built from code defaults, then optionally narrowed by remote data. */
public record Allowlist(
        Set<ActionType> actions,
        Set<CropType> crops,
        Set<HeldKey> holdKeys,
        Set<MineTarget> mineTargets,
        boolean chatEnabled,
        int maxRadius,
        int maxMacros,
        int maxSteps,
        int minIntervalTicks,
        int maxIntervalTicks
) {
    public static Allowlist defaults() {
        return new Allowlist(
                EnumSet.allOf(ActionType.class),
                EnumSet.allOf(CropType.class),
                EnumSet.allOf(HeldKey.class),
                EnumSet.allOf(MineTarget.class),
                true,
                Limits.MAX_RADIUS,
                Limits.MAX_MACROS,
                Limits.MAX_STEPS,
                Limits.MIN_INTERVAL_TICKS,
                Limits.MAX_INTERVAL_TICKS
        );
    }

    /**
     * Builds an allowlist from remote JSON. Unknown names are ignored, and every number is clamped
     * to the code limits, so the remote file can only make the rules stricter.
     * Throws IllegalArgumentException if the result would allow no actions or crops, so the caller keeps the old rules.
     */
    public static Allowlist fromRemote(JsonObject json) {
        Set<ActionType> actions = names(json, "actions", ActionType.class);
        Set<CropType> crops = names(json, "crops", CropType.class);
        Set<HeldKey> holdKeys = names(json, "holdKeys", HeldKey.class);
        Set<MineTarget> mineTargets = names(json, "mineTargets", MineTarget.class);

        if (actions.isEmpty() || crops.isEmpty()) {
            throw new IllegalArgumentException("Remote allowlist has no usable actions or crops");
        }

        return new Allowlist(
                actions,
                crops,
                holdKeys,
                mineTargets,
                json.has("chat") ? json.get("chat").getAsBoolean() : true,
                clamp(json, "maxRadius", Limits.MAX_RADIUS, 1, Limits.MAX_RADIUS),
                clamp(json, "maxMacros", Limits.MAX_MACROS, 1, Limits.MAX_MACROS),
                clamp(json, "maxSteps", Limits.MAX_STEPS, 1, Limits.MAX_STEPS),
                clamp(json, "minIntervalTicks", Limits.MIN_INTERVAL_TICKS, Limits.MIN_INTERVAL_TICKS, Limits.MAX_INTERVAL_TICKS),
                clamp(json, "maxIntervalTicks", Limits.MAX_INTERVAL_TICKS, Limits.MIN_INTERVAL_TICKS, Limits.MAX_INTERVAL_TICKS)
        );
    }

    /** Reads a JSON string array into an enum set. Unknown names are skipped. */
    private static <E extends Enum<E>> Set<E> names(JsonObject json, String key, Class<E> type) {
        Set<E> result = EnumSet.noneOf(type);
        if (!json.has(key)) {
            return result;
        }
        for (JsonElement element : json.getAsJsonArray(key)) {
            String id = element.getAsString();
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(id)) {
                    result.add(constant);
                }
            }
        }
        return result;
    }

    private static int clamp(JsonObject json, String key, int fallback, int min, int max) {
        int value = json.has(key) ? json.get(key).getAsInt() : fallback;
        return Math.max(min, Math.min(max, value));
    }
}
