package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/** Modern block with one or more fixed collision cuboids. */
public class BlockModernShape extends ModernBlock {
    protected final int firstState;
    protected final int lastState;
    protected final ProtocolVersion protocol;
    private final double[][] boxes;

    public BlockModernShape(Material material, ProtocolVersion protocol, int firstState, int lastState, double[]... boxes) {
        super(material);
        this.protocol = protocol;
        this.firstState = firstState;
        this.lastState = lastState;
        this.boxes = boxes;
    }

    public int getViaStateIdMin() { return firstState; }
    public int getViaStateIdMax() { return lastState; }
    public ProtocolVersion getViaStateProtocol() { return protocol; }
    public IBlockState getStateFromViaStateId(int stateId) { return getDefaultState(); }
    public boolean isOpaqueCube() { return false; }
    public boolean isFullCube() { return false; }

    public void addCollisionBoxesToList(World world, BlockPos pos, IBlockState state, AxisAlignedBB mask, List<AxisAlignedBB> list, Entity entity) {
        for (double[] b : boxes) {
            AxisAlignedBB box = new AxisAlignedBB(pos.getX() + b[0] / 16.0, pos.getY() + b[1] / 16.0, pos.getZ() + b[2] / 16.0, pos.getX() + b[3] / 16.0, pos.getY() + b[4] / 16.0, pos.getZ() + b[5] / 16.0);
            if (box.intersectsWith(mask)) list.add(box);
        }
    }

    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state) {
        if (boxes.length == 0) return null;
        double minX=16,minY=16,minZ=16,maxX=0,maxY=0,maxZ=0;
        for(double[] b:boxes){minX=Math.min(minX,b[0]);minY=Math.min(minY,b[1]);minZ=Math.min(minZ,b[2]);maxX=Math.max(maxX,b[3]);maxY=Math.max(maxY,b[4]);maxZ=Math.max(maxZ,b[5]);}
        return new AxisAlignedBB(pos.getX()+minX/16,pos.getY()+minY/16,pos.getZ()+minZ/16,pos.getX()+maxX/16,pos.getY()+maxY/16,pos.getZ()+maxZ/16);
    }
}
