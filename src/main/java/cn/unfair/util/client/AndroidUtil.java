package cn.unfair.util.client;

public final class AndroidUtil {
    private AndroidUtil() {
    }

    public static boolean isAndroid() {
        return System.getProperty("os.version", "").startsWith("Android-");
    }
}
