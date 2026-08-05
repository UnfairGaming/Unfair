package net.optifine.shaders;

import java.io.InputStream;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ShaderPackNone implements IShaderPack
{
    public void close()
    {
    }

    public InputStream getResourceAsStream(String resName)
    {
        return null;
    }

    public boolean hasDirectory(String name)
    {
        return false;
    }

    public String getName()
    {
        return "OFF";
    }
}
