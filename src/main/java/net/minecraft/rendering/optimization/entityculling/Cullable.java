package net.minecraft.rendering.optimization.entityculling;

public interface Cullable {
    void setTimeout();

    boolean isForcedVisible();

    boolean isCulled();

    void setCulled(boolean value);

    boolean isOutOfCamera();

    void setOutOfCamera(boolean value);
}