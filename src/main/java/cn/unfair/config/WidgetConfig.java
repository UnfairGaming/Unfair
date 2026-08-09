package cn.unfair.config;

import cn.unfair.Unfair;
import cn.unfair.ui.widget.Widget;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class WidgetConfig {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File file;

    public WidgetConfig(String name) {
        this.file = new File("./config/Unfair/", name + ".json");
    }

    public void load() {
        if (!this.file.exists() || Unfair.widgetManager == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(this.file))) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            for (Widget widget : Unfair.widgetManager.widgets) {
                if (!obj.has(widget.name)) {
                    continue;
                }
                JsonObject widgetObject = obj.getAsJsonObject(widget.name);
                widget.loadConfig(widgetObject);
            }
        } catch (Exception ignored) {
        }
    }

    public void save() {
        if (Unfair.widgetManager == null) {
            return;
        }
        try {
            File parent = this.file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            JsonObject obj = new JsonObject();
            for (Widget widget : Unfair.widgetManager.widgets) {
                JsonObject widgetObject = new JsonObject();
                widget.saveConfig(widgetObject);
                obj.add(widget.name, widgetObject);
            }

            try (FileWriter writer = new FileWriter(this.file)) {
                writer.write(gson.toJson(obj));
            }
        } catch (Exception ignored) {
        }
    }
}
