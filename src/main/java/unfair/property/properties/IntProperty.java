package unfair.property.properties;

import com.google.gson.JsonObject;
import unfair.property.Property;

import java.util.function.BooleanSupplier;

public class IntProperty extends Property<Integer> {
    private final Integer minimum;
    private final Integer maximum;

    public IntProperty(String name, Integer value, Integer minimum, Integer maximum) {
        this(name, value, minimum, maximum, null);
    }

    public IntProperty(
            String name, Integer value, Integer minimum, Integer maximum, BooleanSupplier check
    ) {
        super(name, value, v -> v >= minimum && v <= maximum, check);
        this.minimum = minimum;
        this.maximum = maximum;
    }

    @Override
    public String getValuePrompt() {
        return String.format("%d-%d", this.minimum, this.maximum);
    }

    @Override
    public String formatValue() {
        return String.format("&e%s", this.getValue());
    }

    @Override
    public boolean parseString(String string) {
        return this.setValue(Integer.parseInt(string));
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        return this.setValue(jsonObject.get(this.getName()).getAsNumber().intValue());
    }

    @Override
    public void write(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), this.getValue());
    }

    public Integer getMinimum() {
        return minimum;
    }

    public Integer getMaximum() {
        return maximum;
    }
}
