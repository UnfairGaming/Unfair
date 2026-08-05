package cn.unfair.event.types;

/**
 * Event listener priority constants.
 * Lower numeric values run earlier in {@code EventManager}, so handlers marked
 * {@link #HIGHEST} are invoked before {@link #HIGH}, {@link #MEDIUM},
 * {@link #LOW}, and {@link #LOWEST}.
 */
public final class Priority {
    public static final byte

    /**
     * Highest priority, called first.
     */
    HIGHEST = 0,
    /**
     * High priority, called after the highest priority.
     */
    HIGH = 1,
    /**
     * Medium priority, called after the high priority.
     */
    MEDIUM = 2,
    /**
     * Low priority, called after the medium priority.
     */
    LOW = 3,
    /**
     * Lowest priority, called after all the other priorities.
     */
    LOWEST = 4;
    /**
     * All priority values in dispatcher order.
     */
    public static final byte[] VALUE_ARRAY;

    /**
     * Initializes the dispatcher order once when the class is loaded.
     */
    static {
        VALUE_ARRAY = new byte[]{
                HIGHEST,
                HIGH,
                MEDIUM,
                LOW,
                LOWEST
        };
    }
}
