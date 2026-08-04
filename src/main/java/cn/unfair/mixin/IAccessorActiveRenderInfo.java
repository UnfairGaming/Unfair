package cn.unfair.mixin;

import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

@SideOnly(Side.CLIENT)
@Mixin({ActiveRenderInfo.class})
public interface IAccessorActiveRenderInfo {
    @Accessor("MODELVIEW")
    static FloatBuffer getModelView() {
        throw new AssertionError();
    }

    @Accessor("PROJECTION")
    static FloatBuffer getProjection() {
        throw new AssertionError();
    }

    @Accessor("VIEWPORT")
    static IntBuffer getViewport() {
        throw new AssertionError();
    }

    @Accessor("OBJECTCOORDS")
    static FloatBuffer getObjectCoords() {
        throw new AssertionError();
    }
}
