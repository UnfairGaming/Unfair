package cn.unfair.property;

import com.google.gson.JsonObject;
import cn.unfair.config.Config;
import cn.unfair.module.Module;
import cn.unfair.module.ModuleWithModuleSettings;

import java.util.Objects;
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

    protected Property(String name, Object value, BooleanSupplier visibleChecker) {
        this(name, value, null, visibleChecker);
    }

    protected Property(String name, Object value, Predicate<T> predicate, BooleanSupplier visibleChecker) {
        this.name = name;
        this.type = (T) value;
        this.validator = predicate;
        this.visibleChecker = visibleChecker;
        this.value = (T) value;
        this.owner = null;
    }

    public String getName() {
        return this.name;
    }

    public abstract String getValuePrompt();

    public boolean isVisible() {
        Module visibilitySource = this.visibilityOwner != null ? this.visibilityOwner : this.owner;
        if (this.visibleModes != null && visibilitySource instanceof ModuleWithModuleSettings) {
            ModuleWithModuleSettings moduleWithSettings = (ModuleWithModuleSettings) visibilitySource;
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

    public abstract String formatValue();

    public boolean setValue(Object object) {
        if (this.validator != null && !this.validator.test((T) object)) {
            return false;
        } else {
            T oldValue = this.value;
            this.value = (T) object;
            if (this.owner != null) {
                this.owner.verifyValue(this.name);
            }
            if (!Objects.equals(oldValue, this.value)) {
                Config.markDirty();
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
