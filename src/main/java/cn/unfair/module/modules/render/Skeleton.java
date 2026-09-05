package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.Priority;
import cn.unfair.events.Render3DEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public class Skeleton extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Map<EntityPlayer, float[][]> modelRotations = new HashMap<>();
    public final BooleanProperty self = new BooleanProperty("Self", false);

    public Skeleton() {
        super("Skeleton", false, true);
    }

    @Override
    public void onDisabled() {
        modelRotations.clear();
    }

    @EventTarget(Priority.LOWEST)
    public void onRender3D(Render3DEvent event) {
        RenderUtil.enableRenderState();
        modelRotations.keySet().removeIf(player -> !mc.theWorld.loadedEntityList.contains(player));
        mc.theWorld.loadedEntityList.forEach(bruh -> {
            if (!(bruh instanceof EntityPlayer)) {
                return;
            }
            EntityPlayer player = (EntityPlayer) bruh;
            if ((player == mc.thePlayer && (!this.self.getValue() || mc.gameSettings.thirdPersonView == 0)) || player.isInvisible()) {
                return;
            }

            float[][] rotations = modelRotations.get(player);
            if (rotations == null) {
                return;
            }

            glPushMatrix();

            glLineWidth(1.0F);
            glColor4f(1, 1, 1, 1);
            double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.partialTicks()) - mc.getRenderManager().getRenderPosX();
            double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.partialTicks()) - mc.getRenderManager().getRenderPosY();
            double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.partialTicks()) - mc.getRenderManager().getRenderPosZ();
            glTranslated(x, y, z);
            float bodyYawOffset = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * event.partialTicks();
            glRotatef(-bodyYawOffset, 0, 1, 0);

            glTranslated(0, 0, player.isSneaking() ? -0.235 : 0.0);

            float legHeight = player.isSneaking() ? 0.6F : 0.75F;

            float armWidth = 0;
            Render render = mc.getRenderManager().getEntityRenderObject(player);
            if (render instanceof RenderPlayer && ((RenderPlayer) render).getMainModel().isSmallArms()) {
                armWidth = 0.05F;
            }

            {
                glPushMatrix();
                glTranslated(-0.125, legHeight, 0);
                if (rotations[3][0] != 0.0F)
                    glRotatef(rotations[3][0] * (180F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
                if (rotations[3][1] != 0.0F)
                    glRotatef(rotations[3][1] * (180F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
                if (rotations[3][2] != 0.0F)
                    glRotatef(rotations[3][2] * (180F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
                glBegin(GL_LINE_STRIP);
                glVertex3d(0, 0, 0);
                glVertex3d(0, -legHeight, 0);
                glEnd();
                glPopMatrix();
            }

            {
                glPushMatrix();
                glTranslated(0.125, legHeight, 0);
                if (rotations[4][0] != 0.0F)
                    glRotatef(rotations[4][0] * (180F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
                if (rotations[4][1] != 0.0F)
                    glRotatef(rotations[4][1] * (180F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
                if (rotations[4][2] != 0.0F)
                    glRotatef(rotations[4][2] * (180F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
                glBegin(GL_LINE_STRIP);
                glVertex3d(0, 0, 0);
                glVertex3d(0, -legHeight, 0);
                glEnd();
                glPopMatrix();
            }

            glTranslated(0, 0, player.isSneaking() ? 0.25 : 0.0);

            glPushMatrix();

            glTranslated(0, player.isSneaking() ? -0.05 : 0, player.isSneaking() ? -0.01725 : 0);

            {
                glPushMatrix();
                glTranslated(-0.375 + armWidth, legHeight + 0.55, 0);
                if (rotations[1][0] != 0.0F)
                    glRotatef(rotations[1][0] * (180F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
                if (rotations[1][1] != 0.0F)
                    glRotatef(rotations[1][1] * (180F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
                if (rotations[1][2] != 0.0F)
                    glRotatef(-rotations[1][2] * (180F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
                glBegin(GL_LINE_STRIP);
                glVertex3d(0, 0, 0);
                glVertex3d(0, -0.5, 0);
                glEnd();
                glPopMatrix();
            }

            {
                glPushMatrix();
                glTranslated(0.375 - armWidth, legHeight + 0.55, 0);
                if (rotations[2][0] != 0.0F)
                    glRotatef(rotations[2][0] * (180F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
                if (rotations[2][1] != 0.0F)
                    glRotatef(rotations[2][1] * (180F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
                if (rotations[2][2] != 0.0F)
                    glRotatef(-rotations[2][2] * (180F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
                glBegin(GL_LINE_STRIP);
                glVertex3d(0, 0, 0);
                glVertex3d(0, -0.5, 0);
                glEnd();
                glPopMatrix();
            }

            {
                glRotatef(bodyYawOffset - player.rotationYawHead, 0, 1, 0);
                glPushMatrix();
                glTranslated(0.0, legHeight + 0.55, 0);
                if (rotations[0][0] != 0.0F)
                    glRotatef(rotations[0][0] * (180F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
                glBegin(GL_LINE_STRIP);
                glVertex3d(0, 0, 0);
                glVertex3d(0, 0.3, 0);
                glEnd();
                glPopMatrix();
            }

            glPopMatrix();

            glRotatef(player.isSneaking() ? 25 : 0, 1, 0, 0);
            glTranslated(0, player.isSneaking() ? -0.16175 : 0, player.isSneaking() ? -0.48025 : 0);

            {
                glPushMatrix();
                glTranslated(0.0, legHeight, 0);
                glBegin(GL_LINE_STRIP);
                glVertex3d(-0.125, 0, 0);
                glVertex3d(0.125, 0, 0);
                glEnd();
                glPopMatrix();
            }

            {
                glPushMatrix();
                glTranslated(0.0, legHeight, 0);
                glBegin(GL_LINE_STRIP);
                glVertex3d(0, 0, 0);
                glVertex3d(0, 0.55, 0);
                glEnd();
                glPopMatrix();
            }

            {
                glPushMatrix();
                glTranslated(0.0, legHeight + 0.55, 0);
                glBegin(GL_LINE_STRIP);
                glVertex3d(-0.375 + armWidth, 0, 0);
                glVertex3d(0.375 - armWidth, 0, 0);
                glEnd();
                glPopMatrix();
            }

            glPopMatrix();
        });
        RenderUtil.disableRenderState();
    }

    public static void updateModel(EntityPlayer player, ModelPlayer model) {
        float[][] rotations = modelRotations.getOrDefault(player, new float[5][3]);
        rotations[0][0] = model.bipedHead.rotateAngleX;
        rotations[0][1] = model.bipedHead.rotateAngleY;
        rotations[0][2] = model.bipedHead.rotateAngleZ;

        rotations[1][0] = model.bipedRightArm.rotateAngleX;
        rotations[1][1] = model.bipedRightArm.rotateAngleY;
        rotations[1][2] = model.bipedRightArm.rotateAngleZ;

        rotations[2][0] = model.bipedLeftArm.rotateAngleX;
        rotations[2][1] = model.bipedLeftArm.rotateAngleY;
        rotations[2][2] = model.bipedLeftArm.rotateAngleZ;

        rotations[3][0] = model.bipedRightLeg.rotateAngleX;
        rotations[3][1] = model.bipedRightLeg.rotateAngleY;
        rotations[3][2] = model.bipedRightLeg.rotateAngleZ;

        rotations[4][0] = model.bipedLeftLeg.rotateAngleX;
        rotations[4][1] = model.bipedLeftLeg.rotateAngleY;
        rotations[4][2] = model.bipedLeftLeg.rotateAngleZ;

        modelRotations.put(player, rotations);
    }
}
