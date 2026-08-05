package cn.unfair.property.properties;

import com.google.gson.JsonObject;
import cn.unfair.property.Property;

import java.util.function.BooleanSupplier;

public class ModeProperty extends Property<Integer> {
    private final String[] modes;

    public ModeProperty(String name, Integer value, String[] modes) {
        this(name, value, modes, null);
    }

    public ModeProperty(String name, Integer value, String[] modes, BooleanSupplier check) {
        super(name, value, check);
        this.modes = modes;
    }

    @Override
    public String getValuePrompt() {
        String[] displayModes = new String[this.modes.length];
        for (int i = 0; i < this.modes.length; i++) {
            displayModes[i] = this.getDisplayModeString(i);
        }
        return String.join(", ", displayModes);
    }

    public String getModeString() {
        int index = this.getValue();
        return index >= 0 && index < this.modes.length ? this.modes[index] : "";
    }

    public String getDisplayModeString() {
        return this.getDisplayModeString(this.getValue());
    }

    public String getDisplayModeString(int index) {
        return index >= 0 && index < this.modes.length ? toDisplayName(this.modes[index]) : "";
    }

    public String[] getDisplayModes() {
        String[] displayModes = new String[this.modes.length];
        for (int i = 0; i < this.modes.length; i++) {
            displayModes[i] = this.getDisplayModeString(i);
        }
        return displayModes;
    }

    @Override
    public String formatValue() {
        String index = this.getDisplayModeString();
        return index.isEmpty() ? "&4?" : String.format("&9%s", index);
    }

    @Override
    public boolean parseString(String string) {
        String valueStr = normalizeName(string);
        for (int i = 0; i < this.modes.length; i++) {
            if (valueStr.equalsIgnoreCase(normalizeName(this.modes[i]))
                    || valueStr.equalsIgnoreCase(normalizeName(this.getDisplayModeString(i)))) {
                return this.setValue(i);
            }
        }
        return false;
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        return this.parseString(jsonObject.get(this.getName()).getAsString());
    }

    @Override
    public void write(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), this.getModeString());
    }

    public void nextMode() {
        int current = this.getValue();
        int next = current + 1;
        if (next >= this.modes.length) {
            next = 0;
        }
        this.setValue(next);
    }

    public void previousMode() {
        int current = this.getValue();
        int prev = current - 1;
        if (prev < 0) {
            prev = this.modes.length - 1;
        }
        this.setValue(prev);
    }
}
