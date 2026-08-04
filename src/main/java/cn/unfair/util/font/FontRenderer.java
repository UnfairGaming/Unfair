package cn.unfair.util.font;

import cn.unfair.Unfair;
import cn.unfair.module.modules.misc.NickHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GLContext;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glScaled;
import static org.lwjgl.opengl.GL11.glTexCoord2d;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glVertex2f;

public class FontRenderer {
    private static final int GLYPH_PADDING = 1;
    private static final float LEGACY_DISPLAY_SCALE = 2.0F;
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int[] colorCode = new int[32];

    static {
        for (int i = 0; i < 32; ++i) {
            int base = (i >> 3 & 1) * 85;
            int r = (i >> 2 & 1) * 170 + base;
            int g = (i >> 1 & 1) * 170 + base;
            int b = (i & 1) * 170 + base;
            if (i == 6) {
                r += 85;
            }

            if (i >= 16) {
                r /= 4;
                g /= 4;
                b /= 4;
            }

            colorCode[i] = (r & 255) << 16 | (g & 255) << 8 | b & 255;
        }
    }

    private final Font font;
    private final float size;
    private final Map<Integer, FontAtlas> atlases = new HashMap<>();

    public FontRenderer(Font font) {
        this.font = font;
        this.size = font.getSize2D();
    }

    public final float drawCenteredString(String text, float x, float y, int color) {
        return drawString(text, x - getStringWidth(text) / 2f, y, color);
    }

    public final float drawCenteredString(String text, double x, double y, int color) {
        return drawString(text, (float) (x - getStringWidth(text) / 2f), (float) y, color);
    }

    public final void drawCenteredStringWithShadow(String text, double x, double y, int color) {
        drawStringWithShadow(text, (x - (float) getStringWidth(text) / 2), y, color);
    }

    public final int getHeight() {
        FontAtlas atlas = getAtlas(getScaleFactor());
        return Math.round(atlas.fontHeight / (float) atlas.scaleFactor);
    }

    protected final int drawChar(char chr, float x, float y) {
        return getAtlas(getScaleFactor()).drawChar(chr, x, y);
    }

    public int drawString(String str, float x, float y, int color) {
        return drawString(str, x, y, color, false);
    }

    public int drawString(String str, double x, double y, int color) {
        return drawString(str, (float) x, (float) y, color, false);
    }

    public final int drawString(String str, float x, float y, int color, boolean darken) {
        if (str == null) {
            return 0;
        }
        NickHider nickHider = getNickHider();
        if (nickHider != null && nickHider.isEnabled()) {
            str = nickHider.replaceNick(str);
        }

        int scaleFactor = getScaleFactor();
        FontAtlas atlas = getAtlas(scaleFactor);
        x = Math.round(x * scaleFactor);
        y = Math.round(y * scaleFactor);

        int offset = 0;
        if (darken) {
            color = (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
        }
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8 & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = (color >> 24 & 0xFF) / 255f;
        if (a == 0) {
            a = 1;
        }

        GlStateManager.color(r, g, b, a);
        glPushMatrix();
        glScaled(1.0D / scaleFactor, 1.0D / scaleFactor, 1.0D / scaleFactor);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int activeRegion = -1;
        boolean drawing = false;
        int length = str.length();
        for (int i = 0; i < length; i++) {
            char chr = str.charAt(i);
            if (isFormattingPrefix(chr) && i != length - 1) {
                if (drawing) {
                    glEnd();
                    drawing = false;
                    activeRegion = -1;
                }
                color = "0123456789abcdef".indexOf(str.charAt(++i));
                if (color != -1) {
                    if (darken) {
                        color |= 0x10;
                    }
                    color = colorCode[color];
                    r = (color >> 16 & 0xFF) / 255f;
                    g = (color >> 8 & 0xFF) / 255f;
                    b = (color & 0xFF) / 255f;
                    GlStateManager.color(r, g, b, a);
                }
                continue;
            }

            int region = chr >> 8;
            if (!drawing) {
                activeRegion = region;
                GlStateManager.bindTexture(atlas.getOrGenerateCharTexture(activeRegion));
                glBegin(GL_QUADS);
                drawing = true;
            }
            int oldRegion = activeRegion;
            offset += atlas.drawCharBatched(chr, x + offset, y, activeRegion);
            if (region != oldRegion) {
                activeRegion = region;
            }
        }
        if (drawing) {
            glEnd();
        }
        glPopMatrix();
        return Math.round(offset / (float) scaleFactor);
    }

    public float getMiddleOfBox(float height) {
        return height / 2f - getHeight() / 2f;
    }

    public final int getStringWidth(String text) {
        NickHider nickHider = getNickHider();
        if (nickHider != null && nickHider.isEnabled()) {
            text = nickHider.replaceNick(text);
        }

        if (text == null) {
            return 0;
        }
        return this.getStringWidth(text, getScaleFactor());
    }

    private int getStringWidth(String text, int scaleFactor) {
        if (text == null) {
            return 0;
        }
        FontAtlas atlas = getAtlas(scaleFactor);
        int width = 0;
        int size = text.length();
        int i = 0;
        while (i < size) {
            char chr = text.charAt(i);
            if (isFormattingPrefix(chr)) {
                ++i;
            } else {
                width += atlas.getOrGenerateCharWidthMap(chr >> 8)[chr & 0xFF];
            }
            ++i;
        }
        return Math.round(width / (float) scaleFactor);
    }

    public final float getSize() {
        return size;
    }

    public final float drawStringWithShadow(String newstr, float i, float i1, int rgb) {
        float shadowWidth = drawString(newstr, i + 0.5f, i1 + 0.5f, rgb, true);
        return Math.max(shadowWidth, drawString(newstr, i, i1, rgb, false));
    }

    public final void drawOutlinedString(String str, float x, float y, int internalCol, int externalCol) {
        this.drawString(str, x - 0.5f, y, externalCol);
        this.drawString(str, x + 0.5f, y, externalCol);
        this.drawString(str, x, y - 0.5f, externalCol);
        this.drawString(str, x, y + 0.5f, externalCol);
        this.drawString(str, x, y, internalCol);
    }

    public void drawStringWithShadow(String z, double x, double positionY, int mainTextColor) {
        drawStringWithShadow(z, (float) x, (float) positionY, mainTextColor);
    }

    public void drawGradientWithShadow(String text, float x, float y, GradientApplier colorSupplier) {
        int index = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (isFormattingPrefix(ch) && i + 1 < text.length()) {
                i++;
                continue;
            }

            String character = String.valueOf(ch);
            x += drawStringWithShadow(character, x, y, colorSupplier.colour(index).getRGB());
            index++;
        }
    }

    public void drawGradient(String text, float x, float y, GradientApplier colorSupplier) {
        int index = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (isFormattingPrefix(ch) && i + 1 < text.length()) {
                i++;
                continue;
            }

            String character = String.valueOf(ch);
            x += drawString(character, x, y, colorSupplier.colour(index).getRGB());
            index++;
        }
    }

    public String trimStringToWidth(final String p_trimStringToWidth_1_, final int p_trimStringToWidth_2_) {
        return trimStringToWidth(p_trimStringToWidth_1_, p_trimStringToWidth_2_, false);
    }

    public String trimStringToWidth(final String p_trimStringToWidth_1_, final int p_trimStringToWidth_2_, final boolean p_trimStringToWidth_3_) {
        final StringBuilder stringbuilder = new StringBuilder();
        int i = 0;
        final int j = p_trimStringToWidth_3_ ? (p_trimStringToWidth_1_.length() - 1) : 0;
        final int k = p_trimStringToWidth_3_ ? -1 : 1;
        boolean flag = false;
        boolean flag2 = false;
        for (int l = j; l >= 0 && l < p_trimStringToWidth_1_.length() && i < p_trimStringToWidth_2_; l += k) {
            final char c0 = p_trimStringToWidth_1_.charAt(l);
            final int i2 = this.getStringWidth(String.valueOf(c0));
            if (flag) {
                flag = false;
                if (c0 != 'l' && c0 != 'L') {
                    if (c0 == 'r' || c0 == 'R') {
                        flag2 = false;
                    }
                } else {
                    flag2 = true;
                }
            } else if (i2 < 0) {
                flag = true;
            } else {
                i += i2;
                if (flag2) {
                    ++i;
                }
            }
            if (i > p_trimStringToWidth_2_) {
                break;
            }
            if (p_trimStringToWidth_3_) {
                stringbuilder.insert(0, c0);
            } else {
                stringbuilder.append(c0);
            }
        }
        return stringbuilder.toString();
    }

    private FontAtlas getAtlas(int scaleFactor) {
        scaleFactor = Math.max(1, scaleFactor);
        FontAtlas atlas = this.atlases.get(scaleFactor);
        if (atlas == null) {
            atlas = new FontAtlas(scaleFactor);
            this.atlases.put(scaleFactor, atlas);
        }
        return atlas;
    }

    private int resizeToOpenGLSupportResolution(int size) {
        if (GLContext.getCapabilities().GL_ARB_texture_non_power_of_two) {
            return size;
        }
        return Math.max(1, Integer.highestOneBit(size - 1) << 1);
    }

    private static int getScaleFactor() {
        return Math.max(1, new ScaledResolution(mc).getScaleFactor());
    }

    private static boolean isFormattingPrefix(char chr) {
        return chr == '搂';
    }

    private static NickHider getNickHider() {
        if (Unfair.moduleManager == null) {
            return null;
        }
        return (NickHider) Unfair.moduleManager.modules.get(NickHider.class);
    }

    protected final void finalize() {
        for (FontAtlas atlas : this.atlases.values()) {
            atlas.delete();
        }
    }

    private class FontAtlas {
        private final int scaleFactor;
        private final byte[][] charwidth = new byte[256][];
        private final int[] textures = new int[256];
        private final FontRenderContext context = new FontRenderContext(new AffineTransform(), true, true);
        private final Font scaledFont;
        private final int fontWidth;
        private final int fontHeight;
        private final int cellWidth;
        private final int cellHeight;
        private final int textureWidth;
        private final int textureHeight;
        private final char[] atlasChar = new char[1];
        private final char[] widthChar = new char[1];
        private BufferedImage atlasImage;
        private Graphics2D atlasGraphics;
        private ByteBuffer atlasBuffer;
        private int[] atlasPixels;

        private FontAtlas(int scaleFactor) {
            this.scaleFactor = scaleFactor;
            this.scaledFont = font.deriveFont(Font.PLAIN, size * scaleFactor / LEGACY_DISPLAY_SCALE);
            Arrays.fill(this.textures, -1);
            Rectangle2D maxBounds = this.scaledFont.getMaxCharBounds(this.context);
            this.fontWidth = (int) Math.ceil(maxBounds.getWidth());
            this.fontHeight = (int) Math.ceil(maxBounds.getHeight());
            this.cellWidth = this.fontWidth + GLYPH_PADDING * 2;
            this.cellHeight = this.fontHeight + GLYPH_PADDING * 2;
            this.textureWidth = resizeToOpenGLSupportResolution(this.cellWidth * 16);
            this.textureHeight = resizeToOpenGLSupportResolution(this.cellHeight * 16);
        }

        private int drawChar(char chr, float x, float y) {
            int region = chr >> 8;
            int id = chr & 0xFF;
            int width = getOrGenerateCharWidthMap(region)[id];
            GlStateManager.bindTexture(getOrGenerateCharTexture(region));
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBegin(GL_QUADS);
            drawCharQuad(id, width, x, y);
            glEnd();
            return width;
        }

        private void drawCharQuad(int id, int width, float x, float y) {
            int xTexCoord = (id & 0xF) * this.cellWidth + GLYPH_PADDING;
            int yTexCoord = (id >> 4) * this.cellHeight + GLYPH_PADDING;

            glTexCoord2d(wrapTextureCoord(xTexCoord, this.textureWidth), wrapTextureCoord(yTexCoord, this.textureHeight));
            glVertex2f(x, y);
            glTexCoord2d(wrapTextureCoord(xTexCoord, this.textureWidth), wrapTextureCoord(yTexCoord + this.fontHeight, this.textureHeight));
            glVertex2f(x, y + this.fontHeight);
            glTexCoord2d(wrapTextureCoord(xTexCoord + width, this.textureWidth), wrapTextureCoord(yTexCoord + this.fontHeight, this.textureHeight));
            glVertex2f(x + width, y + this.fontHeight);
            glTexCoord2d(wrapTextureCoord(xTexCoord + width, this.textureWidth), wrapTextureCoord(yTexCoord, this.textureHeight));
            glVertex2f(x + width, y);
        }

        private int drawCharBatched(char chr, float x, float y, int activeRegion) {
            int region = chr >> 8;
            int id = chr & 0xFF;
            int width = getOrGenerateCharWidthMap(region)[id];
            if (region != activeRegion) {
                glEnd();
                GlStateManager.bindTexture(getOrGenerateCharTexture(region));
                glBegin(GL_QUADS);
            }
            drawCharQuad(id, width, x, y);
            return width;
        }

        private int generateCharTexture(int id) {
            int textureId = glGenTextures();
            int offset = id << 8;
            Graphics2D g = getAtlasGraphics();

            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, this.textureWidth, this.textureHeight);
            g.setComposite(AlphaComposite.SrcOver);

            FontMetrics fontMetrics = g.getFontMetrics();
            int ascent = fontMetrics.getAscent();
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    this.atlasChar[0] = (char) ((y << 4 | x) | offset);
                    g.drawChars(this.atlasChar, 0, 1, x * this.cellWidth + GLYPH_PADDING, y * this.cellHeight + GLYPH_PADDING + ascent);
                }
            }
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, this.textureWidth, this.textureHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, imageToBuffer());
            return textureId;
        }

        private Graphics2D getAtlasGraphics() {
            if (this.atlasImage == null) {
                this.atlasImage = new BufferedImage(this.textureWidth, this.textureHeight, BufferedImage.TYPE_INT_ARGB);
                this.atlasGraphics = this.atlasImage.createGraphics();
                this.atlasGraphics.setColor(Color.WHITE);
                this.atlasGraphics.setFont(this.scaledFont);
                this.atlasGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                this.atlasGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                this.atlasPixels = new int[this.textureWidth * this.textureHeight];
                this.atlasBuffer = ByteBuffer.allocateDirect(4 * this.atlasPixels.length);
            }
            this.atlasGraphics.setColor(Color.WHITE);
            this.atlasGraphics.setFont(this.scaledFont);
            return this.atlasGraphics;
        }

        private int getOrGenerateCharTexture(int id) {
            if (this.textures[id] == -1) {
                return this.textures[id] = generateCharTexture(id);
            }
            return this.textures[id];
        }

        private byte[] generateCharWidthMap(int id) {
            int offset = id << 8;
            byte[] widthmap = new byte[256];
            for (int i = 0; i < widthmap.length; i++) {
                this.widthChar[0] = (char) (i | offset);
                widthmap[i] = (byte) Math.ceil(this.scaledFont.getStringBounds(this.widthChar, 0, 1, this.context).getWidth());
            }
            return widthmap;
        }

        private byte[] getOrGenerateCharWidthMap(int id) {
            if (this.charwidth[id] == null) {
                return this.charwidth[id] = generateCharWidthMap(id);
            }
            return this.charwidth[id];
        }

        private double wrapTextureCoord(int coord, int size) {
            return coord / (double) size;
        }

        private ByteBuffer imageToBuffer() {
            this.atlasImage.getRGB(0, 0, this.textureWidth, this.textureHeight, this.atlasPixels, 0, this.textureWidth);
            this.atlasBuffer.clear();

            for (int pixel : this.atlasPixels) {
                this.atlasBuffer.putInt(pixel << 8 | pixel >> 24 & 0xFF);
            }

            this.atlasBuffer.flip();
            return this.atlasBuffer;
        }

        private void delete() {
            if (this.atlasGraphics != null) {
                this.atlasGraphics.dispose();
            }
            for (int textureId : this.textures) {
                if (textureId != -1) {
                    glDeleteTextures(textureId);
                }
            }
        }
    }
}
