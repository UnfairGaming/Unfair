package unfair.util.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class FontManager {
    private static final String MINECRAFT = "Minecraft";
    private static final String RESOURCE_ROOT = "/assets/unfair/fonts/";
    private static final int MAX_CACHED_RENDERERS = 512;
    private static final float DEFAULT_HUD_FONT_SIZE = 10.0f;
    private static final float DEFAULT_CLICK_GUI_HEADER_HEIGHT = 9.0f;
    private static final float DEFAULT_CLICK_GUI_SETTING_HEIGHT = 9.0f;
    private static final float DEFAULT_NAMETAG_FONT_SIZE = 9.0f;

    private static final BundledFont[] BUNDLED_FONTS = {
            new BundledFont("Sf-Bold", "Sf-Bold.ttf"),
            new BundledFont("Sf-Regular", "Sf-Regular.ttf"),
            new BundledFont("Sf-Ui", "Sf-Ui.ttf")
    };

    private static final String[] HUD_FONT_OPTIONS = buildHudFontOptions();
    private static final Map<String, BundledFont> BUNDLED_FONT_MAP = buildBundledFontMap();
    private static final Map<String, Font> BASE_FONT_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, FontRenderer> FONT_CACHE = new LinkedHashMap<>(16, 0.75f, true);

    private FontManager() {
    }

    public static String[] getHudFontOptions() {
        return HUD_FONT_OPTIONS.clone();
    }

    public static FontRenderer getHudRenderer(String family, float scale) {
        float safeScale = Math.max(0.5f, Math.min(2.0f, scale));
        return getRenderer(family, DEFAULT_HUD_FONT_SIZE * safeScale);
    }

    public static FontRenderer getClickGuiHeaderRenderer(String family) {
        return getRendererForPixelHeight(family, DEFAULT_CLICK_GUI_HEADER_HEIGHT);
    }

    public static FontRenderer getClickGuiSettingRenderer(String family) {
        return getRendererForPixelHeight(family, DEFAULT_CLICK_GUI_SETTING_HEIGHT);
    }

    public static FontRenderer getNametagRenderer(String family) {
        return getRenderer(family, DEFAULT_NAMETAG_FONT_SIZE);
    }

    private static FontRenderer getRenderer(String family, float fontSize) {
        float safeFontSize = Math.max(1.0f, fontSize);
        BundledFont bundledFont;

        if (family == null || isMinecraftFont(family)) {
            return getMinecraftRenderer(safeFontSize);
        }

        bundledFont = BUNDLED_FONT_MAP.get(family);
        if (bundledFont == null) {
            return getMinecraftRenderer(safeFontSize);
        }

        String key = family + "#" + quantizeForCacheKey(safeFontSize) + "#" + getUiScale();
        return getCachedRenderer(key, () -> {
            Font baseFont = BASE_FONT_CACHE.computeIfAbsent(bundledFont.fileName, FontManager::loadBaseFont);
            if (baseFont == null) {
                return getMinecraftRenderer(safeFontSize);
            }

            return new GlyphFontRenderer(baseFont.deriveFont(safeFontSize), true);
        });
    }

    private static FontRenderer getRendererForPixelHeight(String family, float targetHeight) {
        float safeTargetHeight = Math.max(1.0f, targetHeight);

        if (family == null || isMinecraftFont(family)) {
            return getMinecraftRenderer(safeTargetHeight);
        }

        BundledFont bundledFont = BUNDLED_FONT_MAP.get(family);
        if (bundledFont == null) {
            return getMinecraftRenderer(safeTargetHeight);
        }

        String key = family + "#height#" + quantizeForCacheKey(safeTargetHeight) + "#" + getUiScale();
        return getCachedRenderer(key, () -> {
            Font baseFont = BASE_FONT_CACHE.computeIfAbsent(bundledFont.fileName, FontManager::loadBaseFont);
            if (baseFont == null) {
                return getMinecraftRenderer(safeTargetHeight);
            }

            return createHeightMatchedRenderer(baseFont, safeTargetHeight);
        });
    }

    private static FontRenderer createHeightMatchedRenderer(Font baseFont, float targetHeight) {
        float derivedSize = targetHeight;
        GlyphFontRenderer renderer = new GlyphFontRenderer(baseFont.deriveFont(derivedSize), true);

        for (int i = 0; i < 2; i++) {
            float measuredHeight = Math.max(1.0f, renderer.getFontHeight());
            float difference = Math.abs(measuredHeight - targetHeight);
            if (difference <= 0.5f) {
                break;
            }

            GlyphFontRenderer previousRenderer = renderer;
            derivedSize = Math.max(1.0f, derivedSize * (targetHeight / measuredHeight));
            renderer = new GlyphFontRenderer(baseFont.deriveFont(derivedSize), true);
            previousRenderer.destroy();
        }

        return renderer;
    }

    private static FontRenderer getMinecraftRenderer(float fontSize) {
        float vanillaHeight = Math.max(1.0f, Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT);
        float scale = Math.max(0.5f, Math.min(2.0f, fontSize / vanillaHeight));
        String key = MINECRAFT + "#" + quantizeForCacheKey(scale);
        return getCachedRenderer(key, () -> new MinecraftFontAdapter(Minecraft.getMinecraft().fontRendererObj, scale));
    }

    public static boolean isMinecraftFont(String family) {
        return family == null || MINECRAFT.equalsIgnoreCase(family);
    }

    private static String[] buildHudFontOptions() {
        String[] options = new String[BUNDLED_FONTS.length + 1];
        options[0] = MINECRAFT;

        for (int i = 0; i < BUNDLED_FONTS.length; i++) {
            options[i + 1] = BUNDLED_FONTS[i].displayName;
        }

        return options;
    }

    private static Map<String, BundledFont> buildBundledFontMap() {
        LinkedHashMap<String, BundledFont> fontMap = new LinkedHashMap<>();

        for (BundledFont bundledFont : BUNDLED_FONTS) {
            fontMap.put(bundledFont.displayName, bundledFont);
        }

        return fontMap;
    }

    private static Font loadBaseFont(String fileName) {
        byte[] fontData = readFontData(fileName);
        if (fontData == null) {
            return null;
        }

        try {
            return Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(fontData));
        }
        catch (Exception ignored) {
            try {
                return Font.createFont(Font.TYPE1_FONT, new ByteArrayInputStream(fontData));
            }
            catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static int getUiScale() {
        try {
            return Math.max(1, new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor());
        }
        catch (Exception ignored) {
            return 1;
        }
    }

    private static float quantizeForCacheKey(float value) {
        return Math.round(value * 100.0f) / 100.0f;
    }

    private static byte[] readFontData(String fileName) {
        try (InputStream inputStream = FontManager.class.getResourceAsStream(RESOURCE_ROOT + fileName)) {
            if (inputStream == null) {
                return null;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;

            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            return outputStream.toByteArray();
        }
        catch (IOException ignored) {
            return null;
        }
    }

    private static synchronized FontRenderer getCachedRenderer(String key, Supplier<FontRenderer> rendererSupplier) {
        FontRenderer renderer = FONT_CACHE.get(key);
        if (renderer != null) {
            return renderer;
        }

        renderer = rendererSupplier.get();
        FONT_CACHE.put(key, renderer);
        trimFontCache();
        return renderer;
    }

    private static void trimFontCache() {
        while (FONT_CACHE.size() > MAX_CACHED_RENDERERS) {
            Iterator<Map.Entry<String, FontRenderer>> iterator = FONT_CACHE.entrySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }

            Map.Entry<String, FontRenderer> eldestEntry = iterator.next();
            iterator.remove();
            if (eldestEntry.getValue() != null) {
                eldestEntry.getValue().destroy();
            }
        }
    }

    private static final class BundledFont {
        private final String displayName;
        private final String fileName;

        private BundledFont(String displayName, String fileName) {
            this.displayName = displayName;
            this.fileName = fileName;
        }
    }
}
