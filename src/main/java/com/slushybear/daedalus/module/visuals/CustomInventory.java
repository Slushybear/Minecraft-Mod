package com.slushybear.daedalus.module.visuals;

import com.slushybear.daedalus.mixin.AbstractContainerScreenAccessor;
import com.slushybear.daedalus.mixin.ScreenInvoker;
import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.ModuleRegistry;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.ChoiceSetting;
import com.slushybear.daedalus.module.setting.SliderSetting;
import com.slushybear.daedalus.ui.Draw;
import com.slushybear.daedalus.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Restyles storage menus (chests, barrels, ender chests, shulker boxes, hoppers, dispensers and droppers).
 * The vanilla background picture is replaced with a drawn panel and slots; the items, labels and tooltips are
 * still drawn by vanilla on top. The player inventory and crafting screens are left alone, because their
 * backgrounds also hold the player model and recipe book.
 */
public final class CustomInventory extends Module {
    private final SliderSetting padding = addSetting("Panel", "Extra space around the slots", new SliderSetting("Panel padding", 0, 0, 16, 1).unit("px"));
    private final BoolSetting border = addSetting("Panel", "Thin outline around the panel", new BoolSetting("Border", true));
    private final BoolSetting shadow = addSetting("Panel", "Soft shadow under the panel", new BoolSetting("Shadow", true));
    private final ChoiceSetting colours = addSetting("Panel", "Colour scheme for the panel", new ChoiceSetting("Colours", "Client", "Client", "Vanilla", "Dark"));
    private final SliderSetting dim = addSetting("Background", "How dark the world gets behind it", new SliderSetting("Dim", 55, 0, 100, 1).unit("%"));
    private final BoolSetting blur = addSetting("Background", "Blur the world behind the panel", new BoolSetting("Blur world", false));
    private final ChoiceSetting slots = addSetting("Slots", "How each item slot is drawn", new ChoiceSetting("Slots", "Wells", "Wells", "Flat", "Outlined"));

    /** Colours for one palette. */
    private record Palette(int panel, int border, int slot, int slotDark, int slotLight, int label) {}

    public CustomInventory() {
        super("Custom inventory", "Restyles chests and other storage menus", Category.VISUALS, false);
    }

    public static boolean styles(Screen screen) {
        return ModuleRegistry.CUSTOM_INVENTORY.enabled()
                && (screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen
                    || screen instanceof HopperScreen || screen instanceof DispenserScreen);
    }

    private Palette palette() {
        return switch (colours.get()) {
            case "Vanilla" -> new Palette(0xFFC6C6C6, 0xFF555555, 0xFF8B8B8B, 0xFF373737, 0xFFFFFFFF, 0xFF404040);
            case "Dark" -> new Palette(0xFF1C1C1F, 0xFF3A3A40, 0xFF121214, 0xFF0A0A0B, 0xFF2C2C31, 0xFFDDDDDD);
            default -> new Palette(Theme.panel(), Theme.line(), Theme.bg(), Theme.side(), Theme.cardSelected(), Theme.text());
        };
    }

    /** Called instead of the vanilla background. Returns false to let vanilla draw it after all. */
    public static boolean drawBackground(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
        if (!styles(screen)) {
            return false;
        }
        ModuleRegistry.CUSTOM_INVENTORY.draw(screen, g);
        return true;
    }

    /** The colour for the title and "Inventory" labels, which vanilla draws dark grey. */
    public static int labelColor(Screen screen, int vanillaColor) {
        return styles(screen) ? ModuleRegistry.CUSTOM_INVENTORY.palette().label() : vanillaColor;
    }

    private void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
        if (blur.get()) {
            ((ScreenInvoker) screen).daedalus$extractBlurredBackground(g);
        }
        g.fill(0, 0, screen.width, screen.height, Theme.withAlpha(0x000000, dim.get().floatValue() / 100f));

        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        Palette p = palette();
        int pad = (int) Math.round(padding.get());
        int x = acc.daedalus$leftPos() - pad;
        int y = acc.daedalus$topPos() - pad;
        int w = acc.daedalus$imageWidth() + pad * 2;
        int h = acc.daedalus$imageHeight() + pad * 2;

        if (shadow.get()) {
            for (int i = 3; i >= 1; i--) {
                Draw.round(g, x - i + 2, y - i + 3, w + i * 2 - 2, h + i * 2 - 2, Theme.corner(h) + i, 0x18000000);
            }
        }
        if (border.get()) {
            Draw.box(g, x, y, w, h, p.panel(), p.border());
        } else {
            Draw.round(g, x, y, w, h, Theme.corner(h), p.panel());
        }

        for (Slot slot : screen.getMenu().slots) {
            if (slot.isActive()) {
                drawSlot(g, acc.daedalus$leftPos() + slot.x - 1, acc.daedalus$topPos() + slot.y - 1, p);
            }
        }
    }

    /** One 18x18 slot. Wells are sunken like vanilla: dark top-left edge, light bottom-right edge. */
    private void drawSlot(GuiGraphicsExtractor g, int x, int y, Palette p) {
        int s = 18;
        switch (slots.get()) {
            case "Flat" -> g.fill(x, y, x + s, y + s, p.slot());
            case "Outlined" -> {
                g.fill(x, y, x + s, y + 1, p.border());
                g.fill(x, y + s - 1, x + s, y + s, p.border());
                g.fill(x, y, x + 1, y + s, p.border());
                g.fill(x + s - 1, y, x + s, y + s, p.border());
            }
            default -> {
                g.fill(x, y, x + s, y + s, p.slot());
                g.fill(x, y, x + s - 1, y + 1, p.slotDark());
                g.fill(x, y, x + 1, y + s - 1, p.slotDark());
                g.fill(x + 1, y + s - 1, x + s, y + s, p.slotLight());
                g.fill(x + s - 1, y + 1, x + s, y + s, p.slotLight());
            }
        }
    }
}
