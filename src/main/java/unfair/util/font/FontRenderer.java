package unfair.util.font;

// from raven bs
public interface FontRenderer {
    @FunctionalInterface
    interface GlyphColorProvider {
        int colorForGlyph(char character, float xOffset, float width, Integer formattingColor);
    }

    int drawString(String text, float x, float y, int color, boolean shadow);

    int drawGlyphString(String text, float x, float y, GlyphColorProvider colorProvider, boolean shadow);

    default int drawString(String text, float x, float y, int color) {
        return drawString(text, x, y, color, false);
    }

    default int drawGlyphString(String text, float x, float y, GlyphColorProvider colorProvider) {
        return drawGlyphString(text, x, y, colorProvider, false);
    }

    default int drawStringWithShadow(String text, float x, float y, int color) {
        return drawString(text, x, y, color, true);
    }

    int getStringWidth(String text);

    int getFontHeight();

    default int getLineHeight() {
        return getFontHeight();
    }

    default int getTextTopOffset() {
        return 0;
    }

    default int getTextBottomOffset() {
        return getFontHeight();
    }

    default void destroy() {
    }
}
