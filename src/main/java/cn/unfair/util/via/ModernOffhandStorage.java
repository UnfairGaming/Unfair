package cn.unfair.util.via;

import com.viaversion.viaversion.api.connection.StorableObject;
import com.viaversion.viaversion.api.minecraft.item.Item;

/**
 * Temporary per-connection bridge for the slot 45 item removed by ViaRewind.
 */
public final class ModernOffhandStorage implements StorableObject {

    /**
     * A window id ignored by vanilla 1.8 and consumed by our client hook.
     */
    public static final byte CLIENT_WINDOW_ID = -2;

    private Item item;
    private boolean pending;

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
        this.pending = true;
    }

    public boolean hasPendingItem() {
        return pending;
    }

    public Item takeItem() {
        pending = false;
        final Item result = item;
        item = null;
        return result;
    }
}
