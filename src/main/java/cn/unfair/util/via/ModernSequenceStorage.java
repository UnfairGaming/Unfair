package cn.unfair.util.via;

import com.viaversion.viaversion.api.connection.StorableObject;

public final class ModernSequenceStorage implements StorableObject {

    private int sequence;

    public synchronized int next() {
        if (sequence == Integer.MAX_VALUE) {
            sequence = 0;
        }
        return ++sequence;
    }

    public synchronized void reset() {
        sequence = 0;
    }
}
