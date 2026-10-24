package com.slushybear.daedalus.module.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettingTest {
    @Test
    void sliderSnapsToItsStepAndStaysInRange() {
        SliderSetting slider = new SliderSetting("Zoom", 4, 1.5, 10, 0.5).unit("x");
        slider.set(4.3);
        assertEquals(4.5, slider.get());
        slider.set(50.0);
        assertEquals(10.0, slider.get());
        slider.setFraction(0);
        assertEquals("1.5x", slider.display());
    }

    @Test
    void invalidTextStaysAsADraftAndIsNotApplied() {
        TextSetting text = new TextSetting("Label", "ZOOM", t -> t.contains("!") ? "No ! allowed" : null);
        text.edit("ZOOM!");
        assertEquals("ZOOM", text.get());
        assertEquals("ZOOM!", text.shown());
        assertEquals("No ! allowed", text.draftError());

        text.edit("ZOOMED");
        assertEquals("ZOOMED", text.get());
        assertNull(text.draftError());
    }

    @Test
    void textOverTheLimitIsRejected() {
        TextSetting text = new TextSetting("Label", "");
        text.edit("x".repeat(TextSetting.MAX_LENGTH + 1));
        assertEquals("", text.get());
        assertEquals("Max " + TextSetting.MAX_LENGTH + " characters", text.draftError());
    }

    @Test
    void choiceIgnoresUnknownOptions() {
        ChoiceSetting choice = new ChoiceSetting("Activation", "Hold", "Hold", "Toggle");
        choice.set("Sometimes");
        assertEquals("Hold", choice.get());
    }

    @Test
    void multiChoiceKeepsOnlyRealOptions() {
        MultiChoiceSetting multi = new MultiChoiceSetting("Blocks", List.of("Diamond", "Gold"), "Diamond");
        multi.set(Set.of("Gold", "Bedrock"));
        assertEquals(Set.of("Gold"), multi.get());
        multi.toggle("Diamond");
        assertEquals(Set.of("Gold", "Diamond"), multi.get());
    }

    @Test
    void resetGoesBackToTheDefault() {
        BoolSetting bool = new BoolSetting("Smooth", true);
        bool.toggle();
        bool.reset();
        assertEquals(true, bool.get());
    }
}
