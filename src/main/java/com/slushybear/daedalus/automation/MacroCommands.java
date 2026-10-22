package com.slushybear.daedalus.automation;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.slushybear.daedalus.sync.DefinitionSync;
import java.util.List;
import java.util.function.IntSupplier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/** Client-side commands. They never reach the server, so they are not sent as chat. */
public final class MacroCommands {
    private MacroCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("macro")
                        .then(ClientCommands.literal("new")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> create(ctx, 0))
                                        .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(0, Limits.MAX_INTERVAL_TICKS / 20))
                                                .executes(ctx -> create(ctx, IntegerArgumentType.getInteger(ctx, "seconds") * 20)))))
                        .then(ClientCommands.literal("step")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .then(ClientCommands.argument("line", StringArgumentType.greedyString())
                                                .executes(MacroCommands::addStep))))
                        .then(ClientCommands.literal("bind")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .then(ClientCommands.argument("slot", IntegerArgumentType.integer(0, Limits.KEY_SLOTS))
                                                .executes(MacroCommands::bind))))
                        .then(named("run", MacroCommands::runMacro))
                        .then(named("stop", MacroCommands::stopMacro))
                        .then(named("show", MacroCommands::showMacro))
                        .then(named("pause", ctx -> setPaused(ctx, true)))
                        .then(named("resume", ctx -> setPaused(ctx, false)))
                        .then(named("clear", MacroCommands::clearMacro))
                        .then(named("delete", MacroCommands::deleteMacro))
                        .then(ClientCommands.literal("list").executes(MacroCommands::listMacros))
                        .then(ClientCommands.literal("sync").executes(ctx -> {
                            DefinitionSync.refreshNow();
                            feedback(ctx, "Requested a definitions refresh");
                            return 1;
                        }))
        ));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> named(String literal, Command<FabricClientCommandSource> action) {
        return ClientCommands.literal(literal)
                .then(ClientCommands.argument("name", StringArgumentType.word()).executes(action));
    }

    private static int create(CommandContext<FabricClientCommandSource> ctx, int ticks) {
        String name = StringArgumentType.getString(ctx, "name");
        return guard(ctx, () -> {
            Macro macro = DefinitionValidator.newMacro(name, ticks, MacroManager.allowlist(), MacroManager.all());
            MacroManager.add(macro);
            feedback(ctx, "Created '" + name + "'" + (ticks == 0 ? " (manual)" : " (every " + ticks / 20 + "s)"));
            return 1;
        });
    }

    private static int addStep(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String line = StringArgumentType.getString(ctx, "line");
        return guard(ctx, () -> {
            Macro updated = DefinitionValidator.addStep(require(name), line, MacroManager.allowlist());
            MacroManager.update(name, macro -> updated);
            feedback(ctx, "Step " + updated.steps().size() + " added to '" + name + "'");
            return 1;
        });
    }

    private static int bind(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        return guard(ctx, () -> {
            require(name);
            MacroManager.bind(name, slot);
            feedback(ctx, slot == 0 ? "Unbound '" + name + "'" : "'" + name + "' is on key slot " + slot + " (set the key in Controls)");
            return 1;
        });
    }

    private static int runMacro(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        return guard(ctx, () -> {
            MacroManager.start(require(name));
            feedback(ctx, "Running '" + name + "'");
            return 1;
        });
    }

    private static int stopMacro(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (MacroManager.stop(name)) {
            feedback(ctx, "Stopped '" + name + "'");
            return 1;
        }
        error(ctx, "'" + name + "' is not running");
        return 0;
    }

    private static int showMacro(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        return guard(ctx, () -> {
            Macro macro = require(name);
            feedback(ctx, macro.name() + " | " + trigger(macro) + (macro.paused() ? " | PAUSED" : ""));
            if (macro.steps().isEmpty()) {
                feedback(ctx, "  (no steps yet, use /macro step " + name + " <action> ...)");
            }
            for (int i = 0; i < macro.steps().size(); i++) {
                feedback(ctx, "  " + (i + 1) + ". " + macro.steps().get(i).describe());
            }
            return 1;
        });
    }

    private static int setPaused(CommandContext<FabricClientCommandSource> ctx, boolean paused) {
        String name = StringArgumentType.getString(ctx, "name");
        return guard(ctx, () -> {
            require(name);
            MacroManager.update(name, macro -> macro.withPaused(paused));
            feedback(ctx, (paused ? "Paused '" : "Resumed '") + name + "'");
            return 1;
        });
    }

    private static int clearMacro(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        return guard(ctx, () -> {
            require(name);
            MacroManager.stop(name);
            MacroManager.update(name, macro -> macro.withSteps(List.of()));
            feedback(ctx, "Cleared the steps of '" + name + "'");
            return 1;
        });
    }

    private static int deleteMacro(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (MacroManager.remove(name)) {
            feedback(ctx, "Deleted '" + name + "'");
            return 1;
        }
        error(ctx, "No macro called '" + name + "'");
        return 0;
    }

    private static int listMacros(CommandContext<FabricClientCommandSource> ctx) {
        List<Macro> all = MacroManager.all();
        if (all.isEmpty()) {
            feedback(ctx, "No macros. Try /macro new <name> [seconds]");
            return 1;
        }
        for (Macro macro : all) {
            String status = MacroManager.isRunning(macro.name()) ? " | RUNNING" : macro.paused() ? " | PAUSED" : "";
            feedback(ctx, macro.name() + " | " + trigger(macro) + " | " + macro.steps().size() + " steps"
                    + (macro.keySlot() == 0 ? "" : " | key " + macro.keySlot()) + status);
        }
        return all.size();
    }

    private static String trigger(Macro macro) {
        return macro.intervalTicks() == 0 ? "manual" : "every " + macro.intervalTicks() / 20 + "s";
    }

    private static Macro require(String name) {
        return MacroManager.find(name).orElseThrow(() -> new IllegalArgumentException("No macro called '" + name + "'"));
    }

    /** Runs the action and turns validation errors into a red message, so one bad command never throws into the chat. */
    private static int guard(CommandContext<FabricClientCommandSource> ctx, IntSupplier action) {
        try {
            return action.getAsInt();
        } catch (IllegalArgumentException | IllegalStateException e) {
            error(ctx, e.getMessage());
            return 0;
        }
    }

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, String message) {
        ctx.getSource().sendFeedback(Component.literal(message));
    }

    private static void error(CommandContext<FabricClientCommandSource> ctx, String message) {
        ctx.getSource().sendError(Component.literal(message));
    }
}
