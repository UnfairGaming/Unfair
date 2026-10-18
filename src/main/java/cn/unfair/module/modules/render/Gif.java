package cn.unfair.module.modules.render;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.render.GifTexture;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

public class Gif extends Module {
    public static final String[] GIF_NAMES = {"Deobf", "Fucker", "MonsterCat", "Pig"};
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final float MAX_SIZE = 96.0F;

    /** Individual switches for each GIF widget. */
    public final BooleanProperty deobf = new BooleanProperty("Deobf", true);
    public final BooleanProperty fucker = new BooleanProperty("Fucker", true);
    public final BooleanProperty monsterCat = new BooleanProperty("MonsterCat", true);
    public final BooleanProperty pig = new BooleanProperty("Pig", true);

    private final Map<String, GifTexture> textures = new LinkedHashMap<>();

    public Gif() {
        super("Gif", false);
        for (String name : GIF_NAMES) {
            this.textures.put(name, new GifTexture(name));
        }
    }

    public boolean shouldRenderWidget() {
        return this.isEnabled()
                && MC.theWorld != null
                && MC.thePlayer != null
                && !MC.gameSettings.showDebugInfo;
    }

    public boolean shouldRenderWidget(String name) {
        return this.shouldRenderWidget() && this.isGifEnabled(name);
    }

    private boolean isGifEnabled(String name) {
        return switch (name) {
            case "Deobf" -> this.deobf.getValue();
            case "Fucker" -> this.fucker.getValue();
            case "MonsterCat" -> this.monsterCat.getValue();
            case "Pig" -> this.pig.getValue();
            default -> false;
        };
    }

    public GifTexture getTexture(String name) {
        return this.textures.get(name);
    }

    public float[] getWidgetSize(String name) {
        GifTexture texture = this.getTexture(name);
        if (texture == null) {
            return new float[]{MAX_SIZE, MAX_SIZE};
        }

        texture.prepare();
        float width = texture.getFittedWidth(MAX_SIZE, MAX_SIZE);
        float height = texture.getFittedHeight(MAX_SIZE, MAX_SIZE);
        return new float[]{Math.max(1.0F, width), Math.max(1.0F, height)};
    }

    public void renderWidget(String name, float x, float y, float width, float height, long time) {
        GifTexture texture = this.getTexture(name);
        if (texture == null) {
            return;
        }
        texture.prepare();
        texture.draw(x, y, width, height, time);
    }

    @Override
    public void onDisabled() {
        for (GifTexture texture : this.textures.values()) {
            texture.release();
        }
    }
}
