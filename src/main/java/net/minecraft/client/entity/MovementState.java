package net.minecraft.client.entity;

public record MovementState(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak,
                            boolean sprint) {
    public byte toByte() {
        byte value = 0;
        if (forward) value |= 1;
        if (backward) value |= 2;
        if (left) value |= 4;
        if (right) value |= 8;
        if (jump) value |= 0x10;
        if (sneak) value |= 0x20;
        if (sprint) value |= 0x40;
        return value;
    }
}
