package net.optifine.shaders;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public interface ICustomTexture
{
    int getTextureId();

    int getTextureUnit();

    void deleteTexture();

    int getTarget();
}
