package net.minecraft.rendering;

import lombok.SneakyThrows;
import net.minecraft.client.renderer.texture.NativeBackedImage;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author IzumiiKonata
 * Date: 2025/10/12 21:37
 */
public class ResPackPreview {

    private final IResourcePack pack;
    private final String path;
    private final AnimatedTexture animatedTexture;

    @SneakyThrows
    public ResPackPreview(IResourcePack pack, NativeBackedImage img, String path) {
        this.pack = pack;
        this.path = path;
        InputStream meta = null;
        try {
            meta = pack.getInputStream(ResourceLocation.of(path + ".mcmeta"));
        } catch (IOException ignored) {
        }
        animatedTexture = new AnimatedTexture(img, meta);
    }

    public void cleanUp() {

    }

    public void render(double x, double y, double width, double height) {
        animatedTexture.render(x, y, width, height);
    }

}
