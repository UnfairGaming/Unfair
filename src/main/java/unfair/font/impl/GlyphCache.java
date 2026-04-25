package unfair.font.impl;

import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;
import java.util.List;

/**
 * The GlyphCache class is responsible for caching pre-rendered images of every glyph using OpenGL textures.
 */
public class GlyphCache {
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int STRING_WIDTH = 256;
    private static final int STRING_HEIGHT = 64;
    private static final int GLYPH_BORDER = 1;
    private static final int GLYPH_PAD = 1;
    private static final Color BACK_COLOR = new Color(255, 255, 255, 0);

    private int fontSize = 18;
    private boolean antiAliasEnabled = false;

    private BufferedImage stringImage;
    private Graphics2D stringGraphics;

    private final BufferedImage glyphCacheImage = new BufferedImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    private final Graphics2D glyphCacheGraphics = glyphCacheImage.createGraphics();
    private final FontRenderContext fontRenderContext = glyphCacheGraphics.getFontRenderContext();
    private final int[] imageData = new int[TEXTURE_WIDTH * TEXTURE_HEIGHT];
    private final IntBuffer imageBuffer = ByteBuffer.allocateDirect(4 * TEXTURE_WIDTH * TEXTURE_HEIGHT).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
    private final IntBuffer singleIntBuffer = GLAllocation.createDirectIntBuffer(1);
    private final List<Font> allFonts = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts());
    private final List<Font> usedFonts = new ArrayList<>();

    private int textureName;
    private final LinkedHashMap<Font, Integer> fontCache = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Entry> glyphCache = new LinkedHashMap<>();

    private int cachePosX = GLYPH_BORDER;
    private int cachePosY = GLYPH_BORDER;
    private int cacheLineHeight = 0;

    static class Entry {
        public int textureName;
        public int width;
        public int height;
        public int offsetX;
        public int offsetY;
        public float u1;
        public float v1;
        public float u2;
        public float v2;
    }

    public GlyphCache() {
        glyphCacheGraphics.setBackground(BACK_COLOR);
        glyphCacheGraphics.setComposite(AlphaComposite.Src);
        allocateGlyphCacheTexture();
        allocateStringImage(STRING_WIDTH, STRING_HEIGHT);
        GraphicsEnvironment.getLocalGraphicsEnvironment().preferLocaleFonts();
    }

    void setDefaultFont(Font font, int size, boolean antiAlias) {
        usedFonts.clear();
        usedFonts.add(font);
        fontSize = size;
        antiAliasEnabled = antiAlias;
        setRenderingHints();
        resetCache();
    }

    GlyphVector layoutGlyphVector(Font font, char[] text, int start, int limit, int layoutFlags) {
        if (!fontCache.containsKey(font)) {
            fontCache.put(font, fontCache.size());
        }
        return font.layoutGlyphVector(fontRenderContext, text, start, limit, layoutFlags);
    }

    Font lookupFont(char[] text, int start, int limit, int style) {
        Iterator<Font> iterator = usedFonts.iterator();
        while (iterator.hasNext()) {
            Font font = iterator.next();
            if (font.canDisplayUpTo(text, start, limit) != start) {
                return font.deriveFont(style, fontSize);
            }
        }

        iterator = allFonts.iterator();
        while (iterator.hasNext()) {
            Font font = iterator.next();
            if (font.canDisplayUpTo(text, start, limit) != start) {
                usedFonts.add(font);
                return font.deriveFont(style, fontSize);
            }
        }

        Font font = usedFonts.get(0);
        return font.deriveFont(style, fontSize);
    }

    Entry lookupGlyph(Font font, int glyphCode) {
        long fontKey = (long) fontCache.get(font) << 32;
        return glyphCache.get(fontKey | glyphCode);
    }

    private void resetCache() {
        glyphCache.clear();
        fontCache.clear();
        cachePosX = GLYPH_BORDER;
        cachePosY = GLYPH_BORDER;
        cacheLineHeight = 0;
        allocateGlyphCacheTexture();
    }

    void cacheGlyphs(Font font, char[] text, int start, int limit, int layoutFlags) {
        GlyphVector vector = layoutGlyphVector(font, text, start, limit, layoutFlags);
        Rectangle vectorBounds = null;
        long fontKey = (long) fontCache.get(font) << 32;

        int numGlyphs = vector.getNumGlyphs();
        Rectangle dirty = null;
        boolean vectorRendered = false;

        for (int index = 0; index < numGlyphs; index++) {
            int glyphCode = vector.getGlyphCode(index);
            if (glyphCache.containsKey(fontKey | glyphCode)) {
                continue;
            }

            if (!vectorRendered) {
                vectorRendered = true;

                for (int i = 0; i < numGlyphs; i++) {
                    Point2D pos = vector.getGlyphPosition(i);
                    pos.setLocation(pos.getX() + (8 + GLYPH_PAD * 2) * i, pos.getY());
                    vector.setGlyphPosition(i, pos);
                }

                vectorBounds = vector.getPixelBounds(fontRenderContext, 0, 0);
                int pad = GLYPH_PAD;
                vectorBounds = new Rectangle(
                        vectorBounds.x - pad,
                        vectorBounds.y - pad,
                        vectorBounds.width + pad * 2,
                        vectorBounds.height + pad * 2
                );

                if (stringImage == null)
                    return;

                if (vectorBounds.width > stringImage.getWidth() || vectorBounds.height > stringImage.getHeight()) {
                    int width = Math.max(vectorBounds.width, stringImage.getWidth());
                    int height = Math.max(vectorBounds.height, stringImage.getHeight());
                    allocateStringImage(width, height);
                }

                stringGraphics.clearRect(0, 0, vectorBounds.width, vectorBounds.height);
                stringGraphics.drawGlyphVector(vector, -vectorBounds.x, -vectorBounds.y);
            }

            Rectangle rect = vector.getGlyphPixelBounds(index, null, -vectorBounds.x, -vectorBounds.y);
            int pad = GLYPH_PAD;
            int srcW = stringImage.getWidth();
            int srcH = stringImage.getHeight();
            int x1 = Math.max(rect.x - pad, 0);
            int y1 = Math.max(rect.y - pad, 0);
            int x2 = Math.min(rect.x + rect.width + pad, srcW);
            int y2 = Math.min(rect.y + rect.height + pad, srcH);
            rect = new Rectangle(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));

            if (cachePosX + rect.width + GLYPH_BORDER > TEXTURE_WIDTH) {
                cachePosX = GLYPH_BORDER;
                cachePosY += cacheLineHeight + GLYPH_BORDER;
                cacheLineHeight = 0;
            }

            if (cachePosY + rect.height + GLYPH_BORDER > TEXTURE_HEIGHT) {
                updateTexture(dirty);
                dirty = null;
                allocateGlyphCacheTexture();
                cachePosY = cachePosX = GLYPH_BORDER;
                cacheLineHeight = 0;
            }

            if (rect.height > cacheLineHeight) {
                cacheLineHeight = rect.height;
            }

            glyphCacheGraphics.drawImage(stringImage,
                    cachePosX, cachePosY, cachePosX + rect.width, cachePosY + rect.height,
                    rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, null);

            rect.setLocation(cachePosX, cachePosY);

            Entry entry = new Entry();
            entry.textureName = textureName;
            entry.width = rect.width;
            entry.height = rect.height;
            entry.offsetX = pad;
            entry.offsetY = pad * 2;
            entry.u1 = ((float) rect.x) / TEXTURE_WIDTH;
            entry.v1 = ((float) rect.y) / TEXTURE_HEIGHT;
            entry.u2 = ((float) (rect.x + rect.width)) / TEXTURE_WIDTH;
            entry.v2 = ((float) (rect.y + rect.height)) / TEXTURE_HEIGHT;

            glyphCache.put(fontKey | glyphCode, entry);

            if (dirty == null) {
                dirty = new Rectangle(cachePosX, cachePosY, rect.width, rect.height);
            } else {
                dirty.add(rect);
            }

            cachePosX += rect.width + GLYPH_BORDER;
        }

        updateTexture(dirty);
    }

    private void updateTexture(Rectangle dirty) {
        if (dirty != null) {
            updateImageBuffer(dirty.x, dirty.y, dirty.width, dirty.height);
            GlStateManager.bindTexture(textureName);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, dirty.x, dirty.y, dirty.width, dirty.height,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, imageBuffer);
        }
    }

    private void allocateStringImage(int width, int height) {
        stringImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        stringGraphics = stringImage.createGraphics();
        stringGraphics.setColor(BACK_COLOR);
        stringGraphics.fillRect(0, 0, width, height);
        stringGraphics.setBackground(BACK_COLOR);
        setRenderingHints();
        stringGraphics.setColor(Color.WHITE);
    }

    private void setRenderingHints() {
        stringGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                antiAliasEnabled ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        stringGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                antiAliasEnabled ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        stringGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                fontSize <= 14 ? RenderingHints.VALUE_FRACTIONALMETRICS_OFF : RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    private void allocateGlyphCacheTexture() {
        glyphCacheGraphics.clearRect(0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        singleIntBuffer.clear();
        GL11.glGenTextures(singleIntBuffer);
        textureName = singleIntBuffer.get(0);
        updateImageBuffer(0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        GlStateManager.bindTexture(textureName);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_ALPHA8, TEXTURE_WIDTH, TEXTURE_HEIGHT, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, imageBuffer);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        int filter = fontSize <= 14 ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
    }

    private void updateImageBuffer(int x, int y, int width, int height) {
        glyphCacheImage.getRGB(x, y, width, height, imageData, 0, width);
        for (int i = 0; i < width * height; i++) {
            int color = imageData[i];
            imageData[i] = (color << 8) | (color >>> 24);
        }
        imageBuffer.clear();
        imageBuffer.put(imageData);
        imageBuffer.flip();
    }

    public ByteBuffer readImageToBuffer(BufferedImage bufferedImage) {
        int[] rgbArray = bufferedImage.getRGB(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), null, 0, bufferedImage.getWidth());
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * rgbArray.length);
        for (int rgb : rgbArray) {
            byteBuffer.putInt(rgb << 8 | rgb >> 24 & 255);
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    public int loadGlTexture(BufferedImage bufferedImage) {
        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, bufferedImage.getWidth(), bufferedImage.getHeight(),
                0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, imageBuffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return textureId;
    }
}
