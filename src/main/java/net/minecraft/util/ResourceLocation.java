package net.minecraft.util;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.util.HashMap;
import java.util.Map;

@Getter
public class ResourceLocation {
    public static final Map<String, Map<String, ResourceLocation>> twoDimensionsCache = new HashMap<>();
    private static final Map<String, ResourceLocation> locationCache = new HashMap<>();
    protected final String resourceDomain;
    protected final String resourcePath;

    public ResourceLocation(String resourceName) {
        this(splitObjectName(resourceName));
    }

    public ResourceLocation(String resourceDomain, String resourcePath) {
        this.resourceDomain = IdentifierCaches.NAMESPACES.deduplicate(
                StringUtils.isEmpty(resourceDomain) ? "minecraft" : resourceDomain.toLowerCase()
        );
        this.resourcePath = IdentifierCaches.PATH.deduplicate(resourcePath);
        Validate.notNull(this.resourcePath);
    }

    protected ResourceLocation(String[] resourceName) {
        this(resourceName[0], resourceName[1]);
    }

    /**
     * Splits an object name (such as minecraft:apple) into the domain and path parts and returns these as an array of
     * length 2. If no colon is present in the passed value the returned array will contain {null, toSplit}.
     */
    protected static String[] splitObjectName(String toSplit) {
        String[] astring = new String[]{null, toSplit};
        int i = toSplit.indexOf(':');

        if (i >= 0) {
            astring[1] = toSplit.substring(i + 1);

            if (i > 1) {
                astring[0] = toSplit.substring(0, i);
            }
        }

        return astring;
    }

    public static ResourceLocation of(String path) {
        if (!locationCache.containsKey(path)) {
            String[] strings = splitObjectName(path);
            ResourceLocation location = new ResourceLocation(strings[0], strings[1]); // 鏀逛负鐩存帴鍒涘缓瀹炰緥
            locationCache.put(path, location);
            return location;
        }

        return locationCache.get(path);
    }

    public static ResourceLocation of(String resourceDomainIn, String resourcePathIn) {
        String domain = StringUtils.isEmpty(resourceDomainIn) ? "minecraft" : resourceDomainIn.toLowerCase();

        Map<String, ResourceLocation> v1 = twoDimensionsCache.get(domain);

        if (v1 != null) {
            ResourceLocation v2 = v1.get(resourcePathIn);

            if (v2 != null) {
                return v2;
            }

            ResourceLocation location = new ResourceLocation(domain, resourcePathIn);
            v1.put(resourcePathIn, location);
            return location;
        }

        ResourceLocation location = new ResourceLocation(domain, resourcePathIn);
        Map<String, ResourceLocation> map = new HashMap<>();
        map.put(resourcePathIn, location);
        twoDimensionsCache.put(domain, map);
        return location;
    }

    public String toString() {
        return this.resourceDomain + ':' + this.resourcePath;
    }

    public boolean equals(Object p_equals_1_) {
        if (this == p_equals_1_) {
            return true;
        } else if (!(p_equals_1_ instanceof ResourceLocation resourcelocation)) {
            return false;
        } else {
            return this.resourceDomain.equals(resourcelocation.resourceDomain) &&
                    this.resourcePath.equals(resourcelocation.resourcePath);
        }
    }

    public int hashCode() {
        return 31 * this.resourceDomain.hashCode() + this.resourcePath.hashCode();
    }
}
