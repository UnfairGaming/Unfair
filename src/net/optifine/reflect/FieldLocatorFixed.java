package net.optifine.reflect;

import java.lang.reflect.Field;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class FieldLocatorFixed implements IFieldLocator
{
    private Field field;

    public FieldLocatorFixed(Field field)
    {
        this.field = field;
    }

    public Field getField()
    {
        return this.field;
    }
}
