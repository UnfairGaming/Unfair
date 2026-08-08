package cn.unfair.property;

import cn.unfair.module.Module;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class PropertyManager {
    public LinkedHashMap<Class<?>, ArrayList<Property<?>>> properties = new LinkedHashMap<>();

    public Property<?> getProperty(Module module, String string) {
        if (module == null) {
            return null;
        }

        ArrayList<Property<?>> moduleProperties = properties.get(module.getClass());
        if (moduleProperties == null) {
            return null;
        }

        for (Property<?> property : moduleProperties) {
            if (property.matchesName(string)) {
                return property;
            }
        }
        return null;
    }
}
