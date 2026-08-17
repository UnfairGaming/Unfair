package cn.unfair.module;

import lombok.Getter;

@Getter
public enum Category {
    COMBAT("Combat", "cn.unfair.module.modules.combat"),
    PLAYER("Player", "cn.unfair.module.modules.player"),
    MOVEMENT("Movement", "cn.unfair.module.modules.movement"),
    RENDER("Render", "cn.unfair.module.modules.render"),
    WORLD("World", "cn.unfair.module.modules.world"),
    MISC("Misc", "cn.unfair.module.modules.misc"),
    EXPLOIT("Exploit", "cn.unfair.module.modules.exploit");

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
