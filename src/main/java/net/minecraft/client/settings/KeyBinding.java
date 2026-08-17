package net.minecraft.client.settings;

import cn.unfair.event.EventManager;
import cn.unfair.events.SwapItemEvent;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.IntHashMap;

import java.util.List;
import java.util.Set;

public class KeyBinding implements Comparable<KeyBinding> {
    private static final List<KeyBinding> keybindArray = Lists.newArrayList();
    private static final IntHashMap<KeyBinding> hash = new IntHashMap<>();
    private static final Set<String> keybindSet = Sets.newHashSet();
    @Getter
    private final String keyDescription;
    @Getter
    private final int keyCodeDefault;
    @Getter
    private final String keyCategory;
    /**
     * Is the key held down?
     */
    public boolean pressed;
    @Getter
    @Setter
    private int keyCode;
    private int pressTime;

    public KeyBinding(String description, int keyCode, String category) {
        this.keyDescription = description;
        this.keyCode = keyCode;
        this.keyCodeDefault = keyCode;
        this.keyCategory = category;
        keybindArray.add(this);
        hash.addKey(keyCode, this);
        keybindSet.add(category);
    }

    public static void onTick(int keyCode) {
        if (keyCode != 0) {
            KeyBinding keybinding = hash.lookup(keyCode);

            if (keybinding != null) {
                ++keybinding.pressTime;
            }
        }
    }

    public static void setKeyBindState(int keyCode, boolean pressed) {
        if (keyCode != 0) {
            KeyBinding keybinding = hash.lookup(keyCode);

            if (keybinding != null) {
                keybinding.pressed = pressed;
            }
        }
    }

    public static void unPressAllKeys() {
        for (KeyBinding keybinding : keybindArray) {
            keybinding.unpressKey();
        }
    }

    public static void resetKeyBindingArrayAndHash() {
        hash.clearMap();

        for (KeyBinding keybinding : keybindArray) {
            hash.addKey(keybinding.keyCode, keybinding);
        }
    }

    public static Set<String> getKeybinds() {
        return keybindSet;
    }

    /**
     * Returns true if the key is pressed (used for continuous querying). Should be used in tickers.
     */
    public boolean isKeyDown() {
        return this.pressed;
    }

    /**
     * Returns true on the initial key press. Should be used in key
     * events.
     */
    public boolean isPressed() {
        if (this.pressTime == 0) {
            return false;
        } else {
            --this.pressTime;
            Minecraft mc = Minecraft.getMinecraft();

            for (int i = 0; i < 9; i++) {
                if (mc.gameSettings.keyBindsHotbar[i].getKeyDescription().equals(this.keyDescription)) {
                    SwapItemEvent event = new SwapItemEvent(i, 0);
                    EventManager.call(event);

                    if (event.isCancelled()) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    private void unpressKey() {
        this.pressTime = 0;
        this.pressed = false;
    }

    public int compareTo(KeyBinding p_compareTo_1_) {
        int i = I18n.format(this.keyCategory).compareTo(I18n.format(p_compareTo_1_.keyCategory));

        if (i == 0) {
            i = I18n.format(this.keyDescription).compareTo(I18n.format(p_compareTo_1_.keyDescription));
        }

        return i;
    }
}
