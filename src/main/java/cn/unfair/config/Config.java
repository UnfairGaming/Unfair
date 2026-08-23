package cn.unfair.config;

import cn.unfair.Unfair;
import cn.unfair.module.Module;
import cn.unfair.property.Property;
import cn.unfair.util.client.ChatUtil;
import com.google.gson.*;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

public class Config {
    public static Minecraft mc = Minecraft.getMinecraft();
    public static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public static String lastConfig;
    public String name;
    public File file;

    public Config(String name, boolean newConfig) {
        this.name = name;
        lastConfig = name;
        if (name.equals("!") || name.equals("default")) {
            this.name = "default";
        }
        this.file = new File("./config/Unfair/", String.format("%s.json", this.name));
        try {
            file.getParentFile().mkdirs();
            if (newConfig) {
                Minecraft.getLogger().info(String.format("Created: %s", this.file.getName()));
            }
        } catch (Exception e) {
            Minecraft.getLogger().error(e.getMessage());
        }
    }

    public void load() {
        try {
            if (!file.exists()) {
                ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r). Creating default config...&r", Unfair.clientName, file.getName()));
                save();
                return;
            }

            JsonElement parsed;
            try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                parsed = JsonParser.parseReader(reader);
            }
            if (parsed == null || !parsed.isJsonObject()) {
                ChatUtil.sendFormatted(String.format("%sInvalid config format (&c&o%s&r)&r", Unfair.clientName, file.getName()));
                return;
            }

            JsonObject jsonObject = parsed.getAsJsonObject();
            for (Module module : Unfair.moduleManager.modules.values()) {
                try {
                    loadModuleSettings(module, jsonObject.get(module.getName()));
                } catch (Exception e) {
                    Minecraft.getLogger().error(
                            String.format("Failed to load settings for module %s", module.getName()),
                            e
                    );
                }
            }
            for (Module module : Unfair.moduleManager.modules.values()) {
                try {
                    loadModuleEnabled(module, jsonObject.get(module.getName()));
                } catch (Exception e) {
                    Minecraft.getLogger().error(
                            String.format("Failed to restore enabled state for module %s", module.getName()),
                            e
                    );
                }
            }
            ChatUtil.sendFormatted(String.format("%sConfig has been loaded (&a&o%s&r)&r", Unfair.clientName, file.getName()));
        } catch (FileNotFoundException e) {
            ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r)&r", Unfair.clientName, file.getName()));
        } catch (JsonSyntaxException e) {
            ChatUtil.sendFormatted(String.format("%sConfig has invalid JSON syntax (&c&o%s&r)&r", Unfair.clientName, file.getName()));
            Minecraft.getLogger().error("JSON syntax error in config " + file.getName(), e);
        } catch (Exception e) {
            Minecraft.getLogger().error("Error loading config " + file.getName(), e);
            ChatUtil.sendFormatted(String.format("%sConfig couldn't be loaded (&c&o%s&r)&r", Unfair.clientName, file.getName()));
        }
    }

    public void save() {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            JsonObject object = new JsonObject();
            for (Module module : Unfair.moduleManager.modules.values()) {
                object.add(module.getName(), saveModule(module));
            }

            File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
                gson.toJson(object, writer);
                writer.write(System.lineSeparator());
            }
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ChatUtil.sendFormatted(String.format("%sConfig has been saved (&a&o%s&r)&r", Unfair.clientName, file.getName()));
        } catch (IOException e) {
            Minecraft.getLogger().error("Error saving config: " + e.getMessage());
            ChatUtil.sendFormatted(String.format("%sConfig couldn't be saved (&c&o%s&r)&r", Unfair.clientName, file.getName()));
        }
    }

    private void loadModuleSettings(Module module, JsonElement moduleElement) {
        if (moduleElement == null || !moduleElement.isJsonObject()) {
            return;
        }

        JsonObject object = moduleElement.getAsJsonObject();
        ArrayList<Property<?>> list = Unfair.propertyManager.properties.get(module.getClass());
        if (list != null) {
            for (Property<?> property : list) {
                if (!object.has(property.getName())) {
                    continue;
                }
                try {
                    property.read(object);
                } catch (Exception e) {
                    Minecraft.getLogger().warn(String.format("Failed to load property %s for module %s", property.getName(), module.getName()));
                }
            }
        }

        readInt(object, "key", module::setKey);
        readBoolean(object, "hidden", module::setHidden);
    }

    private void loadModuleEnabled(Module module, JsonElement moduleElement) {
        if (moduleElement == null || !moduleElement.isJsonObject()) {
            return;
        }
        readBoolean(moduleElement.getAsJsonObject(), "toggled", module::setEnabled);
    }

    private JsonObject saveModule(Module module) {
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
                    Minecraft.getLogger().warn(String.format("Failed to save property %s for module %s", property.getName(), module.getName()));
                }
            }
        }
        return moduleObject;
    }

    private void readBoolean(JsonObject object, String key, BooleanReader reader) {
        JsonElement element = object.get(key);
        if (element != null && element.isJsonPrimitive()) {
            reader.read(element.getAsBoolean());
        }
    }

    private void readInt(JsonObject object, String key, IntReader reader) {
        JsonElement element = object.get(key);
        if (element != null && element.isJsonPrimitive()) {
            reader.read(element.getAsInt());
        }
    }

    private interface BooleanReader {
        void read(boolean value);
    }

    private interface IntReader {
        void read(int value);
    }
}
