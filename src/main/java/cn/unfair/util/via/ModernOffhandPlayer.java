package cn.unfair.util.via;

/** Client-side animation state for the modern offhand. */
public interface ModernOffhandPlayer {

    void swingOffhand();

    float getOffhandSwingProgress(float partialTicks);
}
