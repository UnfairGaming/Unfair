package cn.unfair.events;

import cn.unfair.event.events.callables.EventCancellable;

public class SwapItemEvent extends EventCancellable {
    private final int slot;
    private final int offset;

    public SwapItemEvent(int slot, int offset) {
        this.slot = slot;
        this.offset = Math.clamp(offset, -1, 1);
    }

    public int setSlot(int integer) {
        return this.slot >= 0 && this.slot <= 8 ? this.slot : Math.floorMod(integer - this.offset, 9);
    }
}
