package unfair.property;

import com.google.gson.JsonObject;
import unfair.module.Module;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public abstract class Property<T> {
    private final String name;
    private final T type;
    private final Predicate<T> validator;
    private final BooleanSupplier visibleChecker;
    private T value;
    private Module owner;

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
            this.value = (T) object;
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

    public abstract boolean parseString(String string);

    public abstract boolean read(JsonObject jsonObject);

    public abstract void write(JsonObject jsonObject);
}
