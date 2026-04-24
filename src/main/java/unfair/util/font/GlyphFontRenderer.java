package unfair.util.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GlyphFontRenderer implements FontRenderer {
    private static final int FIRST_GLYPH = 0;
    private static final int LAST_GLYPH = 255;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int GLYPH_MARGIN = 4;
    private static final float MIN_RENDER_SCALE = 2.0f;
    private static final float QUALITY_MULTIPLIER = 2.0f;
    private static final String ALPHABET = "ABCDEFGHOKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String COLOR_CODES = "0123456789abcdefklmnor";

    private final Font renderFont;
    private final boolean antiAlias;
    private final FontRenderContext fontRenderContext;
    private final GlyphData[] defaultGlyphs = new GlyphData[LAST_GLYPH + 1];
    private final Map<Character, GlyphData> extendedGlyphs = new ConcurrentHashMap<>();
    private final float drawScale;
    private final float rawScale;
    private final float rawTextTop;
    private final float rawTextBottom;
    private final float fontHeight;
    private final float lineHeight;
    private boolean destroyed;

    public GlyphFontRenderer(Font sourceFont, boolean antiAlias) {
        float renderScale = resolveRenderScale();
        this.drawScale = 1.0f / renderScale;
        this.rawScale = renderScale;
        this.renderFont = sourceFont.deriveFont(sourceFont.getStyle(), Math.max(1.0f, sourceFont.getSize2D() * renderScale));
        this.antiAlias = antiAlias;
        this.fontRenderContext = new FontRenderContext(new AffineTransform(), antiAlias, true);

        for (int codePoint = FIRST_GLYPH; codePoint <= LAST_GLYPH; codePoint++) {
            defaultGlyphs[codePoint] = createGlyph((char) codePoint);
        }

        this.rawTextTop = computeRawTextTop();
        this.rawTextBottom = computeRawTextBottom();
        this.fontHeight = Math.max(1.0f, (rawTextBottom - rawTextTop) * drawScale);
        this.lineHeight = computeLineHeight();
    }

    @Override
    public int drawString(String text, float x, float y, int color, boolean shadow) {
        if (destroyed || text == null || text.isEmpty()) {
            return 0;
        }

        int width = 0;
        if (shadow) {
            width = drawInternal(text, x + 0.5f, y + 0.5f, color, true);
        }

        return Math.max(width, drawInternal(text, x, y, color, false));
    }

    @Override
    public int drawGlyphString(String text, float x, float y, GlyphColorProvider colorProvider, boolean shadow) {
        if (destroyed || text == null || text.isEmpty()) {
            return 0;
        }

        int width = 0;
        if (shadow) {
            width = drawGlyphInternal(text, x + 0.5f, y + 0.5f, colorProvider, true);
        }

        return Math.max(width, drawGlyphInternal(text, x, y, colorProvider, false));
    }

    @Override
    public int getStringWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        float width = 0.0f;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (isMalformedSectionPrefix(text, i)) {
                continue;
            }

            if (character == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }

            if (character == '\n') {
                continue;
            }

            width += getGlyph(character).advance;
        }

        return Math.round(width);
    }

    @Override
    public int getFontHeight() {
        return Math.round(fontHeight);
    }

    @Override
    public int getLineHeight() {
        return Math.round(lineHeight);
    }

    @Override
    public int getTextTopOffset() {
        return 0;
    }

    @Override
    public int getTextBottomOffset() {
        return Math.round(fontHeight);
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }

        destroyed = true;
        deleteGlyphTextures(defaultGlyphs);
        for (GlyphData glyph : extendedGlyphs.values()) {
            deleteGlyphTexture(glyph);
        }
        extendedGlyphs.clear();
    }

    private int drawInternal(String text, float x, float y, int color, boolean shadowPass) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0) {
            alpha = 0xFF;
        }

        int activeColor = shadowPass ? applyShadowColor(color) : withAlpha(color, alpha);
        float startX = x * rawScale;
        float drawX = startX;
        float drawY = (y * rawScale) - rawTextTop;

        GL11.glPushMatrix();
        try {
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.enableTexture2D();
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.scale(drawScale, drawScale, 1.0f);

            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (isMalformedSectionPrefix(text, i)) {
                    continue;
                }

                if (character == '\u00a7' && i + 1 < text.length()) {
                    char formatCode = Character.toLowerCase(text.charAt(++i));
                    int colorIndex = COLOR_CODES.indexOf(formatCode);

                    if (colorIndex >= 0) {
                        if (colorIndex < 16) {
                            activeColor = getMinecraftColor(colorIndex, alpha, shadowPass);
                        }
                        else if (formatCode == 'r') {
                            activeColor = shadowPass ? applyShadowColor(color) : withAlpha(color, alpha);
                        }
                    }

                    continue;
                }

                if (character == '\n') {
                    drawX = startX;
                    drawY += lineHeight * rawScale;
                    continue;
                }

                GlyphData glyph = getGlyph(character);
                if (glyph.textureId != 0 && glyph.textureWidth > 0.0f && glyph.textureHeight > 0.0f) {
                    renderGlyph(glyph, drawX - GLYPH_MARGIN, drawY, activeColor);
                }
                drawX += glyph.rawAdvance;
            }
        }
        finally {
            GlStateManager.bindTexture(0);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glPopMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        }
        return Math.round((drawX - startX) * drawScale);
    }

    private int drawGlyphInternal(String text, float x, float y, GlyphColorProvider colorProvider, boolean shadowPass) {
        float startX = x * rawScale;
        float drawX = startX;
        float drawY = (y * rawScale) - rawTextTop;
        Integer formattingColor = null;

        GL11.glPushMatrix();
        try {
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.enableTexture2D();
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.scale(drawScale, drawScale, 1.0f);

            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (isMalformedSectionPrefix(text, i)) {
                    continue;
                }

                if (character == '\u00a7' && i + 1 < text.length()) {
                    char formatCode = Character.toLowerCase(text.charAt(++i));
                    int colorIndex = COLOR_CODES.indexOf(formatCode);

                    if (colorIndex >= 0 && colorIndex < 16) {
                        formattingColor = getMinecraftColor(colorIndex, 0xFF, false);
                    }
                    else if (formatCode == 'r') {
                        formattingColor = null;
                    }

                    continue;
                }

                if (character == '\n') {
                    drawX = startX;
                    drawY += lineHeight * rawScale;
                    continue;
                }

                GlyphData glyph = getGlyph(character);
                int glyphColor = colorProvider.colorForGlyph(character, (drawX - startX) * drawScale, glyph.advance, formattingColor);
                int gAlpha = (glyphColor >>> 24) & 0xFF;
                if (gAlpha == 0) {
                    gAlpha = 0xFF;
                }
                glyphColor = withAlpha(glyphColor, gAlpha);
                if (shadowPass) {
                    glyphColor = applyShadowColor(glyphColor);
                }

                if (glyph.textureId != 0 && glyph.textureWidth > 0.0f && glyph.textureHeight > 0.0f) {
                    renderGlyph(glyph, drawX - GLYPH_MARGIN, drawY, glyphColor);
                }
                drawX += glyph.rawAdvance;
            }
        }
        finally {
            GlStateManager.bindTexture(0);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glPopMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        }
        return Math.round((drawX - startX) * drawScale);
    }

    private void renderGlyph(GlyphData glyph, float x, float y, int color) {
        GlStateManager.bindTexture(glyph.textureId);

        float alpha = ((color >>> 24) & 0xFF) / 255.0f;
        float red = ((color >>> 16) & 0xFF) / 255.0f;
        float green = ((color >>> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        GlStateManager.color(red, green, blue, alpha);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0.0f, 1.0f);
        GL11.glVertex2f(x, y + glyph.textureHeight);
        GL11.glTexCoord2f(1.0f, 1.0f);
        GL11.glVertex2f(x + glyph.textureWidth, y + glyph.textureHeight);
        GL11.glTexCoord2f(1.0f, 0.0f);
        GL11.glVertex2f(x + glyph.textureWidth, y);
        GL11.glEnd();
    }

    private GlyphData getGlyph(char character) {
        if (character >= FIRST_GLYPH && character <= LAST_GLYPH) {
            GlyphData glyph = defaultGlyphs[character];
            if (glyph != null) {
                return glyph;
            }
        }

        return extendedGlyphs.computeIfAbsent(character, this::createGlyph);
    }

    private GlyphData createGlyph(char character) {
        if (destroyed) {
            return new GlyphData(0, 0.0f, 0.0f, 0.0f, 0.0f, 0, 0);
        }

        if (Character.isISOControl(character) && character != '\n') {
            return new GlyphData(0, 0.0f, 0.0f, 0.0f, 0.0f, 0, 0);
        }

        String glyphText = String.valueOf(character);
        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D metricsGraphics = metricsImage.createGraphics();
        try {
            metricsGraphics.setFont(renderFont);
            applyRenderHints(metricsGraphics);
            FontMetrics metrics = metricsGraphics.getFontMetrics();
            Rectangle2D bounds = metrics.getStringBounds(glyphText, metricsGraphics);
            float rawAdvance = Math.max(1.0f, (float) bounds.getWidth());
            int textureWidth = Math.max(1, (int) Math.ceil(rawAdvance) + GLYPH_MARGIN * 2);
            int textureHeight = Math.max(1, metrics.getHeight());
            BufferedImage glyphImage = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);

            Graphics2D glyphGraphics = glyphImage.createGraphics();
            try {
                glyphGraphics.setFont(renderFont);
                glyphGraphics.setBackground(new Color(255, 255, 255, 0));
                glyphGraphics.clearRect(0, 0, textureWidth, textureHeight);
                glyphGraphics.setColor(Color.WHITE);
                applyRenderHints(glyphGraphics);
                glyphGraphics.drawString(glyphText, GLYPH_MARGIN, metrics.getAscent());
            }
            finally {
                glyphGraphics.dispose();
            }

            int textureId = uploadTexture(glyphImage);
            int[] visibleBounds = findVisibleRowBounds(glyphImage);
            return new GlyphData(textureId, textureWidth, textureHeight, rawAdvance, rawAdvance * drawScale, visibleBounds[0], visibleBounds[1]);
        }
        finally {
            metricsGraphics.dispose();
        }
    }

    private int uploadTexture(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, new int[width * height], 0, width);
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(width * height * 4);

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int pixel = pixels[px + py * width];
                byteBuffer.put((byte) ((pixel >> 16) & CHANNEL_MASK));
                byteBuffer.put((byte) ((pixel >> 8) & CHANNEL_MASK));
                byteBuffer.put((byte) (pixel & CHANNEL_MASK));
                byteBuffer.put((byte) ((pixel >> 24) & CHANNEL_MASK));
            }
        }

        byteBuffer.flip();
        int textureId = GL11.glGenTextures();
        GlStateManager.bindTexture(textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, byteBuffer);
        return textureId;
    }

    private float computeRawTextTop() {
        float minTop = Float.MAX_VALUE;

        for (int i = 0; i < ALPHABET.length(); i++) {
            GlyphData glyph = getGlyph(ALPHABET.charAt(i));
            if (!glyph.hasVisiblePixels()) {
                continue;
            }
            minTop = Math.min(minTop, glyph.visibleTop);
        }

        return minTop == Float.MAX_VALUE ? 0.0f : minTop;
    }

    private float computeRawTextBottom() {
        float maxBottom = 0.0f;

        for (int i = 0; i < ALPHABET.length(); i++) {
            GlyphData glyph = getGlyph(ALPHABET.charAt(i));
            if (!glyph.hasVisiblePixels()) {
                continue;
            }
            maxBottom = Math.max(maxBottom, glyph.visibleBottom);
        }

        if (maxBottom <= 0.0f) {
            return Math.max(1.0f, renderFont.getSize2D());
        }

        return maxBottom;
    }

    private float computeLineHeight() {
        return Math.max(fontHeight, (float) renderFont.getStringBounds(ALPHABET, fontRenderContext).getHeight() * drawScale);
    }

    private static int[] findVisibleRowBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int top = -1;
        int bottom = -1;

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                if (((image.getRGB(px, py) >>> 24) & CHANNEL_MASK) != 0) {
                    if (top == -1) {
                        top = py;
                    }
                    bottom = py + 1;
                    break;
                }
            }
        }

        if (top == -1) {
            return new int[]{0, 0};
        }

        return new int[]{top, bottom};
    }

    private static float resolveRenderScale() {
        Minecraft mc = Minecraft.getMinecraft();
        int uiScale = 1;

        try {
            uiScale = Math.max(1, new ScaledResolution(mc).getScaleFactor());
        }
        catch (Exception ignored) {
        }

        return Math.max(MIN_RENDER_SCALE, uiScale * QUALITY_MULTIPLIER);
    }

    private void applyRenderHints(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, antiAlias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antiAlias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static void deleteGlyphTextures(GlyphData[] glyphs) {
        for (int i = 0; i < glyphs.length; i++) {
            deleteGlyphTexture(glyphs[i]);
        }
    }

    private static void deleteGlyphTexture(GlyphData glyph) {
        if (glyph != null && glyph.textureId != 0) {
            GL11.glDeleteTextures(glyph.textureId);
        }
    }

    private static int getMinecraftColor(int colorIndex, int alpha, boolean shadow) {
        int offset = (colorIndex >> 3 & 1) * 85;
        int red = (colorIndex >> 2 & 1) * 170 + offset;
        int green = (colorIndex >> 1 & 1) * 170 + offset;
        int blue = (colorIndex & 1) * 170 + offset;

        if (colorIndex == 6) {
            red += 85;
        }

        if (shadow) {
            red /= 4;
            green /= 4;
            blue /= 4;
        }

        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int applyShadowColor(int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0) {
            alpha = 0xFF;
        }

        int red = ((color >>> 16) & 0xFF) / 4;
        int green = ((color >>> 8) & 0xFF) / 4;
        int blue = (color & 0xFF) / 4;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    private static boolean isMalformedSectionPrefix(String text, int index) {
        if (!isFormattingArtifact(text.charAt(index))) {
            return false;
        }

        for (int i = index + 1; i < text.length(); i++) {
            char next = text.charAt(i);
            if (next == '\u00a7') {
                return true;
            }
            if (!isFormattingArtifact(next)) {
                return false;
            }
        }

        return false;
    }

    private static boolean isFormattingArtifact(char character) {
        return character == '\u00c2' || character == '\u00c3' || character == '\u0082' || character == '\u201a';
    }

    private static final class GlyphData {
        private final int textureId;
        private final float textureWidth;
        private final float textureHeight;
        private final float rawAdvance;
        private final float advance;
        private final int visibleTop;
        private final int visibleBottom;

        private GlyphData(int textureId, float textureWidth, float textureHeight, float rawAdvance, float advance, int visibleTop, int visibleBottom) {
            this.textureId = textureId;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.rawAdvance = rawAdvance;
            this.advance = advance;
            this.visibleTop = visibleTop;
            this.visibleBottom = visibleBottom;
        }

        private boolean hasVisiblePixels() {
            return visibleBottom > visibleTop;
        }
    }
}
