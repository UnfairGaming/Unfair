package cn.unfair.module;

import cn.unfair.property.Property;
import cn.unfair.property.properties.ModeProperty;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class ModuleWithModuleSettings extends Module {
    private final SubModule[] subModules;
    public final ModeProperty modeProperty;

    public ModuleWithModuleSettings(String name, boolean enabled, SubModule... subModules) {
        this(name, enabled, "Mode", subModules);
    }

    public ModuleWithModuleSettings(String name, boolean enabled, String modeName, SubModule... subModules) {
        super(name, enabled);
        this.subModules = subModules;
        String[] modeNames = Arrays.stream(subModules).map(Module::getName).toArray(String[]::new);
        this.modeProperty = new ModeProperty(modeName, 0, modeNames);
    }

    @Override
    public void onEnabled() {
        syncSubModules();
    }

    @Override
    public void onDisabled() {
        for (SubModule subModule : subModules) {
            subModule.setEnabled(false);
        }
    }

    @Override
    public void verifyValue(String name) {
        if (modeProperty.getName().replace("-", "").equalsIgnoreCase(name.replace("-", ""))) {
            syncSubModules();
        }
    }

    public void syncSubModules() {
        if (!isEnabled()) return;
        String currentMode = modeProperty.getModeString();
        for (SubModule subModule : subModules) {
            subModule.setEnabled(subModule.getName().equals(currentMode));
        }
    }

    public SubModule getCurrentSubModule() {
        String currentMode = modeProperty.getModeString();
        return Arrays.stream(subModules)
                .filter(module -> module.getName().equals(currentMode))
                .findFirst()
                .orElse(subModules.length == 0 ? null : subModules[0]);
    }

    public List<SubModule> getSubModules() {
        return Arrays.asList(subModules);
    }

    public List<Property<?>> collectSubModuleProperties() {
        List<Property<?>> result = new ArrayList<>();
        for (SubModule subModule : subModules) {
            Class<?> clazz = subModule.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    try {
                        Object obj = field.get(subModule);
                        if (obj instanceof Property<?>) {
                            Property<?> property = (Property<?>) obj;
                            property.setOwner(subModule);
                            property.setVisibilityOwner(this);
                            property.setVisibleModes(subModule.getName());
                            result.add(property);
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
        return result;
    }
}
