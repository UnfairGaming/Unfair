package cn.unfair.property;

import cn.unfair.module.Module;
import cn.unfair.module.ModuleWithModuleSettings;
import com.google.gson.JsonObject;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public abstract class Property<T> {
    private final String name;
    private final T type;
    private final Predicate<T> validator;
    private final BooleanSupplier visibleChecker;
    private String[] visibleModes;
    private T value;
    private Module owner;
    private Module visibilityOwner;

    protected Property(String name, T value, BooleanSupplier visibleChecker) {
        this(name, value, null, visibleChecker);
    }

    protected Property(String name, T value, Predicate<T> predicate, BooleanSupplier visibleChecker) {
        this.name = name;
        this.type = value;
        this.validator = predicate;
        this.visibleChecker = visibleChecker;
        this.value = value;
        this.owner = null;
    }

    protected static String toDisplayName(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String normalized = value.replace('_', ' ').replace('-', ' ');
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean upperNext = true;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c)) {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ' ') {
                    builder.append(' ');
                }
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString().trim();
    }

    protected static String normalizeName(String value) {
        return value == null ? "" : value.replace("-", "").replace("_", "").replace(" ", "");
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return toDisplayName(this.name);
    }

    public boolean matchesName(String input) {
        return normalizeName(this.name).equalsIgnoreCase(normalizeName(input))
                || normalizeName(this.getDisplayName()).equalsIgnoreCase(normalizeName(input));
    }

    public abstract String getValuePrompt();

    public boolean isVisible() {
        Module visibilitySource = this.visibilityOwner != null ? this.visibilityOwner : this.owner;
        if (this.visibleModes != null && visibilitySource instanceof ModuleWithModuleSettings moduleWithSettings) {
            String currentMode = moduleWithSettings.modeProperty.getModeString();
            boolean modeVisible = false;
            for (String visibleMode : this.visibleModes) {
                if (visibleMode != null && visibleMode.equalsIgnoreCase(currentMode)) {
                    modeVisible = true;
                    break;
                }
            }
            if (!modeVisible) {
                return false;
            }
        }
        return this.visibleChecker == null || this.visibleChecker.getAsBoolean();
    }

    public T getValue() {
        return this.value;
    }

    public boolean resetValue() {
        return this.setValue(this.type);
    }

    public abstract String formatValue();

    public boolean setValue(T object) {
        if (this.validator != null && !this.validator.test(object)) {
            return false;
        } else {
            this.value = object;
            if (this.owner != null) {
                this.owner.verifyValue(this.name);
            }
            return true;
        }
    }

    public void parseString() {
    }

    public void setOwner(Module module) {
        this.owner = module;
    }

    public void setVisibilityOwner(Module module) {
        this.visibilityOwner = module;
    }

    public void setVisibleModes(String... visibleModes) {
        this.visibleModes = visibleModes;
    }

    public abstract boolean parseString(String string);

    public abstract boolean read(JsonObject jsonObject);

    public abstract void write(JsonObject jsonObject);
}
