package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.BlockPos;

import java.util.List;

/**
 * Base class for blocks whose real state must be preserved before ViaBackwards
 * replaces it with a legacy fallback.
 */
public abstract class ModernBlock extends Block {
    private MiningTool modernMiningTool = MiningTool.NONE;
    private boolean modernRequiresTool;
    private float modernSwordSpeed = 1.0F;
    private boolean modernMiningConfigured;
    protected ModernBlock(Material materialIn) {
        super(materialIn);
        // The 1.8 mining formula treats zero hardness as an instant break. Most
        // modern blocks are not instant in the native client, so give them a
        // usable default and let genuinely instant blocks opt back out.
        this.setHardness(1.0F);
        configureMiningDefaults(materialIn);
    }

    protected ModernBlock(Material materialIn, MapColor mapColorIn) {
        super(materialIn, mapColorIn);
        this.setHardness(1.0F);
        configureMiningDefaults(materialIn);
    }

    private void configureMiningDefaults(Material material) {
        if (material == Material.wood) modernMiningTool = MiningTool.AXE;
        else if (material == Material.ground || material == Material.clay || material == Material.craftedSnow)
            modernMiningTool = MiningTool.SHOVEL;
        else if (material == Material.rock || material == Material.iron || material == Material.anvil || material == Material.glass)
            modernMiningTool = MiningTool.PICKAXE;
        else modernMiningTool = MiningTool.NONE;
        modernRequiresTool = material == Material.rock || material == Material.iron || material == Material.anvil;
    }

    public ModernBlock setModernMining(float hardness, MiningTool tool, boolean requiresTool) {
        this.setHardness(hardness);
        this.modernMiningTool = tool == null ? MiningTool.NONE : tool;
        this.modernRequiresTool = requiresTool;
        this.modernMiningConfigured = true;
        return this;
    }

    public boolean isModernToolCorrect(ItemStack stack) {
        if (stack == null) return false;
        switch (this.modernMiningTool) {
            case PICKAXE:
                return stack.getItem() instanceof ItemPickaxe;
            case AXE:
                return stack.getItem() instanceof ItemAxe;
            case SHOVEL:
                return stack.getItem() instanceof ItemSpade;
            case HOE:
                return stack.getItem() instanceof ItemHoe;
            default:
                return false;
        }
    }

    public boolean requiresModernTool() {
        return this.modernRequiresTool;
    }

    public boolean isModernMiningConfigured() {
        return this.modernMiningConfigured;
    }

    public boolean isModernToolEffective(ItemStack stack) {
        return isModernToolCorrect(stack);
    }

    public float getModernSwordSpeed() {
        return this.modernSwordSpeed;
    }

    public ModernBlock setModernSwordSpeed(float speed) {
        this.modernSwordSpeed = speed;
        return this;
    }

    @Override
    public float getPlayerRelativeBlockHardness(EntityPlayer player, net.minecraft.world.World world, BlockPos pos) {
        if (!modernMiningConfigured) return super.getPlayerRelativeBlockHardness(player, world, pos);
        float hardness = getBlockHardness(world, pos);
        if (hardness < 0.0F) return 0.0F;
        ItemStack held = player.inventory.getCurrentItem();
        boolean correct = isModernToolCorrect(held);
        float speed = player.getToolDigEfficiency(this);
        return speed / hardness / (modernRequiresTool && !correct ? 100.0F : 30.0F);
    }

    /**
     * First inclusive 1.14 block-state ID handled by this block.
     */
    public abstract int getViaStateIdMin();

    /**
     * Last inclusive 1.14 block-state ID handled by this block.
     */
    public abstract int getViaStateIdMax();

    /**
     * Protocol layer where this block still has its original state ID.
     */
    public ProtocolVersion getViaStateProtocol() {
        return ProtocolVersion.v1_14;
    }

    public final boolean handlesViaStateId(int stateId) {
        return stateId >= this.getViaStateIdMin() && stateId <= this.getViaStateIdMax();
    }

    public boolean handlesViaState(ProtocolVersion protocol, int stateId) {
        return this.getViaStateProtocol().equals(protocol) && this.handlesViaStateId(stateId);
    }

    public IBlockState getStateFromViaState(ProtocolVersion protocol, int stateId) {
        return this.getStateFromViaStateId(stateId);
    }

    /**
     * Modern-only blocks still have to fit through the 1.8 block registry's
     * four-bit metadata table. Use the block state's stable valid-state order
     * as the local encoding unless a block needs an explicit legacy layout.
     */
    @Override
    public IBlockState getStateFromMeta(int meta) {
        List<IBlockState> states = this.blockState.getValidStates();
        return meta >= 0 && meta < states.size() ? states.get(meta) : this.getDefaultState();
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        List<IBlockState> states = this.blockState.getValidStates();

        if (states.size() > 16) {
            throw new IllegalStateException("Modern block " + state.getBlock()
                    + " has " + states.size() + " local states; the 1.8 registry supports at most 16");
        }

        int meta = states.indexOf(state);
        if (meta < 0) {
            throw new IllegalArgumentException("State " + state + " does not belong to " + this);
        }

        return meta;
    }

    /**
     * Converts a matching 1.14 block-state ID into this client's local state.
     */
    public abstract IBlockState getStateFromViaStateId(int stateId);

    /**
     * Called after the tracker installs a decoded state into the client world.
     */
    public void onModernStateApplied(BlockPos pos, IBlockState state) {
    }

    public enum MiningTool {NONE, PICKAXE, AXE, SHOVEL, HOE}
}
