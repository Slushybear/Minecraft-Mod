package com.slushybear.daedalus.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A number between min and max that moves in steps. Shown as a slider. */
public final class SliderSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private String unit = "";

    public SliderSetting(String name, double defaultValue, double min, double max, double step) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    /** Text shown after the number, like "%" or " blocks". Returns this so it can follow the constructor. */
    public SliderSetting unit(String text) {
        unit = text;
        return this;
    }

    /** The value with its unit, as the menu shows it. */
    public String display() {
        return format() + unit;
    }

    /** Snaps to the nearest step and keeps the value inside min and max. */
    @Override
    public void set(Double newValue) {
        double snapped = min + Math.round((newValue - min) / step) * step;
        snapped = Math.max(min, Math.min(max, snapped));
        value = Math.round(snapped * 1_000_000) / 1_000_000.0;
    }

    /** How far along the slider the value is, from 0 to 1. */
    public double fraction() {
        return (value - min) / (max - min);
    }

    public void setFraction(double fraction) {
        set(min + fraction * (max - min));
    }

    /** The value as text: whole numbers for whole steps, otherwise one or two decimals. */
    public String format() {
        if (step >= 1) {
            return String.valueOf(Math.round(value));
        }
        return String.format(step < 0.1 ? "%.2f" : "%.1f", value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        set(json.getAsDouble());
    }
}
