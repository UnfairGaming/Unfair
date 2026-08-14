package cn.unfair;

import cn.unfair.command.CommandManager;
import cn.unfair.command.commands.*;
import cn.unfair.config.Config;
import cn.unfair.config.WidgetConfig;
import cn.unfair.event.EventManager;
import cn.unfair.management.*;
import cn.unfair.module.Module;
import cn.unfair.module.ModuleManager;
import cn.unfair.module.ModuleWithModuleSettings;
import cn.unfair.property.Property;
import cn.unfair.property.PropertyManager;
import cn.unfair.ui.widget.WidgetManager;
import cn.unfair.ui.widget.impl.HudWidgets;
import cn.unfair.util.rotation.AdvancedJitterHandler;
import de.florianmichael.viamcp.ViaMCP;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class Unfair {
    public static String clientName = "&l[&b&lUnfair&f&l]&r ";
    public static String version = "B22";
    public static int skippedPlayerTicks;
    public static RotationManager rotationManager;
    public static FloatManager floatManager;
    public static BlinkManager blinkManager;
    public static DelayManager delayManager;
    public static LagManager lagManager;
    public static PlayerStateManager playerStateManager;
    public static FriendManager friendManager;
    public static TargetManager targetManager;
    public static PropertyManager propertyManager;
    public static ModuleManager moduleManager;
    public static CommandManager commandManager;
    public static WidgetManager widgetManager;
    public static WidgetConfig widgetConfig;

    public Unfair() {
        this.init();
    }

    public void init() {
        ViaMCP viaMCP = ViaMCP.create();
        viaMCP.getOrCreateAsyncVersionSlider().setVersion(ViaMCP.NATIVE_VERSION);

        rotationManager = new RotationManager();
        floatManager = new FloatManager();
        blinkManager = new BlinkManager();
        delayManager = new DelayManager();
        lagManager = new LagManager();
        playerStateManager = new PlayerStateManager();
        friendManager = new FriendManager();
        targetManager = new TargetManager();
        propertyManager = new PropertyManager();
        moduleManager = new ModuleManager();
        commandManager = new CommandManager();
        widgetManager = new WidgetManager();
        widgetConfig = new WidgetConfig("widgets");
        EventManager.register(rotationManager);
        EventManager.register(floatManager);
        EventManager.register(blinkManager);
        EventManager.register(delayManager);
        EventManager.register(lagManager);
        EventManager.register(new AdvancedJitterHandler());
        EventManager.register(moduleManager);
        EventManager.register(commandManager);
        commandManager.commands.add(new BindCommand());
        commandManager.commands.add(new ConfigCommand());
        commandManager.commands.add(new DenickCommand());
        commandManager.commands.add(new FriendCommand());
        commandManager.commands.add(new HelpCommand());
        commandManager.commands.add(new HideCommand());
        commandManager.commands.add(new IgnCommand());
        commandManager.commands.add(new ItemCommand());
        commandManager.commands.add(new ListCommand());
        commandManager.commands.add(new ModuleCommand());
        commandManager.commands.add(new PlayerCommand());
        commandManager.commands.add(new ShowCommand());
        commandManager.commands.add(new TargetCommand());
        commandManager.commands.add(new ToggleCommand());
        commandManager.commands.add(new VclipCommand());
        for (Module module : moduleManager.modules.values()) {
            ArrayList<Property<?>> properties = new ArrayList<>();
            Class<?> clazz = module.getClass();
            while (clazz != null && clazz != Object.class) {
                for (final Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    final Object obj;
                    try {
                        obj = field.get(module);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                    if (obj instanceof Property<?>) {
                        ((Property<?>) obj).setOwner(module);
                        properties.add((Property<?>) obj);
                    }
                }
                clazz = clazz.getSuperclass();
            }
            if (module instanceof ModuleWithModuleSettings mws) {
                properties.addAll(mws.collectSubModuleProperties());
                for (Module subModule : mws.getSubModules()) {
                    EventManager.register(subModule);
                }
            }
            propertyManager.properties.put(module.getClass(), properties);
            EventManager.register(module);
        }
        HudWidgets.registerAll();
        widgetConfig.load();
        EventManager.register(widgetManager);
        Config config = new Config("default", true);
        if (config.file.exists()) {
            config.load();
        }
        if (friendManager.file.exists()) {
            friendManager.load();
        }
        if (targetManager.file.exists()) {
            targetManager.load();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            config.save();
            widgetConfig.save();
        }));
    }
}
