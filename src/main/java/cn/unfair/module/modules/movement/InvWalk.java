package cn.unfair.module.modules.movement;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.ui.clickgui.augustus.AugustusClickGui;
import cn.unfair.util.client.KeyBindUtil;
import cn.unfair.util.player.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvWalk extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Vanilla", "Legit", "Hypixel", "Legit+"});
    public final BooleanProperty guiEnabled = new BooleanProperty("Click Gui", true);
    public final IntProperty openDelay = new IntProperty("Open Delay", 0, 0, 20, ()-> this.mode.getValue() == 3);
    public final IntProperty closeDelay = new IntProperty("Close Delay", 2, 0, 20, ()-> this.mode.getValue() == 3);

    private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    private boolean keysPressed = false;
    private C16PacketClientStatus pendingStatus = null;
    private int delayTicks = 0;
    private int openDelayTicks = -1;
    private int closeDelayTicks = -1;

    public InvWalk() {
        super("InvWalk", false);
    }

    private KeyBinding[] getMovementKeys() {
        return new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump,
                mc.gameSettings.keyBindSprint
        };
    }

    public void pressMovementKeys() {
        for (KeyBinding keyBinding : getMovementKeys()) {
            KeyBindUtil.updateKeyState(keyBinding.getKeyCode());
        }
        if (Unfair.moduleManager.modules.get(Sprint.class).isEnabled()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
        this.keysPressed = true;
    }

    public boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer)) return false;
        if (mc.currentScreen instanceof GuiContainerCreative) return false;

        switch (this.mode.getValue()) {
            case 1: // Legit
                if (!(mc.currentScreen instanceof GuiInventory)) return false;
                return this.pendingStatus != null && this.clickQueue.isEmpty();
            case 2: // Hypixel
                return this.clickQueue.isEmpty();
            case 3: // Legit+
                if (!(mc.currentScreen instanceof GuiInventory)) return false;
                return this.closeDelayTicks == -1 && this.clickQueue.isEmpty();
            default: // Vanilla
                return true;
        }
    }

    private boolean temporaryStackIsEmpty() {
        if (mc.thePlayer.inventory.getItemStack() != null) return false;
        if (mc.thePlayer.inventoryContainer instanceof ContainerPlayer) {
            ContainerPlayer containerPlayer = (ContainerPlayer) mc.thePlayer.inventoryContainer;
            for (int i = 0; i < containerPlayer.craftMatrix.getSizeInventory(); i++) {
                ItemStack stack = containerPlayer.craftMatrix.getStackInSlot(i);
                if (stack != null) {
                    return false;
                }
            }
        }
        return true;
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            this.clickQueue.clear();
            return;
        }
        if (event.type() == EventType.PRE) {
            if (this.openDelayTicks >= 0) {
                this.openDelayTicks--;
                return;
            }
            while (!this.clickQueue.isEmpty()) {
                PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
            }
            if (this.closeDelayTicks > 0) {
                if (this.temporaryStackIsEmpty()) {
                    this.closeDelayTicks--;
                }
            } else if (this.closeDelayTicks == 0) {
                if (mc.currentScreen instanceof GuiInventory)
                    PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
                this.closeDelayTicks = -1;
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        if ((mc.currentScreen instanceof AugustusClickGui) && this.guiEnabled.getValue()) {
            pressMovementKeys();
            return;
        }

        if (this.canInvWalk() && this.delayTicks == 0) {
            this.pressMovementKeys();
        } else {
            if (this.keysPressed) {
                if (mc.currentScreen != null) {
                    KeyBinding.unPressAllKeys();
                }
                this.keysPressed = false;
            }
            if (this.pendingStatus != null) {
                PacketUtil.sendPacketNoEvent(this.pendingStatus);
                this.pendingStatus = null;
            }
            if (this.delayTicks > 0) {
                this.delayTicks--;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND) return;

        if (event.getPacket() instanceof C16PacketClientStatus packet) {
            if (this.mode.getValue() == 1 || this.mode.getValue() == 3) {
                if (packet.getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                    event.setCancelled(true);
                    if (this.mode.getValue() == 1) {
                        this.pendingStatus = packet;
                    }
                }
            }
        } else if (!(event.getPacket() instanceof C0EPacketClickWindow packet)) {
            if (event.getPacket() instanceof C0DPacketCloseWindow packet) {
                if (this.mode.getValue() == 3) {
                    if (packet.getWindowId() == 0) {
                        if (!this.clickQueue.isEmpty()) {
                            this.clickQueue.clear();
                        }
                        if (this.openDelayTicks >= 0) {
                            this.openDelayTicks = -1;
                        }
                        if (this.closeDelayTicks >= 0) {
                            this.closeDelayTicks = -1;
                        } else {
                            event.setCancelled(true);
                        }
                    } else {
                        if (!this.clickQueue.isEmpty()) {
                            this.clickQueue.clear();
                        }
                        if (this.openDelayTicks >= 0) {
                            this.openDelayTicks = -1;
                        }
                        if (this.closeDelayTicks >= 0) {
                            this.closeDelayTicks = -1;
                        }
                    }
                } else {
                    if (this.pendingStatus != null && packet.getWindowId() == 0) {
                        this.pendingStatus = null;
                        event.setCancelled(true);
                    }
                }
            }
        } else {
            switch (this.mode.getValue()) {
                case 1:
                    if (packet.getWindowId() == 0) {
                        if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                            event.setCancelled(true);
                            return;
                        }
                        if (this.pendingStatus != null) {
                            KeyBinding.unPressAllKeys();
                            this.keysPressed = false;
                            event.setCancelled(true);
                            this.clickQueue.offer(packet);
                        }
                    }
                    break;
                case 2:
                    if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                        event.setCancelled(true);
                    } else {
                        KeyBinding.unPressAllKeys();
                        this.keysPressed = false;
                        event.setCancelled(true);
                        this.clickQueue.offer(packet);
                        this.delayTicks = 8;
                    }
                    break;
                case 3:
                    if (packet.getWindowId() == 0) {
                        if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                            event.setCancelled(true);
                            return;
                        }
                        KeyBinding.unPressAllKeys();
                        this.keysPressed = false;
                        event.setCancelled(true);
                        this.clickQueue.offer(packet);
                        if (this.closeDelayTicks < 0 && this.openDelayTicks < 0) {
                            this.pendingStatus = new C16PacketClientStatus(EnumState.OPEN_INVENTORY_ACHIEVEMENT);
                            this.openDelayTicks = this.openDelay.getValue();
                        }
                        this.closeDelayTicks = this.closeDelay.getValue();
                    }
                    break;
            }
            if (this.pendingStatus != null) {
                PacketUtil.sendPacketNoEvent(this.pendingStatus);
                this.pendingStatus = null;
            }
        }
    }

    @Override
    public void onDisabled() {
        if (mc.currentScreen != null) {
            KeyBinding.unPressAllKeys();
        } else {
            for (KeyBinding keyBinding : getMovementKeys()) {
                KeyBindUtil.updateKeyState(keyBinding.getKeyCode());
            }
        }
        this.keysPressed = false;
        this.clickQueue.clear();
        this.delayTicks = 0;
        this.openDelayTicks = -1;
        this.closeDelayTicks = -1;
        if (this.pendingStatus != null) {
            PacketUtil.sendPacketNoEvent(this.pendingStatus);
            this.pendingStatus = null;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}