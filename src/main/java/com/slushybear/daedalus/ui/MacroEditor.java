package com.slushybear.daedalus.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.slushybear.daedalus.automation.ActionType;
import com.slushybear.daedalus.automation.Allowlist;
import com.slushybear.daedalus.automation.CropType;
import com.slushybear.daedalus.automation.DefinitionValidator;
import com.slushybear.daedalus.automation.HeldKey;
import com.slushybear.daedalus.automation.Limits;
import com.slushybear.daedalus.automation.Macro;
import com.slushybear.daedalus.automation.MacroKeys;
import com.slushybear.daedalus.automation.MacroManager;
import com.slushybear.daedalus.automation.MineTarget;
import com.slushybear.daedalus.automation.Step;
import com.slushybear.daedalus.module.KeyNames;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * The Macros tab: a list of macros on the left, and the selected macro's settings and steps on the right.
 *
 * Every change goes through MacroManager, and every step is built by DefinitionValidator from the same text a
 * /macro step command would use, so the editor follows exactly the same rules and limits as the commands.
 */
final class MacroEditor {
    private static final int LIST_W = 136;
    private static final int ROW_GAP = 18;
    private static final IntPredicate NAME_CHARS = c -> Character.isLetterOrDigit(c) || c == '_' || c == '-';

    /** Remembered between openings of the menu. */
    private static String selectedName;

    private final Ui ui;
    private final Runnable closeMenu;
    private final Ui.Scroll listScroll = new Ui.Scroll();
    private final Ui.Scroll editScroll = new Ui.Scroll();
    private StepDraft draft;
    private boolean confirmDelete;

    MacroEditor(Ui ui, Runnable closeMenu) {
        this.ui = ui;
        this.closeMenu = closeMenu;
    }

    void draw(int left, int top, int right, int bottom) {
        List<Macro> macros = MacroManager.all();
        Macro selected = selectedName == null ? null : MacroManager.find(selectedName).orElse(null);
        if (selected == null && !macros.isEmpty()) {
            selected = macros.getFirst();
            select(selected);
        }
        int split = left + LIST_W;
        ui.g.fill(split + 1, top, right, bottom, Theme.panel());
        ui.g.fill(split, top, split + 1, bottom, Theme.line());
        drawList(left, top, split, bottom, macros, selected);
        drawEditor(split + 1, top, right, bottom, selected);
    }

    // ---- Left: the macro list ----

    private void drawList(int left, int top, int right, int bottom, List<Macro> macros, Macro selected) {
        int x = left + 8;
        int w = right - left - 16;
        int y = top + 7;
        ui.text("MACROS", x, y, Theme.accent());
        ui.chipRight(x + w, y - 2, "+ New", this::newMacro);
        ui.heading("Macros", x, y + 11, Theme.text());
        ui.text(macros.size() + " of " + MacroManager.allowlist().maxMacros(), x, y + 27, Theme.muted());

        int listTop = y + 40;
        int cy = ui.beginScroll(left, listTop, right, bottom - 4, listScroll);
        if (macros.isEmpty()) {
            cy = ui.wrapped("No macros yet. Click + New to make one.", x, cy + 4, w, Theme.muted());
        }
        for (Macro macro : macros) {
            macroCard(macro, x, cy, w, macro == selected);
            cy += 27;
        }
        ui.endScroll(listScroll, cy);
    }

    private void macroCard(Macro macro, int x, int y, int w, boolean isSelected) {
        int h = 24;
        float hover = ui.hoverFade("macro:" + macro.name(), x, y, w, h);
        int border = isSelected ? Theme.accent()
                : Theme.mix(Theme.mix(Theme.accent(), Theme.line(), 0.55), Theme.line(), hover);
        ui.box(x, y, w, h, isSelected ? Theme.cardSelected() : Theme.card(), border);
        ui.hit(x, y, w, h, Ui.left(() -> select(macro)));

        int textRight = x + w - 6;
        if (macro.keySlot() != 0 && !KeyNames.isNone(MacroKeys.keyName(macro.keySlot()))) {
            String key = KeyNames.display(MacroKeys.keyName(macro.keySlot()));
            int keyW = Math.min(w / 2, ui.font.width(key) + 8);
            textRight -= keyW;
            ui.box(textRight, y + 6, keyW, 12, Theme.card(), Theme.line());
            ui.text(ui.truncate(key, keyW - 6), textRight + 4, y + 8, Theme.muted());
            textRight -= 4;
        }
        int nameColor = macro.paused() ? Theme.mix(Theme.text(), Theme.card(), 0.6) : Theme.text();
        ui.text(ui.truncate(macro.name(), textRight - x - 7), x + 7, y + 4, nameColor);
        String info = macro.steps().size() + (macro.steps().size() == 1 ? " step" : " steps") + " · "
                + (macro.paused() ? "paused" : intervalLabel(macro).toLowerCase(Locale.ROOT));
        ui.text(ui.truncate(info, textRight - x - 7), x + 7, y + 14, Theme.muted());
    }

    // ---- Right: the selected macro ----

    private void drawEditor(int left, int top, int right, int bottom, Macro macro) {
        int x = left + 10;
        int w = right - left - 20;
        if (macro == null) {
            ui.wrapped("Make a macro with + New. A macro is a list of steps, like walking somewhere, mining or "
                    + "harvesting crops, that runs in order.", x, top + 30, w, Theme.muted());
            return;
        }
        Allowlist rules = MacroManager.allowlist();
        int y = ui.beginScroll(left, top, right, bottom, editScroll) + 9;

        // Header: label plus Run and Delete.
        ui.text("MACRO", x, y, Theme.accent());
        int bx = x + w;
        bx -= ui.solidChip(bx, y - 3, "Run", Theme.accent(), () -> run(macro)) + 4;
        if (confirmDelete) {
            ui.solidChip(bx, y - 3, "Really delete?", Theme.danger(), () -> delete(macro));
        } else {
            ui.chipRight(bx, y - 3, "Delete", () -> confirmDelete = true);
        }
        y += 16;

        ui.textField("macro-name:" + macro.name(), x, y, w, 16, macro.name(), "Name", Limits.MAX_NAME_LENGTH,
                NAME_CHARS, text -> {}, text -> rename(macro, text), false);
        y += 23;

        y = ui.toggleRow("macro-on:" + macro.name(), "Enabled", x, y, w, !macro.paused(),
                () -> MacroManager.update(macro.name(), m -> m.withPaused(!m.paused())));

        ui.text("Repeat every", x, y + 3, Theme.text());
        ui.stepper(x + w, y, intervalLabel(macro), 44,
                () -> setIntervalSeconds(macro, stepInterval(macro, -1, rules)),
                () -> setIntervalSeconds(macro, stepInterval(macro, 1, rules)));
        y += 18;

        String key = macro.keySlot() == 0 ? KeyNames.NONE : MacroKeys.keyName(macro.keySlot());
        y = ui.bindRow("macro-key:" + macro.name(), "Run key", x, y, w, key, name -> setKey(macro, name));
        y += 2;
        ui.line(x, y, x + w);
        y += 8;

        // Steps.
        ui.text("STEPS", x, y + 3, Theme.accent());
        ui.text(macro.steps().size() + "/" + rules.maxSteps(), x + ui.font.width("STEPS") + 6, y + 3, Theme.muted());
        ui.chipRight(x + w, y, "+ Add step", () -> draft = new StepDraft(-1));
        y += 19;
        if (macro.steps().isEmpty() && draft == null) {
            y = ui.wrapped("No steps yet. Click + Add step.", x, y + 2, w, Theme.muted()) + 4;
        }
        for (int i = 0; i < macro.steps().size(); i++) {
            stepRow(macro, i, x, y, w);
            y += 20;
        }
        if (draft != null) {
            y = drawDraft(macro, x, y + 4, w, rules);
        }
        ui.endScroll(editScroll, y + 10);
    }

    private void stepRow(Macro macro, int index, int x, int y, int w) {
        int h = 18;
        boolean editing = draft != null && draft.index == index;
        float hover = ui.hoverFade("step:" + index, x, y, w, h);
        int border = editing ? Theme.accent() : Theme.mix(Theme.mix(Theme.accent(), Theme.line(), 0.55), Theme.line(), hover);
        ui.box(x, y, w, h, editing ? Theme.cardSelected() : Theme.card(), border);
        ui.hit(x, y, w, h, Ui.left(() -> draft = StepDraft.of(macro.steps().get(index), index)));

        String number = String.valueOf(index + 1);
        ui.text(number, x + 6, Ui.textY(y, h), Theme.accent());
        int buttons = 3 * 14 + 2 * 2;
        int textX = x + 8 + Math.max(ui.font.width("00"), ui.font.width(number)) + 4;
        ui.text(ui.truncate(macro.steps().get(index).describe(), x + w - buttons - 8 - textX), textX, Ui.textY(y, h),
                Theme.text());

        int bx = x + w - 2 - 14;
        int by = y + 2;
        ui.iconButton(bx, by, 14, Icons.CLOSE, () -> editSteps(macro, steps -> steps.remove(index)));
        bx -= 16;
        ui.iconButton(bx, by, 14, Icons.DOWN, () -> editSteps(macro, steps -> {
            if (index < steps.size() - 1) {
                Collections.swap(steps, index, index + 1);
            }
        }));
        bx -= 16;
        ui.iconButton(bx, by, 14, Icons.UP, () -> editSteps(macro, steps -> {
            if (index > 0) {
                Collections.swap(steps, index, index - 1);
            }
        }));
    }

    /** The form for a new or edited step. Returns the y below it. */
    private int drawDraft(Macro macro, int x, int y, int w, Allowlist rules) {
        StepDraft d = draft;
        ui.g.fill(x, y, x + w, y + 1, Theme.accent());
        y += 7;
        ui.text(d.index < 0 ? "NEW STEP" : "EDIT STEP " + (d.index + 1), x, y, Theme.accent());
        y += 13;

        List<ActionType> actions = Arrays.stream(ActionType.values()).filter(rules.actions()::contains).toList();
        y = ui.chipGrid(x, y, w, actions.stream().map(MacroEditor::actionLabel).toList(),
                actions.stream().map(Enum::name).toList(), d.type.name(), name -> d.setType(ActionType.valueOf(name))) + 9;

        switch (d.type) {
            case WAIT -> y = ticksRow(d, x, y, w, Limits.MAX_WAIT_TICKS);
            case HOLD -> {
                y = label("Key to hold", x, y);
                List<String> keys = Arrays.stream(HeldKey.values()).filter(rules.holdKeys()::contains).map(Enum::name).toList();
                y = ui.chipGrid(x, y, w, keys.stream().map(MacroEditor::pretty).toList(), keys, d.holdKey,
                        name -> d.holdKey = name) + 8;
                y = ticksRow(d, x, y, w, Limits.MAX_HOLD_TICKS);
            }
            case SWAP_SLOT -> {
                y = label("Hotbar slot", x, y);
                List<String> slots = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
                ui.segmented(x, y, w, 14, slots, String.valueOf(d.slot), s -> d.slot = Integer.parseInt(s));
                y += 20;
            }
            case CHAT -> {
                y = label("Message (commands starting with / are not allowed)", x, y);
                ui.textField("draft-chat", x, y, w, 14, d.chat, "Hello!", Limits.MAX_CHAT_LENGTH, Ui.ANY_TEXT,
                        text -> d.chat = text);
                y += 20;
            }
            case HARVEST_CROPS, REPLANT_CROPS -> {
                y = label("Crop", x, y);
                List<String> crops = Arrays.stream(CropType.values()).filter(rules.crops()::contains).map(Enum::name).toList();
                y = ui.chipGrid(x, y, w, crops.stream().map(MacroEditor::pretty).toList(), crops, d.crop,
                        name -> d.crop = name) + 8;
                y = radiusRow(d, x, y, w, rules.maxRadius());
            }
            case GOTO -> {
                y = label("Walk to block X Y Z", x, y);
                int hereW = ui.font.width("Here") + 14;
                int fieldW = (w - hereW - 4 - 8) / 3;
                ui.textField("draft-x", x, y, fieldW, 14, d.x, "X", 9, Ui.WHOLE_NUMBER, text -> d.x = text);
                ui.textField("draft-y", x + fieldW + 4, y, fieldW, 14, d.y, "Y", 9, Ui.WHOLE_NUMBER, text -> d.y = text);
                ui.textField("draft-z", x + 2 * (fieldW + 4), y, fieldW, 14, d.z, "Z", 9, Ui.WHOLE_NUMBER, text -> d.z = text);
                ui.chipRight(x + w, y, "Here", () -> d.useCurrentPosition());
                y += 20;
            }
            case MINE -> {
                y = label("Block", x, y);
                List<String> blocks = Arrays.stream(MineTarget.values()).filter(rules.mineTargets()::contains).map(Enum::name).toList();
                y = ui.chipGrid(x, y, w, blocks.stream().map(MacroEditor::pretty).toList(), blocks, d.block,
                        name -> d.block = name) + 8;
                y = radiusRow(d, x, y, w, Limits.MAX_MINE_RADIUS);
            }
        }

        if (d.error != null) {
            y = ui.wrapped(d.error, x, y + 2, w, Theme.danger()) + 4;
        }
        int bx = x + w;
        bx -= ui.solidChip(bx, y, d.index < 0 ? "Add" : "Save", Theme.accent(), () -> saveDraft(macro)) + 4;
        ui.chipRight(bx, y, "Cancel", () -> draft = null);
        return y + ROW_GAP;
    }

    private int label(String text, int x, int y) {
        ui.text(text, x, y, Theme.muted());
        return y + 11;
    }

    private int ticksRow(StepDraft d, int x, int y, int w, int max) {
        ui.text("Ticks", x, y + 3, Theme.text());
        String seconds = secondsHint(d.ticks);
        ui.text(seconds, x + ui.font.width("Ticks") + 6, y + 3, Theme.muted());
        ui.textField("draft-ticks", x + w - 56, y, 56, 14, d.ticks, "1-" + max, 5, Ui.WHOLE_NUMBER, text -> d.ticks = text);
        return y + ROW_GAP + 2;
    }

    private int radiusRow(StepDraft d, int x, int y, int w, int max) {
        d.radius = Math.max(1, Math.min(max, d.radius));
        ui.text("Radius", x, y + 3, Theme.text());
        ui.stepper(x + w, y, d.radius + (d.radius == 1 ? " block" : " blocks"), 52,
                () -> d.radius = Math.max(1, d.radius - 1),
                () -> d.radius = Math.min(max, d.radius + 1));
        return y + ROW_GAP + 2;
    }

    private static String secondsHint(String ticks) {
        try {
            return String.format("(%.1fs)", Integer.parseInt(ticks) / 20.0);
        } catch (NumberFormatException e) {
            return "(20 ticks = 1s)";
        }
    }

    // ---- Actions ----

    private void select(Macro macro) {
        if (!macro.name().equals(selectedName)) {
            selectedName = macro.name();
            draft = null;
            confirmDelete = false;
            editScroll.reset();
        }
    }

    private void newMacro() {
        List<Macro> all = MacroManager.all();
        int n = 1;
        while (MacroManager.find("macro" + n).isPresent()) {
            n++;
        }
        try {
            Macro macro = DefinitionValidator.newMacro("macro" + n, 0, MacroManager.allowlist(), all);
            MacroManager.add(macro);
            select(macro);
            ui.toast("Created " + macro.name(), true);
        } catch (IllegalArgumentException e) {
            ui.error(e.getMessage());
        }
    }

    private void rename(Macro macro, String text) {
        String name = text.trim();
        if (name.equals(macro.name())) {
            return;
        }
        // Reuse the new-macro checks (valid characters, length, not taken) against every other macro.
        List<Macro> others = MacroManager.all().stream().filter(m -> !m.name().equalsIgnoreCase(macro.name())).toList();
        try {
            DefinitionValidator.newMacro(name, 0, MacroManager.allowlist(), others);
        } catch (IllegalArgumentException e) {
            ui.error(e.getMessage());
            return;
        }
        MacroManager.stop(macro.name());
        MacroManager.update(macro.name(), m -> new Macro(name, m.steps(), m.intervalTicks(), m.keySlot(), m.paused()));
        selectedName = name;
        ui.toast("Renamed to " + name, true);
    }

    private void delete(Macro macro) {
        MacroManager.remove(macro.name());
        selectedName = null;
        draft = null;
        confirmDelete = false;
        ui.toast("Deleted " + macro.name(), false);
    }

    /** Macros stop while any menu is open, so Run starts the macro and closes the menu. */
    private void run(Macro macro) {
        if (Minecraft.getInstance().player == null) {
            ui.error("Join a world to run macros");
            return;
        }
        try {
            MacroManager.start(macro);
            closeMenu.run();
        } catch (IllegalStateException e) {
            ui.error(e.getMessage());
        }
    }

    private static String intervalLabel(Macro macro) {
        return macro.intervalTicks() == 0 ? "Manual" : macro.intervalTicks() / 20 + "s";
    }

    /** Steps the repeat interval by one second. Below the minimum it turns into Manual (0). */
    private static int stepInterval(Macro macro, int direction, Allowlist rules) {
        int seconds = macro.intervalTicks() / 20;
        int min = Math.max(1, rules.minIntervalTicks() / 20);
        int max = rules.maxIntervalTicks() / 20;
        if (direction > 0) {
            return seconds == 0 ? min : Math.min(max, seconds + 1);
        }
        return seconds <= min ? 0 : seconds - 1;
    }

    private static void setIntervalSeconds(Macro macro, int seconds) {
        MacroManager.update(macro.name(),
                m -> new Macro(m.name(), m.steps(), seconds * 20, m.keySlot(), m.paused()));
    }

    /**
     * Macro keys work through 10 key slots in Controls. Picking a key here quietly gives the macro a free slot
     * and sets that slot's key, so players never have to think about slots.
     */
    private void setKey(Macro macro, String keyName) {
        if (KeyNames.isNone(keyName)) {
            if (macro.keySlot() != 0) {
                MacroKeys.setKey(macro.keySlot(), InputConstants.UNKNOWN);
                MacroManager.bind(macro.name(), 0);
            }
            return;
        }
        int slot = macro.keySlot();
        if (slot == 0) {
            Set<Integer> used = MacroManager.all().stream().map(Macro::keySlot).collect(Collectors.toSet());
            for (int s = 1; s <= Limits.KEY_SLOTS && slot == 0; s++) {
                if (!used.contains(s)) {
                    slot = s;
                }
            }
            if (slot == 0) {
                ui.error("All " + Limits.KEY_SLOTS + " macro keys are in use");
                return;
            }
        }
        MacroKeys.setKey(slot, KeyNames.parse(keyName));
        MacroManager.bind(macro.name(), slot);
    }

    private void editSteps(Macro macro, Consumer<List<Step>> change) {
        List<Step> steps = new ArrayList<>(macro.steps());
        change.accept(steps);
        MacroManager.update(macro.name(), m -> m.withSteps(steps));
        draft = null;
    }

    private void saveDraft(Macro macro) {
        StepDraft d = draft;
        try {
            Step step = DefinitionValidator.parseStep(d.line(), MacroManager.allowlist());
            List<Step> steps = new ArrayList<>(macro.steps());
            if (d.index < 0) {
                if (steps.size() >= MacroManager.allowlist().maxSteps()) {
                    throw new IllegalArgumentException("This macro is full (max " + MacroManager.allowlist().maxSteps() + " steps)");
                }
                steps.add(step);
            } else {
                steps.set(d.index, step);
            }
            MacroManager.update(macro.name(), m -> m.withSteps(steps));
            draft = null;
            ui.toast(d.index < 0 ? "Step added" : "Step saved", true);
        } catch (IllegalArgumentException e) {
            d.error = e.getMessage();
        }
    }

    private static String actionLabel(ActionType type) {
        return switch (type) {
            case WAIT -> "Wait";
            case HOLD -> "Hold key";
            case SWAP_SLOT -> "Hotbar slot";
            case CHAT -> "Chat";
            case HARVEST_CROPS -> "Harvest";
            case REPLANT_CROPS -> "Replant";
            case GOTO -> "Walk to";
            case MINE -> "Mine";
        };
    }

    /** "DEEPSLATE" becomes "Deepslate". */
    private static String pretty(String name) {
        String lower = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /**
     * The step being added or edited, as the form's raw values. line() turns it into the same text a
     * /macro step command takes, which DefinitionValidator then checks.
     */
    private static final class StepDraft {
        final int index;
        ActionType type = ActionType.WAIT;
        String ticks = "20";
        String holdKey = HeldKey.FORWARD.name();
        int slot = 1;
        String chat = "";
        String crop = CropType.WHEAT.name();
        int radius = 3;
        String x = "";
        String y = "";
        String z = "";
        String block = MineTarget.STONE.name();
        String error;

        /** index is the step being edited, or -1 for a new step. */
        StepDraft(int index) {
            this.index = index;
        }

        static StepDraft of(Step step, int index) {
            StepDraft d = new StepDraft(index);
            d.type = step.type();
            switch (step.type()) {
                case WAIT -> d.ticks = String.valueOf(step.amount());
                case HOLD -> {
                    d.holdKey = step.target();
                    d.ticks = String.valueOf(step.amount());
                }
                case SWAP_SLOT -> d.slot = step.amount();
                case CHAT -> d.chat = step.target();
                case HARVEST_CROPS, REPLANT_CROPS -> {
                    d.crop = step.target();
                    d.radius = step.amount();
                }
                case GOTO -> {
                    String[] xyz = step.target().split(" ");
                    if (xyz.length == 3) {
                        d.x = xyz[0];
                        d.y = xyz[1];
                        d.z = xyz[2];
                    }
                }
                case MINE -> {
                    d.block = step.target();
                    d.radius = step.amount();
                }
            }
            return d;
        }

        void setType(ActionType value) {
            type = value;
            error = null;
            if (type == ActionType.GOTO && x.isEmpty()) {
                useCurrentPosition();
            }
        }

        void useCurrentPosition() {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                BlockPos pos = player.blockPosition();
                x = String.valueOf(pos.getX());
                y = String.valueOf(pos.getY());
                z = String.valueOf(pos.getZ());
            }
        }

        String line() {
            return switch (type) {
                case WAIT -> "WAIT " + ticks;
                case HOLD -> "HOLD " + holdKey + " " + ticks;
                case SWAP_SLOT -> "SWAP_SLOT " + slot;
                case CHAT -> "CHAT " + chat;
                case HARVEST_CROPS, REPLANT_CROPS -> type.name() + " " + crop + " " + radius;
                case GOTO -> "GOTO " + x + " " + y + " " + z;
                case MINE -> "MINE " + block + " " + radius;
            };
        }
    }
}
