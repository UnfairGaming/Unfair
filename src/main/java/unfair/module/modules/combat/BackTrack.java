package unfair.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.*;
import unfair.mixin.S14PacketEntityAccessor;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.IntProperty;
import unfair.util.PacketUtil;
import unfair.util.TimedPacket;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {
    public BackTrack() {
        super("BackTrack", false);
    }
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final IntProperty minLatency = new IntProperty("MinMS", 50, 10, 1000);
    private final IntProperty maxLatency = new IntProperty("MaxMS", 100, 10, 1000);
    private final FloatProperty minDistance = new FloatProperty("MinDistance", 0.0F, 0.0F, 3.0F);
    private final FloatProperty maxDistance = new FloatProperty("MaxDistance", 6.0F, 0.0F, 10.0F);
    private final IntProperty stopOnTargetHurtTime = new IntProperty("PlayerHurtTime", -1, -1, 10);
    private final IntProperty stopOnSelfHurtTime = new IntProperty("StopOnSelfHurtTime", -1, -1, 10);
    private final BooleanProperty drawRealPosition = new BooleanProperty("DrawRealPosition", true);
    private final Queue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final List<Packet<?>> skipPackets = new ArrayList<>();

    private Vec3 vec3;
    private EntityPlayer target;

    private Vec3 renderVec;

    private int currentLatency = 0;


    @Override
    public void onEnabled() {
        packetQueue.clear();
        skipPackets.clear();
        vec3 = null;
        target = null;
        renderVec = null;
    }
    public static final int color = new Color(255, 255, 255, 200).getRGB();
    public static void drawBox(@NotNull Vec3 pos) {
        GlStateManager.pushMatrix();
        double x = pos.xCoord - mc.getRenderManager().viewerPosX;
        double y = pos.yCoord - mc.getRenderManager().viewerPosY;
        double z = pos.zCoord - mc.getRenderManager().viewerPosZ;
        AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox().expand(0.1D, 0.1, 0.1);
        AxisAlignedBB axis = new AxisAlignedBB(bbox.minX - mc.thePlayer.posX + x, bbox.minY - mc.thePlayer.posY + y, bbox.minZ - mc.thePlayer.posZ + z, bbox.maxX - mc.thePlayer.posX + x, bbox.maxY - mc.thePlayer.posY + y, bbox.maxZ - mc.thePlayer.posZ + z);
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(r, g, b, a);
        drawBoundingBox(axis, r, g, b);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
        GlStateManager.popMatrix();
    }
    public static void drawBoundingBox(AxisAlignedBB abb, float r, float g, float b) {
        drawBoundingBox(abb, r, g, b, 0.25f);
    }
    public static void drawBoundingBox(@NotNull AxisAlignedBB abb, float r, float g, float b, float a) {
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer vb = ts.getWorldRenderer();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        ts.draw();
    }
    @Override
    public void onDisabled() {
        releaseAll();
        renderVec = null;
    }

    @EventTarget
    public void onUpdate(UpdateEvent e) {
        if (!isEnabled() || e.getType() == EventType.POST) return;
        if (vec3 == null || target == null || mc.thePlayer == null) {
            currentLatency = 0;
            return;
        }
        if (vec3.distanceTo(mc.thePlayer.getPositionVector()) < target.getPositionVector().distanceTo(mc.thePlayer.getPositionVector())){
            currentLatency = 0;
        }
        try {
            final double distance = vec3.distanceTo(mc.thePlayer.getPositionVector());
            if (distance > maxDistance.getValue()
                    || distance < minDistance.getValue()
            ) {
                currentLatency = 0;
            }

        } catch (NullPointerException ignored) {
        }
    }

    @EventTarget
    public void onTick(TickEvent e) {
        if (!isEnabled() || e.getType() == EventType.POST) return;
        while (!packetQueue.isEmpty()) {
            try {
                if (packetQueue.element().getCold().getCum(currentLatency)) {
                    Packet<?> packet = packetQueue.remove().getPacket();
                    skipPackets.add(packet);
                    PacketUtil.receivePacket(packet);
                } else {
                    break;
                }
            } catch (NullPointerException ignored) {
            }
        }

        if (packetQueue.isEmpty() && target != null) {
            vec3 = target.getPositionVector();
        }
    }



    @EventTarget
    public void onAttack(AttackEvent e) {
        if (!isEnabled()) return;
        final Vec3 targetPos = e.getTarget().getPositionVector();
        if (e.getTarget() instanceof EntityPlayer) {
            if (target == null || e.getTarget() != target) {
                vec3 = targetPos;
                renderVec = null;
            }
            target = (EntityPlayer) e.getTarget();

            try {
                final double distance = targetPos.distanceTo(mc.thePlayer.getPositionVector());
                if (distance > maxDistance.getValue() || distance < minDistance.getValue())
                    return;
            } catch (NullPointerException ignored) {
            }

            currentLatency = (int) (Math.random() * (maxLatency.getValue() - minLatency.getValue()) + minLatency.getValue());
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (target == null || vec3 == null || target.isDead || !isEnabled()) {
            renderVec = null;
            return;
        }

        if (vec3.distanceTo(target.getPositionVector()) < 0.1) {
            renderVec = null;
            return;
        }

        final net.minecraft.util.Vec3 pos = currentLatency > 0 ? vec3 : target.getPositionVector();

        if (renderVec == null) {
            renderVec = pos;
        } else {
            double smoothFactor = 0.2;
            renderVec = new Vec3(
                    renderVec.xCoord + (pos.xCoord - renderVec.xCoord) * smoothFactor,
                    renderVec.yCoord + (pos.yCoord - renderVec.yCoord) * smoothFactor,
                    renderVec.zCoord + (pos.zCoord - renderVec.zCoord) * smoothFactor
            );
        }

        if (drawRealPosition.getValue()) {
            drawBox(renderVec);
        }
    }

    @EventTarget
    public void onReceivePacket(PacketEvent e) {
        if (!isEnabled() || e.getType() == EventType.SEND) return;
        Packet<?> p = e.getPacket();
        if (skipPackets.contains(p)) {
            skipPackets.remove(p);
            return;
        }

        if (target != null && stopOnTargetHurtTime.getValue() != -1 && target.hurtTime == stopOnTargetHurtTime.getValue()) {
            releaseAll();
            return;
        }
        if (stopOnSelfHurtTime.getValue() != -1 && mc.thePlayer.hurtTime == stopOnSelfHurtTime.getValue()) {
            releaseAll();
            return;
        }

        try {
            if (mc.thePlayer == null || mc.thePlayer.ticksExisted < 20) {
                packetQueue.clear();
                return;
            }

            if (target == null) {
                releaseAll();
                return;
            }

            if (e.isCancelled())
                return;

            if (p instanceof S19PacketEntityStatus
                    || p instanceof S02PacketChat
                    || p instanceof S0BPacketAnimation
                    || p instanceof S06PacketUpdateHealth
            )
                return;

            if (p instanceof S08PacketPlayerPosLook || p instanceof S40PacketDisconnect) {
                releaseAll();
                target = null;
                vec3 = null;
                renderVec = null;
                return;

            } else if (p instanceof S13PacketDestroyEntities) {
                S13PacketDestroyEntities wrapper = (S13PacketDestroyEntities) p;
                for (int id : wrapper.getEntityIDs()) {
                    if (id == target.getEntityId()) {
                        target = null;
                        vec3 = null;
                        renderVec = null;
                        releaseAll();
                        return;
                    }
                }
            } else if (p instanceof S14PacketEntity) {
                S14PacketEntity wrapper = (S14PacketEntity) p;
                if (((S14PacketEntityAccessor) wrapper).getEntityId() == target.getEntityId()) {
                    vec3 = vec3.add(new Vec3(wrapper.func_149062_c() / 32.0D, wrapper.func_149061_d() / 32.0D,
                            wrapper.func_149064_e() / 32.0D));
                }
            } else if (p instanceof S18PacketEntityTeleport) {
                S18PacketEntityTeleport wrapper = (S18PacketEntityTeleport) p;
                if (wrapper.getEntityId() == target.getEntityId()) {
                    vec3 = new Vec3(wrapper.getX() / 32.0D, wrapper.getY() / 32.0D, wrapper.getZ() / 32.0D);
                }
            }

            packetQueue.add(new TimedPacket(p));
            e.setCancelled(true);
        } catch (NullPointerException ignored) {

        }
    }

    private void releaseAll() {
        if (!packetQueue.isEmpty()) {
            for (TimedPacket timedPacket : packetQueue) {
                Packet<?> packet = timedPacket.getPacket();
                skipPackets.add(packet);
                PacketUtil.receivePacket(packet);
            }
            packetQueue.clear();
        }
    }

}