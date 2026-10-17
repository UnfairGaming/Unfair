package cn.unfair.ui.mainmenu;

import cn.unfair.util.render.GifTexture;

import java.util.Arrays;

/**
 * Displays the welcome GIFs as independent, aspect-ratio-preserving layers.
 * A left-button drag moves a layer; a click without movement is reported to the
 * welcome screen so it can continue to the main menu.
 */
public final class GifAnimationLayer {
    private static final String[] GIF_NAMES = {"Deobf", "Fucker", "MonsterCat", "Pig"};
    private static final float[][] DEFAULT_POSITIONS = {
            {0.12F, 0.18F},
            {0.88F, 0.18F},
            {0.12F, 0.76F},
            {0.88F, 0.76F}
    };

    private final Entry[] entries = new Entry[GIF_NAMES.length];
    private int screenWidth;
    private int screenHeight;
    private int activeIndex = -1;
    private float dragOffsetX;
    private float dragOffsetY;
    private int dragStartX;
    private int dragStartY;
    private boolean moved;

    public GifAnimationLayer() {
        for (int i = 0; i < this.entries.length; i++) {
            this.entries[i] = new Entry(GIF_NAMES[i], DEFAULT_POSITIONS[i][0], DEFAULT_POSITIONS[i][1]);
        }
    }

    public void layout(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (this.screenWidth == 0 || this.screenHeight == 0) {
            this.screenWidth = width;
            this.screenHeight = height;
            for (Entry entry : this.entries) {
                entry.centerX = entry.normalizedX * width;
                entry.centerY = entry.normalizedY * height;
            }
            return;
        }
        if (this.screenWidth == width && this.screenHeight == height) {
            return;
        }

        float widthRatio = width / (float) this.screenWidth;
        float heightRatio = height / (float) this.screenHeight;
        for (Entry entry : this.entries) {
            entry.centerX *= widthRatio;
            entry.centerY *= heightRatio;
        }
        this.screenWidth = width;
        this.screenHeight = height;
    }

    public void draw(int width, int height, long time) {
        this.layout(width, height);
        float maxWidth = clamp(Math.min(width, height) * 0.15F, 64.0F, 148.0F);
        float maxHeight = clamp(Math.min(width, height) * 0.20F, 64.0F, 148.0F);
        for (Entry entry : this.entries) {
            entry.texture.prepare();
            Bounds bounds = this.bounds(entry, maxWidth, maxHeight);
            entry.texture.drawFitted(entry.centerX, entry.centerY, bounds.maxWidth, bounds.maxHeight, time);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        float maxWidth = clamp(Math.min(this.screenWidth, this.screenHeight) * 0.15F, 64.0F, 148.0F);
        float maxHeight = clamp(Math.min(this.screenWidth, this.screenHeight) * 0.20F, 64.0F, 148.0F);
        for (int i = this.entries.length - 1; i >= 0; i--) {
            Entry entry = this.entries[i];
            Bounds bounds = this.bounds(entry, maxWidth, maxHeight);
            if (bounds.contains(mouseX, mouseY)) {
                this.activeIndex = i;
                this.dragOffsetX = mouseX - entry.centerX;
                this.dragOffsetY = mouseY - entry.centerY;
                this.dragStartX = mouseX;
                this.dragStartY = mouseY;
                this.moved = false;
                return true;
            }
        }
        return false;
    }

    public boolean mouseClickMove(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || this.activeIndex < 0) {
            return false;
        }
        Entry entry = this.entries[this.activeIndex];
        if (Math.abs(mouseX - this.dragStartX) > 2 || Math.abs(mouseY - this.dragStartY) > 2) {
            this.moved = true;
        }
        entry.centerX = mouseX - this.dragOffsetX;
        entry.centerY = mouseY - this.dragOffsetY;
        this.clampToScreen(entry);
        return true;
    }

    /** Returns true when the release was a click, rather than a drag. */
    public boolean mouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || this.activeIndex < 0) {
            return false;
        }
        this.mouseClickMove(mouseX, mouseY, mouseButton);
        boolean clicked = !this.moved;
        this.activeIndex = -1;
        this.moved = false;
        return clicked;
    }

    public void release() {
        Arrays.stream(this.entries).forEach(entry -> entry.texture.release());
        this.activeIndex = -1;
    }

    private Bounds bounds(Entry entry, float maxWidth, float maxHeight) {
        float aspect = entry.texture.getAspect() > 0.0F ? entry.texture.getAspect() : 1.0F;
        float width = Math.min(maxWidth, maxHeight * aspect);
        float height = Math.min(maxHeight, width / aspect);
        return new Bounds(entry.centerX - width * 0.5F, entry.centerY - height * 0.5F, width, height, maxWidth, maxHeight);
    }

    private void clampToScreen(Entry entry) {
        float maxWidth = clamp(Math.min(this.screenWidth, this.screenHeight) * 0.15F, 64.0F, 148.0F);
        float maxHeight = clamp(Math.min(this.screenWidth, this.screenHeight) * 0.20F, 64.0F, 148.0F);
        Bounds bounds = this.bounds(entry, maxWidth, maxHeight);
        entry.centerX = Math.max(bounds.width * 0.5F, Math.min(this.screenWidth - bounds.width * 0.5F, entry.centerX));
        entry.centerY = Math.max(bounds.height * 0.5F, Math.min(this.screenHeight - bounds.height * 0.5F, entry.centerY));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Entry {
        private final GifTexture texture;
        private final float normalizedX;
        private final float normalizedY;
        private float centerX;
        private float centerY;

        private Entry(String name, float normalizedX, float normalizedY) {
            this.texture = new GifTexture(name);
            this.normalizedX = normalizedX;
            this.normalizedY = normalizedY;
        }
    }

    private static final class Bounds {
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float maxWidth;
        private final float maxHeight;

        private Bounds(float x, float y, float width, float height, float maxWidth, float maxHeight) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
        }

        private boolean contains(float mouseX, float mouseY) {
            return mouseX >= this.x && mouseX <= this.x + this.width
                    && mouseY >= this.y && mouseY <= this.y + this.height;
        }
    }
}
