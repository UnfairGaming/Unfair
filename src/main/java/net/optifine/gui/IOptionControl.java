package net.optifine.gui;

import net.minecraft.client.settings.GameSettings;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public interface IOptionControl {
    GameSettings.Options getOption();
}
