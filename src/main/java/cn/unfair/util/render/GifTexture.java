package cn.unfair.util.render;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GifTexture {
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Unfair-Gif");
        thread.setDaemon(true);
        return thread;
    });
    private static final String DIRECTORY = "/assets/minecraft/unfair/pet/";
    private static final String META_FORMAT = "javax_imageio_gif_image_1.0";
    private static final int CELL_LIMIT = 256;

    private final String name;
    private final String resource;
    private volatile Sheet pending;
    private boolean loading;
    private int texture = -1;
    private float[] frames;
    private long[] timeline;
    private long duration = 1L;
    private float aspect = 1.0F;

    public GifTexture(String name) {
        this.name = name;
        this.resource = DIRECTORY + name + ".gif";
    }

    public String getName() {
        return this.name;
    }

    public float getAspect() {
        return this.aspect;
    }

    public boolean isReady() {
        return this.texture != -1;
    }

    public void prepare() {
        if (this.texture != -1) {
            return;
        }
        Sheet sheet = this.pending;
        if (sheet == null) {
            if (!this.loading) {
                this.loading = true;
                int maximum = limit();
                LOADER.execute(() -> this.pending = read(this.resource, maximum));
            }
            return;
        }
        this.pending = null;
        this.upload(sheet);
    }

    public void release() {
        if (this.texture != -1) {
            GlStateManager.deleteTexture(this.texture);
            this.texture = -1;
        }
    }

    public void draw(float x, float y, float width, float height, long time) {
        if (this.texture == -1) {
            return;
        }
        int index = this.index(time) * 4;
        float u0 = this.frames[index];
        float v0 = this.frames[index + 1];
        float u1 = this.frames[index + 2];
        float v1 = this.frames[index + 3];

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.bindTexture(this.texture);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();
        GlStateManager.bindTexture(0);
        GlStateManager.disableBlend();
    }

    private static int limit() {
        int maximum = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        return maximum <= 0 ? CELL_LIMIT : Math.min(CELL_LIMIT, maximum);
    }

    private void upload(Sheet sheet) {
        this.texture = GlStateManager.generateTexture();
        GlStateManager.bindTexture(this.texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        IntBuffer buffer = BufferUtils.createIntBuffer(sheet.pixels.length);
        buffer.put(sheet.pixels).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, sheet.width, sheet.height, 0,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        GlStateManager.bindTexture(0);

        float insetU = 0.5F / sheet.width;
        float insetV = 0.5F / sheet.height;
        float[] coordinates = new float[sheet.timeline.length * 4];
        for (int i = 0; i < sheet.timeline.length; i++) {
            int column = i % sheet.columns;
            int row = i / sheet.columns;
            coordinates[i * 4] = (column * sheet.frameWidth + insetU) / sheet.width;
            coordinates[i * 4 + 1] = (row * sheet.frameHeight + insetV) / sheet.height;
            coordinates[i * 4 + 2] = ((column + 1) * sheet.frameWidth - insetU) / sheet.width;
            coordinates[i * 4 + 3] = ((row + 1) * sheet.frameHeight - insetV) / sheet.height;
        }

        this.frames = coordinates;
        this.timeline = sheet.timeline;
        this.duration = sheet.duration;
        this.aspect = sheet.aspect;
    }

    private int index(long time) {
        long position = Math.floorMod(time, this.duration);
        for (int i = 0; i < this.timeline.length; i++) {
            if (position < this.timeline[i]) {
                return i;
            }
        }
        return this.timeline.length - 1;
    }

    private static Sheet read(String resource, int maximum) {
        try (InputStream input = GifTexture.class.getResourceAsStream(resource)) {
            if (input == null) {
                return null;
            }
            ImageInputStream stream = ImageIO.createImageInputStream(input);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            reader.setInput(stream, false, false);
            int count = reader.getNumImages(true);
            if (count <= 0) {
                return null;
            }

            int sourceWidth = reader.getWidth(0);
            int sourceHeight = reader.getHeight(0);
            int columns = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
            int rows = (count + columns - 1) / columns;
            int cell = Math.max(1, Math.min(CELL_LIMIT, Math.min(maximum / columns, maximum / rows)));
            float scale = (float) cell / Math.max(sourceWidth, sourceHeight);
            int frameWidth = Math.max(1, Math.round(sourceWidth * scale));
            int frameHeight = Math.max(1, Math.round(sourceHeight * scale));
            int sheetWidth = columns * frameWidth;
            int sheetHeight = rows * frameHeight;

            BufferedImage sheetImage = new BufferedImage(sheetWidth, sheetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sheetGraphics = sheetImage.createGraphics();
            sheetGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            BufferedImage canvas = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D canvasGraphics = canvas.createGraphics();

            long[] timeline = new long[count];
            long elapsed = 0L;
            int previousX = 0;
            int previousY = 0;
            int previousWidth = 0;
            int previousHeight = 0;
            boolean clear = false;

            for (int i = 0; i < count; i++) {
                IIOMetadataNode root = metadata(reader, i);
                IIOMetadataNode descriptor = child(root, "ImageDescriptor");
                IIOMetadataNode control = child(root, "GraphicControlExtension");

                int left = attribute(descriptor, "imageLeftPosition");
                int top = attribute(descriptor, "imageTopPosition");
                int delay = Math.max(2, attribute(control, "delayTime"));
                String disposal = control == null ? "none" : control.getAttribute("disposalMethod");
                BufferedImage frame = reader.read(i);

                if (clear) {
                    canvasGraphics.setComposite(AlphaComposite.Clear);
                    canvasGraphics.fillRect(previousX, previousY, previousWidth, previousHeight);
                }
                canvasGraphics.setComposite(AlphaComposite.SrcOver);
                canvasGraphics.drawImage(frame, left, top, null);

                clear = "restoreToBackgroundColor".equals(disposal);
                previousX = left;
                previousY = top;
                previousWidth = frame.getWidth();
                previousHeight = frame.getHeight();

                int column = i % columns;
                int row = i / columns;
                sheetGraphics.drawImage(canvas, column * frameWidth, row * frameHeight,
                        frameWidth, frameHeight, null);

                elapsed += delay * 10L;
                timeline[i] = elapsed;
            }

            canvasGraphics.dispose();
            sheetGraphics.dispose();
            reader.dispose();
            stream.close();

            int[] pixels = new int[sheetWidth * sheetHeight];
            sheetImage.getRGB(0, 0, sheetWidth, sheetHeight, pixels, 0, sheetWidth);

            Sheet sheet = new Sheet();
            sheet.pixels = pixels;
            sheet.width = sheetWidth;
            sheet.height = sheetHeight;
            sheet.columns = columns;
            sheet.frameWidth = frameWidth;
            sheet.frameHeight = frameHeight;
            sheet.timeline = timeline;
            sheet.duration = Math.max(1L, elapsed);
            sheet.aspect = (float) sourceWidth / (float) sourceHeight;
            return sheet;
        } catch (Exception exception) {
            return null;
        }
    }

    private static IIOMetadataNode metadata(ImageReader reader, int index) throws Exception {
        IIOMetadata information = reader.getImageMetadata(index);
        return (IIOMetadataNode) information.getAsTree(META_FORMAT);
    }

    private static IIOMetadataNode child(IIOMetadataNode node, String name) {
        if (node == null) {
            return null;
        }
        for (int i = 0; i < node.getLength(); i++) {
            if (node.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) node.item(i);
            }
        }
        return null;
    }

    private static int attribute(IIOMetadataNode node, String name) {
        if (node == null) {
            return 0;
        }
        String value = node.getAttribute(name);
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    private static final class Sheet {
        private int[] pixels;
        private int width;
        private int height;
        private int columns;
        private int frameWidth;
        private int frameHeight;
        private long[] timeline;
        private long duration;
        private float aspect;
    }
}
