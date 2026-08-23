package cn.unfair.module.modules.world;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.util.rotation.RayCastUtil;
import cn.unfair.util.rotation.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ContainerBrewingStand;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.List;

import static cn.unfair.management.BlinkManager.mc;

public class ChestAura extends Module {

    public final FloatProperty range = new FloatProperty("Range", 3.0F, 1.0F, 7.0F);
    public final FloatProperty openDelay = new FloatProperty("Open Delay", 100.0F, 0.0F, 600.0F);
    public final BooleanProperty interactOnce = new BooleanProperty("Interact Once", true);
    public final BooleanProperty inventoryCheck = new BooleanProperty("Inventory Check", true);
    private final List<BlockPos> openedList = new ArrayList<>();
    private BlockPos targetPos;
    private boolean waitingOpen;
    private long lastOpenTime;

    public ChestAura() {
        super("ChestAura", false);
    }

    private boolean isContainerOpen() {
        return mc.thePlayer.openContainer instanceof ContainerChest
                || mc.thePlayer.openContainer instanceof ContainerFurnace
                || mc.thePlayer.openContainer instanceof ContainerBrewingStand;
    }

    private boolean isContainerBlock(Block block) {
        return block instanceof BlockChest;
    }

    @Override
    public void onDisabled() {
        this.targetPos = null;
        this.waitingOpen = false;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.openedList.clear();
        this.targetPos = null;
        this.waitingOpen = false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;

        if (this.isInventoryBlocked()) {
            this.targetPos = null;
            this.waitingOpen = false;
            return;
        }

        ChestStealer stealer = (ChestStealer) Unfair.moduleManager.getModule(ChestStealer.class);
        if (!stealer.isEnabled()) return;

        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        Scaffold scaffold = (Scaffold) Unfair.moduleManager.getModule(Scaffold.class);

        if (event.getType() == EventType.PRE) {
            if ((killAura.isEnabled() && killAura.getTarget() != null)
                    || this.isContainerOpen()
                    || scaffold.isEnabled()) {
                this.targetPos = null;
                return;
            }
            if (this.waitingOpen) {
                if (System.currentTimeMillis() - this.lastOpenTime >= this.openDelay.getValue().longValue()) {
                    this.waitingOpen = false;
                }
                return;
            }
            this.targetPos = null;
            float radius = this.range.getValue();
            for (float y = radius; y >= -radius; y--) {
                for (float x = -radius; x <= radius; x++) {
                    for (float z = -radius; z <= radius; z++) {
                        BlockPos pos = new BlockPos(
                                mc.thePlayer.posX - 0.5 + x,
                                mc.thePlayer.posY - 0.5 + y,
                                mc.thePlayer.posZ - 0.5 + z
                        );
                        Block block = mc.theWorld.getBlockState(pos).getBlock();
                        if (!this.isContainerBlock(block) || this.openedList.contains(pos)) {
                            continue;
                        }

                        float[] rotations = RotationUtil.getRotations(pos);
                        RotationUtil.RotationVec rotVec = new RotationUtil.RotationVec(rotations[0], rotations[1]);

                        if (RayCastUtil.overBlock(rotVec, EnumFacing.UP, pos, false)) {
                            event.setRotation(rotations[0], rotations[1], 2);
                            event.setPervRotation(rotations[0], 2);
                            this.targetPos = pos;
                            return;
                        }
                    }
                }
            }
        }
        if (event.getType() == EventType.POST) {
            if ((killAura.isEnabled() && killAura.getTarget() != null)
                    || scaffold.isEnabled()) {
                return;
            }
            if (this.waitingOpen && this.targetPos != null) {
                if (this.isContainerOpen()) {
                    this.openedList.add(this.targetPos);
                    this.targetPos = null;
                    this.waitingOpen = false;
                }
                return;
            }
            if (this.targetPos == null || this.isContainerOpen() || this.openedList.size() >= 50 || this.openedList.contains(this.targetPos)) {
                return;
            }
            float[] rotations = RotationUtil.getRotations(this.targetPos);
            RotationUtil.RotationVec rotVec = new RotationUtil.RotationVec(rotations[0], rotations[1]);

            if (RayCastUtil.overBlock(rotVec, EnumFacing.UP, this.targetPos, false)) {
                C08PacketPlayerBlockPlacement packet = new C08PacketPlayerBlockPlacement(
                        this.targetPos,
                        this.targetPos.getY() + 0.5 < mc.thePlayer.posY + 1.7 ? 1 : 0,
                        mc.thePlayer.getCurrentEquippedItem(),
                        0.0F, 0.0F, 0.0F
                );
                mc.thePlayer.sendQueue.addToSendQueue(packet);

                this.lastOpenTime = System.currentTimeMillis();
                this.waitingOpen = true;

                if (this.interactOnce.getValue()) {
                    this.openedList.add(this.targetPos);
                }
            }
        }
    }

    private boolean isInventoryBlocked() {
        return this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer;
    }
}
