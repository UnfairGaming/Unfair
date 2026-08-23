package net.optifine.util;

import java.util.*;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class PropertiesOrdered extends Properties
{
    private Set<Object> keysOrdered = new LinkedHashSet();

    public synchronized Object put(Object key, Object value)
    {
        this.keysOrdered.add(key);
        return super.put(key, value);
    }

    public Set<Object> keySet()
    {
        Set<Object> set = super.keySet();
        this.keysOrdered.retainAll(set);
        return Collections.unmodifiableSet(this.keysOrdered);
    }

    public synchronized Enumeration<Object> keys()
    {
        return Collections.enumeration(this.keySet());
    }
}
