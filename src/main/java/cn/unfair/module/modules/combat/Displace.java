package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.LeftClickMouseEvent;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.client.KeyBindUtil;
import cn.unfair.util.client.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public class Displace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int ROTATION_PRIORITY = 10;
    private static final long ARROW_LIFETIME_MS = 600L;

    public final ModeProperty mode = new ModeProperty("Mode", 3, new String[]{"Left", "Right", "Random", "Void"});
    public final FloatProperty angle = new FloatProperty("Angle", 90.0F, 5.0F, 180.0F);
    public final FloatProperty cooldown = new FloatProperty("Cooldown", 0.5F, 0.0F, 2.0F);
    public final FloatProperty maxHurtTime = new FloatProperty("MaxHurtTime", 10.0F, 0.0F, 20.0F);
    public final BooleanProperty hideRotations = new BooleanProperty("HideRotations", false);
    public final BooleanProperty onlyKnockbackItems = new BooleanProperty("OnlyKnockbackItems", false);
    public final BooleanProperty overrideAttacks = new BooleanProperty("OverrideAttacks", false);
    public final BooleanProperty playersOnly = new BooleanProperty("PlayersOnly", false);
    public final BooleanProperty renderArrow = new BooleanProperty("RenderArrow", true);
    public final BooleanProperty manual = new BooleanProperty("Manual", true);
    public final BooleanProperty autoClicker = new BooleanProperty("AutoClicker", true);
    public final BooleanProperty killAura = new BooleanProperty("KillAura", true);

    private boolean rotate;
    private long lastAt;
    private boolean holding;
    private int holdTicks;
    private int blinkTicks;
    private int lastSide = -1;
    private int side = -1;
    private EntityLivingBase lastTarget;
    private boolean arrow;
    private long arrowAt;
    private double arrowX, arrowY, arrowZ, dirX, dirZ;
    private boolean attacking;
    private boolean auraDisabledByUs;

    public Displace() {
        super("Displace", false);
    }

    @Override
    public void onEnabled() {
        resetState();
        releaseBlink();
    }

    @Override
    public void onDisabled() {
        resetState();
        releaseBlink();
    }

    private void resetState() {
        rotate = false;
        lastAt = 0L;
        holding = false;
        holdTicks = 0;
        blinkTicks = 0;
        lastSide = -1;
        side = -1;
        lastTarget = null;
        arrow = false;
        attacking = false;
        restoreAuraAttacks();
    }

    private KillAura getKillAura() {
        return (KillAura) Unfair.moduleManager.getModule(KillAura.class);
    }

    private boolean isAuraActive() {
        return killAura.getValue() && getKillAura() != null && getKillAura().isEnabled();
    }

    private void restoreAuraAttacks() {
        if (auraDisabledByUs) {
            auraDisabledByUs = false;
            KillAura killAuraModule = getKillAura();
            if (killAuraModule != null) {
                killAuraModule.attackDisabled = false;
            }
        }
    }

    private void rearm() {
        holdTicks = 3;
    }

    private void startBlink() {
        if (hideRotations.getValue()) {
            blinkTicks = 2;
            Unfair.blinkManager.setBlinkState(true, BlinkModules.DISPLACE);
        }
    }

    private void releaseBlink() {
        Unfair.blinkManager.setBlinkState(false, BlinkModules.DISPLACE);
    }

    private void trigger() {
        rotate = true;
        lastAt = System.currentTimeMillis();
    }

    private void clear() {
        lastAt = 0L;
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (event.isCancelled() || !this.isEnabled()) {
            return;
        }
        if (this.attacking) {
            this.attacking = false;
            if (lastTarget != null && !lastTarget.isDead) {
                mc.objectMouseOver = new MovingObjectPosition(lastTarget);
            }
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (!autoClicker.getValue() && AutoClicker.clicking()) {
            return;
        }
        if (!manual.getValue()) {
            return;
        }
        Entity entity = null;
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            entity = mc.objectMouseOver.entityHit;
        }
        if (entity == null) {
            return;
        }
        if (canDisplace(entity, true, true)) {
            event.setCancelled(true);
            trigger();
        } else if (overrideAttacks.getValue() && canDisplace(entity, false, false)) {
            event.setCancelled(true);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (this.attacking) {
            this.attacking = false;
        }
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            resetState();
            return;
        }
        if (blinkTicks > 0 && --blinkTicks == 0) {
            releaseBlink();
        }
        if (killAura.getValue()) {
            KillAura killAuraModule = getKillAura();
            if (killAuraModule != null && killAuraModule.isEnabled() && KillAura.target != null
                    && canDisplace(KillAura.target.getEntity(), true, true)) {
                trigger();
            }
        }
        if (holdTicks > 0) {
            holding = --holdTicks > 0;
        }
        if (auraDisabledByUs && !holding) {
            restoreAuraAttacks();
        }
        if (!rotate) {
            return;
        }
        rotate = false;
        if (lastTarget == null || lastTarget.isDead) {
            clear();
            return;
        }
        startBlink();
        rearm();
        if (isAuraActive()) {
            auraDisabledByUs = true;
            getKillAura().attackDisabled = true;
        }
        float displacedYaw = mc.thePlayer.rotationYaw + offset();
        event.setRotation(displacedYaw, mc.thePlayer.rotationPitch, ROTATION_PRIORITY);
        event.setPervRotation(displacedYaw, ROTATION_PRIORITY);
        clickAttack();
    }

    @EventTarget(Priority.LOWEST)
    public void onPostUpdate(UpdateEvent event) {
        if (event.getType() == EventType.POST) {
            rotate = false;
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.setEnabled(false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null || !renderArrow.getValue() || !arrow) {
            return;
        }
        long l = System.currentTimeMillis() - arrowAt;
        if (l > ARROW_LIFETIME_MS) {
            arrow = false;
            return;
        }
        double alpha = 1.0D;
        double ease = clamp01((double) l / 100.0D);
        ease = ease * ease * (3.0D - 2.0D * ease);
        alpha *= 0.08D + 0.92D * ease;
        if (l > 500L) {
            ease = clamp01((double) (l - 500L) / 100.0D);
            ease = ease * ease * (3.0D - 2.0D * ease);
            alpha *= 1.0D - 0.92D * ease;
        }
        double travel = clamp01((double) l / 450.0D);
        double dist = 0.8D * (1.0D - (1.0D - travel) * (1.0D - travel) * (1.0D - travel));
        double bob = Math.sin((double) l * 0.015D) * 0.02D * (0.4D + 0.6D * alpha);
        double x = arrowX - mc.getRenderManager().viewerPosX + dirX * dist;
        double y = arrowY - mc.getRenderManager().viewerPosY + bob;
        double z = arrowZ - mc.getRenderManager().viewerPosZ + dirZ * dist;
        float yaw = (float) Math.toDegrees(Math.atan2(-dirZ, dirX));

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glTranslated(x, y, z);
        GL11.glRotatef(yaw, 0.0F, 1.0F, 0.0F);

        GL11.glPushMatrix();
        GL11.glTranslated(0.0384D, -0.0192D, -0.02D);
        color(shadowColor(alpha));
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(-0.2784D * 1.01D, -0.0816D * 1.01D, 0.0D);
        GL11.glVertex3d(0.0D, -0.0288D * 1.01D, 0.0D);
        GL11.glVertex3d(0.0D, 0.0288D * 1.01D, 0.0D);
        GL11.glVertex3d(-0.2784D * 1.01D, 0.0816D * 1.01D, 0.0D);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex3d(0.0D, -0.144D * 1.01D, 0.0D);
        GL11.glVertex3d(0.288D * 1.01D, 0.0D, 0.0D);
        GL11.glVertex3d(0.0D, 0.144D * 1.01D, 0.0D);
        GL11.glEnd();
        GL11.glPopMatrix();

        color(fillColor(alpha));
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(-0.2784D, -0.0816D, 0.0D);
        GL11.glVertex3d(0.0D, -0.0288D, 0.0D);
        GL11.glVertex3d(0.0D, 0.0288D, 0.0D);
        GL11.glVertex3d(-0.2784D, 0.0816D, 0.0D);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex3d(0.0D, -0.144D, 0.0D);
        GL11.glVertex3d(0.288D, 0.0D, 0.0D);
        GL11.glVertex3d(0.0D, 0.144D, 0.0D);
        GL11.glEnd();
        GL11.glLineWidth(2.0F);
        color(outlineColor(alpha));
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(-0.2784D, -0.0816D, 0.0D);
        GL11.glVertex3d(0.0D, -0.0288D, 0.0D);
        GL11.glVertex3d(0.0D, 0.0288D, 0.0D);
        GL11.glVertex3d(-0.2784D, 0.0816D, 0.0D);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(0.0D, -0.144D, 0.0D);
        GL11.glVertex3d(0.288D, 0.0D, 0.0D);
        GL11.glVertex3d(0.0D, 0.144D, 0.0D);
        GL11.glEnd();
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private static void color(int c) {
        GL11.glColor4f((float) (c >> 16 & 0xFF) / 255.0F, (float) (c >> 8 & 0xFF) / 255.0F, (float) (c & 0xFF) / 255.0F, (float) (c >> 24 & 0xFF) / 255.0F);
    }

    private static int shadowColor(double alpha) {
        return (int) (140.0D * alpha) << 24 | 0x05081A;
    }

    private static int fillColor(double alpha) {
        return (int) (225.0D * alpha) << 24 | 0xFFFFFF;
    }

    private static int outlineColor(double alpha) {
        return (int) (255.0D * alpha) << 24 | 0xF6F6F6;
    }

    private static double clamp01(double value) {
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }

    private void clickAttack() {
        KeyBinding attack = mc.gameSettings.keyBindAttack;
        int key = attack.getKeyCode();
        if (key == 0) {
            return;
        }
        KeyBindUtil.setKeyBindState(key, true);
        KeyBindUtil.pressKeyOnce(key);
        KeyBindUtil.setKeyBindState(key, false);
        KeyBindUtil.updateKeyState(key);
        this.attacking = true;
    }

    private boolean canDisplace(Entity entity, boolean checkHurtTime, boolean checkCooldown) {
        if (!(entity instanceof EntityLivingBase)) {
            return false;
        }
        if (checkHurtTime && (double) ((EntityLivingBase) entity).hurtTime > maxHurtTime.getValue()) {
            return false;
        }
        if (playersOnly.getValue() && !(entity instanceof EntityPlayer)) {
            return false;
        }
        int knockback = EnchantmentHelper.getKnockbackModifier(mc.thePlayer);
        if (!mc.thePlayer.isSprinting() && knockback == 0) {
            return false;
        }
        if (onlyKnockbackItems.getValue() && knockback == 0) {
            return false;
        }
        if (checkCooldown && (double) Math.abs(System.currentTimeMillis() - lastAt) < cooldown.getValue() * 1000.0D - 20.0D) {
            return false;
        }
        if (mode.getValue() == 3 && (side = (lastSide = dropSide(entity, lastSide))) == -1) {
            return false;
        }
        lastTarget = (EntityLivingBase) entity;
        return true;
    }

    private float offset() {
        float f = angle.getValue();
        if (side != -1) {
            if (side == 0) {
                f = -f;
            }
            side = -1;
        } else if (mode.getValue() == 0 || mode.getValue() == 2 && MathUtil.nextFloat(0.0F, 1.0F) < 0.5F) {
            f = -f;
        }
        f += MathUtil.nextFloat(-5.0F, 5.0F);
        if (renderArrow.getValue() && lastTarget != null) {
            setArrow(lastTarget, f);
        }
        return f;
    }

    private void setArrow(EntityLivingBase entity, float f) {
        double rad = Math.toRadians(mc.thePlayer.rotationYaw + f);
        dirX = -Math.sin(rad);
        dirZ = Math.cos(rad);
        arrowX = entity.posX + dirX * 0.8D;
        arrowY = entity.posY + 1.2D;
        arrowZ = entity.posZ + dirZ * 0.8D;
        arrowAt = System.currentTimeMillis();
        arrow = true;
    }

    private static int dropSide(Entity entity, int previous) {
        double dx = entity.posX - mc.thePlayer.posX;
        double dz = entity.posZ - mc.thePlayer.posZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001D) {
            return -1;
        }
        dx /= len;
        dz /= len;
        double nx = -dz;
        double nz = dx;
        double y = entity.posY;
        int minusSide = 0;
        int plusSide = 0;
        int behind = 0;
        for (int n : new int[]{1, 2, 3, 4, 6, 8}) {
            int weight = n < 6 ? 2 : 1;
            double bx = entity.posX + dx * (double) n;
            double bz = entity.posZ + dz * (double) n;
            for (int i = -1; i <= 1; i++) {
                double x = bx + nx * (double) i;
                double z = bz + nz * (double) i;
                if (mc.theWorld.rayTraceBlocks(new Vec3(x, y, z), new Vec3(x, y - 6.0D, z), true) == null) {
                    behind += weight;
                }
            }
            double mx = entity.posX - nx * (double) n;
            double mz = entity.posZ - nz * (double) n;
            for (int i = -1; i <= 1; i++) {
                double x = mx + dx * (double) i;
                double z = mz + dz * (double) i;
                if (mc.theWorld.rayTraceBlocks(new Vec3(x, y, z), new Vec3(x, y - 6.0D, z), true) == null) {
                    minusSide += weight;
                }
            }
            double qx = entity.posX + nx * (double) n;
            double qz = entity.posZ + nz * (double) n;
            for (int i = -1; i <= 1; i++) {
                double x = qx + dx * (double) i;
                double z = qz + dz * (double) i;
                if (mc.theWorld.rayTraceBlocks(new Vec3(x, y, z), new Vec3(x, y - 6.0D, z), true) == null) {
                    plusSide += weight;
                }
            }
        }
        if (minusSide == 0 && plusSide == 0) {
            return -1;
        }
        if (behind >= minusSide && behind >= plusSide) {
            return -1;
        }
        if (minusSide == plusSide && previous != -1) {
            return previous;
        }
        return minusSide > plusSide ? 0 : 1;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }
}
