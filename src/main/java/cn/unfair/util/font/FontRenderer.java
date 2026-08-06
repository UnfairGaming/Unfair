package cn.unfair.util.font;

import cn.unfair.Unfair;
import cn.unfair.module.modules.misc.NickHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GLContext;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;

public class FontRenderer {
    private static final int GLYPH_PADDING = 1;
    private static final float LEGACY_DISPLAY_SCALE = 2.0F;
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int[] colorCode = new int[32];
    private static Font harmonyRegularFont;
    private static Font harmonyMediumFont;

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
    private final Map<Integer, FontAtlas> harmonyRegularAtlases = new HashMap<>();
    private final Map<Integer, FontAtlas> harmonyMediumAtlases = new HashMap<>();
    private final Map<String, Integer> stringWidthCache = new LinkedHashMap<String, Integer>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return this.size() > 512;
        }
    };

    public FontRenderer(Font font) {
        this.font = font;
        this.size = font.getSize2D();
    }

    private static void setFontTextureFilter() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }

    private static boolean shouldUseMinecraftFallback(char chr) {
        return chr == '\u2764'
                || chr == '\u221A'
                || chr == '\u00D7'
                || chr == '\u2713'
                || chr == '\u2714'
                || chr == '\u2715'
                || chr == '\u2716'
                || chr == '\u2717'
                || chr == '\u2718'
                || isEmojiBmp(chr);
    }

    private static boolean isEmojiBmp(char chr) {
        return chr == '\u00A9'
                || chr == '\u00AE'
                || chr == '\u203C'
                || chr == '\u2049'
                || chr == '\u2122'
                || chr == '\u2139'
                || chr == '\u2194'
                || chr == '\u2195'
                || chr == '\u2196'
                || chr == '\u2197'
                || chr == '\u2198'
                || chr == '\u2199'
                || chr == '\u21A9'
                || chr == '\u21AA'
                || chr == '\u231A'
                || chr == '\u231B'
                || chr == '\u2328'
                || chr == '\u23CF'
                || chr == '\u23E9'
                || chr == '\u23EA'
                || chr == '\u23EB'
                || chr == '\u23EC'
                || chr == '\u23F0'
                || chr == '\u23F3'
                || chr == '\u24C2'
                || chr == '\u25AA'
                || chr == '\u25AB'
                || chr == '\u25B6'
                || chr == '\u25C0'
                || chr == '\u25FB'
                || chr == '\u25FC'
                || chr == '\u25FD'
                || chr == '\u25FE'
                || chr == '\u2600'
                || chr == '\u2601'
                || chr == '\u2602'
                || chr == '\u2603'
                || chr == '\u2604'
                || chr == '\u260E'
                || chr == '\u2611'
                || chr == '\u2614'
                || chr == '\u2615'
                || chr == '\u2618'
                || chr == '\u261D'
                || chr == '\u2620'
                || chr == '\u2622'
                || chr == '\u2623'
                || chr == '\u2626'
                || chr == '\u262A'
                || chr == '\u262F'
                || chr == '\u2638'
                || chr == '\u2639'
                || chr == '\u263A'
                || chr == '\u2640'
                || chr == '\u2642'
                || chr == '\u2648'
                || chr == '\u2649'
                || chr == '\u264A'
                || chr == '\u264B'
                || chr == '\u264C'
                || chr == '\u264D'
                || chr == '\u264E'
                || chr == '\u264F'
                || chr == '\u2650'
                || chr == '\u2651'
                || chr == '\u2652'
                || chr == '\u2653'
                || chr == '\u265F'
                || chr == '\u2660'
                || chr == '\u2663'
                || chr == '\u2665'
                || chr == '\u2666'
                || chr == '\u267B'
                || chr == '\u267E'
                || chr == '\u267F'
                || chr == '\u2692'
                || chr == '\u2693'
                || chr == '\u2694'
                || chr == '\u2695'
                || chr == '\u2696'
                || chr == '\u2697'
                || chr == '\u2699'
                || chr == '\u269B'
                || chr == '\u269C'
                || chr == '\u26A0'
                || chr == '\u26A1'
                || chr == '\u26A7'
                || chr == '\u26AA'
                || chr == '\u26AB'
                || chr == '\u26B0'
                || chr == '\u26B1'
                || chr == '\u26BD'
                || chr == '\u26BE'
                || chr == '\u26C4'
                || chr == '\u26C5'
                || chr == '\u26C8'
                || chr == '\u26CE'
                || chr == '\u26CF'
                || chr == '\u26D1'
                || chr == '\u26D3'
                || chr == '\u26D4'
                || chr == '\u26E9'
                || chr == '\u26EA'
                || chr == '\u26F0'
                || chr == '\u26F1'
                || chr == '\u26F2'
                || chr == '\u26F3'
                || chr == '\u26F4'
                || chr == '\u26F5'
                || chr == '\u26FA'
                || chr == '\u26FD'
                || chr == '\u2702'
                || chr == '\u2705'
                || chr == '\u2708'
                || chr == '\u2709'
                || chr == '\u270A'
                || chr == '\u270B'
                || chr == '\u270C'
                || chr == '\u270D'
                || chr == '\u270F'
                || chr == '\u2712'
                || chr == '\u2714'
                || chr == '\u2716'
                || chr == '\u2728'
                || chr == '\u2733'
                || chr == '\u2734'
                || chr == '\u2744'
                || chr == '\u2747'
                || chr == '\u274C'
                || chr == '\u274E'
                || chr == '\u2753'
                || chr == '\u2754'
                || chr == '\u2755'
                || chr == '\u2757'
                || chr == '\u2795'
                || chr == '\u2796'
                || chr == '\u2797'
                || chr == '\u27A1'
                || chr == '\u27B0'
                || chr == '\u27BF'
                || chr == '\u2934'
                || chr == '\u2935'
                || chr == '\u3030'
                || chr == '\u303D'
                || chr == '\u3297'
                || chr == '\u3299'
                || chr == '\uFE0E'
                || chr == '\uFE0F'
                || chr == '\u200D'
                || chr >= '\u2190' && chr <= '\u21FF'
                || chr >= '\u2300' && chr <= '\u23FF'
                || chr >= '\u25AA' && chr <= '\u25FF'
                || chr >= '\u2600' && chr <= '\u27BF'
                || chr >= '\u2B00' && chr <= '\u2BFF';
    }

    private static boolean isEmojiCodePoint(int codePoint) {
        return codePoint >= 0x1F000 && codePoint <= 0x1FAFF
                || codePoint >= 0x1FC00 && codePoint <= 0x1FFFD;
    }

    private static boolean shouldUseMinecraftFallback(int codePoint) {
        return codePoint > Character.MAX_VALUE && isEmojiCodePoint(codePoint);
    }

    private static boolean shouldUseMinecraftFallback(String text, int index) {
        int codePoint = text.codePointAt(index);
        return codePoint > Character.MAX_VALUE
                ? shouldUseMinecraftFallback(codePoint)
                : shouldUseMinecraftFallback((char) codePoint);
    }

    private static boolean shouldSkipEmoji(String text, int index) {
        int codePoint = text.codePointAt(index);
        return codePoint <= Character.MAX_VALUE && isEmojiBmp((char) codePoint)
                && !shouldUseMinecraftFallback((char) codePoint);
    }

    private static Font getHarmonyRegularFont() {
        if (harmonyRegularFont == null) {
            harmonyRegularFont = loadFont("HarmonyOS_Sans_SC_Regular");
        }
        return harmonyRegularFont;
    }

    private static Font getHarmonyMediumFont() {
        if (harmonyMediumFont == null) {
            harmonyMediumFont = loadFont("HarmonyOS_Sans_SC_Medium");
        }
        return harmonyMediumFont;
    }

    private static Font loadFont(String file) {
        try (InputStream in = Objects.requireNonNull(
                FontRenderer.class.getResourceAsStream("/assets/minecraft/unfair/font/" + file + ".ttf"), "Font resource is null"
        )) {
            return Font.createFont(0, in);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create fallback font: " + file, ex);
        }
    }

    private static int getVanillaColor(int color, float alpha) {
        int alphaByte = Math.clamp(Math.round(alpha * 255.0F), 0, 255);
        return alphaByte << 24 | color & 0x00FFFFFF;
    }

    private static int getScaleFactor() {
        return Math.max(1, new ScaledResolution(mc).getScaleFactor());
    }

    private static boolean isFormattingPrefix(char chr) {
        return chr == '\u00a7';
    }

    private static NickHider getNickHider() {
        if (Unfair.moduleManager == null) {
            return null;
        }
        return (NickHider) Unfair.moduleManager.modules.get(NickHider.class);
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
        if (shouldUseMinecraftFallback(chr)) {
            mc.fontRendererObj.drawString(String.valueOf(chr), x, y + 1.0F, -1, false);
            return mc.fontRendererObj.getStringWidth(String.valueOf(chr));
        }
        return getAtlasForChar(chr, getScaleFactor()).drawChar(chr, x, y);
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

        FontAtlas activeAtlas = null;
        int activeRegion = -1;
        boolean drawing = false;
        int length = str.length();
        for (int i = 0; i < length; i++) {
            char chr = str.charAt(i);
            if (isFormattingPrefix(chr) && i != length - 1) {
                if (drawing) {
                    glEnd();
                    drawing = false;
                    activeAtlas = null;
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

            if (shouldUseMinecraftFallback(str, i)) {
                if (drawing) {
                    glEnd();
                    drawing = false;
                    activeAtlas = null;
                    activeRegion = -1;
                }
                int codePointLength = Character.charCount(str.codePointAt(i));
                String fallbackText = str.substring(i, i + codePointLength);
                glPopMatrix();
                mc.fontRendererObj.drawString(fallbackText, (x + offset) / (float) scaleFactor, (y + 1.0F * scaleFactor) / (float) scaleFactor, getVanillaColor(color, a), false);
                offset += mc.fontRendererObj.getStringWidth(fallbackText) * scaleFactor;
                GlStateManager.color(r, g, b, a);
                glPushMatrix();
                glScaled(1.0D / scaleFactor, 1.0D / scaleFactor, 1.0D / scaleFactor);
                GlStateManager.enableTexture2D();
                GlStateManager.enableBlend();
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                if (codePointLength > 1) {
                    i++;
                }
                continue;
            }

            if (shouldSkipEmoji(str, i)) {
                continue;
            }

            FontAtlas charAtlas = getAtlasForChar(chr, scaleFactor);
            int region = chr >> 8;
            if (!drawing) {
                activeAtlas = charAtlas;
                activeRegion = region;
                GlStateManager.bindTexture(activeAtlas.getOrGenerateCharTexture(activeRegion));
                setFontTextureFilter();
                glBegin(GL_QUADS);
                drawing = true;
            } else if (activeAtlas != charAtlas || activeRegion != region) {
                glEnd();
                activeAtlas = charAtlas;
                activeRegion = region;
                GlStateManager.bindTexture(activeAtlas.getOrGenerateCharTexture(activeRegion));
                setFontTextureFilter();
                glBegin(GL_QUADS);
            }
            offset += activeAtlas.drawCharInCurrentBatch(chr, x + offset, y);
        }
        if (drawing) {
            glEnd();
        }
        glPopMatrix();
        GlStateManager.bindTexture(0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
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
        int scaleFactor = getScaleFactor();
        String cacheKey = scaleFactor + "\u0000" + text;

        synchronized (this.stringWidthCache) {
            Integer cachedWidth = this.stringWidthCache.get(cacheKey);

            if (cachedWidth != null) {
                return cachedWidth;
            }
        }

        int width = this.getStringWidth(text, scaleFactor);

        synchronized (this.stringWidthCache) {
            this.stringWidthCache.put(cacheKey, width);
        }

        return width;
    }

    public final float getStringVisualCenterOffset(String text) {
        NickHider nickHider = getNickHider();
        if (nickHider != null && nickHider.isEnabled()) {
            text = nickHider.replaceNick(text);
        }

        if (text == null || text.isEmpty()) {
            return 0.0F;
        }

        int scaleFactor = getScaleFactor();
        return getAtlas(scaleFactor).getStringVisualCenterOffset(text) / (float) scaleFactor;
    }

    private int getStringWidth(String text, int scaleFactor) {
        if (text == null) {
            return 0;
        }
        int width = 0;
        int size = text.length();
        int i = 0;
        while (i < size) {
            char chr = text.charAt(i);
            if (isFormattingPrefix(chr)) {
                ++i;
            } else if (shouldUseMinecraftFallback(text, i)) {
                int codePointLength = Character.charCount(text.codePointAt(i));
                width += mc.fontRendererObj.getStringWidth(text.substring(i, i + codePointLength)) * scaleFactor;
                if (codePointLength > 1) {
                    ++i;
                }
            } else if (shouldSkipEmoji(text, i)) {
            } else {
                width += getAtlasForChar(chr, scaleFactor).getOrGenerateCharWidthMap(chr >> 8)[chr & 0xFF];
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
            atlas = new FontAtlas(this.font, scaleFactor);
            this.atlases.put(scaleFactor, atlas);
        }
        return atlas;
    }

    private FontAtlas getAtlasForChar(char chr, int scaleFactor) {
        if (!shouldUseHarmonyFallback(chr)) {
            return this.getAtlas(scaleFactor);
        }

        scaleFactor = Math.max(1, scaleFactor);
        Map<Integer, FontAtlas> atlasMap = this.useMediumHarmonyFallback() ? this.harmonyMediumAtlases : this.harmonyRegularAtlases;
        FontAtlas atlas = atlasMap.get(scaleFactor);
        if (atlas == null) {
            Font fallbackFont = this.useMediumHarmonyFallback() ? getHarmonyMediumFont() : getHarmonyRegularFont();
            atlas = new FontAtlas(fallbackFont, scaleFactor);
            atlasMap.put(scaleFactor, atlas);
        }
        return atlas;
    }

    private boolean shouldUseHarmonyFallback(char chr) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(chr);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    private boolean useMediumHarmonyFallback() {
        String fontName = this.font.getFontName().toLowerCase();
        return this.font.isBold()
                || fontName.contains("medium")
                || fontName.contains("semibold")
                || fontName.contains("bold");
    }

    private int resizeToOpenGLSupportResolution(int size) {
        if (GLContext.getCapabilities().GL_ARB_texture_non_power_of_two) {
            return size;
        }
        return Math.max(1, Integer.highestOneBit(size - 1) << 1);
    }

    public final void close() {
        for (FontAtlas atlas : this.atlases.values()) {
            atlas.delete();
        }
        for (FontAtlas atlas : this.harmonyRegularAtlases.values()) {
            atlas.delete();
        }
        for (FontAtlas atlas : this.harmonyMediumAtlases.values()) {
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

        private FontAtlas(Font sourceFont, int scaleFactor) {
            this.scaleFactor = scaleFactor;
            this.scaledFont = sourceFont.deriveFont(Font.PLAIN, size * scaleFactor / LEGACY_DISPLAY_SCALE);
            Arrays.fill(this.textures, -1);
            this.fontWidth = Math.max(1, (int) Math.ceil(this.scaledFont.getSize2D() * 1.5F));
            this.fontHeight = Math.max(1, (int) Math.ceil(this.scaledFont.getSize2D() * 1.25F));
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

        private int drawCharInCurrentBatch(char chr, float x, float y) {
            int id = chr & 0xFF;
            int width = getOrGenerateCharWidthMap(chr >> 8)[id];
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

            int ascent = Math.max(1, (int) Math.ceil(this.fontHeight * 0.8F));
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
                widthmap[i] = (byte) Math.ceil(this.scaledFont
                        .createGlyphVector(this.context, this.widthChar)
                        .getGlyphMetrics(0)
                        .getAdvance());
            }
            return widthmap;
        }

        private float getStringVisualCenterOffset(String text) {
            float penX = 0.0F;
            float left = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE;
            int size = text.length();
            int i = 0;
            while (i < size) {
                char chr = text.charAt(i);
                if (isFormattingPrefix(chr)) {
                    ++i;
                    ++i;
                    continue;
                }

                if (shouldUseMinecraftFallback(text, i)) {
                    int codePointLength = Character.charCount(text.codePointAt(i));
                    int advance = mc.fontRendererObj.getStringWidth(text.substring(i, i + codePointLength)) * this.scaleFactor;
                    left = Math.min(left, penX);
                    right = Math.max(right, penX + advance);
                    penX += advance;
                    if (codePointLength > 1) {
                        ++i;
                    }
                    ++i;
                    continue;
                }

                if (shouldSkipEmoji(text, i)) {
                    continue;
                }

                FontAtlas charAtlas = getAtlasForChar(chr, this.scaleFactor);
                int region = chr >> 8;
                int id = chr & 0xFF;
                int advance = charAtlas.getOrGenerateCharWidthMap(region)[id];
                left = Math.min(left, penX);
                right = Math.max(right, penX + advance);
                penX += advance;
                ++i;
            }

            if (left == Float.MAX_VALUE) {
                return penX / 2.0F;
            }
            return (left + right) / 2.0F;
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
