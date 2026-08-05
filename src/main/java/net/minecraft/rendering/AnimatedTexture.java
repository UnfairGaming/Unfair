package net.minecraft.rendering;

import com.google.gson.*;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.NativeBackedImage;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.rendering.image.Image;
import net.minecraft.util.ResourceLocation;
import net.optifine.util.TextureUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author IzumiiKonata
 * Date: 2025/11/17 22:43
 */
public class AnimatedTexture {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    int frameWidth;
    int imgHeight;

    List<Frame> frames = new ArrayList<>();
    boolean isAnimated = false;
    ResourceLocation locImg;
    long lastFrameTimeMs;
    int curFrame = 0;

    public AnimatedTexture(ResourceLocation locImg) throws IOException {
        this(Minecraft.getMinecraft().getResourceManager().getResource(locImg));
    }

    AnimatedTexture(IResource res) throws IOException {
        this(Objects.requireNonNull(NativeBackedImage.make(res.getInputStream())), Minecraft.getMinecraft().getResourceManager().getResource(ResourceLocation.of(res.getResourceLocation().getResourceDomain(), res.getResourceLocation().getResourcePath() + ".mcmeta")).getInputStream());
    }

    public static JsonObject toJsonObject(Reader reader) {
        return gson.fromJson(reader, JsonObject.class);
    }

    public AnimatedTexture(BufferedImage img, InputStream isMetadata) {
        this.frameWidth = img.getWidth();
        this.imgHeight = img.getHeight();

        CompletableFuture<Void> textureUploadingTask = CompletableFuture.runAsync(() -> this.locImg = registerDynamicTexture("AnimatedTexture", img));

        CompletableFuture<Void> serializeTask = CompletableFuture.runAsync(() -> this.serializeMetadata(img, isMetadata));

        CompletableFuture
                .allOf(textureUploadingTask, serializeTask)
                .whenComplete((result, ex) -> {

                    if (ex != null) {
                        ex.printStackTrace();
                    }

                    if (img instanceof NativeBackedImage n) {
                        try {
                            n.close();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
    }

    public void render(double x, double y, double width, double height) {
        this.render(x, y, width, height, false);
    }

    public void render(double x, double y, double width, double height, boolean customColor) {

        if (this.locImg == null)
            return;

        Image.Type type = customColor ? Image.Type.NoColor : Image.Type.Normal;

        if (!isAnimated) {
            Image.drawNearest(this.locImg, x, y, width, height, type);
            return;
        }

        if (this.frames.isEmpty())
            return;

        Frame frame = this.frames.get(curFrame);
        if (frame.generated) {
            Image.drawNearest(frame.generatedLoc, x, y, width, height, type);
        } else {
            if (!customColor)
                GlStateManager.color(1, 1, 1, 1);
            GlStateManager.enableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            ITextureObject textureObj = Minecraft.getMinecraft().getTextureManager().getTexture(locImg);
            TextureUtils.bindTexture(textureObj.getGlTextureId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer worldrenderer = tessellator.getWorldRenderer();
            worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
            int v = frame.origFrameIndex * frameWidth;
            worldrenderer.pos(x, y + height, 0.0D).tex(0, (double) (v + frameWidth) / imgHeight).endVertex();
            worldrenderer.pos(x + width, y + height, 0.0D).tex(1, (double) (v + frameWidth) / imgHeight).endVertex();
            worldrenderer.pos(x + width, y, 0.0D).tex(1, (double) (v) / imgHeight).endVertex();
            worldrenderer.pos(x, y, 0.0D).tex(0, (double) (v) / imgHeight).endVertex();
            tessellator.draw();
            GlStateManager.enableAlpha();
        }

        long now = System.currentTimeMillis();

        if (now - lastFrameTimeMs >= (long) (frame.frameTime * 50)) {
            lastFrameTimeMs = now;
            curFrame++;
            if (curFrame >= frames.size()) {
                curFrame = 0;
            }
        }
    }

    public static boolean metadataHasAnimationFrames(InputStream is) {
        try {
            JsonObject jObj = toJsonObject(new InputStreamReader(is));

            if (!jObj.has("animation")) {
                return false;
            }

            JsonElement animationElement = jObj.get("animation");

            if (!animationElement.isJsonObject()) {
                return false;
            }

            JsonObject animationObject = animationElement.getAsJsonObject();

            int frameTime = getInt(animationObject, "frametime", 1);

            if (frameTime != 1) {
                if (frameTime < 1)
                    frameTime = 1;
            }

            if (animationObject.has("frames")) {
                try {
                    JsonArray framesArray = animationObject.getAsJsonArray("frames");

                    for (int j = 0; j < framesArray.size(); ++j) {
                        JsonElement frameElement = framesArray.get(j);
                        Frame animationframe = parseAnimationFrame(j, frameTime, frameElement);

                        if (animationframe != null) {
                            return true;
                        }
                    }
                } catch (ClassCastException classcastexception) {
                    throw new JsonParseException("Invalid animation->frames: expected array, was " + animationObject.get("frames"), classcastexception);
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @SneakyThrows
    private void serializeMetadata(BufferedImage img, InputStream is) {
        if (is == null) {
            return;
        }

        JsonObject jObj = toJsonObject(new InputStreamReader(is));

        if (!jObj.has("animation")) {
            return;
        }

        JsonElement animationElement = jObj.get("animation");

        if (!animationElement.isJsonObject()) {
            return;
        }

        JsonObject animationObject = animationElement.getAsJsonObject();

        int frameTime = getInt(animationObject, "frametime", 1);

        if (frameTime != 1) {
            if (frameTime < 1)
                frameTime = 1;
        }

        if (animationObject.has("frames")) {
            this.isAnimated = true;

            try {
                JsonArray framesArray = animationObject.getAsJsonArray("frames");

                for (int j = 0; j < framesArray.size(); ++j) {
                    JsonElement frameElement = framesArray.get(j);
                    Frame animationframe = parseAnimationFrame(j, frameTime, frameElement);

                    if (animationframe != null) {
                        frames.add(animationframe);
                    }
                }
            } catch (ClassCastException classcastexception) {
                throw new JsonParseException("Invalid animation->frames: expected array, was " + animationObject.get("frames"), classcastexception);
            }
        } else {
            this.isAnimated = true;

            int numFrames = img.getHeight() / img.getWidth();

            for (int i = 0; i < numFrames; ++i) {
                this.frames.add(new Frame(i, frameTime));
            }
        }

        boolean interpolate = getBoolean(animationObject);
        if (interpolate) {
            this.generateInterpolatedFrames(img);
        }
    }

    private static int getInt(JsonObject json, String key, int defaultValue) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonPrimitive()) {
            try {
                return element.getAsInt();
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static boolean getBoolean(JsonObject json) {
        JsonElement element = json.get("interpolate");
        if (element != null && element.isJsonPrimitive()) {
            try {
                return element.getAsBoolean();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private void generateInterpolatedFrames(BufferedImage image) {
        List<Frame> copy = new ArrayList<>(frames);
        this.frames.clear();

        for (int i = 0; i < copy.size(); ++i) {
            Frame frame = copy.get(i);
            frame.frameTime *= 0.5;

            this.frames.add(frame);

            if (i < copy.size() - 1) {
                BufferedImage generated = new BufferedImage(image.getWidth(), image.getWidth(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = (Graphics2D) generated.getGraphics();

                g.setColor(new Color(255, 255, 255, 128));
                g.drawImage(
                        image,
                        0, 0,
                        image.getWidth(), image.getWidth(),
                        0, i * image.getWidth(),
                        image.getWidth(), i * image.getWidth() + image.getWidth(),
                        null
                );
                g.drawImage(
                        image,
                        0, 0,
                        image.getWidth(), image.getWidth(),
                        0, (i + 1) * image.getWidth(),
                        image.getWidth(), (i + 1) * image.getWidth() + image.getWidth(),
                        null
                );

                g.dispose();

                Frame gen = new Frame(-1, frame.frameTime);
                gen.generated = true;
                gen.generatedLoc = registerDynamicTexture("ResourcePackPreviewGenerated", generated);
                this.frames.add(gen);
            }
        }

        for (int i = 0; i < this.frames.size(); i++) {
            Frame frame = this.frames.get(i);
            frame.frameIndex = i;
        }
    }

    private static Frame parseAnimationFrame(int frameIndex, int fixedFrameTime, JsonElement frameElement) {
        if (frameElement.isJsonPrimitive()) {
            return new Frame(getInt(frameElement, fixedFrameTime), fixedFrameTime);
        } else if (frameElement.isJsonObject()) {
            JsonObject frameObject = frameElement.getAsJsonObject();
            int time = getInt(frameObject, "time", -1);

            if (frameObject.has("time")) {
                if (time < 1)
                    time = 1;
            }

            int idx = getInt(frameObject, "index", 0);
            if (idx < 0)
                idx = 0;
            return new Frame(idx, time);
        } else {
            return null;
        }
    }

    private static int getInt(JsonElement element, int defaultValue) {
        if (element != null && element.isJsonPrimitive()) {
            try {
                return element.getAsInt();
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static class Frame {
        @Getter
        private int frameIndex;
        private final int origFrameIndex;
        @Getter
        private double frameTime;

        @Getter
        @Setter
        private boolean generated = false;

        @Getter
        @Setter
        private ResourceLocation generatedLoc;

        public Frame(int idx) {
            this(idx, -1);
        }

        public Frame(int idx, double time) {
            this.frameIndex = idx;
            this.origFrameIndex = idx;
            this.frameTime = time;
        }

        public boolean hasNoTime() {
            return this.frameTime == -1;
        }
    }

    private static ResourceLocation registerDynamicTexture(String name, BufferedImage image) {
        Minecraft minecraft = Minecraft.getMinecraft();
        Callable<ResourceLocation> task = () -> minecraft.getTextureManager().getDynamicTextureLocation(name, new DynamicTexture(image));

        try {
            if (minecraft.isCallingFromMinecraftThread()) {
                return task.call();
            }

            return minecraft.addScheduledTask(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while registering dynamic texture", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to register dynamic texture", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register dynamic texture", e);
        }
    }
}
