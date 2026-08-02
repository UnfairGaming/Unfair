package cn.unfair.config;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.TickEvent;
import cn.unfair.mixin.IAccessorMinecraft;
import cn.unfair.module.Module;
import cn.unfair.property.Property;
import cn.unfair.util.ChatUtil;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

public class Config {
    public static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public static String lastConfig;
    private static final long AUTO_SAVE_DELAY_MS = 1000L;
    private static Config currentConfig;
    private static boolean initialized = false;
    private static boolean loading = false;
    private static boolean dirty = false;
    private static long lastDirtyTime = 0L;
    public String name;
    public File file;

    public Config(String name, boolean newConfig) {
        this.name = name;
        if (name.equals("!") || name.equals("default")) {
            this.name = "default";
        }
        lastConfig = this.name;
        this.file = new File(new File(mc.mcDataDir, "config/Unfair"), String.format("%s.json", this.name));
        try {
            file.getParentFile().mkdirs();
            if (newConfig) {
                ((IAccessorMinecraft) mc).getLogger().info(String.format("Created: %s", this.file.getName()));
            }
        } catch (Exception e) {
            ((IAccessorMinecraft) mc).getLogger().error(e.getMessage());
        }
    }

    public void load() {
        try {
            if (dirty && currentConfig != null && !currentConfig.file.equals(this.file)) {
                saveCurrentSilent();
            }
            currentConfig = this;
            lastConfig = this.name;

            if (!file.exists()) {
                ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r). Creating default config...&r", Unfair.clientName, file.getName()));
                save();
                return;
            }

            JsonElement parsed;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                parsed = new JsonParser().parse(reader);
            }
            if (parsed == null || !parsed.isJsonObject()) {
                ChatUtil.sendFormatted(String.format("%sInvalid config format (&c&o%s&r)&r", Unfair.clientName, file.getName()));
                return;
            }

            JsonObject jsonObject = parsed.getAsJsonObject();
            loading = true;
            try {
                for (Module module : Unfair.moduleManager.modules.values()) {
                    JsonElement moduleObj = jsonObject.get(module.getName());
                    if (moduleObj != null && moduleObj.isJsonObject()) {
                        JsonObject object = moduleObj.getAsJsonObject();

                        ArrayList<Property<?>> list = Unfair.propertyManager.properties.get(module.getClass());
                        if (list != null) {
                            for (Property<?> property : list) {
                                if (object.has(property.getName())) {
                                    try {
                                        property.read(object);
                                    } catch (Exception e) {
                                        ((IAccessorMinecraft) mc).getLogger().warn(String.format("Failed to load property %s for module %s", property.getName(), module.getName()));
                                    }
                                }
                            }
                        }

                        if (object.has("toggled")) {
                            JsonElement toggled = object.get("toggled");
                            if (toggled != null && toggled.isJsonPrimitive()) {
                                module.setEnabled(toggled.getAsBoolean());
                            }
                        }

                        if (object.has("key")) {
                            JsonElement key = object.get("key");
                            if (key != null && key.isJsonPrimitive()) {
                                module.setKey(key.getAsInt());
                            }
                        }

                        if (object.has("hidden")) {
                            JsonElement hidden = object.get("hidden");
                            if (hidden != null && hidden.isJsonPrimitive()) {
                                module.setHidden(hidden.getAsBoolean());
                            }
                        }
                    }
                }
            } finally {
                loading = false;
                dirty = false;
            }
            ChatUtil.sendFormatted(String.format("%sConfig has been loaded (&a&o%s&r)&r", Unfair.clientName, file.getName()));
        } catch (FileNotFoundException e) {
            ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r)&r", Unfair.clientName, file.getName()));
        } catch (JsonSyntaxException e) {
            ChatUtil.sendFormatted(String.format("%sConfig has invalid JSON syntax (&c&o%s&r)&r", Unfair.clientName, file.getName()));
            ((IAccessorMinecraft) mc).getLogger().error("JSON Syntax Error: " + e.getMessage());
        } catch (Exception e) {
            ((IAccessorMinecraft) mc).getLogger().error("Error loading config: " + e.getMessage());
            ChatUtil.sendFormatted(String.format("%sConfig couldn't be loaded (&c&o%s&r)&r", Unfair.clientName, file.getName()));
        }
    }

    public void save() {
        save(false);
    }

    private void save(boolean silent) {
        try {
            currentConfig = this;
            lastConfig = this.name;
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            JsonObject object = new JsonObject();
            for (Module module : Unfair.moduleManager.modules.values()) {
                JsonObject moduleObject = new JsonObject();
                moduleObject.addProperty("toggled", module.isEnabled());
                moduleObject.addProperty("key", module.getKey());
                moduleObject.addProperty("hidden", module.isHidden());

                ArrayList<Property<?>> list = Unfair.propertyManager.properties.get(module.getClass());
                if (list != null) {
                    for (Property<?> property : list) {
                        try {
                            property.write(moduleObject);
                        } catch (Exception e) {
                            ((IAccessorMinecraft) mc).getLogger().warn(String.format("Failed to save property %s for module %s", property.getName(), module.getName()));
                        }
                    }
                }
                object.add(module.getName(), moduleObject);
            }

            File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
            try (PrintWriter printWriter = new PrintWriter(new FileWriter(tempFile))) {
                printWriter.println(gson.toJson(object));
            }
            try {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            if (!silent) {
                ChatUtil.sendFormatted(String.format("%sConfig has been saved (&a&o%s&r)&r", Unfair.clientName, file.getName()));
            }
        } catch (IOException e) {
            ((IAccessorMinecraft) mc).getLogger().error("Error saving config: " + e.getMessage());
            if (!silent) {
                ChatUtil.sendFormatted(String.format("%sConfig couldn't be saved (&c&o%s&r)&r", Unfair.clientName, file.getName()));
            }
        }
    }

    public static void initAutosave(Config config) {
        currentConfig = config;
        lastConfig = config.name;
        initialized = true;
    }

    public static void markDirty() {
        if (!initialized || loading) {
            return;
        }
        dirty = true;
        lastDirtyTime = System.currentTimeMillis();
    }

    public static void markDirtyAndSave() {
        markDirty();
        if (dirty) {
            saveCurrentSilent();
        }
    }

    public static void saveCurrent() {
        getCurrentConfig().save();
    }

    public static void saveCurrentSilent() {
        getCurrentConfig().save(true);
    }

    private static Config getCurrentConfig() {
        if (currentConfig == null) {
            currentConfig = new Config(lastConfig == null ? "default" : lastConfig, false);
        }
        return currentConfig;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE
                && dirty
                && System.currentTimeMillis() - lastDirtyTime >= AUTO_SAVE_DELAY_MS) {
            saveCurrentSilent();
        }
    }
}
