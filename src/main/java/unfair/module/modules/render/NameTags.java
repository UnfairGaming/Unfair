package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL11;
import unfair.Unfair;
import unfair.enums.ChatColors;
import unfair.event.EventTarget;
import unfair.events.Render3DEvent;
import unfair.mixin.IAccessorEntityRenderer;
import unfair.mixin.IAccessorRenderManager;
import unfair.module.Module;
import unfair.property.properties.*;
import unfair.util.ColorUtil;
import unfair.util.RenderUtil;
import unfair.util.TeamUtil;
import unfair.util.font.FontManager;
import unfair.util.font.FontRenderer;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class NameTags extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat healthFormatter = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private static final float RAVEN_AUTO_SCALE_THRESHOLD = 5.0F;
    private static final int ITEM_SPACING = 14;
    private static final Comparator<RavenState> FAR_TO_NEAR = (a, b) -> Double.compare(b.distanceSq, a.distanceSq);
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
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Default", "Raven"});
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F, () -> this.mode.getValue() == 0);
    public final BooleanProperty autoScale = new BooleanProperty("auto-scale", true, () -> this.mode.getValue() == 0);
    public final PercentProperty backgroundOpacity = new PercentProperty("background", 25, () -> this.mode.getValue() == 0);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true, () -> this.mode.getValue() == 0);
    public final ModeProperty distanceMode = new ModeProperty("distance", 0, new String[]{"NONE", "DEFAULT", "VAPE"}, () -> this.mode.getValue() == 0);
    public final ModeProperty healthMode = new ModeProperty("health", 2, new String[]{"NONE", "HP", "HEARTS", "TAB"}, () -> this.mode.getValue() == 0);
    public final BooleanProperty armor = new BooleanProperty("armor", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty effects = new BooleanProperty("effects", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty players = new BooleanProperty("players", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty friends = new BooleanProperty("friends", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty enemies = new BooleanProperty("enemies", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty bossees = new BooleanProperty("bosses", false, () -> this.mode.getValue() == 0);
    public final BooleanProperty mobs = new BooleanProperty("mobs", false, () -> this.mode.getValue() == 0);
    public final BooleanProperty creepers = new BooleanProperty("creepers", false, () -> this.mode.getValue() == 0);
    public final BooleanProperty endermans = new BooleanProperty("endermen", false, () -> this.mode.getValue() == 0);
    public final BooleanProperty blazes = new BooleanProperty("blazes", false, () -> this.mode.getValue() == 0);
    public final BooleanProperty animals = new BooleanProperty("animals", false, () -> this.mode.getValue() == 0);
    public final BooleanProperty self = new BooleanProperty("self", false, () -> this.mode.getValue() == 0);
    public final BooleanProperty bots = new BooleanProperty("bots", false, () -> this.mode.getValue() == 0);
    // raven mode's settings
    public final FloatProperty ravenScale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F, () -> this.mode.getValue() == 1);
    public final ModeProperty ravenFont = new ModeProperty("font", 0, FontManager.getHudFontOptions(), () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenAutoScale = new BooleanProperty("auto-scale", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenBackground = new BooleanProperty("background", true, () -> this.mode.getValue() == 1);
    public final PercentProperty ravenBgOpacity = new PercentProperty("bg-opacity", 50, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenBgBorder = new BooleanProperty("bg-border", false, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenOnlyName = new BooleanProperty("only-name", false, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenShadow = new BooleanProperty("shadow", false, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenHealth = new BooleanProperty("health", false, () -> this.mode.getValue() == 1);
    public final ModeProperty ravenHealthDisplay = new ModeProperty("health-display", 0, new String[]{"Hearts", "HP"}, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenHeartSymbol = new BooleanProperty("heart-symbol", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenDistance = new BooleanProperty("distance", false, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenArmor = new BooleanProperty("armor", false, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenEnchants = new BooleanProperty("enchantments", false, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenDurability = new BooleanProperty("durability", false, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenShowInvis = new BooleanProperty("show-invis", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty ravenShowSelf = new BooleanProperty("show-self", false, () -> this.mode.getValue() == 1);
    public final ColorProperty ravenFriendColor = new ColorProperty("friend-color", 0x55FFFF, () -> this.mode.getValue() == 1);
    public final ColorProperty ravenEnemyColor = new ColorProperty("enemy-color", 0xFF5555, () -> this.mode.getValue() == 1);
    private final List<RavenState> ravenStates = new ArrayList<>();
    private int ravenStateCount = 0;

    public NameTags() {
        super("NameTags", false, true);
    }

    public boolean shouldRenderTags(EntityLivingBase entityLivingBase) {
        if (entityLivingBase.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityLivingBase) > 512.0F) {
            return false;
        } else if (entityLivingBase instanceof EntityPlayer) {
            if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.getRenderViewEntity()) {
                if (TeamUtil.isBot((EntityPlayer) entityLivingBase)) {
                    return this.bots.getValue();
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return this.friends.getValue();
                } else {
                    return TeamUtil.isTarget((EntityPlayer) entityLivingBase) ? this.enemies.getValue() : this.players.getValue();
                }
            } else {
                return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
            }
        } else if (entityLivingBase instanceof net.minecraft.entity.boss.EntityDragon || entityLivingBase instanceof net.minecraft.entity.boss.EntityWither) {
            return !entityLivingBase.isInvisible() && this.bossees.getValue();
        } else if (!(entityLivingBase instanceof net.minecraft.entity.monster.EntityMob) && !(entityLivingBase instanceof net.minecraft.entity.monster.EntitySlime)) {
            return (entityLivingBase instanceof net.minecraft.entity.passive.EntityAnimal
                    || entityLivingBase instanceof net.minecraft.entity.passive.EntityBat
                    || entityLivingBase instanceof net.minecraft.entity.passive.EntitySquid
                    || entityLivingBase instanceof net.minecraft.entity.passive.EntityVillager) && this.animals.getValue();
        } else if (entityLivingBase instanceof net.minecraft.entity.monster.EntityCreeper) {
            return this.creepers.getValue();
        } else if (entityLivingBase instanceof net.minecraft.entity.monster.EntityEnderman) {
            return this.endermans.getValue();
        } else {
            return entityLivingBase instanceof net.minecraft.entity.monster.EntityBlaze ? this.blazes.getValue() : this.mobs.getValue();
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        if (this.mode.getValue() == 1) {
            renderRaven(event);
        } else {
            renderDefault(event);
        }
    }

    private void renderDefault(Render3DEvent event) {
        for (Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
            if (entity instanceof EntityLivingBase
                    && this.shouldRenderTags((EntityLivingBase) entity)
                    && (entity.ignoreFrustumCheck || RenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 10.0))) {
                String teamName = TeamUtil.stripName(entity);
                if (!StringUtils.isBlank(EnumChatFormatting.getTextWithoutFormattingCodes(teamName))) {
                    double x = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, event.getPartialTicks())
                            - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
                    double y = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, event.getPartialTicks())
                            - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()
                            + (double) entity.getEyeHeight();
                    double z = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, event.getPartialTicks())
                            - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
                    double distance = mc.getRenderViewEntity().getDistanceToEntity(entity);
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(x, y + (entity.isSneaking() ? 0.225 : 0.4), z);
                    GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
                    float view = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
                    GlStateManager.rotate(mc.getRenderManager().playerViewX, view, 0.0F, 0.0F);
                    double scale = Math.pow(Math.min(Math.max(this.autoScale.getValue() ? distance : 0.0, 6.0), 128.0), 0.75) * 0.0075;
                    GlStateManager.scale(-scale * (double) this.scale.getValue(), -scale * (double) this.scale.getValue(), 1.0);
                    String distanceText = "";
                    switch (this.distanceMode.getValue()) {
                        case 1:
                            distanceText = String.format("&7%dm&r ", (int) distance);
                            break;
                        case 2:
                            distanceText = String.format("&a[&f%d&a]&r ", (int) distance);
                    }
                    float health = ((EntityLivingBase) entity).getHealth();
                    float absorption = ((EntityLivingBase) entity).getAbsorptionAmount();
                    float max = ((EntityLivingBase) entity).getMaxHealth();
                    float percent = Math.min(Math.max((health + absorption) / max, 0.0F), 1.0F);
                    String healText = "";
                    switch (this.healthMode.getValue()) {
                        case 1:
                            healText = String.format(" %d%s", (int) health, absorption > 0.0F ? String.format(" &6%d&r", (int) absorption) : "&r");
                            break;
                        case 2:
                            healText = String.format(
                                    " %s%s",
                                    healthFormatter.format((double) health / 2.0),
                                    absorption > 0.0F ? String.format(" &6%s&r", healthFormatter.format((double) absorption / 2.0)) : "&r"
                            );
                            break;
                        case 3:
                            if (entity instanceof EntityPlayer) {
                                Scoreboard scoreboard = mc.theWorld.getScoreboard();
                                if (scoreboard != null) {
                                    ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2);
                                    if (objective != null) {
                                        Score score = scoreboard.getValueFromObjective(entity.getName(), objective);
                                        if (score != null) {
                                            healText = String.format(" &e%d&r", score.getScorePoints());
                                        }
                                    }
                                }
                            }
                    }
                    String color = ChatColors.formatColor(String.format("%s&f%s&r%s", distanceText, teamName, healText));
                    int width = mc.fontRendererObj.getStringWidth(color);
                    if (this.backgroundOpacity.getValue() > 0) {
                        Color textColor = !entity.isSneaking() && !entity.isInvisible()
                                ? new Color(0.0F, 0.0F, 0.0F, (float) this.backgroundOpacity.getValue() / 100.0F)
                                : new Color(0.33F, 0.0F, 0.33F, (float) this.backgroundOpacity.getValue() / 100.0F);
                        RenderUtil.enableRenderState();
                        RenderUtil.drawRect(
                                (float) (-width) / 2.0F - 1.0F,
                                (float) (-mc.fontRendererObj.FONT_HEIGHT) - 1.0F,
                                (float) width / 2.0F + (this.shadow.getValue() ? 1.0F : 0.0F),
                                this.shadow.getValue() ? 0.0F : -1.0F,
                                textColor.getRGB()
                        );
                        RenderUtil.disableRenderState();
                    }
                    GlStateManager.disableDepth();
                    mc.fontRendererObj
                            .drawString(
                                    color,
                                    (float) (-width) / 2.0F,
                                    (float) (-mc.fontRendererObj.FONT_HEIGHT),
                                    ColorUtil.getHealthBlend(percent).getRGB(),
                                    this.shadow.getValue()
                            );
                    GlStateManager.enableDepth();
                    if (entity instanceof EntityPlayer) {
                        int height = mc.fontRendererObj.FONT_HEIGHT + 2;
                        if (this.armor.getValue()) {
                            ArrayList<ItemStack> renderingItems = new ArrayList<>();
                            for (int i = 4; i >= 0; i--) {
                                ItemStack itemStack;
                                if (i == 0) {
                                    itemStack = ((EntityPlayer) entity).getHeldItem();
                                } else {
                                    itemStack = ((EntityPlayer) entity).inventory.armorInventory[i - 1];
                                }
                                if (itemStack != null) {
                                    renderingItems.add(itemStack);
                                }
                            }
                            if (!renderingItems.isEmpty()) {
                                int offset = renderingItems.size() * -8;
                                for (int i = 0; i < renderingItems.size(); i++) {
                                    RenderUtil.renderItemInGUI(renderingItems.get(i), offset + i * 16, -height - 16);
                                }
                                height += 16;
                            }
                        }
                        if (this.effects.getValue()) {
                            List<PotionEffect> effects = ((EntityPlayer) entity)
                                    .getActivePotionEffects()
                                    .stream()
                                    .filter(potionEffect -> Potion.potionTypes[potionEffect.getPotionID()].hasStatusIcon())
                                    .collect(Collectors.toList());
                            if (!effects.isEmpty()) {
                                GlStateManager.pushMatrix();
                                GlStateManager.scale(0.5F, 0.5F, 1.0F);
                                int offset = effects.size() * -9;
                                for (int i = 0; i < effects.size(); i++) {
                                    RenderUtil.renderPotionEffect(effects.get(i), offset + i * 18, -(height * 2) - 18);
                                }
                                GlStateManager.popMatrix();
                            }
                        }
                        if (TeamUtil.isFriend((EntityPlayer) entity)) {
                            RenderUtil.enableRenderState();
                            float x1 = (float) (-width) / 2.0F - 1.0F;
                            float y1 = (float) (-mc.fontRendererObj.FONT_HEIGHT) - 1.0F;
                            float x2 = (float) width / 2.0F + 1.0F;
                            float off = this.shadow.getValue() ? 0.0F : -1.0F;
                            int friendColor = Unfair.friendManager.getColor().getRGB();
                            RenderUtil.drawOutlineRect(x1, y1, x2, off, 1.5F, 0, friendColor);
                            RenderUtil.disableRenderState();
                        } else if (TeamUtil.isTarget((EntityPlayer) entity)) {
                            RenderUtil.enableRenderState();
                            float x1 = (float) (-width) / 2.0F - 1.0F;
                            float y1 = (float) (-mc.fontRendererObj.FONT_HEIGHT) - 1.0F;
                            float x2 = (float) width / 2.0F + 1.0F;
                            float off = this.shadow.getValue() ? 0.0F : -1.0F;
                            int targetColor = Unfair.targetManager.getColor().getRGB();
                            RenderUtil.drawOutlineRect(x1, y1, x2, off, 1.5F, 0, targetColor);
                            RenderUtil.disableRenderState();
                        }
                    }
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    private boolean ravenShouldRender(EntityPlayer player) {
        if (player == null) return false;
        if (player == mc.thePlayer) {
            return ravenShowSelf.getValue() && mc.gameSettings.thirdPersonView != 0;
        }
        if (player.isDead || player.deathTime > 0) return false;
        if (!ravenShowInvis.getValue() && player.isInvisible()) return false;
        return !TeamUtil.isBot(player);
    }

    private int resolveRelationshipColor(EntityPlayer player) {
        if (TeamUtil.isFriend(player)) {
            return ravenFriendColor.getValue();
        }
        if (TeamUtil.isTarget(player)) {
            return ravenEnemyColor.getValue();
        }
        return -1;
    }

    private void renderRaven(Render3DEvent event) {

        updateRavenStates();

        if (ravenStateCount == 0) return;

        RenderManager rm = mc.getRenderManager();
        net.minecraft.client.gui.FontRenderer itemFR = mc.fontRendererObj;
        FontRenderer textFR = FontManager.getNametagRenderer(getRavenFontName());

        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);

        for (int i = 0; i < ravenStateCount; i++) {
            RavenState state = ravenStates.get(i);
            if (state.player == null) continue;
            if (!isInViewFrustum(state.player)) continue;
            renderRavenNametag(state, event.getPartialTicks(), rm, textFR, itemFR);
        }

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

    private void updateRavenStates() {
        FontRenderer fr = FontManager.getNametagRenderer(getRavenFontName());
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            ravenStateCount = 0;
            return;
        }

        float baseScale = ravenScale.getValue() * 0.02F;
        boolean showDist = ravenDistance.getValue();
        boolean showArmorItems = ravenArmor.getValue();
        ravenStateCount = 0;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!ravenShouldRender(player)) continue;

            double dx = player.posX - viewer.posX;
            double dy = player.posY - viewer.posY;
            double dz = player.posZ - viewer.posZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            float dist = (float) Math.sqrt(distSq);

            String displayName = buildRavenDisplayName(player, showDist, dist);
            int halfWidth = fr.getStringWidth(displayName) / 2;
            int relColor = resolveRelationshipColor(player);
            int[] nameRange = findPlayerNameRange(displayName, player.getName());

            ItemStack heldItem = null, boots = null, leggings = null, chestplate = null, helmet = null;
            int totalItems = 0;
            if (showArmorItems) {
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

            if (ravenStateCount >= ravenStates.size()) {
                ravenStates.add(new RavenState());
            }

            ravenStates.get(ravenStateCount++).set(
                    player, displayName, halfWidth,
                    getTeamColorInt(player), relColor,
                    nameRange[0], nameRange[1],
                    distSq, baseScale,
                    (player.isSneaking() ? (player.height - 0.3F) : player.height) + 0.3F,
                    heldItem, boots, leggings, chestplate, helmet,
                    totalItems
            );
        }

        if (ravenStateCount > 1) {
            ravenStates.subList(0, ravenStateCount).sort(FAR_TO_NEAR);
        }
    }

    private String buildRavenDisplayName(EntityPlayer entity, boolean showDist, float distance) {
        String name;
        if (ravenOnlyName.getValue()) {

            String formatted = getFirstColorCode(entity.getDisplayName().getFormattedText());
            String prefix = (formatted.length() >= 2 && formatted.charAt(0) == '§') ? formatted : "";
            name = prefix + entity.getName();
        } else {
            name = entity.getDisplayName().getFormattedText();
        }

        if (ravenHealth.getValue()) {
            name = appendRavenHealth(name, entity);
        }

        if (showDist) {
            int d = (int) distance;
            String c = d <= 8 ? "§c" : (d <= 15 ? "§6" : (d <= 25 ? "§e" : "§7"));
            name = c + d + "m§r " + name;
        }

        return name;
    }

    private String appendRavenHealth(String name, EntityPlayer entity) {
        float health = Math.max(0f, entity.getHealth());
        float maxHealth = entity.getMaxHealth();
        if (maxHealth <= 0f) maxHealth = 20f;

        boolean heartsMode = ravenHealthDisplay.getValue() == 0;
        double ratio = health / maxHealth;

        String color = ratio < 0.3 ? "§c" : (ratio < 0.5 ? "§6" : (ratio < 0.7 ? "§e" : "§a"));
        float displayValue = heartsMode ? health / 2.0f : health;
        String valStr = fastOneDecimal(displayValue);
        String heartSuffix = heartsMode && ravenHeartSymbol.getValue() ? " ❤" : "";
        name = name + " " + color + valStr + heartSuffix;

        float abs = entity.getAbsorptionAmount();
        if (abs > 0) {
            float absDisplay = heartsMode ? abs / 2.0f : abs;
            String absStr = fastOneDecimal(absDisplay);
            String absSfx = heartsMode && ravenHeartSymbol.getValue() ? " ❤" : "";
            name = name + " §6+" + absStr + absSfx;
        }
        return name + "§r";
    }

    private String fastOneDecimal(float value) {
        int whole = (int) value;
        if (value == whole) return String.valueOf(whole);
        int tenths = Math.round(value * 10.0F);
        return tenths / 10 + "." + Math.abs(tenths % 10);
    }

    private int getTeamColorInt(EntityPlayer player) {

        String teamName = player.getDisplayName().getFormattedText();
        for (int i = 0; i < teamName.length() - 1; i++) {
            if (teamName.charAt(i) == '§') {
                char code = teamName.charAt(i + 1);
                switch (code) {
                    case '0':
                        return 0x000000;
                    case '1':
                        return 0x0000AA;
                    case '2':
                        return 0x00AA00;
                    case '3':
                        return 0x00AAAA;
                    case '4':
                        return 0xAA0000;
                    case '5':
                        return 0xAA00AA;
                    case '6':
                        return 0xFFAA00;
                    case '7':
                        return 0xAAAAAA;
                    case '8':
                        return 0x555555;
                    case '9':
                        return 0x5555FF;
                    case 'a':
                        return 0x55FF55;
                    case 'b':
                        return 0x55FFFF;
                    case 'c':
                        return 0xFF5555;
                    case 'd':
                        return 0xFF55FF;
                    case 'e':
                        return 0xFFFF55;
                    case 'f':
                        return 0xFFFFFF;
                }
            }
        }
        return 0x666666;
    }

    private String getFirstColorCode(String input) {
        if (input == null || input.length() < 2) return "";
        for (int i = 0; i < input.length() - 1; i++) {
            if (input.charAt(i) == '§' && input.charAt(i + 1) != 'r') {
                return input.substring(i, i + 2);
            }
        }
        return "";
    }

    private int[] findPlayerNameRange(String formattedText, String playerName) {
        String stripped = stripFormat(formattedText);
        int start = stripped.indexOf(playerName);
        if (start < 0) return new int[]{-1, -1};
        return new int[]{start, start + playerName.length()};
    }

    private String stripFormat(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private boolean isInViewFrustum(Entity entity) {
        if (entity == null) return false;
        return RenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 10.0) || entity.ignoreFrustumCheck;
    }

    private String getRavenFontName() {
        String[] options = FontManager.getHudFontOptions();
        int idx = ravenFont.getValue();
        if (idx < 0 || idx >= options.length) idx = 0;
        return options[idx];
    }

    private void renderRavenNametag(RavenState state, float pt, RenderManager rm,
                                    FontRenderer textFR, net.minecraft.client.gui.FontRenderer itemFR) {
        EntityPlayer ent = state.player;
        if (ent == null || ent.isDead || ent.deathTime > 0) return;

        double x = ent.lastTickPosX + (ent.posX - ent.lastTickPosX) * pt - rm.viewerPosX;
        double y = ent.lastTickPosY + (ent.posY - ent.lastTickPosY) * pt - rm.viewerPosY;
        double z = ent.lastTickPosZ + (ent.posZ - ent.lastTickPosZ) * pt - rm.viewerPosZ;

        float renderScale = state.baseScale;
        if (ravenAutoScale.getValue()) {
            float dist = (float) Math.sqrt(x * x + y * y + z * z);
            renderScale = computeRavenAutoScale(dist);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + state.yOffset, (float) z);
        GlStateManager.rotate(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rm.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-renderScale, -renderScale, renderScale);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.translate(0.0F, -10.0F, 0.0F);

        if ((ravenBackground.getValue() && ravenBgOpacity.getValue() > 0.01f)
                || ravenBgBorder.getValue()
                || state.relationshipColor != -1) {
            renderRavenBackground(state.stringHalfWidth, 0f, state.teamColor, state.relationshipColor, textFR);
            applyRavenTextGLState();
        }

        drawRavenDisplayName(state, textFR);
        applyRavenTextGLState();

        if (state.totalItems > 0) {
            int iconX = -(state.totalItems * ITEM_SPACING) / 2;
            int iconY = -20;
            if (state.heldItem != null) {
                renderItem3D(state.heldItem, iconX, iconY, itemFR);
                iconX += ITEM_SPACING;
            }
            if (state.helmet != null) {
                renderItem3D(state.helmet, iconX, iconY, itemFR);
                iconX += ITEM_SPACING;
            }
            if (state.chestplate != null) {
                renderItem3D(state.chestplate, iconX, iconY, itemFR);
                iconX += ITEM_SPACING;
            }
            if (state.leggings != null) {
                renderItem3D(state.leggings, iconX, iconY, itemFR);
                iconX += ITEM_SPACING;
            }
            if (state.boots != null) {
                renderItem3D(state.boots, iconX, iconY, itemFR);
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

    private float computeRavenAutoScale(float distance) {
        float base = ravenScale.getValue() * 0.02F;
        float effDist = Math.max(1.0F, distance);
        float scaled = base * (effDist / RAVEN_AUTO_SCALE_THRESHOLD);
        return Math.max(base, scaled);
    }

    private void applyRavenTextGLState() {
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

    private void drawRavenDisplayName(RavenState state, FontRenderer fr) {
        if (state.relationshipColor == -1 || state.playerNameStart < 0 || state.playerNameEnd <= state.playerNameStart) {
            fr.drawString(state.displayName, -state.stringHalfWidth, 0.0f, 0xFFFFFFFF, ravenShadow.getValue());
            return;
        }

        final int[] visibleIdx = {0};
        fr.drawGlyphString(state.displayName, -state.stringHalfWidth, 0.0f, (ch, xOffset, width, fmtColor) -> {
            int idx = visibleIdx[0]++;
            if (idx >= state.playerNameStart && idx < state.playerNameEnd) {
                return state.relationshipColor;
            }
            return fmtColor != null ? fmtColor : 0xFFFFFFFF;
        }, ravenShadow.getValue());
    }

    private void renderRavenBackground(int stringWidth, float textY, int teamColor, int relColor, FontRenderer fr) {
        GlStateManager.disableTexture2D();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        float alpha = (float) ravenBgOpacity.getValue() / 100.0f;
        float left = -stringWidth - 3.0f;
        float right = stringWidth + 3.0f;
        float top = textY + fr.getTextTopOffset() - 3.0f;
        float bottom = textY + fr.getTextBottomOffset() + 2.0f;

        if (ravenBackground.getValue() && alpha > 0.01f) {
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            wr.pos(left, top, 0).color(0, 0, 0, alpha).endVertex();
            wr.pos(left, bottom, 0).color(0, 0, 0, alpha).endVertex();
            wr.pos(right, bottom, 0).color(0, 0, 0, alpha).endVertex();
            wr.pos(right, top, 0).color(0, 0, 0, alpha).endVertex();
            tess.draw();
        }

        int borderColor = relColor != -1 ? relColor : teamColor;
        if (ravenBgBorder.getValue() || relColor != -1) {
            float r = ((borderColor >> 16) & 255) / 255.0f;
            float g = ((borderColor >> 8) & 255) / 255.0f;
            float b = (borderColor & 255) / 255.0f;
            float bAlpha = relColor != -1 ? alpha : 1.0f;
            float bt = 1.0f;
            float l = left - bt, ri = right + bt, t = top - bt, bo = bottom + bt, bz = -0.001f;

            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

            wr.pos(l, t, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(l, top, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(ri, top, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(ri, t, bz).color(r, g, b, bAlpha).endVertex();

            wr.pos(l, bottom, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(l, bo, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(ri, bo, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(ri, bottom, bz).color(r, g, b, bAlpha).endVertex();

            wr.pos(l, top, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(l, bottom, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(left, bottom, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(left, top, bz).color(r, g, b, bAlpha).endVertex();

            wr.pos(right, top, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(right, bottom, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(ri, bottom, bz).color(r, g, b, bAlpha).endVertex();
            wr.pos(ri, t, bz).color(r, g, b, bAlpha).endVertex();
            tess.draw();
        }

        GlStateManager.enableTexture2D();
    }

    private void renderItem3D(ItemStack stack, int xPos, int yPos, net.minecraft.client.gui.FontRenderer fr) {
        if (stack == null) return;

        RenderUtil.renderItemAndEffectIntoGui3D(stack, xPos, yPos);

        if (ravenEnchants.getValue()) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5, 0.5, 0.5);
            GlStateManager.translate(0, -10, 0);
            renderEnchantText(stack, xPos, yPos, fr);
            GlStateManager.popMatrix();
        }

        GlStateManager.disableDepth();

        if (stack.stackSize > 1) {
            String count = String.valueOf(stack.stackSize);
            fr.drawStringWithShadow(count, xPos + 17 - fr.getStringWidth(count), yPos + 9, 0xFFFFFF);
        }

        if (ravenDurability.getValue() && stack.isItemStackDamageable() && stack.getItemDamage() > 0) {
            float ratio = 1.0f - (float) stack.getItemDamage() / (float) stack.getMaxDamage();
            RenderUtil.drawDurabilityBar(xPos, yPos, ratio);
        }

        GlStateManager.enableDepth();
    }

    private void renderEnchantText(ItemStack stack, int xPos, int yPos, net.minecraft.client.gui.FontRenderer fr) {
        int[] ids;
        String[] abbrs;
        Item item = stack.getItem();

        if (item instanceof ItemArmor) {
            ids = ARMOR_ENCHANT_IDS;
            abbrs = ARMOR_ENCHANT_ABBR;
        } else if (item instanceof ItemSword) {
            ids = SWORD_ENCHANT_IDS;
            abbrs = SWORD_ENCHANT_ABBR;
        } else if (item instanceof ItemBow) {
            ids = BOW_ENCHANT_IDS;
            abbrs = BOW_ENCHANT_ABBR;
        } else if (item instanceof ItemTool) {
            ids = TOOL_ENCHANT_IDS;
            abbrs = TOOL_ENCHANT_ABBR;
        } else {
            ids = MISC_ENCHANT_IDS;
            abbrs = MISC_ENCHANT_ABBR;
        }

        int dx = xPos * 2;
        int dy = yPos - 24;

        for (int i = 0; i < ids.length; i++) {
            int lvl = EnchantmentHelper.getEnchantmentLevel(ids[i], stack);
            if (lvl <= 0) continue;
            fr.drawStringWithShadow(abbrs[i], dx, dy, 0xFFFFFF);
            int adv = fr.getStringWidth(abbrs[i]);
            fr.drawStringWithShadow(String.valueOf(lvl), dx + adv, dy, enchantColorForLvl(lvl));
            dy += 8;
        }
    }

    private int enchantColorForLvl(int lvl) {
        switch (lvl) {
            case 1:
                return 0xFFFFFF;
            case 2:
                return 0x55FFFF;
            case 3:
                return 0x00AAAA;
            case 4:
                return 0xAA00AA;
            case 5:
                return 0xFFAA00;
            default:
                return 0xFF55FF;
        }
    }

    private static class RavenState {
        EntityPlayer player;
        String displayName;
        int stringHalfWidth;
        int teamColor;
        int relationshipColor;
        int playerNameStart;
        int playerNameEnd;
        double distanceSq;
        float baseScale;
        float yOffset;
        ItemStack heldItem, boots, leggings, chestplate, helmet;
        int totalItems;

        void set(EntityPlayer p, String dn, int shw, int tc, int rc,
                 int pns, int pne, double dsq, float bs, float yo,
                 ItemStack hi, ItemStack bt, ItemStack lg, ItemStack ch, ItemStack hm, int ti) {
            this.player = p;
            this.displayName = dn;
            this.stringHalfWidth = shw;
            this.teamColor = tc;
            this.relationshipColor = rc;
            this.playerNameStart = pns;
            this.playerNameEnd = pne;
            this.distanceSq = dsq;
            this.baseScale = bs;
            this.yOffset = yo;
            this.heldItem = hi;
            this.boots = bt;
            this.leggings = lg;
            this.chestplate = ch;
            this.helmet = hm;
            this.totalItems = ti;
        }
    }
}