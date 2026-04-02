package unfair.property.properties;

import com.google.gson.JsonObject;
import unfair.property.Property;

import java.util.function.BooleanSupplier;

public class BooleanProperty extends Property<Boolean> {
    public BooleanProperty(String name, Boolean value) {
        this(name, value, null);
    }

    public BooleanProperty(String name, Boolean value, BooleanSupplier booleanSupplier) {
        super(name, value, booleanSupplier);
    }

    @Override
    public String getValuePrompt() {
        return "true/false";
    }

    @Override
    public String formatValue() {
        return this.getValue() ? "&atrue" : "&cfalse";
    }

    @Override
    public boolean parseString(String string) {
        if (string == null) {
            return this.setValue(!(Boolean) this.getValue());
        } else if (string.equalsIgnoreCase("true") || string.equalsIgnoreCase("on") || string.equalsIgnoreCase("1")) {
            return this.setValue(true);
        } else {
            return (string.equalsIgnoreCase("false") || string.equalsIgnoreCase("off") || string.equalsIgnoreCase("0")) && this.setValue(false);
        }
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        return this.setValue(jsonObject.get(this.getName()).getAsBoolean());
    }

    @Override
    public void write(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), this.getValue());
    }
}
