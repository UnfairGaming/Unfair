package net.optifine.entity.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelSlime;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSlime;
import net.minecraft.entity.monster.EntitySlime;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ModelAdapterSlime extends ModelAdapter
{
    public ModelAdapterSlime()
    {
        super(EntitySlime.class, "slime", 0.25F);
    }

    public ModelBase makeModel()
    {
        return new ModelSlime(16);
    }

    public ModelRenderer getModelRenderer(ModelBase model, String modelPart)
    {
        if (!(model instanceof ModelSlime))
        {
            return null;
        }
        else
        {
            ModelSlime modelslime = (ModelSlime)model;
            return modelPart.equals("body") ? modelslime.slimeBodies : (modelPart.equals("left_eye") ? modelslime.slimeLeftEye : (modelPart.equals("right_eye") ? modelslime.slimeRightEye : (modelPart.equals("mouth") ? modelslime.slimeMouth : null)));
        }
    }

    public String[] getModelRendererNames()
    {
        return new String[] {"body", "left_eye", "right_eye", "mouth"};
    }

    public IEntityRenderer makeEntityRender(ModelBase modelBase, float shadowSize)
    {
        RenderManager rendermanager = Minecraft.getMinecraft().getRenderManager();
        RenderSlime renderslime = new RenderSlime(rendermanager, modelBase, shadowSize);
        return renderslime;
    }
}
