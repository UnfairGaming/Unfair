package net.optifine.shaders;

import java.io.InputStream;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public interface IShaderPack {
    String getName();

    InputStream getResourceAsStream(String var1);

    boolean hasDirectory(String var1);

    void close();
}
