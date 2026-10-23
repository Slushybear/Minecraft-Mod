package com.slushybear.daedalus.module.visuals;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.ModuleRegistry;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.ChoiceSetting;
import com.slushybear.daedalus.ui.Theme;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Lists your enabled modules in a top corner of the screen, longest name first, with an accent bar on the edge. */
public final class ArrayListHud extends Module {
    private static final int LINE_H = 11;

    private final ChoiceSetting side = addSetting(new ChoiceSetting("Side", "Right", "Right", "Left"));
    private final BoolSetting background = addSetting(new BoolSetting("Background", true));

    public ArrayListHud() {
        super("ArrayList", "Lists enabled modules on screen", Category.VISUALS, false);
    }

    @Override
    public void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("daedalus", "arraylist"), this::draw);
    }

    private void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!enabled()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        List<String> names = ModuleRegistry.all().stream()
                .filter(m -> m.enabled() && m != this)
                .map(Module::name)
                .sorted(Comparator.comparingInt((String name) -> font.width(name)).reversed())
                .toList();
        boolean right = side.get().equals("Right");
        int y = 2;
        for (String name : names) {
            int w = font.width(name) + 8;
            int x = right ? g.guiWidth() - w - 2 : 2;
            if (background.get()) {
                g.fill(x, y, x + w, y + LINE_H, Theme.withAlpha(Theme.side(), 0.75f));
            }
            int barX = right ? x + w - 1 : x;
            g.fill(barX, y, barX + 1, y + LINE_H, Theme.accent());
            g.text(font, name, x + 4, y + 2, Theme.text(), true);
            y += LINE_H;
        }
    }
}
