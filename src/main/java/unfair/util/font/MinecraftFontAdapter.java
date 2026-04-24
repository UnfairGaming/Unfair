package unfair.util.font;

import net.minecraft.client.renderer.GlStateManager;

public final class MinecraftFontAdapter implements FontRenderer {
    private static final String COLOR_CODES = "0123456789abcdef";
    private static final char SECTION_SIGN = '\u00a7';
    private final net.minecraft.client.gui.FontRenderer fontRenderer;
    private final float scale;

    public MinecraftFontAdapter(net.minecraft.client.gui.FontRenderer fontRenderer) {
        this(fontRenderer, 1.0f);
    }

    public MinecraftFontAdapter(net.minecraft.client.gui.FontRenderer fontRenderer, float scale) {
        this.fontRenderer = fontRenderer;
        this.scale = Math.max(0.5f, Math.min(2.0f, scale));
    }

    @Override
    public int drawString(String text, float x, float y, int color, boolean shadow) {
        if (scale == 1.0f) {
            return fontRenderer.drawString(text, x, y, color, shadow);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0f);
        GlStateManager.scale(scale, scale, 1.0f);
        int width = fontRenderer.drawString(text, 0.0f, 0.0f, color, shadow);
        GlStateManager.popMatrix();
        return Math.round(width * scale);
    }

    @Override
    public int drawGlyphString(String text, float x, float y, GlyphColorProvider colorProvider, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int rawMeasuredWidth = 0;
        Integer formattingColor = null;
        StringBuilder activeFormats = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == SECTION_SIGN && i + 1 < text.length()) {
                char formatCode = Character.toLowerCase(text.charAt(++i));
                int colorIndex = COLOR_CODES.indexOf(formatCode);
                if (colorIndex >= 0) {
                    formattingColor = 0xFF000000 | fontRenderer.getColorCode(formatCode);
                    activeFormats.setLength(0);
                }
                else if (formatCode == 'r') {
                    formattingColor = null;
                    activeFormats.setLength(0);
                }
                else if (isStyleCode(formatCode)) {
                    appendFormatCode(activeFormats, formatCode);
                }
                continue;
            }

            if (character == '\n') {
                continue;
            }

            int rawGlyphWidth = fontRenderer.getCharWidth(character);
            if (rawGlyphWidth < 0) {
                continue;
            }
            if (hasFormat(activeFormats, 'l') && rawGlyphWidth > 0) {
                rawGlyphWidth++;
            }

            float glyphXOffset = rawMeasuredWidth * scale;
            float glyphWidth = rawGlyphWidth * scale;
            int glyphColor = colorProvider.colorForGlyph(character, glyphXOffset, glyphWidth, formattingColor);
            drawFormattedGlyph(character, activeFormats, x + glyphXOffset, y, glyphColor, shadow);
            rawMeasuredWidth += rawGlyphWidth;
        }

        return Math.round(rawMeasuredWidth * scale);
    }

    @Override
    public int getStringWidth(String text) {
        return Math.round(fontRenderer.getStringWidth(text) * scale);
    }

    @Override
    public int getFontHeight() {
        return Math.round(fontRenderer.FONT_HEIGHT * scale);
    }

    @Override
    public int getLineHeight() {
        return Math.round(fontRenderer.FONT_HEIGHT * scale);
    }

    @Override
    public int getTextTopOffset() {
        return 0;
    }

    @Override
    public int getTextBottomOffset() {
        return Math.max(1, Math.round((fontRenderer.FONT_HEIGHT - 1.0f) * scale));
    }

    public float getScale() {
        return scale;
    }

    private void drawFormattedGlyph(char character, StringBuilder activeFormats, float x, float y, int color, boolean shadow) {
        if (activeFormats.length() == 0) {
            drawString(String.valueOf(character), x, y, color, shadow);
            return;
        }

        StringBuilder glyphText = new StringBuilder(activeFormats.length() + 1);
        glyphText.append(activeFormats).append(character);
        drawString(glyphText.toString(), x, y, color, shadow);
    }

    private static boolean isStyleCode(char formatCode) {
        return formatCode >= 'k' && formatCode <= 'o';
    }

    private static boolean hasFormat(StringBuilder activeFormats, char formatCode) {
        for (int i = 1; i < activeFormats.length(); i += 2) {
            if (activeFormats.charAt(i) == formatCode) {
                return true;
            }
        }
        return false;
    }

    private static void appendFormatCode(StringBuilder activeFormats, char formatCode) {
        if (hasFormat(activeFormats, formatCode)) {
            return;
        }
        activeFormats.append(SECTION_SIGN).append(formatCode);
    }
}
