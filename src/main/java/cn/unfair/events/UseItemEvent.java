package cn.unfair.events;

import cn.unfair.event.events.callables.EventCancellable;
import net.minecraft.item.ItemStack;

public class UseItemEvent extends EventCancellable {
    private final ItemStack itemStack;

    public UseItemEvent(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public ItemStack getItemStack() {
        return this.itemStack;
    }
}
