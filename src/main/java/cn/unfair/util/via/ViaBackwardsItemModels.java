package cn.unfair.util.via;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.limiter.TagLimiter;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.NumberTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.viaversion.api.data.MappingDataLoader;
import com.viaversion.viaversion.api.data.Mappings;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ViaBackwardsItemModels {
    private static final Map<Integer, String> MODELS_BY_CUSTOM_MODEL_DATA = Maps.newHashMap();
    private static final Map<String, String> MODELS_BY_BACKUP_TAG = Maps.newHashMap();
    private static final Map<String, String> MODELS_BY_DISPLAY_NAME = Maps.newHashMap();
    private static final List<String> MODEL_NAMES = Lists.newArrayList();
    private static boolean initialized;

    private ViaBackwardsItemModels() {
    }

    public static synchronized List<String> getModelNames() {
        initialize();
        return MODEL_NAMES;
    }

    public static ModelResourceLocation getModelLocation(ItemStack stack) {
        initialize();

        String model = getModelName(stack);
        return model == null ? null : new ModelResourceLocation(model, "inventory");
    }

    public static String getModelName(ItemStack stack) {
        initialize();
        String model = getMappedModel(stack);
        return isBrokenElytra(stack, model) ? "elytra_broken" : model;
    }

    public static boolean isModel(ItemStack stack, String modelName) {
        return modelName != null && modelName.equals(getModelName(stack));
    }

    private static void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        registerMappingSet("1.10to1.9.4", "1.10");
        registerMappingSet("1.11to1.10", "1.11");
        registerMappingSet("1.12to1.11", "1.12");
        registerMappingSet("1.13to1.12", "1.13");
        registerMappingSet("1.13.2to1.13", "1.13.2");
        registerMappingSet("1.14to1.13.2", "1.14");
        registerMappingSet("1.15to1.14", "1.15");
        registerMappingSet("1.16to1.15", "1.16");
        registerMappingSet("1.16.2to1.16", "1.16.2");
        registerMappingSet("1.17to1.16.2", "1.17");
        registerMappingSet("1.18to1.17", "1.18");
        registerMappingSet("1.19to1.18", "1.19");
        registerMappingSet("1.19.3to1.19", "1.19.3");
        registerMappingSet("1.19.4to1.19.3", "1.19.4");
        registerMappingSet("1.20to1.19.4", "1.20");
        registerMappingSet("1.20.2to1.20", "1.20.2");
        registerMappingSet("1.20.3to1.20.2", "1.20.3");
        registerMappingSet("1.20.5to1.20.3", "1.20.5");
        registerMappingSet("1.21to1.20.5", "1.21");
        registerMappingSet("1.21.2to1.21", "1.21.2");
        registerMappingSet("1.21.4to1.21.2", "1.21.4");
        registerMappingSet("1.21.5to1.21.4", "1.21.5");
        registerMappingSet("1.21.6to1.21.5", "1.21.6");
        registerMappingSet("1.21.7to1.21.6", "1.21.7");
        registerMappingSet("1.21.9to1.21.7", "1.21.9");
        registerMappingSet("1.21.11to1.21.9", "1.21.11");

        addModelName("respawn_anchor");
        addModelName("dirt_path");
        addModelName("grass_path");
        addModelName("farmland");
        addModelName("campfire");
        addModelName("soul_campfire");
        addModelName("crossbow");
        addModelName("shield");
        addModelName("shield_blocking");
        addModelName("elytra");
        addModelName("elytra_broken");
        addModelName("totem_of_undying");
        addModelName("end_crystal");
        addModelName("crossbow_pulling_0");
        addModelName("crossbow_pulling_1");
        addModelName("crossbow_pulling_2");
        addModelName("crossbow_arrow");
        addModelName("crossbow_firework");
        addModelName("trident_in_hand");
        addModelName("trident_throwing");
        addModelName("spyglass_in_hand");

        Collections.sort(MODEL_NAMES);
    }

    private static void addModelName(String model) {
        if (model == null || model.isEmpty()) {
            return;
        }

        if (!hasModelResource(model)) {
            return;
        }

        if (!MODEL_NAMES.contains(model)) {
            MODEL_NAMES.add(model);
        }
    }

    private static boolean hasModelResource(String model) {
        try (InputStream stream = getResourceStream(ResourceLocation.of("minecraft", "models/item/" + model + ".json"))) {
            return stream != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static InputStream getResourceStream(ResourceLocation location) {
        String path = "assets/" + location.getResourceDomain() + "/" + location.getResourcePath();
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        InputStream stream = contextClassLoader == null ? null : contextClassLoader.getResourceAsStream(path);
        return stream == null ? ViaBackwardsItemModels.class.getClassLoader().getResourceAsStream(path) : stream;
    }

    private static boolean isBrokenElytra(ItemStack stack, String model) {
        return "elytra".equals(model) && stack != null && stack.isItemStackDamageable() && stack.getItemDamage() >= stack.getMaxDamage() - 1;
    }

    private static void registerMappingSet(String mappingsVersion, String identifierVersion) {
        try {
            CompoundTag mappings = readMappings("assets/viabackwards/data/mappings-" + mappingsVersion + ".nbt");
            CompoundTag itemNames = mappings.getCompoundTag("itemnames");
            if (itemNames == null || itemNames.isEmpty()) {
                return;
            }

            CompoundTag itemData = mappings.getCompoundTag("itemdata");
            List<String> identifiers = readIdentifiers("assets/viaversion/data/identifiers-" + identifierVersion + ".nbt");
            if (identifiers == null || identifiers.isEmpty()) {
                return;
            }

            for (Map.Entry<String, com.viaversion.nbt.tag.Tag> entry : itemNames.entrySet()) {
                int itemId = Integer.parseInt(entry.getKey());
                if (itemId < 0 || itemId >= identifiers.size()) {
                    continue;
                }

                String displayName = entry.getValue() instanceof StringTag stringTag ? stringTag.getValue() : null;
                String identifier = identifiers.get(itemId);
                String model = normalizeModelName(identifier);
                if (model == null || displayName == null) {
                    continue;
                }

                if (!hasModelResource(model)) {
                    continue;
                }

                addModelName(model);
                MODELS_BY_DISPLAY_NAME.putIfAbsent(normalizeDisplayName(displayName), model);
                MODELS_BY_BACKUP_TAG.putIfAbsent(makeBackupKey(mappingsVersion, itemId), model);

                if (itemData != null) {
                    CompoundTag data = itemData.getCompoundTag(entry.getKey());
                    NumberTag customModelData = data == null ? null : data.getNumberTag("custom_model_data");
                    if (customModelData != null) {
                        MODELS_BY_CUSTOM_MODEL_DATA.putIfAbsent(customModelData.asInt(), model);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String normalizeModelName(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return null;
        }

        return identifier.startsWith("minecraft:") ? identifier.substring("minecraft:".length()) : identifier;
    }

    private static String getMappedModel(ItemStack stack) {
        if (stack == null) {
            return null;
        }

        String displayModel = getDisplayKnownModel(stack);
        if (displayModel != null) {
            return displayModel;
        }

        Integer customModelData = getCustomModelData(stack);
        if (customModelData != null) {
            String model = MODELS_BY_CUSTOM_MODEL_DATA.get(customModelData);
            if (model != null) {
                return model;
            }
        }

        String backupModel = getBackupModel(stack);
        if (backupModel != null) {
            return backupModel;
        }

        String stackDisplayModel = getKnownModernModelFromDisplayName(stack.getDisplayName());
        if (stackDisplayModel != null) {
            return stackDisplayModel;
        }

        if (!stack.hasTagCompound()) {
            return null;
        }

        NBTTagCompound display = stack.getTagCompound().getCompoundTag("display");
        if (display == null || !display.hasKey("Name", 8)) {
            return null;
        }

        String displayName = display.getString("Name");
        String rawDisplayModel = getKnownModernModelFromDisplayName(displayName);
        if (rawDisplayModel != null) {
            return rawDisplayModel;
        }

        String normalized = normalizeDisplayName(displayName);
        String direct = getKnownModernModel(normalized);
        return direct != null ? direct : MODELS_BY_DISPLAY_NAME.get(normalized);
    }

    private static String getKnownModernModel(String normalized) {
        if (normalized == null) {
            return null;
        }
        if (normalized.equals("elytra") || normalized.endsWith("_elytra")) {
            return "elytra";
        }
        if (normalized.equals("totem_of_undying") || normalized.endsWith("_totem_of_undying")) {
            return "totem_of_undying";
        }
        if (normalized.equals("shield") || normalized.endsWith("_shield")) {
            return "shield";
        }
        if (normalized.equals("crossbow") || normalized.endsWith("_crossbow")) {
            return "crossbow";
        }
        if (normalized.equals("respawn_anchor") || normalized.endsWith("_respawn_anchor")) {
            return "respawn_anchor";
        }
        if (normalized.equals("dirt_path") || normalized.endsWith("_dirt_path")) {
            return "dirt_path";
        }
        if (normalized.equals("grass_path") || normalized.endsWith("_grass_path")) {
            return "grass_path";
        }
        if (normalized.equals("farmland") || normalized.endsWith("_farmland")) {
            return "farmland";
        }
        if (normalized.equals("soul_campfire") || normalized.endsWith("_soul_campfire")) {
            return "soul_campfire";
        }
        if (normalized.equals("campfire") || normalized.endsWith("_campfire")) {
            return "campfire";
        }
        if (normalized.equals("end_crystal") || normalized.endsWith("_end_crystal")) {
            return "end_crystal";
        }
        return null;
    }

    private static String getBackupModel(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }

        NBTTagCompound tag = stack.getTagCompound();
        for (String key : tag.getKeySet()) {
            if (!key.startsWith("VB|") || !key.endsWith("|id") || !tag.hasKey(key, 99)) {
                continue;
            }

            String model = MODELS_BY_BACKUP_TAG.get(key + ":" + tag.getInteger(key));
            if (model != null) {
                return model;
            }
        }

        return null;
    }

    private static Integer getCustomModelData(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }

        NBTTagCompound tag = stack.getTagCompound();
        Integer value = getNumber(tag, "CustomModelData");
        if (value != null) {
            return value;
        }

        value = getNumber(tag, "custom_model_data");
        if (value != null) {
            return value;
        }

        value = getNumber(tag, "minecraft:custom_model_data");
        if (value != null) {
            return value;
        }

        NBTTagCompound display = tag.getCompoundTag("display");
        if (display != null && display.hasKey("Name", 8)) {
            value = getNumber(display, "CustomModelData");
            if (value != null) {
                return value;
            }
        }

        NBTTagCompound components = tag.getCompoundTag("components");
        value = getNumber(components, "minecraft:custom_model_data");
        if (value != null) {
            return value;
        }

        return getCustomModelDataFromCompound(components.getCompoundTag("minecraft:custom_model_data"));
    }

    private static Integer getNumber(NBTTagCompound tag, String key) {
        return tag != null && tag.hasKey(key, 99) ? tag.getInteger(key) : null;
    }

    private static Integer getCustomModelDataFromCompound(NBTTagCompound tag) {
        if (tag == null) {
            return null;
        }

        NBTTagList floats = tag.getTagList("floats", 5);
        if (floats.tagCount() > 0) {
            return (int) floats.getFloatAt(0);
        }

        return getNumber(tag, "value");
    }

    private static String getDisplayKnownModel(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }

        NBTTagCompound display = stack.getTagCompound().getCompoundTag("display");
        if (display == null || !display.hasKey("Name", 8)) {
            return null;
        }

        String displayName = display.getString("Name");
        String raw = getKnownModernModelFromDisplayName(displayName);
        return raw != null ? raw : getKnownModernModel(normalizeDisplayName(displayName));
    }

    private static String getKnownModernModelFromDisplayName(String name) {
        String text = extractText(name);
        if (text == null) {
            return null;
        }

        String lowered = text.replaceAll("\\u00A7.", "").replaceAll("搂.", "").toLowerCase();
        if (lowered.contains("dirt_path") || lowered.contains("dirt path")
                || lowered.contains("minecraft:dirt_path") || lowered.contains("block.minecraft.dirt_path")
                || text.contains("土径") || text.contains("泥土小径")) {
            return "dirt_path";
        }
        if (lowered.contains("grass_path") || lowered.contains("grass path")
                || lowered.contains("minecraft:grass_path") || lowered.contains("block.minecraft.grass_path")
                || text.contains("草径") || text.contains("草径方块")) {
            return "grass_path";
        }

        if (lowered.contains("farmland")
                || lowered.contains("minecraft:farmland") || lowered.contains("block.minecraft.farmland")
                || text.contains("耕地")) {
            return "farmland";
        }
        if (lowered.contains("soul_campfire") || lowered.contains("soul campfire")
                || lowered.contains("minecraft:soul_campfire") || lowered.contains("block.minecraft.soul_campfire")
                || text.contains("灵魂营火") || text.contains("灵魂篝火")) {
            return "soul_campfire";
        }
        if (lowered.contains("campfire") || lowered.contains("camp fire")
                || lowered.contains("minecraft:campfire") || lowered.contains("block.minecraft.campfire")
                || text.contains("营火") || text.contains("篝火")) {
            return "campfire";
        }

        return null;
    }

    private static CompoundTag readMappings(String resource) throws Exception {
        try (InputStream inputStream = ClassLoader.getSystemResourceAsStream(resource)) {
            return NBTIO.readTag(new DataInputStream(inputStream), TagLimiter.noop(), true, CompoundTag.class);
        }
    }

    private static String makeBackupKey(String mappingsVersion, int itemId) {
        return "VB|Protocol" + mappingsVersion.replace(".", "_").replace("to", "To") + "|id:" + itemId;
    }

    private static List<String> readIdentifiers(String resource) throws Exception {
        CompoundTag tag = readMappings(resource);
        Mappings mappings = MappingDataLoader.INSTANCE.loadMappings(tag, "items");
        if (mappings == null) {
            return null;
        }

        CompoundTag table = readMappings("assets/viaversion/data/identifier-table.nbt");
        ListTag<StringTag> globalItems = table.getListTag("items", StringTag.class);
        List<String> identifiers = Lists.newArrayList();

        for (int i = 0; i < mappings.size(); i++) {
            int globalId = mappings.getNewId(i);
            identifiers.add(globalId >= 0 && globalId < globalItems.size() ? globalItems.get(globalId).getValue() : null);
        }

        return identifiers;
    }

    private static String normalizeDisplayName(String name) {
        if (name == null) {
            return null;
        }

        String cleaned = extractText(name);
        cleaned = cleaned.replaceAll("\\u00A7.", "");
        cleaned = cleaned.replaceAll("§.", "");
        cleaned = cleaned.replace('"', ' ').trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        cleaned = cleaned.replaceAll("^[0-9]+\\.[0-9]+\\s+", "");
        cleaned = cleaned.toLowerCase();
        cleaned = cleaned.replaceAll("[^a-z0-9]+", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        return cleaned;
    }

    private static String extractText(String name) {
        String trimmed = name.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return name;
        }

        try {
            JsonElement element = new JsonParser().parse(trimmed);
            StringBuilder builder = new StringBuilder();
            appendText(element, builder);
            return builder.length() == 0 ? name : builder.toString();
        } catch (Throwable ignored) {
            return name;
        }
    }

    private static void appendText(JsonElement element, StringBuilder builder) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonPrimitive()) {
            builder.append(element.getAsString());
            return;
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                appendText(child, builder);
            }
            return;
        }

        JsonObject object = element.getAsJsonObject();
        if (object.has("text")) {
            builder.append(object.get("text").getAsString());
        } else if (object.has("translate")) {
            builder.append(object.get("translate").getAsString());
        }

        if (object.has("extra")) {
            appendText(object.get("extra"), builder);
        }
    }
}
