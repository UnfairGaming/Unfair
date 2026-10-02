package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.Priority;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.RenderEntityEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.module.modules.world.BedNuker;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.client.TeamUtil;
import cn.unfair.util.player.ItemUtil;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.rotation.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class LagRange extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty delay = new IntProperty("Delay", 150, 0, 1000);
    public final FloatProperty range = new FloatProperty("Range", 10.0F, 3.0F, 100.0F);
    public final BooleanProperty weaponsOnly = new BooleanProperty("WeaponsOnly", true);
    public final BooleanProperty allowTools = new BooleanProperty("AllowTools", false, this.weaponsOnly::getValue);
    public final ModeProperty esp = new ModeProperty("RenderMode", 3, new String[]{"FakePlayer", "Box", "OnlineBox", "None"});
    public final ModeProperty boxColor = new ModeProperty("BoxColor", 1, new String[]{"Default", "Hud", "Custom"}, () -> this.esp.getValue() == 1 || this.esp.getValue() == 2);
    public final ColorProperty boxCustomColor = new ColorProperty("BoxCustomColor", new Color(0, 0, 0).getRGB(), () -> (this.esp.getValue() == 1 || this.esp.getValue() == 2) && this.boxColor.getValue() == 2);
    public final FloatProperty outlineWidth = new FloatProperty("OutlineWidth", 1.0F, 0.0F, 5.0F, () -> this.esp.getValue() == 2);
    private boolean hasTarget = false;
    private Vec3 lastPosition = null;
    private Vec3 currentPosition = null;

    public LagRange() {
        super("LagRange", false);
    }

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return false;
            } else {
                return !TeamUtil.shouldBlockTarget(entityPlayer);
            }
        } else {
            return false;
        }
    }

    private boolean shouldResetOnPacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            return true;
        } else if (packet instanceof C07PacketPlayerDigging) {
            return ((C07PacketPlayerDigging) packet).getStatus() != Action.RELEASE_USE_ITEM;
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            ItemStack item = ((C08PacketPlayerBlockPlacement) packet).getStack();
            return item == null || !(item.getItem() instanceof ItemSword);
        } else {
            return false;
        }
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.type()) {
                case PRE:
                    Unfair.lagManager.setDelay(0);
                    this.hasTarget = false;
                    BedNuker bedNuker = (BedNuker) Unfair.moduleManager.modules.get(BedNuker.class);
                    if ((!bedNuker.isEnabled() || !bedNuker.isReady())
                            && !mc.playerController.getIsHittingBlock()
                            && (!mc.thePlayer.isUsingItem() || mc.thePlayer.isBlocking())
                            && (
                            !(Boolean) this.weaponsOnly.getValue()
                                    || ItemUtil.hasRawUnbreakingEnchant()
                                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()
                    )) {
                        List<EntityPlayer> players = mc.theWorld
                                .loadedEntityList
                                .stream()
                                .filter(entity -> entity instanceof EntityPlayer)
                                .map(entity -> (EntityPlayer) entity)
                                .filter(this::isValidTarget)
                                .collect(Collectors.toList());
                        if (players.isEmpty()) {
                            return;
                        }
                        double height = mc.thePlayer.getEyeHeight();
                        Vec3 eyePosition = Unfair.lagManager.getLastPosition().addVector(0.0, height, 0.0);
                        Vec3 targetEyePosition = new Vec3(mc.thePlayer.lastTickPosX, mc.thePlayer.lastTickPosY + height, mc.thePlayer.lastTickPosZ);
                        Vec3 playerEyePosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + height, mc.thePlayer.posZ);
                        for (EntityPlayer player : players) {
                            double distance = RotationUtil.distanceToBox(player, playerEyePosition);
                            if (!(distance > (double) this.range.getValue())) {
                                double targetDist = RotationUtil.distanceToBox(player, targetEyePosition);
                                double eyeDist = RotationUtil.distanceToBox(player, eyePosition);
                                if (distance < targetDist || distance < eyeDist) {
                                    Unfair.lagManager.setDelay(this.delay.getValue());
                                    this.hasTarget = true;
                                    return;
                                }
                            }
                        }
                    }
                    break;
                case POST:
                    Vec3 savedPosition = Unfair.lagManager.getLastPosition();
                    if (this.currentPosition == null) {
                        this.lastPosition = savedPosition;
                    } else {
                        this.lastPosition = this.currentPosition;
                    }
                    this.currentPosition = savedPosition;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled()) {
            if (this.shouldResetOnPacket(event.getPacket())) {
                Unfair.lagManager.setDelay(0);
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !this.hasTarget || (this.esp.getValue() != 1 && this.esp.getValue() != 2)) {
            return;
        }

        if (mc.gameSettings.thirdPersonView == 0) {
            return;
        }

        AxisAlignedBB bb = this.getRenderBox(event.partialTicks());
        Color color = this.getBoxColor();
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(bb, color.getRed(), color.getGreen(), color.getBlue());
        if (this.esp.getValue() == 2) {
            RenderUtil.drawBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 255, this.outlineWidth.getValue());
        }
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onRenderEntity(RenderEntityEvent event) {
        if (!this.isEnabled() || this.esp.getValue() != 0 || !this.hasTarget || mc.gameSettings.thirdPersonView == 0) {
            return;
        }

        float partialTicks = mc.timer.renderPartialTicks;
        Vec3 renderPosition = this.getRenderPosition(partialTicks).addVector(
                -mc.getRenderManager().getRenderPosX(),
                -mc.getRenderManager().getRenderPosY(),
                -mc.getRenderManager().getRenderPosZ()
        );

        mc.getRenderManager().doRenderEntity(mc.thePlayer, renderPosition.xCoord, renderPosition.yCoord, renderPosition.zCoord, mc.thePlayer.rotationYawHead, partialTicks, true);
    }

    private Vec3 getRenderPosition(float partialTicks) {
        if (this.currentPosition == null || this.lastPosition == null) {
            return this.currentPosition != null ? this.currentPosition : Unfair.lagManager.getLastPosition();
        }
        return new Vec3(
                RenderUtil.lerpDouble(this.currentPosition.xCoord, this.lastPosition.xCoord, partialTicks),
                RenderUtil.lerpDouble(this.currentPosition.yCoord, this.lastPosition.yCoord, partialTicks),
                RenderUtil.lerpDouble(this.currentPosition.zCoord, this.lastPosition.zCoord, partialTicks)
        );
    }

    private AxisAlignedBB getRenderBox(float partialTicks) {
        Vec3 position = this.getRenderPosition(partialTicks);
        float size = mc.thePlayer.getCollisionBorderSize();
        return new AxisAlignedBB(
                position.xCoord - (double) mc.thePlayer.width / 2.0D,
                position.yCoord,
                position.zCoord - (double) mc.thePlayer.width / 2.0D,
                position.xCoord + (double) mc.thePlayer.width / 2.0D,
                position.yCoord + (double) mc.thePlayer.height,
                position.zCoord + (double) mc.thePlayer.width / 2.0D
        )
                .expand(size, size, size)
                .offset(
                        -mc.getRenderManager().getRenderPosX(),
                        -mc.getRenderManager().getRenderPosY(),
                        -mc.getRenderManager().getRenderPosZ()
                );
    }

    private Color getBoxColor() {
        switch (this.boxColor.getValue()) {
            case 1:
                Unfair.moduleManager.modules.get(HUD.class);
                return HUD.getColor(System.currentTimeMillis());
            case 2:
                return new Color(this.boxCustomColor.getValue());
            default:
                return TeamUtil.getTeamColor(mc.thePlayer, 1.0F);
        }
    }

    @Override
    public void onDisabled() {
        Unfair.lagManager.setDelay(0);
        this.hasTarget = false;
        this.lastPosition = null;
        this.currentPosition = null;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%dms", this.delay.getValue())};
    }
}
