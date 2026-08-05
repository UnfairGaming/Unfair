package cn.unfair.util.font;

import java.awt.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public enum Fonts {
    interBold("Inter_Bold"),
    interMedium("Inter_Medium"),
    interRegular("Inter_Regular"),
    interSemiBold("Inter_SemiBold"),
    interLight("Inter_Light"),
    nursultan("Nursultan"),
    urbanist("Urbanist"),
    exhi("Exhi"),
    genshin("Genshin"),
    tahoma("tahoma"),
    tahomaBold("tahomaBold"),
    consola("consola"),
    comfortaa("Comfortaa"),
    helveticaNeue("helveticaNeue"),
    esp("esp"); // Got this specifically for the shit font that Augustus uses.

    private final String file;
    private final Map<Float, FontRenderer> fontMap = new HashMap<>();
    private Font baseFont;

    public FontRenderer get(float size) {
        return this.fontMap.computeIfAbsent(size, font -> {
            try {
                return create(this.file, size);
            } catch (Exception var5) {
                throw new RuntimeException("Unable to load font: " + this, var5);
            }
        });
    }

    public FontRenderer create(String file, float size) {
        return new FontRenderer(this.getBaseFont(file).deriveFont(Font.PLAIN, size));
    }

    private Font getBaseFont(String file) {
        if (this.baseFont != null) {
            return this.baseFont;
        }
        try (InputStream in = Objects.requireNonNull(
                Fonts.class.getResourceAsStream("/assets/minecraft/unfair/font/" + file + ".ttf"), "Font resource is null"
        )) {
            this.baseFont = Font.createFont(0, in);
            return this.baseFont;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create font", ex);
        }
    }

    Fonts(String file) {
        this.file = file;
    }
}
