package cn.unfair.util.font;

import cn.unfair.Unfair;
import cn.unfair.module.modules.misc.NickHider;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GLContext;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11.*;

public class FontRenderer {
    private static final int GLYPH_PADDING = 1;
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

    public final float drawCenteredString(String text, float x, float y, int color) {
        return drawString(text, x - getStringWidth(text) / 2f, y, color);
    }

    public final float drawCenteredString(String text, double x, double y, int color) {
        return drawString(text, (float) (x - getStringWidth(text) / 2f), (float) y, color);
    }

    public final void drawCenteredStringWithShadow(String text, double x, double y, int color) {
        drawStringWithShadow(text, (x - (float) getStringWidth(text) / 2), y, color);
    }

    private final byte[][] charwidth = new byte[256][];
    private final int[] textures = new int[256];
    private final FontRenderContext context = new FontRenderContext(new AffineTransform(), true, true);
    private final Font font;

    private final float size;
    private final int fontWidth;
    private final int fontHeight;
    private final int cellWidth;
    private final int cellHeight;
    private final int textureWidth;
    private final int textureHeight;

    public FontRenderer(Font font) {
        this.font = font;
        size = font.getSize2D();
        Arrays.fill(textures, -1);
        Rectangle2D maxBounds = font.getMaxCharBounds(context);
        this.fontWidth = (int) Math.ceil(maxBounds.getWidth());
        this.fontHeight = (int) Math.ceil(maxBounds.getHeight());
        this.cellWidth = this.fontWidth + GLYPH_PADDING * 2;
        this.cellHeight = this.fontHeight + GLYPH_PADDING * 2;
        this.textureWidth = resizeToOpenGLSupportResolution(cellWidth * 16);
        this.textureHeight = resizeToOpenGLSupportResolution(cellHeight * 16);
    }

    public final int getHeight() {
        return fontHeight / 2;
    }

    protected final int drawChar(char chr, float x, float y) {
        int region = chr >> 8;
        int id = chr & 0xFF;
        int xTexCoord = (id & 0xF) * cellWidth + GLYPH_PADDING,
                yTexCoord = (id >> 4) * cellHeight + GLYPH_PADDING;
        int width = getOrGenerateCharWidthMap(region)[id];
        GlStateManager.bindTexture(getOrGenerateCharTexture(region));
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        glTexCoord2d(wrapTextureCoord(xTexCoord, textureWidth), wrapTextureCoord(yTexCoord, textureHeight));
        glVertex2f(x, y);
        glTexCoord2d(wrapTextureCoord(xTexCoord, textureWidth), wrapTextureCoord(yTexCoord + fontHeight, textureHeight));
        glVertex2f(x, y + fontHeight);
        glTexCoord2d(wrapTextureCoord(xTexCoord + width, textureWidth), wrapTextureCoord(yTexCoord + fontHeight, textureHeight));
        glVertex2f(x + width, y + fontHeight);
        glTexCoord2d(wrapTextureCoord(xTexCoord + width, textureWidth), wrapTextureCoord(yTexCoord, textureHeight));
        glVertex2f(x + width, y);
        glEnd();
        return width;
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

        GlStateManager.color(1F, 1F, 1F, 1F);
        x *= 2;
        y *= 2;
        int offset = 0;
        if (darken) {
            color = (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
        }
        float r, g, b, a;
        r = (color >> 16 & 0xFF) / 255f;
        g = (color >> 8 & 0xFF) / 255f;
        b = (color & 0xFF) / 255f;
        a = (color >> 24 & 0xFF) / 255f;
        if (a == 0)
            a = 1;
        GlStateManager.color(r, g, b, a);
        glPushMatrix();
        glScaled(0.5, 0.5, 0.5);
        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char chr = chars[i];
            if (isFormattingPrefix(chr) && i != chars.length - 1) {
                i++;
                color = "0123456789abcdef".indexOf(chars[i]);
                if (color != -1) {
                    if (darken) color |= 0x10;
                    color = colorCode[color];
                    r = (color >> 16 & 0xFF) / 255f;
                    g = (color >> 8 & 0xFF) / 255f;
                    b = (color & 0xFF) / 255f;
                    GlStateManager.color(r, g, b, a);
                }
                continue;
            }
            offset += drawChar(chr, x + offset, y);
        }
        glPopMatrix();
        return offset;
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
        int width = 0;
        char[] currentData = text.toCharArray();

        int size = text.length();
        int i = 0;
        while (i < size) {
            char chr = currentData[i];

            char character = text.charAt(i);
            if (isFormattingPrefix(character)) {
                ++i;
            } else {
                width += getOrGenerateCharWidthMap(chr >> 8)[chr & 0xFF];
            }
            ++i;
        }
        return width / 2;
    }

    public final float getSize() {
        return size;
    }

    private int generateCharTexture(int id) {
        int textureId = glGenTextures();
        int offset = id << 8;
        BufferedImage img = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, textureWidth, textureHeight);
        g.setComposite(AlphaComposite.SrcOver);

        g.setColor(Color.WHITE);
        g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        FontMetrics fontMetrics = g.getFontMetrics();
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                String chr = String.valueOf((char) ((y << 4 | x) | offset));
                g.drawString(chr, x * cellWidth + GLYPH_PADDING, y * cellHeight + GLYPH_PADDING + fontMetrics.getAscent());
            }
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, textureWidth, textureHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, imageToBuffer(img));
        return textureId;
    }

    private int getOrGenerateCharTexture(int id) {
        if (textures[id] == -1)
            return textures[id] = generateCharTexture(id);
        return textures[id];
    }

    private int resizeToOpenGLSupportResolution(int size) {
        if (GLContext.getCapabilities().GL_ARB_texture_non_power_of_two) {
            return size;
        }
        return Math.max(1, Integer.highestOneBit(size - 1) << 1);
    }

    private byte[] generateCharWidthMap(int id) {
        int offset = id << 8;
        byte[] widthmap = new byte[256];
        for (int i = 0; i < widthmap.length; i++) {
            widthmap[i] = (byte) Math.ceil(font.getStringBounds(String.valueOf((char) (i | offset)), context).getWidth());
        }
        return widthmap;
    }

    private byte[] getOrGenerateCharWidthMap(int id) {
        if (charwidth[id] == null)
            return charwidth[id] = generateCharWidthMap(id);
        return charwidth[id];
    }

    private double wrapTextureCoord(int coord, int size) {
        return coord / (double) size;
    }

    private static ByteBuffer imageToBuffer(BufferedImage img) {
        int[] arr = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        ByteBuffer buf = ByteBuffer.allocateDirect(4 * arr.length);

        for (int i : arr) {
            buf.putInt(i << 8 | i >> 24 & 0xFF);
        }

        buf.flip();
        return buf;
    }

    private static boolean isFormattingPrefix(char chr) {
        return chr == '§';
    }

    private static NickHider getNickHider() {
        if (Unfair.moduleManager == null) {
            return null;
        }
        return (NickHider) Unfair.moduleManager.modules.get(NickHider.class);
    }

    protected final void finalize() {
        for (int textureId : textures) {
            if (textureId != -1)
                glDeleteTextures(textureId);
        }
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
            x += drawStringWithShadow(character, x, y, colorSupplier.colour(index).getRGB()) / 2f;
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
            x += drawString(character, x, y, colorSupplier.colour(index).getRGB()) / 2f;
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
}
