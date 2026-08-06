package cn.unfair.util;

public final class AndroidUtil {
    private AndroidUtil() {
    }

    public static boolean isAndroid() {
        return System.getProperty("os.version", "").startsWith("Android-");
    }
}
