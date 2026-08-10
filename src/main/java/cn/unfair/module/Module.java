package cn.unfair.module;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.util.KeyBindUtil;
import lombok.Getter;
import lombok.Setter;

public abstract class Module {
    @Getter
    protected final String name;
    @Getter
    protected final Category category;
    protected final boolean defaultEnabled;
    protected final int defaultKey;
    protected final boolean defaultHidden;
    @Getter
    protected boolean enabled;
    @Setter
    @Getter
    protected int key;
    @Setter
    @Getter
    protected boolean hidden;

    public Module(String name, boolean enabled) {
        this(name, enabled, false);
    }

    public Module(String name, boolean enabled, boolean hidden) {
        this.name = name;
        this.category = Category.fromClass(this.getClass());
        this.enabled = this.defaultEnabled = enabled;
        this.key = this.defaultKey = 0;
        this.hidden = this.defaultHidden = hidden;
    }

    public String formatModule() {
        return String.format(
                "%s%s &r(%s&r)",
                this.key == 0 ? "" : String.format("&l[%s] &r", KeyBindUtil.getKeyName(this.key)),
                this.name,
                this.enabled ? "&a&lON" : "&c&lOFF"
        );
    }

    public String[] getSuffix() {
        return new String[0];
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                this.onEnabled();
            } else {
                this.onDisabled();
            }
        }
    }

    public boolean toggle() {
        boolean enabled = !this.enabled;
        this.setEnabled(enabled);
        if (this.enabled == enabled) {
            if (((HUD) Unfair.moduleManager.modules.get(HUD.class)).toggleSound.getValue()) {
                Unfair.moduleManager.playSound(enabled);
            }
            return true;
        } else {
            return false;
        }
    }

    public void resetSettings() {
        this.key = this.defaultKey;
        this.hidden = this.defaultHidden;
    }

    public void onEnabled() {
    }

    public void onDisabled() {
    }

    public void verifyValue(String string) {
    }
}
