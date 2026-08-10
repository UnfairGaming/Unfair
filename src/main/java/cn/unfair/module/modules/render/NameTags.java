package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.events.Render3DEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.TeamUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NameTags extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float AUTO_SCALE_THRESHOLD = 5.0F;
    private static final int ITEM_SPACING = 14;
    private static final int ENCHANT_LINE_HEIGHT = 8;
    private static final int ENCHANT_Y_OFFSET = 24;
    private static final Comparator<NametagRenderState> FAR_TO_NEAR = (a, b) -> Double.compare(b.distanceSq, a.distanceSq);

    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.1F, 2.0F);
    public final ModeProperty font = new ModeProperty("font", 0, new String[]{"MINECRAFT", "INTER", "TAHOMA", "COMFORTAA"});
    public final BooleanProperty autoScale = new BooleanProperty("auto-scale", false);
    public final BooleanProperty background = new BooleanProperty("background", true);
    public final BooleanProperty onlyRenderName = new BooleanProperty("only-render-name", false);
    public final PercentProperty backgroundOpacity = new PercentProperty("background-opacity", 50);
    public final BooleanProperty backgroundBorder = new BooleanProperty("background-border", false);
    public final ModeProperty healthMode = new ModeProperty("health", 0, new String[]{"NONE", "HEARTS", "HEALTH", "TAB"});
    public final BooleanProperty heartSymbol = new BooleanProperty("heart-symbol", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", false);
    public final ModeProperty distanceMode = new ModeProperty("distance", 0, new String[]{"NONE", "DEFAULT", "VAPE"});
    public final BooleanProperty invisibles = new BooleanProperty("invisibles", true);
    public final BooleanProperty armor = new BooleanProperty("armor", false);
    public final BooleanProperty enchantments = new BooleanProperty("enchantments", false);
    public final BooleanProperty durability = new BooleanProperty("durability", false);
    public final BooleanProperty self = new BooleanProperty("self", false);
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty friends = new BooleanProperty("friends", true);
    public final BooleanProperty enemies = new BooleanProperty("enemies", true);
    public final BooleanProperty bosses = new BooleanProperty("bosses", false);
    public final BooleanProperty mobs = new BooleanProperty("mobs", false);
    public final BooleanProperty creepers = new BooleanProperty("creepers", false);
    public final BooleanProperty endermen = new BooleanProperty("endermen", false);
    public final BooleanProperty blazes = new BooleanProperty("blazes", false);
    public final BooleanProperty animals = new BooleanProperty("animals", false);
    public final BooleanProperty bots = new BooleanProperty("bots", false);
    public final ColorProperty friendColor = new ColorProperty("friend-color", 0x55FFFF);
    public final ColorProperty enemyColor = new ColorProperty("enemy-color", 0xFF5555);

    private final List<NametagRenderState> renderStates = new ArrayList<>();
    private int renderStateCount;

    private static class NametagRenderState {
        private Entity entity;
        private String displayName;
        private int stringHalfWidth;
        private int teamColor;
        private int relationshipColor;
        private int playerNameStart;
        private int playerNameEnd;
        private double distanceSq;
        private float baseScale;
        private float yOffset;
        private ItemStack heldItem;
        private ItemStack boots;
        private ItemStack leggings;
        private ItemStack chestplate;
        private ItemStack helmet;
        private int totalItems;

        private void set(Entity entity, String displayName, int stringHalfWidth, int teamColor, int relationshipColor,
                         int playerNameStart, int playerNameEnd, double distanceSq, float baseScale, float yOffset,
                         ItemStack heldItem, ItemStack boots, ItemStack leggings, ItemStack chestplate, ItemStack helmet,
                         int totalItems) {
            this.entity = entity;
            this.displayName = displayName;
            this.stringHalfWidth = stringHalfWidth;
            this.teamColor = teamColor;
            this.relationshipColor = relationshipColor;
            this.playerNameStart = playerNameStart;
            this.playerNameEnd = playerNameEnd;
            this.distanceSq = distanceSq;
            this.baseScale = baseScale;
            this.yOffset = yOffset;
            this.heldItem = heldItem;
            this.boots = boots;
            this.leggings = leggings;
            this.chestplate = chestplate;
            this.helmet = helmet;
            this.totalItems = totalItems;
        }
    }

    public NameTags() {
        super("NameTags", false, true);
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled() || mc.theWorld == null || mc.thePlayer == null || mc.getRenderViewEntity() == null) {
            renderStateCount = 0;
            return;
        }

        updateRenderStates();
        renderNametags(event.partialTicks());
    }

    public boolean shouldRenderTags(Entity entity) {
        if (entity == null || entity.isDead || (entity instanceof EntityLivingBase living && living.deathTime > 0)) {
            return false;
        }
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null || viewer.getDistanceToEntity(entity) > 512.0F) {
            return false;
        }
        if (!invisibles.getValue() && entity.isInvisible()) {
            return false;
        }
        if (entity instanceof EntityPlayer player) {
            if (player == mc.thePlayer || player == viewer) {
                return self.getValue() && mc.gameSettings.thirdPersonView != 0;
            }
            if (TeamUtil.shouldBlockRenderTarget(player)) {
                return false;
            }
            if (!TeamUtil.isAntiBotEnabled() && TeamUtil.isBot(player)) {
                return bots.getValue();
            }
            if (TeamUtil.isFriend(player)) {
                return friends.getValue();
            }
            return TeamUtil.isTarget(player) ? enemies.getValue() : players.getValue();
        }
        if (entity instanceof EntityDragon || entity instanceof EntityWither) {
            return !entity.isInvisible() && bosses.getValue();
        }
        if (!(entity instanceof EntityMob) && !(entity instanceof EntitySlime)) {
            return (entity instanceof EntityAnimal
                    || entity instanceof EntityBat
                    || entity instanceof EntitySquid
                    || entity instanceof EntityVillager) && animals.getValue();
        }
        if (entity instanceof EntityCreeper) {
            return creepers.getValue();
        }
        if (entity instanceof EntityEnderman) {
            return endermen.getValue();
        }
        return entity instanceof EntityBlaze ? blazes.getValue() : mobs.getValue();
    }

    private void updateRenderStates() {
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            renderStateCount = 0;
            return;
        }

        renderStateCount = 0;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!shouldRenderTags(entity)) {
                continue;
            }
            if (!entity.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 10.0)) {
                continue;
            }

            String teamName = getDisplayName(entity);
            if (StringUtils.isBlank(EnumChatFormatting.getTextWithoutFormattingCodes(teamName))) {
                continue;
            }

            double dx = entity.posX - viewer.posX;
            double dy = entity.posY - viewer.posY;
            double dz = entity.posZ - viewer.posZ;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            float distance = (float) Math.sqrt(distanceSq);
            String displayName = buildDisplayName(entity, teamName, distance);
            int stringHalfWidth = getStringWidth(displayName) / 2;
            int relationshipColor = entity instanceof EntityPlayer ? resolveRelationshipColor((EntityPlayer) entity) : -1;
            int[] playerNameRange = entity instanceof EntityPlayer
                    ? findVisiblePlayerNameRange(displayName, ((EntityPlayer) entity).getName())
                    : new int[]{-1, -1};

            ItemStack heldItem = null;
            ItemStack boots = null;
            ItemStack leggings = null;
            ItemStack chestplate = null;
            ItemStack helmet = null;
            int totalItems = 0;
            if (armor.getValue() && entity instanceof EntityPlayer player) {
                heldItem = player.getEquipmentInSlot(0);
                if (heldItem != null) totalItems++;
                boots = player.getEquipmentInSlot(1);
                if (boots != null) totalItems++;
                leggings = player.getEquipmentInSlot(2);
                if (leggings != null) totalItems++;
                chestplate = player.getEquipmentInSlot(3);
                if (chestplate != null) totalItems++;
                helmet = player.getEquipmentInSlot(4);
                if (helmet != null) totalItems++;
            }

            if (renderStateCount >= renderStates.size()) {
                renderStates.add(new NametagRenderState());
            }
            renderStates.get(renderStateCount++).set(
                    entity,
                    displayName,
                    stringHalfWidth,
                    getColorFromEntity(entity),
                    relationshipColor,
                    playerNameRange[0],
                    playerNameRange[1],
                    distanceSq,
                    computeBaseScaleValue(),
                    (entity.isSneaking() ? (entity.height - 0.3F) : entity.height) + 0.3F,
                    heldItem,
                    boots,
                    leggings,
                    chestplate,
                    helmet,
                    totalItems
            );
        }

        if (renderStateCount > 1) {
            renderStates.subList(0, renderStateCount).sort(FAR_TO_NEAR);
        }
    }

    private void renderNametags(float partialTicks) {
        RenderManager renderManager = mc.getRenderManager();
        if (renderManager == null || renderStateCount == 0) {
            return;
        }

        RenderUtil.GlRenderState previousGlState = RenderUtil.captureGlState();
        for (int i = 0; i < renderStateCount; i++) {
            renderCustomName(renderStates.get(i), partialTicks, renderManager);
        }
        previousGlState.restore();

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderCustomName(NametagRenderState state, float partialTicks, RenderManager renderManager) {
        Entity entity = state.entity;
        if (entity == null || entity.isDead || (entity instanceof EntityLivingBase living && living.deathTime > 0)) {
            return;
        }

        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - renderManager.viewerPosX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - renderManager.viewerPosY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - renderManager.viewerPosZ;
        float renderScale = state.baseScale;
        if (autoScale.getValue()) {
            renderScale = computeScaleValue((float) Math.sqrt(x * x + y * y + z * z), true);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + state.yOffset, (float) z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-renderScale, -renderScale, renderScale);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.translate(0.0F, -10.0F, 0.0F);

        if ((background.getValue() && backgroundOpacity.getValue() > 1) || backgroundBorder.getValue() || state.relationshipColor != -1) {
            renderBackground(state.stringHalfWidth, 0.0F, state.teamColor, state.relationshipColor);
            applyNametagTextState();
        }

        drawDisplayName(state);
        applyNametagTextState();

        if (state.totalItems > 0) {
            int iconX = -(state.totalItems * ITEM_SPACING) / 2;
            int iconY = -20;
            if (state.heldItem != null) {
                renderItemStack(state.heldItem, iconX, iconY);
                iconX += ITEM_SPACING;
            }
            if (state.helmet != null) {
                renderItemStack(state.helmet, iconX, iconY);
                iconX += ITEM_SPACING;
            }
            if (state.chestplate != null) {
                renderItemStack(state.chestplate, iconX, iconY);
                iconX += ITEM_SPACING;
            }
            if (state.leggings != null) {
                renderItemStack(state.leggings, iconX, iconY);
                iconX += ITEM_SPACING;
            }
            if (state.boots != null) {
                renderItemStack(state.boots, iconX, iconY);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void applyNametagTextState() {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawDisplayName(NametagRenderState state) {
        if (state.relationshipColor == -1 || state.playerNameStart < 0 || state.playerNameEnd <= state.playerNameStart) {
            drawString(state.displayName, -state.stringHalfWidth, 0.0F, 0xFFFFFFFF, shadow.getValue());
            return;
        }

        drawFormattedGlyphString(state.displayName, -state.stringHalfWidth, 0.0F, state.playerNameStart, state.playerNameEnd, state.relationshipColor);
    }

    private void renderBackground(int stringWidth, float textY, int teamColor, int relationshipColor) {
        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        float alpha = backgroundOpacity.getValue() / 100.0F;
        float innerLeft = -stringWidth - 3.0F;
        float innerRight = stringWidth + 3.0F;
        float innerTop = textY - 3.0F;
        float innerBottom = textY + getTextHeight() + 2.0F;

        if (background.getValue() && alpha > 0.01F) {
            worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            worldRenderer.pos(innerLeft, innerTop, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
            worldRenderer.pos(innerLeft, innerBottom, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
            worldRenderer.pos(innerRight, innerBottom, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
            worldRenderer.pos(innerRight, innerTop, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
            tessellator.draw();
        }

        int borderColor = relationshipColor != -1 ? relationshipColor : teamColor;
        if (backgroundBorder.getValue() || relationshipColor != -1) {
            float red = borderColor != -1 ? ((borderColor >> 16) & 255) / 255.0F : 0.6F;
            float green = borderColor != -1 ? ((borderColor >> 8) & 255) / 255.0F : 0.6F;
            float blue = borderColor != -1 ? (borderColor & 255) / 255.0F : 0.6F;
            float borderAlpha = relationshipColor != -1 ? alpha : 1.0F;
            float left = innerLeft - 1.0F;
            float right = innerRight + 1.0F;
            float top = innerTop - 1.0F;
            float bottom = innerBottom + 1.0F;
            float borderZ = -0.001F;

            worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            worldRenderer.pos(left, top, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(left, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, top, borderZ).color(red, green, blue, borderAlpha).endVertex();

            worldRenderer.pos(left, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(left, bottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, bottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();

            worldRenderer.pos(left, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(left, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(innerLeft, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(innerLeft, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();

            worldRenderer.pos(innerRight, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(innerRight, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();
            tessellator.draw();
        }

        GlStateManager.enableTexture2D();
    }

    private String buildDisplayName(Entity entity, String teamName, float distance) {
        String name = teamName;
        if (onlyRenderName.getValue() && entity instanceof EntityPlayer player) {
            String color = getFirstColorCode(player.getDisplayName().getFormattedText());
            name = (color.length() == 2 ? color : "") + player.getName();
        }

        name = appendHealth(name, entity);
        switch (distanceMode.getValue()) {
            case 1 -> {
                int dist = (int) distance;
                String distColor = dist <= 8 ? "\u00a7c" : (dist <= 15 ? "\u00a76" : (dist <= 25 ? "\u00a7e" : "\u00a77"));
                name = distColor + dist + "m\u00a7r " + name;
            }
            case 2 -> name = "\u00a7a[\u00a7f" + (int) distance + "\u00a7a]\u00a7r " + name;
            default -> {
            }
        }
        return name;
    }

    private String appendHealth(String name, Entity entity) {
        if (!(entity instanceof net.minecraft.entity.EntityLivingBase living)) {
            return name;
        }
        int mode = healthMode.getValue();
        if (mode == 0) {
            return name;
        }
        if (mode == 3 && entity instanceof EntityPlayer) {
            Scoreboard scoreboard = mc.theWorld.getScoreboard();
            if (scoreboard != null) {
                ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2);
                if (objective != null) {
                    Score score = scoreboard.getValueFromObjective(entity.getName(), objective);
                    if (score != null) {
                        return name + " \u00a7e" + score.getScorePoints() + "\u00a7r";
                    }
                }
            }
            return name;
        }

        float health = Math.max(0.0F, living.getHealth());
        float maxHealth = living.getMaxHealth();
        if (maxHealth <= 0.0F) {
            maxHealth = 20.0F;
        }
        boolean heartsMode = mode == 1;
        double ratio = health / maxHealth;
        String color = ratio < 0.3 ? "\u00a7c" : (ratio < 0.5 ? "\u00a76" : (ratio < 0.7 ? "\u00a7e" : "\u00a7a"));
        float displayValue = heartsMode ? health / 2.0F : health;
        String suffix = heartsMode && heartSymbol.getValue() ? " \u2764" : "";
        name = name + " " + color + fastOneDecimal(displayValue) + suffix;

        float absorption = living.getAbsorptionAmount();
        if (absorption > 0) {
            float absDisplay = heartsMode ? absorption / 2.0F : absorption;
            String absSuffix = heartsMode && heartSymbol.getValue() ? " \u2764" : "";
            name = name + " \u00a76+" + fastOneDecimal(absDisplay) + absSuffix;
        }
        return name + "\u00a7r";
    }

    private void renderItemStack(ItemStack stack, int xPos, int yPos) {
        if (stack == null) {
            return;
        }

        RenderUtil.renderItemAndEffectIntoGui3D(stack, xPos, yPos);

        if (enchantments.getValue()) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5, 0.5, 0.5);
            GlStateManager.translate(0, -10, 0);
            renderEnchantText(stack, xPos, yPos);
            GlStateManager.popMatrix();
        }

        GlStateManager.disableDepth();
        if (stack.stackSize > 1) {
            String countStr = String.valueOf(stack.stackSize);
            mc.fontRendererObj.drawStringWithShadow(countStr, xPos + 17 - mc.fontRendererObj.getStringWidth(countStr), yPos + 9, 0xFFFFFF);
        }
        if (durability.getValue() && stack.isItemStackDamageable() && stack.getItemDamage() > 0) {
            int maxDamage = stack.getMaxDamage();
            int currentDamage = stack.getItemDamage();
            float durabilityRatio = 1.0F - (float) currentDamage / (float) maxDamage;
            RenderUtil.drawDurabilityBar(xPos, yPos, durabilityRatio);
        }
        GlStateManager.enableDepth();
    }

    private static final int[] ARMOR_ENCHANT_IDS = {0, 7, 34};
    private static final String[] ARMOR_ENCHANT_ABBR = {"P", "T", "U"};
    private static final int[] SWORD_ENCHANT_IDS = {16, 20, 19};
    private static final String[] SWORD_ENCHANT_ABBR = {"S", "F", "K"};
    private static final int[] BOW_ENCHANT_IDS = {48, 49, 50};
    private static final String[] BOW_ENCHANT_ABBR = {"Pw", "Pu", "Fl"};
    private static final int[] TOOL_ENCHANT_IDS = {32, 35, 34};
    private static final String[] TOOL_ENCHANT_ABBR = {"E", "Fo", "U"};
    private static final int[] MISC_ENCHANT_IDS = {19};
    private static final String[] MISC_ENCHANT_ABBR = {"K"};

    private void renderEnchantText(ItemStack stack, int xPos, int yPos) {
        int[] ids;
        String[] abbreviations;
        Item item = stack.getItem();
        if (item instanceof ItemArmor) {
            ids = ARMOR_ENCHANT_IDS;
            abbreviations = ARMOR_ENCHANT_ABBR;
        } else if (item instanceof ItemSword) {
            ids = SWORD_ENCHANT_IDS;
            abbreviations = SWORD_ENCHANT_ABBR;
        } else if (item instanceof ItemBow) {
            ids = BOW_ENCHANT_IDS;
            abbreviations = BOW_ENCHANT_ABBR;
        } else if (item instanceof ItemTool) {
            ids = TOOL_ENCHANT_IDS;
            abbreviations = TOOL_ENCHANT_ABBR;
        } else {
            ids = MISC_ENCHANT_IDS;
            abbreviations = MISC_ENCHANT_ABBR;
        }

        int drawX = xPos * 2;
        int drawY = yPos - ENCHANT_Y_OFFSET;
        for (int i = 0; i < ids.length; i++) {
            int level = EnchantmentHelper.getEnchantmentLevel(ids[i], stack);
            if (level <= 0) {
                continue;
            }
            drawEnchantLine(abbreviations[i], level, drawX, drawY);
            drawY += ENCHANT_LINE_HEIGHT;
        }
    }

    private void drawEnchantLine(String abbreviation, int level, int x, int y) {
        mc.fontRendererObj.drawStringWithShadow(abbreviation, x, y, 0xFFFFFF);
        int advance = mc.fontRendererObj.getStringWidth(abbreviation);
        mc.fontRendererObj.drawStringWithShadow(String.valueOf(level), x + advance, y, colorForEnchantLevel(level));
    }

    private int colorForEnchantLevel(int level) {
        if (level <= 5) {
            if (level == 1) return 0xFFFFFF;
            if (level == 2) return 0x55FFFF;
            if (level == 3) return 0x00AAAA;
            if (level == 4) return 0xAA00AA;
            if (level == 5) return 0xFFAA00;
        }
        return 0xFF55FF;
    }

    private float computeBaseScaleValue() {
        return scale.getValue() * 0.02F;
    }

    private float computeScaleValue(float distance, boolean scaleByDistance) {
        float scaleValue = computeBaseScaleValue();
        if (!scaleByDistance) {
            return scaleValue;
        }
        float effectiveDistance = Math.max(1.0F, distance);
        float scaledValue = scaleValue * (effectiveDistance / AUTO_SCALE_THRESHOLD);
        return Math.max(scaleValue, scaledValue);
    }

    private int resolveRelationshipColor(EntityPlayer entity) {
        if (TeamUtil.isFriend(entity)) {
            return (Unfair.friendManager != null ? Unfair.friendManager.getColor().getRGB() : friendColor.getValue()) & 0xFFFFFF;
        }
        if (TeamUtil.isTarget(entity)) {
            return (Unfair.targetManager != null ? Unfair.targetManager.getColor().getRGB() : enemyColor.getValue()) & 0xFFFFFF;
        }
        return -1;
    }

    private int getColorFromEntity(Entity entity) {
        if (entity instanceof EntityPlayer player) {
            ScorePlayerTeam team = (ScorePlayerTeam) player.getTeam();
            if (team != null) {
                String prefix = net.minecraft.client.gui.FontRenderer.getFormatFromString(team.getColorPrefix());
                if (prefix.length() >= 2) {
                    return mc.fontRendererObj.getColorCode(prefix.charAt(1));
                }
            }
        }
        return -1;
    }

    private String getDisplayName(Entity entity) {
        return entity.getDisplayName().getFormattedText().replaceAll("\u00a7\\S$", "").replaceAll("(?i)\u00a7r", "\u00a7f").trim();
    }

    private String getFirstColorCode(String text) {
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '\u00a7' && "0123456789abcdefABCDEF".indexOf(text.charAt(i + 1)) >= 0) {
                return "\u00a7" + Character.toLowerCase(text.charAt(i + 1));
            }
        }
        return "";
    }

    private int[] findVisiblePlayerNameRange(String formattedText, String playerName) {
        String strippedText = stripFormattingCodes(formattedText);
        int nameStart = strippedText.indexOf(playerName);
        if (nameStart < 0) {
            return new int[]{-1, -1};
        }
        return new int[]{nameStart, nameStart + playerName.length()};
    }

    private String stripFormattingCodes(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            builder.append(character);
        }
        return builder.toString();
    }

    private String fastOneDecimal(float value) {
        int whole = (int) value;
        if (value == whole) {
            return String.valueOf(whole);
        }
        int tenths = Math.round(value * 10.0F);
        int intPart = tenths / 10;
        int fracPart = Math.abs(tenths % 10);
        return intPart + "." + fracPart;
    }

    private int getStringWidth(String text) {
        if (font.getValue() == 0) {
            return mc.fontRendererObj.getStringWidth(text);
        }
        return getFontRenderer().getStringWidth(text);
    }

    private int getTextHeight() {
        return font.getValue() == 0 ? mc.fontRendererObj.FONT_HEIGHT : getFontRenderer().getHeight();
    }

    private void drawString(String text, float x, float y, int color, boolean shadow) {
        if (font.getValue() == 0) {
            mc.fontRendererObj.drawString(text, x, y, color, shadow);
        } else if (shadow) {
            getFontRenderer().drawStringWithShadow(text, x, y, color);
        } else {
            getFontRenderer().drawString(text, x, y, color);
        }
    }

    private FontRenderer getFontRenderer() {
        return switch (font.getValue()) {
            case 1 -> Fonts.interRegular.get(18.0F);
            case 2 -> Fonts.tahoma.get(18.0F);
            case 3 -> Fonts.comfortaa.get(18.0F);
            default -> Fonts.interRegular.get(18.0F);
        };
    }

    private void drawFormattedGlyphString(String text, float x, float y, int overrideStart, int overrideEnd, int overrideColor) {
        int visibleIndex = 0;
        int activeColor = 0xFFFFFFFF;
        float drawX = x;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                int colorIndex = "0123456789abcdef".indexOf(code);
                activeColor = colorIndex >= 0 ? 0xFF000000 | mc.fontRendererObj.getColorCode(code) : 0xFFFFFFFF;
                continue;
            }

            String glyph = String.valueOf(character);
            int color = visibleIndex >= overrideStart && visibleIndex < overrideEnd ? 0xFF000000 | overrideColor : activeColor;
            drawString(glyph, drawX, y, color, shadow.getValue());
            drawX += getStringWidth(glyph);
            visibleIndex++;
        }
    }
}
