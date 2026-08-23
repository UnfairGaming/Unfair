package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.module.Module;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Scoreboard extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float BASE_FONT_SIZE = 16.0F;
    private static final float BASE_PADDING_X = 3.0F;
    private static final float BASE_PADDING_TOP = 3.0F;
    private static final float BASE_PADDING_BOTTOM = 3.0F;
    private static final float BASE_MIN_WIDTH = 70.0F;
    private static final int BACKGROUND_RGB = 8 << 16 | 10 << 8 | 14;
    private static final int TITLE_COLOR = 0xFFF4F6FB;
    private static final int TEXT_COLOR = 0xE8E9EDF5;

    public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty background = new PercentProperty("Background", 55);

    private float cachedWidth = BASE_MIN_WIDTH;
    private float cachedHeight = 24.0F;

    public Scoreboard() {
        super("Scoreboard", false, true);
    }

    private FontRenderer getFontRenderer() {
        return Fonts.interMedium.get(BASE_FONT_SIZE * this.scale.getValue());
    }

    public boolean shouldRenderWidget() {
        return this.isEnabled()
                && mc.theWorld != null
                && mc.thePlayer != null
                && !mc.gameSettings.showDebugInfo
                && this.getSidebarObjective() != null;
    }

    public boolean shouldRenderWidgetEffects() {
        return this.shouldRenderWidget() && this.background.getValue() > 0;
    }

    public boolean shouldReplaceVanilla() {
        return this.shouldRenderWidget();
    }

    public float[] getWidgetSize() {
        ScoreObjective objective = this.getSidebarObjective();
        if (objective == null) {
            return new float[]{this.cachedWidth, this.cachedHeight};
        }
        this.updateLayout(objective);
        return new float[]{this.cachedWidth, this.cachedHeight};
    }

    public void renderWidget(float x, float y) {
        ScoreObjective objective = this.getSidebarObjective();
        if (!this.shouldRenderWidget() || objective == null) {
            return;
        }

        List<ScoreboardLine> lines = this.getLines(objective);
        this.updateLayout(objective, lines);

        RenderUtil.enableRenderState();
        this.drawBackground(x, y, this.getBackgroundColor());
        RenderUtil.disableRenderState();

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        this.drawString(
                objective.getDisplayName(),
                x + this.cachedWidth / 2.0F - this.getStringWidth(objective.getDisplayName()) / 2.0F,
                y + this.getPaddingTop(),
                TITLE_COLOR
        );

        float lineY = y + this.getPaddingTop() + this.getFontHeight();
        for (int i = lines.size() - 1; i >= 0; i--) {
            ScoreboardLine line = lines.get(i);
            this.drawString(line.name, x + this.getPaddingX(), lineY, TEXT_COLOR);
            lineY += this.getLineHeight();
        }
        GlStateManager.disableBlend();
    }

    public void renderWidgetMask(float x, float y, int color) {
        ScoreObjective objective = this.getSidebarObjective();
        if (!this.shouldRenderWidgetEffects() || objective == null) {
            return;
        }
        this.updateLayout(objective);
        RenderUtil.enableRenderState();
        this.drawBackground(x, y, color);
        RenderUtil.disableRenderState();
    }

    private float getPaddingX() {
        return BASE_PADDING_X * this.scale.getValue();
    }

    private float getPaddingTop() {
        return BASE_PADDING_TOP * this.scale.getValue();
    }

    private float getPaddingBottom() {
        return BASE_PADDING_BOTTOM * this.scale.getValue();
    }

    private void updateLayout(ScoreObjective objective) {
        this.updateLayout(objective, this.getLines(objective));
    }

    private void updateLayout(ScoreObjective objective, List<ScoreboardLine> lines) {
        float scale = this.scale.getValue();
        float width = this.getStringWidth(objective.getDisplayName());
        for (ScoreboardLine line : lines) {
            float lineWidth = this.getStringWidth(line.name);
            width = Math.max(width, lineWidth);
        }
        this.cachedWidth = Math.max(BASE_MIN_WIDTH * scale, width + this.getPaddingX() * 2.0F);
        this.cachedHeight = this.getPaddingTop() + this.getFontHeight() + lines.size() * this.getLineHeight() + this.getPaddingBottom();
    }

    private void drawBackground(float x, float y, int color) {
        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        Float radius = hud.roundRadius.getValue() * hud.scale.getValue();

        if (((color >> 24) & 0xFF) <= 0) {
            return;
        }
        RenderUtil.drawRoundedRectangle(x, y, x + this.cachedWidth, y + this.cachedHeight, radius, color);
    }

    private int getBackgroundColor() {
        int alpha = (int) (this.background.getValue().floatValue() / 100.0F * 230.0F);
        return alpha << 24 | BACKGROUND_RGB;
    }

    private float getLineHeight() {
        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        Boolean shouldShadow = hud.shadow.getValue();
        float scale = this.scale.getValue();
        float shadowOffset = shouldShadow ? 0.5F * scale : 0.0F;
        return Math.max(5.0F * scale, this.getFontHeight() + shadowOffset - 2.0F * scale);
    }

    private float getFontHeight() {
        return this.getFontRenderer().getHeight();
    }

    private int getStringWidth(String text) {
        return this.getFontRenderer().getStringWidth(text);
    }

    private void drawString(String text, float x, float y, int color) {
        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        Boolean shouldShadow = hud.shadow.getValue();
        FontRenderer fr = this.getFontRenderer();
        if (shouldShadow) {
            fr.drawStringWithShadow(text, x, y, color);
        } else {
            fr.drawString(text, x, y, color);
        }
    }

    private ScoreObjective getSidebarObjective() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }
        net.minecraft.scoreboard.Scoreboard scoreboard = mc.theWorld.getScoreboard();
        ScoreObjective scoreObjective = null;
        ScorePlayerTeam scorePlayerTeam = scoreboard.getPlayersTeam(mc.thePlayer.getName());

        if (scorePlayerTeam != null) {
            int colorIndex = scorePlayerTeam.getChatFormat().getColorIndex();

            if (colorIndex >= 0) {
                scoreObjective = scoreboard.getObjectiveInDisplaySlot(3 + colorIndex);
            }
        }

        return scoreObjective != null ? scoreObjective : scoreboard.getObjectiveInDisplaySlot(1);
    }

    private List<ScoreboardLine> getLines(ScoreObjective objective) {
        net.minecraft.scoreboard.Scoreboard scoreboard = objective.getScoreboard();
        Collection<Score> scores = scoreboard.getSortedScores(objective);
        List<Score> visibleScores = Lists.newArrayList(Iterables.filter(scores, score ->
                score.getPlayerName() != null && !score.getPlayerName().startsWith("#")));

        Collection<Score> renderedScores;
        if (visibleScores.size() > 15) {
            renderedScores = Lists.newArrayList(Iterables.skip(visibleScores, visibleScores.size() - 15));
        } else {
            renderedScores = visibleScores;
        }

        List<ScoreboardLine> lines = new ArrayList<>();
        for (Score score : renderedScores) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String name = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            lines.add(new ScoreboardLine(name));
        }
        return lines;
    }

    private record ScoreboardLine(String name) {
    }
}