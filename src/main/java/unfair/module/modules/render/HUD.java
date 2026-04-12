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

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float ANIMATION_DURATION = 200.0F;
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
        return this.calculateStringWidth(
                this.getModuleName(module), this.getModuleSuffix(module)
        );
    }

    private int calculateStringWidth(String string, String[] arr) {
        int width = mc.fontRendererObj.getStringWidth(string);
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + mc.fontRendererObj.getStringWidth(str);
            }
        }
        return width;
    }

    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }

    public Color getColor(long time, long offset) {
        Color color = Color.white;
        switch (this.colorMode.getValue()) {
            case 0:
                color = ColorUtil.fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = this.getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(this.custom1.getValue());
                break;
            case 4:
                double cycle1 = this.getColorCycle(time, offset);
                color = ColorUtil.interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(this.custom1.getValue()),
                        new Color(this.custom2.getValue())
                );
                break;
            case 5:
                double cycle2 = this.getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = ColorUtil.interpolate(floor * 2.0F, new Color(this.custom1.getValue()), new Color(this.custom2.getValue()));
                } else {
                    color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(this.custom2.getValue()), new Color(this.custom3.getValue()));
                }
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (this.colorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (this.colorBrightness.getValue().floatValue() / 100.0F)
        );
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
                    // If the module was hidden (not disabled), remove immediately — no fade-out animation
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
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        this.getColor(System.currentTimeMillis()).getRGB()
                );
                RenderUtil.disableRenderState();
            }
        }
        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            float height = (float) mc.fontRendererObj.FONT_HEIGHT - 1.0F;
            float x = (float) this.offsetX.getValue()
                    + (1.0F + (this.showBar.getValue() ? (this.shadow.getValue() ? 2.0F : 1.0F) : 0.0F)) * this.scale.getValue();
            float y = (float) this.offsetY.getValue() + 1.0F * this.scale.getValue();
            if (this.posX.getValue() == 1) {
                x = (float) new ScaledResolution(mc).getScaledWidth() - x;
            }
            if (this.posY.getValue() == 1) {
                y = (float) new ScaledResolution(mc).getScaledHeight() - y - height * this.scale.getValue();
            }
            GlStateManager.pushMatrix();
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);
            long l = System.currentTimeMillis();
            long offset = 0L;

            List<Module> renderList = new ArrayList<>(this.activeModules);
            for (Module fading : this.fadingOutModules) {
                // Don't render hidden or fully faded-out modules
                if (!fading.isHidden() && !renderList.contains(fading)) {
                    renderList.add(fading);
                }
            }

            renderList.sort(Comparator.comparingInt(this::getModuleWidth).reversed());

            for (Module module : renderList) {
                String moduleName = this.getModuleName(module);
                String[] moduleSuffix = this.getModuleSuffix(module);
                float totalWidth = (float) (this.calculateStringWidth(moduleName, moduleSuffix) - (this.shadow.getValue() ? 0 : 1));
                int color = this.getColor(l, offset).getRGB();

                float animProgress = 1.0F;
                boolean isFadingOut = !module.isEnabled();
                Timer animTimer = this.animationMap.get(module);
                if (animTimer != null && animTimer.last > 0 && animTimer.cached != 1.0F) {
                    try {
                        if (isFadingOut) {

                            animProgress = Math.max(0.0F, 1.0F - animTimer.getValueFloat(0.0F, 1.0F, 2));
                        } else {

                            animProgress = animTimer.getValueFloat(0.0F, 1.0F, 2);
                        }
                    } catch (Exception ignored) {
                        animProgress = isFadingOut ? 0.0F : 1.0F;
                    }
                }

                if (isFadingOut && animProgress <= 0.01F) {
                    continue;
                }

                boolean alignLeft = this.posX.getValue() == 0;
                boolean alignTop = this.posY.getValue() == 0;

                float xSlideDir = alignLeft ? -1.0F : 1.0F;
                float xSlideAmount = (1.0F - animProgress) * totalWidth * xSlideDir;

                float ySlideDir = alignTop ? 1.0F : -1.0F;
                float ySlideAmount = (1.0F - animProgress) * (height + 2.0F) * ySlideDir;

                float currentX = x + xSlideAmount;
                float currentY = y + ySlideAmount;

                int animatedColor = color;
                if (animProgress < 1.0F) {
                    int alpha = Math.max(0, Math.min(255, (int) (animProgress * 255.0F)));
                    animatedColor = (color & 0x00FFFFFF) | (alpha << 24);
                } else {
                    animatedColor = color;
                }

                RenderUtil.enableRenderState();
                if (this.background.getValue() > 0 && animProgress > 0.02F) {
                    int bgAlpha = (int) (animProgress * this.background.getValue().floatValue() / 100.0F * 255.0F);
                    bgAlpha = Math.min(bgAlpha, 255);
                    RenderUtil.drawRect(
                            currentX / this.scale.getValue() - 1.0F - (alignLeft ? 0.0F : totalWidth),
                            currentY / this.scale.getValue() - (alignTop ? (offset == 0L ? 1.0F : 0.0F) : (this.shadow.getValue() ? 1.0F : 0.0F)),
                            currentX / this.scale.getValue() + 1.0F + (alignLeft ? totalWidth : 0.0F),
                            currentY / this.scale.getValue() + height + (alignTop ? (this.shadow.getValue() ? 1.0F : 0.0F) : (offset == 0L ? 1.0F : 0.0F)),
                            new Color(0.0F, 0.0F, 0.0F, bgAlpha / 255.0F).getRGB()
                    );
                }
                if (this.showBar.getValue() && animProgress > 0.02F) {
                    int barAlpha = (int) (animProgress * 255.0F);
                    barAlpha = Math.min(barAlpha, 255);
                    int barColor = (color & 0x00FFFFFF) | (barAlpha << 24);
                    RenderUtil.drawRect(
                            currentX / this.scale.getValue() + (alignLeft ? -3.0F : 1.0F),
                            currentY / this.scale.getValue() - (alignTop ? (offset == 0L ? 1.0F : 0.0F) : 1.0F),
                            currentX / this.scale.getValue() + (alignLeft ? -2.0F : 2.0F),
                            currentY / this.scale.getValue() + height + (alignTop ? 1.0F : (offset == 0L ? 1.0F : 0.0F)),
                            barColor
                    );
                }
                RenderUtil.disableRenderState();
                GlStateManager.disableDepth();
                if (animProgress > 0.05F) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    if (this.shadow.getValue()) {
                        mc.fontRendererObj
                                .drawStringWithShadow(moduleName, currentX / this.scale.getValue() - (alignLeft ? 0.0F : totalWidth), currentY / this.scale.getValue(), animatedColor);
                    } else {
                        mc.fontRendererObj
                                .drawString(
                                        moduleName,
                                        currentX / this.scale.getValue() - (alignLeft ? 0.0F : totalWidth),
                                        currentY / this.scale.getValue() + (alignTop ? 0.0F : 1.0F),
                                        animatedColor,
                                        false
                                );
                    }
                    if (this.suffixes.getValue() && moduleSuffix.length > 0 && animProgress > 0.5F) {
                        float width = (float) mc.fontRendererObj.getStringWidth(moduleName) + 3.0F;
                        int suffixAlpha = (int) (((animProgress - 0.5F) / 0.5F) * 255.0F);
                        suffixAlpha = Math.min(suffixAlpha, 255);
                        int suffixColor = ChatColors.GRAY.toAwtColor() & 0x00FFFFFF | (suffixAlpha << 24);
                        for (String string : moduleSuffix) {
                            if (this.shadow.getValue()) {
                                mc.fontRendererObj
                                        .drawStringWithShadow(
                                                string,
                                                currentX / this.scale.getValue() - (alignLeft ? 0.0F : totalWidth) + width,
                                                currentY / this.scale.getValue(),
                                                suffixColor
                                        );
                            } else {
                                mc.fontRendererObj
                                        .drawString(
                                                string,
                                                currentX / this.scale.getValue() - (alignLeft ? 0.0F : totalWidth) + width,
                                                currentY / this.scale.getValue() + (alignTop ? 0.0F : 1.0F),
                                                suffixColor,
                                                false
                                        );
                            }
                            width += (float) mc.fontRendererObj.getStringWidth(string) + (this.shadow.getValue() ? 3.0F : 2.0F);
                        }
                    }
                    GlStateManager.disableBlend();
                }
                y += (height + (this.shadow.getValue() ? 1.0F : 0.0F)) * this.scale.getValue() * (alignTop ? 1.0F : -1.0F);
                offset++;
            }
            if (this.blinkTimer.getValue()) {
                BlinkModules blinkingModule = Unfair.blinkManager.getBlinkingModule();
                if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                    long movementPacketSize = Unfair.blinkManager.countMovement();
                    if (movementPacketSize > 0L) {
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        mc.fontRendererObj
                                .drawString(
                                        String.valueOf(movementPacketSize),
                                        (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / this.scale.getValue()
                                                - (float) mc.fontRendererObj.getStringWidth(String.valueOf(movementPacketSize)) / 2.0F,
                                        (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 3.0F / this.scale.getValue(),
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