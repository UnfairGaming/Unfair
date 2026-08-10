package cn.unfair.module;

import lombok.Getter;

@Getter
public enum Category {
    COMBAT("Combat", "cn.unfair.module.modules.combat"),
    MOVEMENT("Movement", "cn.unfair.module.modules.movement"),
    RENDER("Render", "cn.unfair.module.modules.render"),
    PLAYER("Player", "cn.unfair.module.modules.player"),
    MISC("Misc", "cn.unfair.module.modules.misc");

    private final String displayName;
    private final String packageName;

    Category(String displayName, String packageName) {
        this.displayName = displayName;
        this.packageName = packageName;
    }

    public static Category fromClass(Class<?> clazz) {
        String className = clazz.getName();
        for (Category category : values()) {
            if (className.startsWith(category.packageName)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown category for class: " + className);
    }

}
