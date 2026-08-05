package net.minecraft.client.main;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.properties.PropertyMap.Serializer;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.lwjgl.Sys;
import org.lwjgl.glfw.GlfwEventLoop;
import org.lwjgl.system.Configuration;

import java.io.File;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    public static final long startupTime = System.currentTimeMillis();

    public static void main(String[] args) {
        Configuration.MEMORY_ALLOCATOR.set("jemalloc");

        Configuration.DISABLE_CHECKS.set(true);
        Configuration.DISABLE_FUNCTION_CHECKS.set(true);
        Configuration.DISABLE_HASH_CHECKS.set(true);
        Configuration.DEBUG.set(false);
        Configuration.DEBUG_FUNCTIONS.set(false);

        System.setProperty("java.net.preferIPv4Stack", "true");

        OptionParser optionParser = new OptionParser();
        optionParser.allowsUnrecognizedOptions();
        optionParser.accepts("demo");
        optionParser.accepts("fullscreen");
        optionParser.accepts("checkGlErrors");

        OptionSpec<String> server = optionParser.accepts("server").withRequiredArg();

        OptionSpec<Integer> port = optionParser.accepts("port").withRequiredArg().ofType(Integer.class).defaultsTo(25565);

        OptionSpec<File> gameDir = optionParser.accepts("gameDir").withRequiredArg().ofType(File.class).defaultsTo(new File("."));
        OptionSpec<File> assetsDir = optionParser.accepts("assetsDir").withRequiredArg().ofType(File.class);
        OptionSpec<File> resourcePackDir = optionParser.accepts("resourcePackDir").withRequiredArg().ofType(File.class);

        OptionSpec<String> proxyHost = optionParser.accepts("proxyHost").withRequiredArg();
        OptionSpec<Integer> proxyPort = optionParser.accepts("proxyPort").withRequiredArg().defaultsTo("8080", new String[0]).ofType(Integer.class);
        OptionSpec<String> proxyUser = optionParser.accepts("proxyUser").withRequiredArg();
        OptionSpec<String> proxyPass = optionParser.accepts("proxyPass").withRequiredArg();

        OptionSpec<String> username = optionParser.accepts("username").withRequiredArg().defaultsTo("Player" + System.currentTimeMillis() % 1000L);
        OptionSpec<String> uuid = optionParser.accepts("uuid").withRequiredArg();
        OptionSpec<String> accessToken = optionParser.accepts("accessToken").withRequiredArg().required();

        OptionSpec<String> version = optionParser.accepts("version").withRequiredArg().required();

        OptionSpec<Integer> width = optionParser.accepts("width").withRequiredArg().ofType(Integer.class).defaultsTo(854);
        OptionSpec<Integer> height = optionParser.accepts("height").withRequiredArg().ofType(Integer.class).defaultsTo(480);

        OptionSpec<String> userProperties = optionParser.accepts("userProperties").withRequiredArg().defaultsTo("{}");
        OptionSpec<String> profileProperties = optionParser.accepts("profileProperties").withRequiredArg().defaultsTo("{}");

        OptionSpec<String> assetIndex = optionParser.accepts("assetIndex").withRequiredArg();

        OptionSpec<String> userType = optionParser.accepts("userType").withRequiredArg().defaultsTo("legacy");
        OptionSpec<String> nonOptions = optionParser.nonOptions();
        OptionSet parse = optionParser.parse(args);

        List<String> list = parse.valuesOf(nonOptions);

        if (!list.isEmpty()) {
            System.out.println("Completely ignored arguments: " + list);
        }

        String proxyHostStr = parse.valueOf(proxyHost);
        Proxy proxy = Proxy.NO_PROXY;

        if (proxyHostStr != null) {
            try {
                proxy = new Proxy(Type.SOCKS, new InetSocketAddress(proxyHostStr, parse.valueOf(proxyPort)));
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }

        String s1 = parse.valueOf(proxyUser);
        String s2 = parse.valueOf(proxyPass);

        if (!proxy.equals(Proxy.NO_PROXY) && isNullOrEmpty(s1) && isNullOrEmpty(s2)) {
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(s1, s2.toCharArray());
                }
            });
        }

        int i = parse.valueOf(width);
        int j = parse.valueOf(height);

        boolean flag = parse.has("fullscreen");
        boolean flag1 = parse.has("checkGlErrors");
        boolean flag2 = parse.has("demo");

        String s3 = parse.valueOf(version);
        Gson gson = (new GsonBuilder()).registerTypeAdapter(PropertyMap.class, new Serializer()).create();
        PropertyMap propertymap = gson.fromJson(parse.valueOf(userProperties), PropertyMap.class);
        PropertyMap propertymap1 = gson.fromJson(parse.valueOf(profileProperties), PropertyMap.class);

        File file1 = parse.valueOf(gameDir);
        File file2 = parse.has(assetsDir) ? parse.valueOf(assetsDir) : new File(file1, "assets/");
        File file3 = parse.has(resourcePackDir) ? parse.valueOf(resourcePackDir) : new File(file1, "resourcepacks/");

        String s4 = parse.has(uuid) ? uuid.value(parse) : username.value(parse);
        String s5 = parse.has(assetIndex) ? assetIndex.value(parse) : null;
        String s6 = parse.valueOf(server);

        Integer integer = parse.valueOf(port);

        Session session = new Session(username.value(parse), s4, accessToken.value(parse), userType.value(parse));

        GameConfiguration gameconfiguration = new GameConfiguration(new GameConfiguration.UserInformation(session, propertymap, propertymap1, proxy), new GameConfiguration.DisplayInformation(i, j, flag, flag1), new GameConfiguration.FolderInformation(file1, file3, file2, s5), new GameConfiguration.GameInformation(flag2, s3), new GameConfiguration.ServerInformation(s6, integer));
        Runtime.getRuntime().addShutdownHook(new Thread("Client Shutdown Thread") {
            public void run() {
                Minecraft.stopIntegratedServer();
            }
        });
        Thread.currentThread().setName("GLFW Event Thread");
        GlfwEventLoop.initializeCurrentThread();
        Sys.initialize();

        AtomicReference<Throwable> clientFailure = new AtomicReference<>();
        Thread clientThread = new Thread(() -> {
            try {
                Minecraft minecraft = new Minecraft(gameconfiguration);
                minecraft.run();
            } catch (Throwable throwable) {
                clientFailure.set(throwable);
            }
        }, "Client thread");

        GlfwEventLoop.runEventLoop(clientThread);

        Throwable throwable = clientFailure.get();
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        if (throwable != null) {
            throw new RuntimeException("Client thread failed", throwable);
        }
    }

    private static boolean isNullOrEmpty(String str) {
        return str != null && !str.isEmpty();
    }
}
