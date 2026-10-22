package com.slushybear.daedalus.automation;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** One running step. A macro runs one task at a time. tick returns true once the task is over. */
@FunctionalInterface
public interface Task {
    boolean tick(Minecraft client);

    /** Releases keys and other state when the macro stops before the task finishes. */
    default void cancel(Minecraft client) {}

    default boolean failed() {
        return false;
    }

    default String failure() {
        return "";
    }

    /** Finishes on its first tick, after running the action once. */
    static Task instant(Consumer<Minecraft> action) {
        return client -> {
            action.accept(client);
            return true;
        };
    }

    /** Does nothing and finishes on its first tick. */
    static Task done() {
        return client -> true;
    }

    /** Finishes at once and marks the macro as failed, with the reason. */
    static Task failWith(String message) {
        return new Task() {
            @Override
            public boolean tick(Minecraft client) {
                return true;
            }

            @Override
            public boolean failed() {
                return true;
            }

            @Override
            public String failure() {
                return message;
            }
        };
    }
}
