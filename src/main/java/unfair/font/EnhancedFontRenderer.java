package unfair.font;

import net.minecraft.client.renderer.GLAllocation;
import unfair.font.optimize.CachedString;
import unfair.font.optimize.StringHash;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class EnhancedFontRenderer {

    private static final List<EnhancedFontRenderer> instances = new ArrayList<>();
    private final List<StringHash> obfuscated = new ArrayList<>();
    private final Map<String, Integer> stringWidthCache = new HashMap<>();
    private final Queue<Integer> glRemoval = new ConcurrentLinkedQueue<>();
    private final Map<StringHash, CachedString> stringCache = new HashMap<>();

    public EnhancedFontRenderer() {
        instances.add(this);
    }

    public static List<EnhancedFontRenderer> getInstances() {
        return instances;
    }

    public static void invalidateAllInstances() {
        for (EnhancedFontRenderer instance : instances) {
            instance.invalidateAll();
        }
    }

    public static void tickAllInstances() {
        for (EnhancedFontRenderer instance : instances) {
            instance.tick();
        }
    }

    public static void releaseAllInstances() {
        for (EnhancedFontRenderer instance : instances) {
            instance.releaseAll();
        }
    }

    public String getName() {
        return "Enhanced Font Renderer";
    }

    public void tick() {
        for (StringHash hash : obfuscated) {
            release(stringCache.remove(hash));
        }
        obfuscated.clear();
    }

    public int getGlList() {
        final Integer poll = glRemoval.poll();
        return poll == null ? GLAllocation.generateDisplayLists(1) : poll;
    }

    public CachedString get(StringHash key) {
        return stringCache.get(key);
    }

    public void cache(StringHash key, CachedString value) {
        int maxCacheSize = 5000;
        if (stringCache.size() >= maxCacheSize) {
            releaseCache();
        }
        stringCache.put(key, value);
    }

    public Map<String, Integer> getStringWidthCache() {
        return stringWidthCache;
    }

    public void invalidateAll() {
        releaseCache();
        this.stringWidthCache.clear();
        this.obfuscated.clear();
    }

    public void releaseAll() {
        invalidateAll();
    }

    public List<StringHash> getObfuscated() {
        return obfuscated;
    }

    private void releaseCache() {
        for (CachedString cachedString : stringCache.values()) {
            release(cachedString);
        }
        stringCache.clear();
    }

    private void release(CachedString cachedString) {
        if (cachedString != null) {
            glRemoval.offer(cachedString.getListId());
        }
    }
}
