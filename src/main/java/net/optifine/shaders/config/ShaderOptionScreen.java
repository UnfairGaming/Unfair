package net.optifine.shaders.config;

import net.optifine.shaders.Shaders;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ShaderOptionScreen extends ShaderOption
{
    public ShaderOptionScreen(String name)
    {
        super(name, null, null, new String[0], null, null);
    }

    public String getNameText()
    {
        return Shaders.translate("screen." + this.getName(), this.getName());
    }

    public String getDescriptionText()
    {
        return Shaders.translate("screen." + this.getName() + ".comment", null);
    }
}
