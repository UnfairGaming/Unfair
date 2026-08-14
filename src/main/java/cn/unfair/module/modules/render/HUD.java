package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.enums.ChatColors;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.Render2DEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.*;
import cn.unfair.util.ColorUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class HUD extends Module {
    public static final ModeProperty colorMode = new ModeProperty(
            "Color", 3, new String[]{"Rainbow", "Chroma", "Astolfo", "Custom1", "Custom12", "Custom123"}
    );
    public static final FloatProperty colorSpeed = new FloatProperty("Color Speed", 1.0F, 0.5F, 1.5F);
    public static final PercentProperty colorSaturation = new PercentProperty("Color Saturation", 50);
    public static final PercentProperty colorBrightness = new PercentProperty("Color Brightness", 100);
    public static final ColorProperty custom1 = new ColorProperty("Custom Color 1", Color.WHITE.getRGB(), () -> colorMode.getValue() == 3 || colorMode.getValue() == 4 || colorMode.getValue() == 5);
    public static final ColorProperty custom2 = new ColorProperty("Custom Color 2", Color.WHITE.getRGB(), () -> colorMode.getValue() == 4 || colorMode.getValue() == 5);
    public static final ColorProperty custom3 = new ColorProperty("Custom Color 3", Color.WHITE.getRGB(), () -> colorMode.getValue() == 5);
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float ANIMATION_DURATION = 200.0F;
    private static final float HUD_FONT_SIZE = 16.0F;
    private static final String MINECRAFT_FONT = "Minecraft";
    public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 1.5F);
    public final ModeProperty font = new ModeProperty("Font", 0, getFontModes());
    public final PercentProperty background = new PercentProperty("Background", 50);
    public final BooleanProperty round = new BooleanProperty("Round", true, () -> this.background.getValue() > 0);
    public final BooleanProperty showBar = new BooleanProperty("Bar", true);
    public final ModeProperty barPos = new ModeProperty("Bar Mode", 0, new String[]{"Left", "Right", "Top"}, this.showBar::getValue);
    public final BooleanProperty shadow = new BooleanProperty("Shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("Suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("Lower Case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("Chat Outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("Blink Timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("Toggle Sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("Toggle Alerts", false);
    private final Set<Module> fadingOutModules = new HashSet<>();
    private final Map<Module, HudAnimation> animationMap = new HashMap<>();
    private List<Module> activeModules = new ArrayList<>();

    public HUD() {
        super("HUD", true, true);
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

    public static float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.clamp(colorSpeed.getValue(), 0.5F, 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    public static Color getColor(long time) {
        return getColor(time, 0L);
    }

    public static Color getColor(long time, long offset) {
        Color color = Color.white;
        switch (colorMode.getValue()) {
            case 0:
                color = ColorUtil.fromHSB(getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(custom1.getValue());
                break;
            case 4:
                double cycle1 = getColorCycle(time, offset);
                color = ColorUtil.interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(custom1.getValue()),
                        new Color(custom2.getValue())
                );
                break;
            case 5:
                double cycle2 = getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = ColorUtil.interpolate(floor * 2.0F, new Color(custom1.getValue()), new Color(custom2.getValue()));
                } else {
                    color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(custom2.getValue()), new Color(custom3.getValue()));
                }
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (colorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (colorBrightness.getValue().floatValue() / 100.0F)
        );
    }

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] moduleSuffix = module.getSuffix();
        if (this.lowerCase.getValue()) {
            for (int i = 0; i < moduleSuffix.length; i++) {
                moduleSuffix[i] = moduleSuffix[i].toLowerCase();
            }
        }
        return moduleSuffix;
    }

    private int getModuleWidth(Module module) {
        return this.calculateStringWidth(
                this.getModuleName(module), this.getModuleSuffix(module)
        );
    }

    private int calculateStringWidth(String string, String[] arr) {
        int width = this.getTextWidth(string);
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + this.getTextWidth(str);
            }
        }
        return width;
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
        return fonts[fontIndex].get(HUD_FONT_SIZE);
    }

    private int getTextWidth(String text) {
        if (this.useMinecraftFont()) {
            return mc.fontRendererObj.getStringWidth(text);
        }
        FontRenderer fontRenderer = this.getCustomFont();
        return fontRenderer == null ? mc.fontRendererObj.getStringWidth(text) : fontRenderer.getStringWidth(text);
    }

    private float getTextHeight() {
        if (this.useMinecraftFont()) {
            return (float) mc.fontRendererObj.FONT_HEIGHT - 1.0F;
        }
        FontRenderer fontRenderer = this.getCustomFont();
        return fontRenderer == null ? (float) mc.fontRendererObj.FONT_HEIGHT - 1.0F : (float) fontRenderer.getHeight();
    }

    private void drawHudString(String text, float x, float y, int color, boolean shadow, boolean alignTop) {
        float renderY = y + (!shadow && !alignTop ? 1.0F : 0.0F);
        if (this.useMinecraftFont()) {
            if (shadow) {
                mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
            } else {
                mc.fontRendererObj.drawString(text, x, renderY, color, false);
            }
            return;
        }

        FontRenderer fontRenderer = this.getCustomFont();
        if (fontRenderer == null) {
            mc.fontRendererObj.drawString(text, x, renderY, color, shadow);
            return;
        }
        if (shadow) {
            fontRenderer.drawStringWithShadow(text, x, y, color);
        } else {
            fontRenderer.drawString(text, x, renderY, color);
        }
    }

    private float getAnimationProgress(Module module, float partialTicks) {
        HudAnimation animation = this.animationMap.get(module);
        if (animation == null) {
            return module.isEnabled() && !module.isHidden() ? 1.0F : 0.0F;
        }
        return Math.clamp(RenderUtil.lerpFloat(
                animation.currentProgress,
                animation.lastProgress,
                partialTicks
        ), 0.0F, 1.0F);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.type() == EventType.POST) {
            List<Module> newActiveModules = Unfair.moduleManager.modules.values().stream()
                    .filter(module -> module.isEnabled() && !module.isHidden())
                    .sorted(Comparator.comparingInt(this::getModuleWidth).reversed())
                    .collect(Collectors.<Module>toList());

            Set<Module> trackedModules = new HashSet<>();
            trackedModules.addAll(this.activeModules);
            trackedModules.addAll(newActiveModules);
            trackedModules.addAll(this.fadingOutModules);

            for (Module module : trackedModules) {
                if (module == null || module.isHidden()) {
                    // If the module was hidden, remove immediately without fade-out animation.
                    if (module != null) {
                        this.animationMap.remove(module);
                        this.fadingOutModules.remove(module);
                        continue;
                    }
                    continue;
                }
                boolean active = newActiveModules.contains(module);
                HudAnimation animation = this.animationMap.get(module);
                int targetIndex = active ? newActiveModules.indexOf(module) : this.activeModules.indexOf(module);
                if (targetIndex < 0) {
                    targetIndex = newActiveModules.size();
                }
                if (animation == null) {
                    animation = new HudAnimation(active ? 0.0F : 1.0F, targetIndex);
                    this.animationMap.put(module, animation);
                }

                if (active) {
                    animation.unfreezeIndex();
                    animation.moveTo(targetIndex, false);
                } else {
                    animation.freezeIndex(targetIndex);
                }
                animation.tick(active);
                if (active) {
                    this.fadingOutModules.remove(module);
                } else {
                    this.fadingOutModules.add(module);
                    if (animation.isFinishedOut()) {
                        this.animationMap.remove(module);
                        this.fadingOutModules.remove(module);
                    }
                }
            }

            Iterator<Map.Entry<Module, HudAnimation>> it = this.animationMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Module, HudAnimation> entry = it.next();
                Module m = entry.getKey();
                HudAnimation animation = entry.getValue();
                if (m == null || animation == null || m.isHidden()) {
                    it.remove();
                    this.fadingOutModules.remove(m);
                    continue;
                }
                if (!m.isEnabled() && animation.isFinishedOut()) {
                    it.remove();
                    this.fadingOutModules.remove(m);
                }

            }

            this.activeModules = newActiveModules;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((GuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Unfair.commandManager != null && Unfair.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        getColor(System.currentTimeMillis()).getRGB()
                );
                RenderUtil.disableRenderState();
            }
        }
        this.renderStandaloneHud();
    }

    public boolean shouldRenderWidget() {
        return this.isEnabled() && !mc.gameSettings.showDebugInfo;
    }

    public boolean shouldRenderWidgetEffects() {
        return this.shouldRenderWidget() && this.background.getValue() > 0 && !this.getRenderList().isEmpty();
    }

    public float getEntryHeight() {
        return (this.getTextHeight() + (this.shadow.getValue() ? 1.0F : 0.0F)) * this.scale.getValue();
    }

    public float[] getWidgetSize() {
        List<Module> renderList = this.getRenderList();
        if (renderList.isEmpty()) {
            return new float[]{80.0F, 20.0F};
        }

        float maxWidth = 0.0F;
        for (Module module : renderList) {
            String moduleName = this.getModuleName(module);
            String[] moduleSuffix = this.getModuleSuffix(module);
            maxWidth = Math.max(maxWidth, (float) (this.calculateStringWidth(moduleName, moduleSuffix) - (this.shadow.getValue() ? 0 : 1)));
        }
        float barExtra = this.showBar.getValue() && this.barPos.getValue() != 2 ? 3.0F : 0.0F;
        return new float[]{(maxWidth + 2.0F + barExtra) * this.scale.getValue(), renderList.size() * this.getEntryHeight() + 2.0F};
    }

    public void renderWidget(float partialTicks, float x, float y, boolean alignLeft, boolean alignTop) {
        if (!this.shouldRenderWidget()) {
            return;
        }
        this.renderModuleList(partialTicks, x, y, alignLeft, alignTop, false, 0);
    }

    public void renderWidgetMask(float partialTicks, float x, float y, boolean alignLeft, boolean alignTop, int maskColor) {
        if (!this.shouldRenderWidgetEffects()) {
            return;
        }
        this.renderModuleList(partialTicks, x, y, alignLeft, alignTop, true, maskColor);
    }

    private void renderStandaloneHud() {
        if (!this.isEnabled() || mc.gameSettings.showDebugInfo) {
            return;
        }
        if (this.blinkTimer.getValue()) {
            BlinkModules blinkingModule = Unfair.blinkManager.getBlinkingModule();
            if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                long movementPacketSize = Unfair.blinkManager.countMovement();
                if (movementPacketSize > 0L) {
                    long colorOffset = this.getRenderList().size();
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    String movementText = String.valueOf(movementPacketSize);
                    this.drawHudString(
                            movementText,
                            (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / this.scale.getValue()
                                    - (float) this.getTextWidth(movementText) / 2.0F,
                            (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 3.0F / this.scale.getValue(),
                            getColor(System.currentTimeMillis(), colorOffset).getRGB() & 16777215 | -1090519040,
                            this.shadow.getValue(),
                            true
                    );
                    GlStateManager.disableBlend();
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    private void renderModuleList(float partialTicks, float x, float y, boolean alignLeft, boolean alignTop, boolean mask, int maskColor) {
        List<Module> renderList = this.getRenderList();
        if (renderList.isEmpty()) {
            return;
        }

        float height = this.getTextHeight();
        float currentBaseX = x;
        long time = System.currentTimeMillis();
        long offset = 0L;

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);

        for (Module module : renderList) {
            String moduleName = this.getModuleName(module);
            String[] moduleSuffix = this.getModuleSuffix(module);
            float totalWidth = (float) (this.calculateStringWidth(moduleName, moduleSuffix) - (this.shadow.getValue() ? 0 : 1));
            int color = getColor(time, offset).getRGB();

            boolean isFadingOut = !module.isEnabled();
            float animProgress = this.getAnimationProgress(module, partialTicks);

            if (isFadingOut && animProgress <= 0.01F) {
                offset++;
                continue;
            }

            float xSlideDir = alignLeft ? -1.0F : 1.0F;
            float xSlideAmount = (1.0F - animProgress) * totalWidth * xSlideDir;

            float rowOffset = this.getRenderRow(module, offset, partialTicks) * this.getEntryHeight();
            float currentX = currentBaseX + xSlideAmount;
            float currentY = y + rowOffset * (alignTop ? 1.0F : -1.0F);

            if (mask) {
                if (animProgress > 0.02F) {
                    RenderUtil.enableRenderState();
                    HudEntry entry = this.buildHudEntry(renderList, module, currentX, currentY, totalWidth, height, alignLeft, alignTop, offset);
                    this.drawHudMask(entry, maskColor);
                    RenderUtil.disableRenderState();
                }
            } else {
                int animatedColor = color;
                if (animProgress < 1.0F) {
                    int alpha = Math.clamp((int) (animProgress * 255.0F), 0, 255);
                    animatedColor = (color & 0x00FFFFFF) | (alpha << 24);
                }

                RenderUtil.enableRenderState();
                if (this.background.getValue() > 0 && animProgress > 0.02F) {
                    int bgAlpha = (int) (animProgress * this.background.getValue().floatValue() / 100.0F * 255.0F);
                    bgAlpha = Math.min(bgAlpha, 255);
                    HudEntry entry = this.buildHudEntry(renderList, module, currentX, currentY, totalWidth, height, alignLeft, alignTop, offset);
                    this.drawHudBackground(entry, new Color(0.0F, 0.0F, 0.0F, bgAlpha / 255.0F).getRGB());
                }
                if (this.showBar.getValue() && animProgress > 0.02F) {
                    this.drawHudBar(currentX, currentY, totalWidth, height, alignLeft, alignTop, offset, color, animProgress);
                }
                RenderUtil.disableRenderState();

                GlStateManager.disableDepth();
                if (animProgress > 0.05F) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    this.drawHudString(
                            moduleName,
                            currentX / this.scale.getValue() - (alignLeft ? 0.0F : totalWidth),
                            currentY / this.scale.getValue(),
                            animatedColor,
                            this.shadow.getValue(),
                            alignTop
                    );
                    if (this.suffixes.getValue() && moduleSuffix.length > 0 && animProgress > 0.5F) {
                        float width = (float) this.getTextWidth(moduleName) + 3.0F;
                        int suffixAlpha = (int) (((animProgress - 0.5F) / 0.5F) * 255.0F);
                        suffixAlpha = Math.min(suffixAlpha, 255);
                        int suffixColor = ChatColors.GRAY.toAwtColor() & 0x00FFFFFF | (suffixAlpha << 24);
                        for (String string : moduleSuffix) {
                            this.drawHudString(
                                    string,
                                    currentX / this.scale.getValue() - (alignLeft ? 0.0F : totalWidth) + width,
                                    currentY / this.scale.getValue(),
                                    suffixColor,
                                    this.shadow.getValue(),
                                    alignTop
                            );
                            width += (float) this.getTextWidth(string) + (this.shadow.getValue() ? 3.0F : 2.0F);
                        }
                    }
                    GlStateManager.disableBlend();
                }
                GlStateManager.enableDepth();
            }

            offset++;
        }

        GlStateManager.popMatrix();
    }

    private void drawHudBar(float currentX, float currentY, float totalWidth, float height,
                            boolean alignLeft, boolean alignTop, long offset, int color, float animProgress) {
        int barAlpha = (int) (animProgress * 255.0F);
        barAlpha = Math.min(barAlpha, 255);
        int barColor = (color & 0x00FFFFFF) | (barAlpha << 24);

        float barX = 0, barX2 = 0;
        float barY = 0, barY2 = 0;
        boolean shouldDrawBar = true;

        if (barPos.getValue() == 0) {
            if (alignLeft) {
                barX = currentX / this.scale.getValue() - 2.0F;
                barX2 = currentX / this.scale.getValue() - 1.0F;
            } else {
                barX = currentX / this.scale.getValue() - totalWidth - 2.0F;
                barX2 = currentX / this.scale.getValue() - totalWidth - 1.0F;
            }
            barY = currentY / this.scale.getValue() - (alignTop ? (offset == 0L ? 1.0F : 0.0F) : 1.0F);
            barY2 = currentY / this.scale.getValue() + height + (alignTop ? 1.0F : (offset == 0L ? 1.0F : 0.0F));
        } else if (barPos.getValue() == 1) {
            if (alignLeft) {
                barX = currentX / this.scale.getValue() + totalWidth + 1.0F;
                barX2 = currentX / this.scale.getValue() + totalWidth + 2.0F;
            } else {
                barX = currentX / this.scale.getValue() + 1.0F;
                barX2 = currentX / this.scale.getValue() + 2.0F;
            }
            barY = currentY / this.scale.getValue() - (alignTop ? (offset == 0L ? 1.0F : 0.0F) : 1.0F);
            barY2 = currentY / this.scale.getValue() + height + (alignTop ? 1.0F : (offset == 0L ? 1.0F : 0.0F));
        } else {
            if (offset == 0L) {
                if (alignLeft) {
                    barX = currentX / this.scale.getValue() - 1.0F;
                    barX2 = currentX / this.scale.getValue() + totalWidth + 1.0F;
                } else {
                    barX = currentX / this.scale.getValue() - totalWidth - 1.0F;
                    barX2 = currentX / this.scale.getValue() + 1.0F;
                }
                if (alignTop) {
                    barY = currentY / this.scale.getValue() - 2.0F;
                    barY2 = currentY / this.scale.getValue() - 1.0F;
                } else {
                    barY = currentY / this.scale.getValue() + height + 1.0F;
                    barY2 = currentY / this.scale.getValue() + height + 2.0F;
                }
            } else {
                shouldDrawBar = false;
            }
        }

        if (shouldDrawBar) {
            RenderUtil.drawRect(barX, barY, barX2, barY2, barColor);
        }
    }

    private List<Module> getRenderList() {
        List<Module> renderList = new ArrayList<>(this.activeModules);
        for (Module fading : this.fadingOutModules) {
            if (!fading.isHidden() && !renderList.contains(fading)) {
                renderList.add(fading);
            }
        }
        renderList.sort(Comparator.comparingInt(this::getModuleWidth).reversed());
        return renderList;
    }

    private float getRenderRow(Module module, long fallbackIndex, float partialTicks) {
        HudAnimation animation = this.animationMap.get(module);
        if (animation == null) {
            return fallbackIndex;
        }
        return animation.getRenderIndex(partialTicks);
    }

    private HudEntry buildHudEntry(List<Module> renderList, Module module, float currentX, float currentY, float totalWidth,
                                   float height, boolean alignLeft, boolean alignTop, long offset) {
        float scaleValue = this.scale.getValue();
        float left = currentX / scaleValue - 1.0F - (alignLeft ? 0.0F : totalWidth);
        float top = currentY / scaleValue - (alignTop ? (offset == 0L ? 1.0F : 0.0F) : (this.shadow.getValue() ? 1.0F : 0.0F));
        float right = currentX / scaleValue + 1.0F + (alignLeft ? totalWidth : 0.0F);
        float bottom = currentY / scaleValue + height + (alignTop ? (this.shadow.getValue() ? 1.0F : 0.0F) : (offset == 0L ? 1.0F : 0.0F));

        int moduleIndex = renderList.indexOf(module);
        float bgWidth = right - left;
        boolean leftTop = false;
        boolean rightTop = false;
        boolean leftBot = false;
        boolean rightBot = false;

        boolean nextWidthSame = false;
        if (moduleIndex < renderList.size() - 1) {
            Module nextModule = renderList.get(moduleIndex + 1);
            float nextWidth = (float) (this.calculateStringWidth(this.getModuleName(nextModule), this.getModuleSuffix(nextModule)) - (this.shadow.getValue() ? 0 : 1)) + 2.0F;
            nextWidthSame = Math.abs(nextWidth - bgWidth) < 1.0F;
        }

        if (moduleIndex == 0) {
            if (renderList.size() == 1) {
                leftTop = true;
                rightTop = true;
                leftBot = true;
                rightBot = true;
            } else if (alignTop) {
                leftTop = true;
                rightTop = true;
                if (!nextWidthSame) {
                    if (alignLeft) rightBot = true;
                    else leftBot = true;
                }
            } else {
                leftBot = true;
                rightBot = true;
                if (!nextWidthSame) {
                    if (alignLeft) rightTop = true;
                    else leftTop = true;
                }
            }
        } else if (moduleIndex == renderList.size() - 1) {
            if (alignTop) {
                leftBot = true;
                rightBot = true;
            } else {
                leftTop = true;
                rightTop = true;
            }
        } else if (alignLeft) {
            if (alignTop) {
                if (!nextWidthSame) rightBot = true;
            } else {
                if (!nextWidthSame) rightTop = true;
            }
        } else {
            if (alignTop) {
                if (!nextWidthSame) leftBot = true;
            } else {
                if (!nextWidthSame) leftTop = true;
            }
        }

        return new HudEntry(left, top, right, bottom, leftTop, rightTop, leftBot, rightBot);
    }

    private void drawHudBackground(HudEntry entry, int color) {
        if (this.round.getValue()) {
            this.drawHudRoundedRect(entry, color, false);
        } else {
            RenderUtil.drawRect(entry.left, entry.top, entry.right, entry.bottom, color);
        }
    }

    private void drawHudMask(HudEntry entry, int color) {
        if (this.round.getValue()) {
            this.drawHudRoundedRect(entry, color, true);
        } else {
            RenderUtil.drawRect(entry.left, entry.top, entry.right, entry.bottom, color);
        }
    }

    private void drawHudRoundedRect(HudEntry entry, int color, boolean mask) {
        float hudScale = this.scale.getValue();
        float radius = 2.0F * hudScale;
        float left = entry.left * hudScale;
        float top = entry.top * hudScale;
        float right = entry.right * hudScale;
        float bottom = entry.bottom * hudScale;

        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0F / hudScale, 1.0F / hudScale, 1.0F);
        if (mask) {
            RenderUtil.drawRoundedRectMaskWithCorners(
                    left, top, right, bottom, color, radius,
                    entry.leftTop, entry.rightTop, entry.leftBot, entry.rightBot
            );
        } else {
            RenderUtil.drawRoundedRectWithCorners(
                    left, top, right, bottom, color, radius,
                    entry.leftTop, entry.rightTop, entry.leftBot, entry.rightBot
            );
        }
        GlStateManager.popMatrix();
    }

    private record HudEntry(float left, float top, float right, float bottom, boolean leftTop, boolean rightTop,
                            boolean leftBot, boolean rightBot) {
    }

    private static class HudAnimation {
        private static final float TICK_MS = 50.0F;
        private final float step;
        private float lastProgress;
        private float currentProgress;
        private float lastIndex;
        private float currentIndex;
        private float targetIndex;
        private boolean indexFrozen;

        private HudAnimation(float progress, float index) {
            this.step = Math.min(1.0F, TICK_MS / ANIMATION_DURATION);
            this.lastProgress = progress;
            this.currentProgress = progress;
            this.lastIndex = index;
            this.currentIndex = index;
            this.targetIndex = index;
            this.indexFrozen = false;
        }

        private void moveTo(float targetIndex, boolean snap) {
            if (this.indexFrozen) {
                return;
            }
            if (snap) {
                this.snapIndex(targetIndex);
            }
            this.targetIndex = targetIndex;
        }

        private void freezeIndex(float targetIndex) {
            if (!this.indexFrozen) {
                this.snapIndex(targetIndex);
                this.indexFrozen = true;
            }
        }

        private void unfreezeIndex() {
            this.indexFrozen = false;
        }

        private void snapIndex(float index) {
            this.lastIndex = index;
            this.currentIndex = index;
            this.targetIndex = index;
        }

        private void tick(boolean forwards) {
            this.lastProgress = this.currentProgress;
            this.currentProgress += forwards ? this.step : -this.step;
            this.currentProgress = Math.clamp(this.currentProgress, 0.0F, 1.0F);

            this.lastIndex = this.currentIndex;
            this.currentIndex += (this.targetIndex - this.currentIndex) * 0.35F;
            if (Math.abs(this.targetIndex - this.currentIndex) < 0.01F) {
                this.currentIndex = this.targetIndex;
            }
        }

        private float getRenderIndex(float partialTicks) {
            return RenderUtil.lerpFloat(this.currentIndex, this.lastIndex, partialTicks);
        }

        private boolean isFinishedOut() {
            return this.currentProgress <= 0.0F && this.lastProgress <= 0.0F;
        }
    }

}
