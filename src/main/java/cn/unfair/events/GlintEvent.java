package cn.unfair.events;

import cn.unfair.event.events.callables.EventCancellable;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;

public final class GlintEvent extends EventCancellable {
    private final ItemStack itemStack;
    private final IBakedModel model;
    private boolean enchanted;

    public GlintEvent(ItemStack itemStack, IBakedModel model, boolean enchanted) {
        this.itemStack = itemStack;
        this.model = model;
        this.enchanted = enchanted;
    }

    public ItemStack getItemStack() {
        return this.itemStack;
    }

    public IBakedModel getModel() {
        return this.model;
    }

    public boolean isEnchanted() {
        return this.enchanted;
    }

    public void setEnchanted(boolean enchanted) {
        this.enchanted = enchanted;
    }
}
