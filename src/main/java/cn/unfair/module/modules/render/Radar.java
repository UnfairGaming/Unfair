package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.enums.ChatColors;
import cn.unfair.module.Module;
import cn.unfair.property.properties.*;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.client.TeamUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class Radar extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float FONT_SIZE = 16.0F;
    private static final String MINECRAFT_FONT = "Minecraft";

    public final ModeProperty font = new ModeProperty("Font", 0, getFontModes());
    public final ModeProperty colorMode = new ModeProperty("Color", 0, new String[]{"Default", "Teams", "Hud"});
    public final IntProperty radarRadius = new IntProperty("Radar Radius", 55, 10, 200);
    public final FloatProperty dotRadius = new FloatProperty("Dot Radius", 1.5F, 0.1F, 5.0F);
    public final BooleanProperty background = new BooleanProperty("Background", true);
    public final BooleanProperty showPlayers = new BooleanProperty("Players", true);
    public final BooleanProperty showFriends = new BooleanProperty("Friends", true);
    public final BooleanProperty showEnemies = new BooleanProperty("Enemies", true);
    public final BooleanProperty showBots = new BooleanProperty("Bots", false);
    public final BooleanProperty showPVP = new BooleanProperty("Show Pvp", false);
    public final ColorProperty fillColor = new ColorProperty("Fill Color", Color.GRAY.getRGB(), this.background::getValue);
    public final ColorProperty outlineColor = new ColorProperty("Outline Color", Color.DARK_GRAY.getRGB());
    public final ColorProperty crossColor = new ColorProperty("Cross Color", Color.LIGHT_GRAY.getRGB());

    public Radar() {
        super("Radar", false, true);
    }

    private static String[] getFontModes() {
        Fonts[] fonts = Fonts.values();
        String[] modes = new String[fonts.length + 1];
        modes[0] = MINECRAFT_FONT;
        for (int i = 0; i < fonts.length; i++) {
            String fontName = fonts[i].name();
            modes[i + 1] = Character.toUpperCase(fontName.charAt(0)) + fontName.substring(1);
        }
        return modes;
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public boolean shouldRenderWidget() {
        return this.isEnabled() && mc.theWorld != null && mc.thePlayer != null && !mc.gameSettings.showDebugInfo;
    }

    public boolean shouldRenderWidgetEffects() {
        return this.shouldRenderWidget() && this.background.getValue();
    }

    public float[] getWidgetSize() {
        float size = this.radarRadius.getValue() * 2.0F + this.getLabelPadding() * 2.0F;
        return new float[]{size, size};
    }

    public void renderWidget(float partialTicks, float x, float y) {
        if (!this.shouldRenderWidget()) {
            return;
        }
        this.renderRadar(partialTicks, x, y, false, 0);
    }

    public void renderWidgetMask(float x, float y, int color) {
        if (!this.shouldRenderWidgetEffects()) {
            return;
        }
        this.renderRadar(0.0F, x, y, true, color);
    }

    private void renderRadar(float partialTicks, float x, float y, boolean mask, int maskColor) {
        float radius = this.radarRadius.getValue();
        float centerX = x + this.getLabelPadding() + radius;
        float centerY = y + this.getLabelPadding() + radius;

        if (mask) {
            RenderUtil.drawShaderCircle(centerX, centerY, radius, maskColor);
            return;
        }

        RenderUtil.enableRenderState();
        GlStateManager.pushMatrix();
        try {
            float yaw = (float) Math.toRadians(mc.thePlayer.rotationYaw);
            if (mc.gameSettings.thirdPersonView != 2) {
                yaw += (float) Math.toRadians(180.0F);
            }

            this.drawRadarCircle(centerX, centerY, yaw, radius);
            this.drawPlayerDots(partialTicks, centerX, centerY, yaw, radius);
            if (this.showPVP.getValue()) {
                this.drawPvpMarker(centerX, centerY, yaw, radius);
            }
        } finally {
            GlStateManager.popMatrix();
            RenderUtil.disableRenderState();
        }
    }

    private void drawRadarCircle(float centerX, float centerY, float angle, float radius) {
        if (this.background.getValue()) {
            Color fill = new Color(this.fillColor.getValue());
            RenderUtil.drawShaderCircle(centerX, centerY, radius, new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 100).getRGB());
        }

        RenderUtil.drawShaderCircleOutline(centerX, centerY, radius, 2.0F, withAlpha(this.outlineColor.getValue(), 255));
        this.drawCross(centerX, centerY, angle, radius, withAlpha(this.crossColor.getValue(), 255));
        this.drawDirections(centerX, centerY, angle, radius);
    }

    private void drawCross(float centerX, float centerY, float angle, float radius, int color) {
        if (((color >>> 24) & 0xFF) == 0) {
            return;
        }

        RenderUtil.setColor(color);
        GL11.glLineWidth(1.5F);
        GL11.glBegin(GL11.GL_LINES);

        double dx1 = Math.sin(angle);
        double dy1 = Math.cos(angle);
        double dx2 = Math.sin(angle + Math.PI / 2.0);
        double dy2 = Math.cos(angle + Math.PI / 2.0);

        GL11.glVertex2d(centerX - dx1 * radius, centerY - dy1 * radius);
        GL11.glVertex2d(centerX + dx1 * radius, centerY + dy1 * radius);
        GL11.glVertex2d(centerX - dx2 * radius, centerY - dy2 * radius);
        GL11.glVertex2d(centerX + dx2 * radius, centerY + dy2 * radius);

        GL11.glEnd();
    }

    private void drawDirections(float centerX, float centerY, float angle, float radius) {
        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        int color = HUD.getColor(System.currentTimeMillis()).getRGB();
        boolean shadow = hud.shadow.getValue();

        double dx1 = Math.sin(angle);
        double dy1 = Math.cos(angle);
        double dx2 = Math.sin(angle + Math.PI / 2.0);
        double dy2 = Math.cos(angle + Math.PI / 2.0);

        this.drawCenteredString("N", centerX - dx1 * (radius + 5.0F), centerY - dy1 * (radius + 5.0F), color, shadow);
        this.drawCenteredString("E", centerX + dx2 * (radius + 5.0F), centerY + dy2 * (radius + 5.0F), color, shadow);
        this.drawCenteredString("S", centerX + dx1 * (radius + 5.0F), centerY + dy1 * (radius + 5.0F), color, shadow);
        this.drawCenteredString("W", centerX - dx2 * (radius + 5.0F), centerY - dy2 * (radius + 5.0F), color, shadow);
    }

    private void drawPlayerDots(float partialTicks, float centerX, float centerY, float yaw, float radius) {
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        for (Object entity : TeamUtil.getLoadedEntitiesSorted()) {
            if (!(entity instanceof EntityPlayer player) || !this.shouldRender(player)) {
                continue;
            }

            double dx = (player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks) - mc.thePlayer.posX;
            double dz = (player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks) - mc.thePlayer.posZ;

            double relX = dx * cos + dz * sin;
            double relY = dz * cos - dx * sin;
            double dist = Math.sqrt(relX * relX + relY * relY);
            double scale = dist < radius ? 1.0D : radius / dist;

            RenderUtil.fillCircle(
                    centerX + relX * scale,
                    centerY + relY * scale,
                    this.dotRadius.getValue(),
                    12,
                    this.getEntityColor(player).getRGB()
            );
        }
    }

    private void drawPvpMarker(float centerX, float centerY, float yaw, float radius) {
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double dx = -mc.thePlayer.posX;
        double dz = -mc.thePlayer.posZ;
        double relX = dx * cos + dz * sin;
        double relY = dz * cos - dx * sin;
        double dist = Math.sqrt(relX * relX + relY * relY);
        double scale = dist < radius * 2.0F ? 1.0D : radius * 2.0F / dist;

        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        this.drawCenteredString(
                "PVP",
                centerX + relX * scale,
                centerY + relY * scale,
                Color.WHITE.getRGB(),
                hud.shadow.getValue()
        );
    }

    private void drawCenteredString(String text, double x, double y, int color, boolean shadow) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        this.drawString(text, (float) (x - this.getStringWidth(text) / 2.0F), (float) (y - this.getFontHeight() / 2.0F), color, shadow);
        GlStateManager.disableTexture2D();
    }

    private float getLabelPadding() {
        float maxLabelWidth = 0.0F;
        maxLabelWidth = Math.max(maxLabelWidth, this.getStringWidth("N"));
        maxLabelWidth = Math.max(maxLabelWidth, this.getStringWidth("E"));
        maxLabelWidth = Math.max(maxLabelWidth, this.getStringWidth("S"));
        maxLabelWidth = Math.max(maxLabelWidth, this.getStringWidth("W"));
        return 5.0F + Math.max(maxLabelWidth / 2.0F, this.getFontHeight() / 2.0F);
    }

    private boolean useMinecraftFont() {
        return this.font.getValue() == 0;
    }

    private FontRenderer getCustomFont() {
        int fontIndex = this.font.getValue() - 1;
        Fonts[] fonts = Fonts.values();
        if (fontIndex < 0 || fontIndex >= fonts.length) {
            return null;
        }
        return fonts[fontIndex].get(FONT_SIZE);
    }

    private int getStringWidth(String text) {
        if (this.useMinecraftFont()) {
            return mc.fontRendererObj.getStringWidth(text);
        }
        FontRenderer fontRenderer = this.getCustomFont();
        return fontRenderer == null ? mc.fontRendererObj.getStringWidth(text) : fontRenderer.getStringWidth(text);
    }

    private int getFontHeight() {
        if (this.useMinecraftFont()) {
            return mc.fontRendererObj.FONT_HEIGHT;
        }
        FontRenderer fontRenderer = this.getCustomFont();
        return fontRenderer == null ? mc.fontRendererObj.FONT_HEIGHT : fontRenderer.getHeight();
    }

    private void drawString(String text, float x, float y, int color, boolean shadow) {
        if (this.useMinecraftFont()) {
            mc.fontRendererObj.drawString(text, x, y, color, shadow);
            return;
        }
        FontRenderer fontRenderer = this.getCustomFont();
        if (fontRenderer == null) {
            mc.fontRendererObj.drawString(text, x, y, color, shadow);
        } else if (shadow) {
            fontRenderer.drawStringWithShadow(text, x, y, color);
        } else {
            fontRenderer.drawString(text, x, y, color);
        }
    }

    private boolean shouldRender(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) {
            return false;
        } else if (entityPlayer != mc.thePlayer && entityPlayer != mc.getRenderViewEntity()) {
            if (TeamUtil.isBot(entityPlayer)) {
                return this.showBots.getValue();
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return this.showFriends.getValue();
            } else {
                return TeamUtil.isTarget(entityPlayer) ? this.showEnemies.getValue() : this.showPlayers.getValue();
            }
        } else {
            return false;
        }
    }

    private Color getEntityColor(EntityPlayer entityPlayer) {
        if (TeamUtil.isFriend(entityPlayer)) {
            Color color = Unfair.friendManager.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
        } else if (TeamUtil.isTarget(entityPlayer)) {
            Color color = Unfair.targetManager.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
        } else {
            switch (this.colorMode.getValue()) {
                case 0:
                    return TeamUtil.getTeamColor(entityPlayer, 1.0F);
                case 1:
                    int teamColor = TeamUtil.isSameTeam(entityPlayer) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
                    return new Color(teamColor | 255 << 24, true);
                case 2:
                    Unfair.moduleManager.modules.get(HUD.class);
                    int color = HUD.getColor(System.currentTimeMillis()).getRGB();
                    return new Color(color | 255 << 24, true);
                default:
                    return Color.WHITE;
            }
        }
    }
}
