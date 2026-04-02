package unfair.module;

public enum Category {
    COMBAT("Combat", "unfair.module.modules.combat"),
    MOVEMENT("Movement", "unfair.module.modules.movement"),
    RENDER("Render", "unfair.module.modules.render"),
    PLAYER("Player", "unfair.module.modules.player"),
    MISC("Misc", "unfair.module.modules.misc");

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

    public String getDisplayName() {
        return displayName;
    }

    public String getPackageName() {
        return packageName;
    }
}
