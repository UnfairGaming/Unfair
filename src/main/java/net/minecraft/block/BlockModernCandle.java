package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import java.util.List;

public class BlockModernCandle extends ModernBlock {
    public static final PropertyInteger CANDLES=PropertyInteger.create("candles",1,4); public static final PropertyBool LIT=PropertyBool.create("lit"); private final int first;
    public BlockModernCandle(int first,int last){super(Material.circuits);this.first=first;setDefaultState(blockState.getBaseState().withProperty(CANDLES,1).withProperty(LIT,false));}
    public int getViaStateIdMin(){return first;} public int getViaStateIdMax(){return first+15;} public ProtocolVersion getViaStateProtocol(){return ProtocolVersion.v1_17;}
    public IBlockState getStateFromViaStateId(int id){int v=id-first;return getDefaultState().withProperty(CANDLES,v/4+1).withProperty(LIT,(v&2)!=0);}
    protected BlockState createBlockState(){return new BlockState(this,new IProperty[]{CANDLES,LIT});} public boolean isOpaqueCube(){return false;} public boolean isFullCube(){return false;}
    public void addCollisionBoxesToList(World w,BlockPos p,IBlockState s,AxisAlignedBB m,List<AxisAlignedBB> l,Entity e){int c=s.getValue(CANDLES);double[][] b={{7,0,7,9,6,9},{3,0,7,5,6,9},{11,0,7,13,6,9},{7,0,3,9,6,5}};for(int i=0;i<c;i++){double[]q=b[i];AxisAlignedBB a=new AxisAlignedBB(p.getX()+q[0]/16,p.getY(),p.getZ()+q[2]/16,p.getX()+q[3]/16,p.getY()+q[4]/16,p.getZ()+q[5]/16);if(a.intersectsWith(m))l.add(a);}}
}
