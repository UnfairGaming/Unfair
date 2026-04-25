package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import unfair.Unfair;
import unfair.enums.BlinkModules;
import unfair.enums.ChatColors;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.Render2DEvent;
import unfair.events.TickEvent;
import unfair.mixin.IAccessorGuiChat;
import unfair.module.Module;
import unfair.property.properties.*;
import unfair.util.ColorUtil;
import unfair.util.RenderUtil;
import unfair.util.Timer;
import unfair.font.impl.UFontRenderer;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float ANIMATION_DURATION = 200.0F;

    private static final float SUFFIX_GAP = 3.0F;
    private static final float BAR_WIDTH = 1.0F;
    private static final float TEXT_Y_OFFSET = -0.5F;

    public final ModeProperty font = new ModeProperty("font", 0, new String[]{"Small", "Medium", "Large"});
    public final ModeProperty colorMode = new ModeProperty(
            "color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123"}
    );
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});

    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
    public final IntProperty bgWidth = new IntProperty("bg-width", 1, 0, 10);
    public final IntProperty bgHeight = new IntProperty("bg-height", 2, 0, 20);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);

    public final PercentProperty background = new PercentProperty("background", 50);
    public final BooleanProperty showBar = new BooleanProperty("bar", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);

    private final Set<Module> fadingOutModules = new HashSet<>();
    private final Map<Module, Timer> animationMap = new HashMap<>();
    private List<Module> activeModules = new ArrayList<>();

    public HUD() {
        super("HUD", true, true);
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
        return (int) this.getExactWidth(this.getModuleName(module), this.getModuleSuffix(module), getFontRenderer());
    }

    private UFontRenderer getFontRenderer() {
        int size = this.font.getValue() == 0 ? 16 : (this.font.getValue() == 1 ? 20 : 24);
        return Unfair.fontManager.getFont((int) (size * this.scale.getValue()));
    }

    private float getExactWidth(String string, String[] arr, UFontRenderer fr) {
        float width = fr.getStringWidth(string);
        if (this.suffixes.getValue() && arr != null) {
            for (String str : arr) {
                width += SUFFIX_GAP + fr.getStringWidth(str);
            }
        }
        return width;
    }

    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }

    public Color getColor(long time, long offset) {
        Color color = Color.white;
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        float cycle = 1.0F - (float) (Math.abs(time - offset * 300L) % speed) / (float) speed;

        switch (this.colorMode.getValue()) {
            case 0: color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F); break;
            case 1: color = ColorUtil.fromHSB(1.0F - (float) (Math.abs(time / 3L) % speed) / (float) speed, 1.0F, 1.0F); break;
            case 2:
                if (cycle % 1.0F < 0.5F) cycle = 1.0F - cycle % 1.0F;
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F); break;
            case 3: color = new Color(this.custom1.getValue()); break;
            case 4:
                float interpolateFactor1 = (float) (2.0 * Math.abs(cycle - Math.floor(cycle + 0.5)));
                color = ColorUtil.interpolate(interpolateFactor1, new Color(this.custom1.getValue()), new Color(this.custom2.getValue())); break;
            case 5:
                float interpolateFactor2 = (float) (2.0 * Math.abs(cycle - Math.floor(cycle + 0.5)));
                if (interpolateFactor2 <= 0.5F) {
                    color = ColorUtil.interpolate(interpolateFactor2 * 2.0F, new Color(this.custom1.getValue()), new Color(this.custom2.getValue()));
                } else {
                    color = ColorUtil.interpolate((interpolateFactor2 - 0.5F) * 2.0F, new Color(this.custom2.getValue()), new Color(this.custom3.getValue()));
                }
                break;
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(hsb[0], hsb[1] * (this.colorSaturation.getValue().floatValue() / 100.0F), hsb[2] * (this.colorBrightness.getValue().floatValue() / 100.0F));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            List<Module> newActiveModules = Unfair.moduleManager.modules.values().stream()
                    .filter(module -> module.isEnabled() && !module.isHidden())
                    .sorted(Comparator.comparingInt(this::getModuleWidth).reversed())
                    .collect(Collectors.<Module>toList());

            for (Module module : newActiveModules) {
                if (!this.activeModules.contains(module) && !this.animationMap.containsKey(module)) {
                    Timer timer = new Timer(ANIMATION_DURATION);
                    timer.start();
                    this.animationMap.put(module, timer);
                    this.fadingOutModules.remove(module);
                } else if (this.fadingOutModules.remove(module)) {
                    Timer timer = new Timer(ANIMATION_DURATION);
                    timer.start();
                    this.animationMap.put(module, timer);
                }
            }

            for (Module module : this.activeModules) {
                if (!newActiveModules.contains(module)) {
                    if (module.isHidden()) {
                        this.animationMap.remove(module);
                        this.fadingOutModules.remove(module);
                        continue;
                    }
                    Timer existing = this.animationMap.get(module);
                    if (existing == null || existing.cached == 1.0F || this.fadingOutModules.contains(module)) {
                        Timer timer = new Timer(ANIMATION_DURATION);
                        timer.start();
                        this.animationMap.put(module, timer);
                    }
                    this.fadingOutModules.add(module);
                }
            }

            Iterator<Map.Entry<Module, Timer>> it = this.animationMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Module, Timer> entry = it.next();
                Module m = entry.getKey();
                Timer t = entry.getValue();
                if (m == null || t == null) {
                    it.remove();
                    continue;
                }
                if (!m.isEnabled()) {
                    long elapsed = System.currentTimeMillis() - t.last;
                    if (t.cached == 0.0F || elapsed > ANIMATION_DURATION + 100) {
                        it.remove();
                        this.fadingOutModules.remove(m);
                    }
                }
            }
            this.activeModules = newActiveModules;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Unfair.commandManager != null && Unfair.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(2.0F, (float) (mc.currentScreen.height - 14), (float) (mc.currentScreen.width - 2), (float) (mc.currentScreen.height - 2), 1.5F, 0, this.getColor(System.currentTimeMillis()).getRGB());
                RenderUtil.disableRenderState();
            }
        }

        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            UFontRenderer fr = getFontRenderer();

            float fontHeight = (float) fr.getHeight();
            float padX = (float) this.bgWidth.getValue();
            float padY = (float) this.bgHeight.getValue();

            float rowHeight = fontHeight + padY;

            float scaleVal = this.scale.getValue();
            ScaledResolution sr = new ScaledResolution(mc);
            float scaledWidth = sr.getScaledWidth() / scaleVal;
            float scaledHeight = sr.getScaledHeight() / scaleVal;

            boolean alignLeft = this.posX.getValue() == 0;
            boolean alignTop = this.posY.getValue() == 0;

            float startX = alignLeft ? this.offsetX.getValue() : scaledWidth - this.offsetX.getValue();
            float startY = alignTop ? this.offsetY.getValue() : scaledHeight - this.offsetY.getValue();

            GlStateManager.pushMatrix();
            GlStateManager.scale(scaleVal, scaleVal, 1.0F);

            long l = System.currentTimeMillis();
            long offset = 0L;

            List<Module> renderList = new ArrayList<>(this.activeModules);
            for (Module fading : this.fadingOutModules) {
                if (!fading.isHidden() && !renderList.contains(fading)) {
                    renderList.add(fading);
                }
            }

            renderList.sort((m1, m2) -> Float.compare(
                    getExactWidth(getModuleName(m2), getModuleSuffix(m2), fr),
                    getExactWidth(getModuleName(m1), getModuleSuffix(m1), fr)
            ));

            float currentY = startY;

            for (Module module : renderList) {
                String moduleName = this.getModuleName(module);
                String[] moduleSuffix = this.getModuleSuffix(module);
                float textWidth = getExactWidth(moduleName, moduleSuffix, fr);

                float animProgress = 1.0F;
                boolean isFadingOut = !module.isEnabled();
                Timer animTimer = this.animationMap.get(module);
                if (animTimer != null && animTimer.last > 0 && animTimer.cached != 1.0F) {
                    try {
                        animProgress = isFadingOut ? Math.max(0.0F, 1.0F - animTimer.getValueFloat(0.0F, 1.0F, 2)) : animTimer.getValueFloat(0.0F, 1.0F, 2);
                    } catch (Exception ignored) {
                        animProgress = isFadingOut ? 0.0F : 1.0F;
                    }
                }

                if (isFadingOut && animProgress <= 0.01F) continue;

                float barSize = this.showBar.getValue() ? BAR_WIDTH : 0.0F;

                float totalModuleWidth = barSize + padX + textWidth + padX;

                float xSlideAmount = (1.0F - animProgress) * (totalModuleWidth + 5.0F);
                float currentX = startX + (alignLeft ? -xSlideAmount : xSlideAmount);

                float moduleLeft, moduleRight, textRenderX;

                if (alignLeft) {
                    moduleLeft = currentX;
                    moduleRight = currentX + totalModuleWidth;

                    textRenderX = moduleLeft + barSize + padX;
                } else {
                    moduleRight = currentX;
                    moduleLeft = currentX - totalModuleWidth;

                    textRenderX = moduleLeft + padX;
                }

                float itemAdvance = rowHeight * animProgress;
                float drawY = alignTop ? currentY : currentY - rowHeight;

                int color = this.getColor(l, offset).getRGB();
                int animatedColor = color;
                if (animProgress < 1.0F) {
                    int alpha = Math.max(0, Math.min(255, (int) (animProgress * 255.0F)));
                    animatedColor = (color & 0x00FFFFFF) | (alpha << 24);
                }

                RenderUtil.enableRenderState();

                if (this.background.getValue() > 0 && animProgress > 0.02F) {
                    int bgAlpha = (int) (animProgress * this.background.getValue().floatValue() / 100.0F * 255.0F);
                    int bgColor = new Color(0, 0, 0, Math.min(bgAlpha, 255)).getRGB();
                    RenderUtil.drawRect(moduleLeft, drawY, moduleRight, drawY + rowHeight, bgColor);
                }

                if (this.showBar.getValue() && animProgress > 0.02F) {
                    int barAlpha = Math.min(255, (int) (animProgress * 255.0F));
                    int barColor = (color & 0x00FFFFFF) | (barAlpha << 24);
                    if (alignLeft) {
                        RenderUtil.drawRect(moduleLeft, drawY, moduleLeft + BAR_WIDTH, drawY + rowHeight, barColor);
                    } else {
                        RenderUtil.drawRect(moduleRight - BAR_WIDTH, drawY, moduleRight, drawY + rowHeight, barColor);
                    }
                }
                RenderUtil.disableRenderState();

                GlStateManager.disableDepth();
                if (animProgress > 0.05F) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                    float textY = drawY + (padY / 2.0F) + TEXT_Y_OFFSET;
                    float currentTextX = textRenderX;

                    fr.drawString(moduleName, currentTextX, textY, animatedColor, this.shadow.getValue());
                    currentTextX += fr.getStringWidth(moduleName);

                    if (this.suffixes.getValue() && moduleSuffix.length > 0 && animProgress > 0.5F) {
                        int suffixAlpha = Math.min(255, (int) (((animProgress - 0.5F) / 0.5F) * 255.0F));
                        int suffixColor = (ChatColors.GRAY.toAwtColor() & 0x00FFFFFF) | (suffixAlpha << 24);

                        for (String string : moduleSuffix) {
                            currentTextX += SUFFIX_GAP;
                            fr.drawString(string, currentTextX, textY, suffixColor, this.shadow.getValue());
                            currentTextX += fr.getStringWidth(string);
                        }
                    }
                    GlStateManager.disableBlend();
                }

                currentY += alignTop ? itemAdvance : -itemAdvance;
                offset++;
            }

            if (this.blinkTimer.getValue()) {
                BlinkModules blinkingModule = Unfair.blinkManager.getBlinkingModule();
                if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                    long movementPacketSize = Unfair.blinkManager.countMovement();
                    if (movementPacketSize > 0L) {
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                        String bText = String.valueOf(movementPacketSize);
                        fr.drawString(
                                bText,
                                scaledWidth / 2.0F - (float) fr.getStringWidth(bText) / 2.0F,
                                scaledHeight * 0.6F,
                                this.getColor(l, offset).getRGB() & 16777215 | -1090519040,
                                this.shadow.getValue()
                        );
                        GlStateManager.disableBlend();
                    }
                }
            }
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }
}