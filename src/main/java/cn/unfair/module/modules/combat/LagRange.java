package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.Priority;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.module.modules.world.BedNuker;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.*;
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
    public final BooleanProperty weaponsOnly = new BooleanProperty("Weapons Only", true);
    public final BooleanProperty allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);
    public final ModeProperty showPosition = new ModeProperty("Show Position", 0, new String[]{"None", "Default", "Hud"});
    private int tickIndex = -1;
    private long delayCounter = 0L;
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
                            this.tickIndex = -1;
                        } else {
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
                                        if (this.tickIndex < 0) {
                                            this.tickIndex = 0;
                                            for (this.delayCounter = this.delayCounter + (long) this.delay.getValue();
                                                 this.delayCounter > 0L;
                                                 this.delayCounter = this.delayCounter - 50
                                            ) {
                                                this.tickIndex++;
                                            }
                                        }
                                        Unfair.lagManager.setDelay(this.tickIndex);
                                        this.hasTarget = true;
                                        return;
                                    }
                                }
                            }
                        }
                    } else {
                        this.tickIndex = -1;
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
                this.tickIndex = -1;
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            if (this.showPosition.getValue() != 0
                    && mc.gameSettings.thirdPersonView != 0
                    && this.hasTarget
                    && this.lastPosition != null
                    && this.currentPosition != null) {
                Color color = new Color(-1);
                switch (this.showPosition.getValue()) {
                    case 1:
                        color = TeamUtil.getTeamColor(mc.thePlayer, 1.0F);
                        break;
                    case 2:
                        Unfair.moduleManager.modules.get(HUD.class);
                        color = HUD.getColor(System.currentTimeMillis());
                }
                double x = RenderUtil.lerpDouble(this.currentPosition.xCoord, this.lastPosition.xCoord, event.partialTicks());
                double y = RenderUtil.lerpDouble(this.currentPosition.yCoord, this.lastPosition.yCoord, event.partialTicks());
                double z = RenderUtil.lerpDouble(this.currentPosition.zCoord, this.lastPosition.zCoord, event.partialTicks());
                float size = mc.thePlayer.getCollisionBorderSize();
                AxisAlignedBB aabb = new AxisAlignedBB(
                        x - (double) mc.thePlayer.width / 2.0,
                        y,
                        z - (double) mc.thePlayer.width / 2.0,
                        x + (double) mc.thePlayer.width / 2.0,
                        y + (double) mc.thePlayer.height,
                        z + (double) mc.thePlayer.width / 2.0
                )
                        .expand(size, size, size)
                        .offset(
                                -mc.getRenderManager().getRenderPosX(),
                                -mc.getRenderManager().getRenderPosY(),
                                -mc.getRenderManager().getRenderPosZ()
                        );
                RenderUtil.enableRenderState();
                RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
                RenderUtil.disableRenderState();
            }
        }
    }

    @Override
    public void onEnabled() {
        BackTrack backTrack = (BackTrack) Unfair.moduleManager.modules.get(BackTrack.class);
        if (backTrack.isEnabled()) {
            this.setEnabled(false);
            ChatUtil.dbg("Disabled LagRange because BackTrack is Enabled");
        }
    }

    @Override
    public void onDisabled() {
        Unfair.lagManager.setDelay(0);
        this.tickIndex = -1;
        this.delayCounter = 0L;
        this.hasTarget = false;
        this.lastPosition = null;
        this.currentPosition = null;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%dms", this.delay.getValue())};
    }
}
