package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.events.RenderItemEvent;
import cn.unfair.events.SwingAnimationEvent;
import cn.unfair.Unfair;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemMap;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

public class Animations extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double PI2 = Math.PI * 2.0D;
    private static final float HALF_TURN = 180.0F;

    public final ModeProperty blockAnimation = new ModeProperty("Block Animation", 0, new String[]{
            "None", "1.7", "Sunny", "Lucid", "Astro", "Smooth", "Spin", "Leaked", "Old",
            "Exhibition", "Exhibition Old", "Exhibition New", "Swong", "Stella", "Flup", "Noov",
            "Komorebi", "Rhys", "Swing", "?", "Stab", "Beta", "Dortware", "Avatar", "Tap"
    });
    public final ModeProperty swingAnimation = new ModeProperty("Swing Animation", 0, new String[]{"None", "Punch", "Shove", "Smooth", "1.9+"});
    public final BooleanProperty onlyWhenBlocking = new BooleanProperty("Update Position Only When Blocking", true);
    public final FloatProperty swingSpeed = new FloatProperty("Swing Speed", 1.0F, -200.0F, 50.0F);
    public final FloatProperty x = new FloatProperty("X", 0.0F, -2.0F, 2.0F);
    public final FloatProperty y = new FloatProperty("Y", 0.0F, -2.0F, 2.0F);
    public final FloatProperty z = new FloatProperty("Z", 0.0F, -2.0F, 2.0F);
    public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.1F, 2.0F);
    public final BooleanProperty alwaysShow = new BooleanProperty("Always Show", false);
    public final BooleanProperty oldBlockHit = new BooleanProperty("1.7 Blockhit", true);
    public final BooleanProperty oldRod = new BooleanProperty("1.7 Rod", true);
    public final BooleanProperty oldBow = new BooleanProperty("1.7 Bow", true);
    public final BooleanProperty oldDamage = new BooleanProperty("1.7 Damage", true);
    public final BooleanProperty oldHearts = new BooleanProperty("1.7 Hearts", true);
    public final BooleanProperty oldSneak = new BooleanProperty("1.7 Sneak", true);
    public final BooleanProperty oldBlockBreak = new BooleanProperty("1.7 Blockbreak", true);
    public final BooleanProperty oldDebug = new BooleanProperty("1.7 Debug Menu", true);
    public final BooleanProperty oldEat = new BooleanProperty("1.7 Eat", true);
    public final BooleanProperty oldPlayerList = new BooleanProperty("1.7 Playerlist", false);

    public Animations() {
        super("Animations", false, true);
    }

    private static Animations instance() {
        if (Unfair.moduleManager == null) return null;
        return (Animations) Unfair.moduleManager.getModule(Animations.class);
    }

    public static boolean legacyEnabled(BooleanProperty property) {
        Animations animations = instance();
        return animations != null && animations.isEnabled() && property.getValue();
    }

    public static boolean oldRodEnabled() { Animations a = instance(); return a != null && legacyEnabled(a.oldRod); }
    public static boolean oldBowEnabled() { Animations a = instance(); return a != null && legacyEnabled(a.oldBow); }
    public static boolean oldDamageEnabled() { Animations a = instance(); return a != null && legacyEnabled(a.oldDamage); }
    public static boolean oldHeartsEnabled() { Animations a = instance(); return a != null && legacyEnabled(a.oldHearts); }
    public static boolean oldSneakEnabled() { Animations a = instance(); return a != null && legacyEnabled(a.oldSneak); }
    public static boolean oldBlockBreakEnabled() { Animations a = instance(); return a != null && legacyEnabled(a.oldBlockBreak); }
    public static boolean oldDebugEnabled() { Animations a = instance(); return a != null && legacyEnabled(a.oldDebug); }
    public static boolean oldPlayerListEnabled() { Animations a = instance(); return a != null && legacyEnabled(a.oldPlayerList); }

    public static void performLegacyBlockBreak() {
        Animations animations = instance();
        if (animations == null || !animations.isEnabled() || !animations.oldBlockBreak.getValue()
                || mc.thePlayer == null || mc.objectMouseOver == null
                || mc.thePlayer.getItemInUseCount() == 0
                || !mc.gameSettings.keyBindAttack.isKeyDown()
                || !mc.gameSettings.keyBindUseItem.isKeyDown()
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        mc.thePlayer.swingItem();
    }

    @EventTarget
    public void onRenderItem(RenderItemEvent event) {
        if (!this.isEnabled() || event.getItemToRender() == null || event.getItemToRender().getItem() instanceof ItemMap) {
            return;
        }

        if (!onlyWhenBlocking.getValue()) {
            GlStateManager.translate(x.getValue(), y.getValue(), z.getValue());
        }

        double scaleValue = scale.getValue();
        EnumAction itemAction = event.getEnumAction();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        ItemRenderer accessor = itemRenderer;
        float animationProgression = alwaysShow.getValue() && event.isUseItem() ? 0.0F : event.getAnimationProgression();
        float swingProgress = event.getSwingProgress();
        if (event.isUseItem() && oldBlockHit.getValue() && itemAction == EnumAction.BLOCK) {
            swingProgress = event.getSwingProgress();
        } else if (event.isUseItem() && itemAction == EnumAction.BLOCK) {
            swingProgress = 0.0F;
        }
        float convertedProgress = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) PI2);

        if (event.isUseItem() && itemAction == EnumAction.BLOCK) {
            if (onlyWhenBlocking.getValue()) {
                GlStateManager.translate(x.getValue(), y.getValue(), z.getValue());
            }
            renderBlockAnimation(accessor, animationProgression, swingProgress, convertedProgress, scaleValue);
            event.setCancelled(true);
        } else if (event.isUseItem() && (itemAction == EnumAction.EAT || itemAction == EnumAction.DRINK)
                && oldEat.getValue()) {
            itemRenderer.performDrinking(mc.thePlayer, event.getPartialTicks());
            itemRenderer.transformFirstPersonItem(animationProgression,
                    oldBlockHit.getValue() ? swingProgress : 0.0F);
            itemRenderer.transformFirstPersonItemEat(animationProgression, 0.0F);
            event.setCancelled(true);
        } else if (!event.isUseItem()) {
            renderSwingAnimation(accessor, animationProgression, swingProgress, scaleValue);
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSwingAnimation(SwingAnimationEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        int animationEnd = event.getAnimationEnd();
        animationEnd *= (-swingSpeed.getValue() / 100.0F) + 1.0F;
        event.setAnimationEnd(Math.max(1, animationEnd));
    }

    private void renderBlockAnimation(ItemRenderer itemRenderer, float animationProgression, float swingProgress, float convertedProgress, double scaleValue) {
        switch (blockAnimation.getModeString()) {
            case "None":
                itemRenderer.transformFirstPersonItem(animationProgression, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                itemRenderer.doBlockTransformations();
                break;
            case "1.7":
                this.transformCheatBreakerFirstPersonItem(animationProgression, swingProgress);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                itemRenderer.doBlockTransformations();
                break;
            case "Sunny":
                scaleValue = 0.99D;
                GlStateManager.translate(0.05F, -0.05F, -0.12F);
                itemRenderer.transformFirstPersonItem(animationProgression + 0.15F, swingProgress);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                itemRenderer.doBlockTransformations();
                GlStateManager.translate(-0.5F, 0.2F, 0.0F);
                break;
            case "Lucid":
                itemRenderer.transformFirstPersonItem(animationProgression - 0.1F, swingProgress);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                itemRenderer.doBlockTransformations();
                break;
            case "Astro":
                GlStateManager.translate(0.0F, 0.03F, -0.05F);
                itemRenderer.transformFirstPersonItem(animationProgression / 2.0F, swingProgress);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GlStateManager.rotate(convertedProgress * 30.0F / 2.0F, -convertedProgress, -0.0F, 9.0F);
                GlStateManager.rotate(convertedProgress * 40.0F, 1.0F, -convertedProgress / 2.0F, -0.0F);
                itemRenderer.doBlockTransformations();
                break;
            case "Tap":
                GL11.glTranslatef(0.0F, 0.3F, 0.0F);
                float tapSmooth = swingProgress * 0.8F - swingProgress * swingProgress * 0.8F;
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(tapSmooth * -90.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.scale(0.37F, 0.37F, 0.37F);
                itemRenderer.doBlockTransformations();
                break;
            case "Beta":
                GL11.glTranslatef(0.0F, 0.3F, 0.0F);
                float beta = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
                itemRenderer.transformFirstPersonItem(itemRenderer.getEquippedProgress() * 0.5F, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GlStateManager.rotate(-beta * 55.0F / 2.0F, -8.0F, -0.0F, 9.0F);
                GlStateManager.rotate(-beta * 45.0F, 1.0F, beta / 2.0F, -0.0F);
                itemRenderer.doBlockTransformations();
                GL11.glTranslated(1.2D, 0.3D, 0.5D);
                GL11.glTranslatef(-1.0F, mc.thePlayer.isSneaking() ? -0.1F : -0.2F, 0.2F);
                break;
            case "Avatar":
                GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                float avatarF = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
                float avatarF1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
                GlStateManager.rotate(avatarF * -20.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(avatarF1 * -20.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotate(avatarF1 * -40.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(0.4F, 0.4F, 0.4F);
                itemRenderer.doBlockTransformations();
                break;
            case "Smooth":
                itemRenderer.transformFirstPersonItem(animationProgression, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                float smoothY = -convertedProgress * 2.0F;
                GlStateManager.translate(0.0F, smoothY / 10.0F + 0.1F, 0.0F);
                GlStateManager.rotate(smoothY * 10.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(250.0F, 0.2F, 1.0F, -0.6F);
                GlStateManager.rotate(-10.0F, 1.0F, 0.5F, 1.0F);
                GlStateManager.rotate(-smoothY * 20.0F, 1.0F, 0.5F, 1.0F);
                break;
            case "Stab":
                float spin = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) PI2);
                GlStateManager.translate(0.6F, 0.3F, -0.6F + -spin * 0.7F);
                GlStateManager.rotate(6090.0F, 0.0F, 0.0F, 0.1F);
                GlStateManager.rotate(6085.0F, 0.0F, 0.1F, 0.0F);
                GlStateManager.rotate(6110.0F, 0.1F, 0.0F, 0.0F);
                itemRenderer.transformFirstPersonItem(0.0F, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                itemRenderer.doBlockTransformations();
                break;
            case "Spin":
                itemRenderer.transformFirstPersonItem(animationProgression, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GlStateManager.translate(0.0F, 0.2F, -1.0F);
                GlStateManager.rotate(-59.0F, -1.0F, 0.0F, 3.0F);
                GlStateManager.rotate(-((float) System.currentTimeMillis() / 2L % 360L), 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
                break;
            case "Leaked":
                GlStateManager.translate(0.0F, -0.03F, -0.13F);
                itemRenderer.transformFirstPersonItem(animationProgression / 3.0F, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GlStateManager.translate(0.0F, 0.1F, 0.0F);
                itemRenderer.doBlockTransformations();
                GlStateManager.rotate(convertedProgress * 20.0F / 2.0F, 0.0F, 1.0F, 1.5F);
                GlStateManager.rotate(-convertedProgress * 200.0F / 4.0F, 1.0F, 0.9F, 0.0F);
                break;
            case "Old":
                GlStateManager.translate(0.0F, 0.1F, 0.0F);
                itemRenderer.transformFirstPersonItem(animationProgression / 2.0F - 0.2F, swingProgress);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                itemRenderer.doBlockTransformations();
                break;
            case "Exhibition":
                GlStateManager.translate(0.0F, -0.05F, -0.0F);
                itemRenderer.transformFirstPersonItem(animationProgression / 2.0F, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GlStateManager.translate(0.0F, 0.3F, -0.0F);
                GlStateManager.rotate(-convertedProgress * 31.0F, 1.0F, 0.0F, 2.0F);
                GlStateManager.rotate(-convertedProgress * 33.0F, 1.5F, convertedProgress / 1.1F, 0.0F);
                itemRenderer.doBlockTransformations();
                break;
            case "Exhibition Old":
                GlStateManager.translate(0.0F, -0.05F, 0.0F);
                GlStateManager.translate(-0.04F, 0.13F, 0.0F);
                itemRenderer.transformFirstPersonItem(animationProgression / 2.5F, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GlStateManager.rotate(-convertedProgress * 40.0F / 2.0F, convertedProgress / 2.0F, 1.0F, 4.0F);
                GlStateManager.rotate(-convertedProgress * 30.0F, 1.0F, convertedProgress / 3.0F, -0.0F);
                itemRenderer.doBlockTransformations();
                break;
            case "Exhibition New":
                GlStateManager.translate(0.0F, -0.04F, -0.01F);
                itemRenderer.transformFirstPersonItem(animationProgression / 2.0F, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GlStateManager.translate(0.0F, 0.3F, -0.0F);
                GlStateManager.rotate(-convertedProgress * 30.0F, 1.0F, 0.0F, 2.0F);
                GlStateManager.rotate(-convertedProgress * 44.0F, 1.5F, convertedProgress / 1.2F, 0.0F);
                itemRenderer.doBlockTransformations();
                break;
            case "Swong":
                GlStateManager.translate(0.0F, 0.1F, -0.05F);
                itemRenderer.transformFirstPersonItem(animationProgression / 2.0F, swingProgress);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GlStateManager.rotate(convertedProgress * 30.0F, -convertedProgress, -0.0F, 9.0F);
                GlStateManager.rotate(convertedProgress * 40.0F, 1.0F, -convertedProgress, -0.0F);
                itemRenderer.doBlockTransformations();
                break;
            case "Stella":
                itemRenderer.transformFirstPersonItem(-0.1F, swingProgress);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GlStateManager.translate(-0.5F, 0.4F, -0.2F);
                GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(-70.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(40.0F, 0.0F, 1.0F, 0.0F);
                break;
            case "Flup":
                GlStateManager.translate(0.0F, 0.1F, -0.05F);
                itemRenderer.transformFirstPersonItem(animationProgression, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                itemRenderer.doBlockTransformations();
                GlStateManager.translate(-0.05F, 0.2F, 0.0F);
                GlStateManager.rotate(-convertedProgress * 70.0F / 2.0F, -8.0F, -0.0F, 9.0F);
                GlStateManager.rotate(-convertedProgress * 70.0F, 1.0F, -0.4F, -0.0F);
                break;
            case "Noov":
                itemRenderer.transformFirstPersonItem(animationProgression / 1.5F, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                itemRenderer.doBlockTransformations();
                GlStateManager.translate(-0.05F, 0.3F, 0.3F);
                GlStateManager.rotate(-convertedProgress * 140.0F, 8.0F, 0.0F, 8.0F);
                GlStateManager.rotate(convertedProgress * HALF_TURN, 8.0F, 0.0F, 8.0F);
                break;
            case "Komorebi":
                itemRenderer.transformFirstPersonItem(-0.25F, 1.0F + convertedProgress / 10.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GL11.glRotated(-convertedProgress * 25.0F, 1.0D, 0.0D, 0.0D);
                itemRenderer.doBlockTransformations();
                break;
            case "Rhys":
                GlStateManager.translate(0.41F, -0.25F, -0.5555557F);
                GlStateManager.translate(0.0F, 0.0F, 0.0F);
                GlStateManager.rotate(35.0F, 0.0F, 1.5F, 0.0F);
                float rhys = MathHelper.sin(swingProgress * swingProgress / 64.0F * (float) PI2);
                GlStateManager.rotate(rhys * -5.0F, 0.0F, 0.0F, 0.0F);
                GlStateManager.rotate(convertedProgress * -12.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotate(convertedProgress * -65.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                itemRenderer.doBlockTransformations();
                break;
            case "Swing":
                itemRenderer.transformFirstPersonItem(animationProgression, swingProgress);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                itemRenderer.doBlockTransformations();
                GlStateManager.translate(-0.3F, -0.1F, -0.0F);
                break;
            case "?":
                itemRenderer.transformFirstPersonItem(animationProgression, swingProgress);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                GL11.glTranslatef(-0.35F, 0.1F, 0.0F);
                GL11.glTranslatef(-0.05F, -0.1F, 0.1F);
                itemRenderer.doBlockTransformations();
                break;
            case "Dortware":
                float dort1 = MathHelper.sin((float) (swingProgress * swingProgress * Math.PI - 3.0D));
                float dort = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
                itemRenderer.transformFirstPersonItem(animationProgression, 1.0F);
                GlStateManager.rotate(-dort * 10.0F, 0.0F, 15.0F, 200.0F);
                GlStateManager.rotate(-dort * 10.0F, 300.0F, dort / 2.0F, 1.0F);
                itemRenderer.doBlockTransformations();
                GL11.glTranslated(2.4D, 0.3D, 0.5D);
                GL11.glTranslatef(-2.10F, -0.2F, 0.1F);
                GlStateManager.rotate(dort1 * 13.0F, -10.0F, -1.4F, -10.0F);
                break;
            default:
                itemRenderer.transformFirstPersonItem(animationProgression, 0.0F);
                GlStateManager.scale(scaleValue, scaleValue, scaleValue);
                itemRenderer.doBlockTransformations();
                break;
        }
    }

    private void transformCheatBreakerFirstPersonItem(float equipProgress, float swingProgress) {
        float itemScale = 0.8F;
        GlStateManager.translate(0.7F * itemScale, -0.65F * itemScale - equipProgress * 0.6F, -0.9F * itemScale);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float swingSin = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float swingSqrtSin = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        GlStateManager.rotate(-swingSin * 20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-swingSqrtSin * 20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(-swingSqrtSin * 80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.4F, 0.4F, 0.4F);
    }

    private void renderSwingAnimation(ItemRenderer itemRenderer, float animationProgression, float swingProgress, double scaleValue) {
        switch (swingAnimation.getModeString()) {
            case "None":
                itemRenderer.doItemUsedTransformations(swingProgress);
                itemRenderer.transformFirstPersonItem(animationProgression, swingProgress);
                break;
            case "Punch":
                itemRenderer.transformFirstPersonItem(animationProgression, swingProgress);
                itemRenderer.doItemUsedTransformations(swingProgress);
                break;
            case "1.9+":
                itemRenderer.doItemUsedTransformations(swingProgress);
                itemRenderer.transformFirstPersonItem(animationProgression, swingProgress);
                break;
            case "Shove":
                itemRenderer.transformFirstPersonItem(animationProgression, animationProgression);
                itemRenderer.doItemUsedTransformations(swingProgress);
                break;
            case "Smooth":
                itemRenderer.transformFirstPersonItem(animationProgression, swingProgress);
                itemRenderer.doItemUsedTransformations(animationProgression);
                break;
            default:
                itemRenderer.doItemUsedTransformations(swingProgress);
                itemRenderer.transformFirstPersonItem(animationProgression, swingProgress);
                break;
        }

        if (!onlyWhenBlocking.getValue()) {
            GlStateManager.scale(scaleValue, scaleValue, scaleValue);
        }
    }
}
