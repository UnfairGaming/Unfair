package net.optifine.entity.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelDragon;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderDragon;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.boss.EntityDragon;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ModelAdapterDragon extends ModelAdapter
{
    public ModelAdapterDragon()
    {
        super(EntityDragon.class, "dragon", 0.5F);
    }

    public ModelBase makeModel()
    {
        return new ModelDragon(0.0F);
    }

    public ModelRenderer getModelRenderer(ModelBase model, String modelPart)
    {
        if (!(model instanceof ModelDragon))
        {
            return null;
        }
        else
        {
            ModelDragon modeldragon = (ModelDragon)model;
            return modelPart.equals("head") ? modeldragon.head : (modelPart.equals("spine") ? modeldragon.spine : (modelPart.equals("jaw") ? modeldragon.jaw : (modelPart.equals("body") ? modeldragon.body : (modelPart.equals("rear_leg") ? modeldragon.rearLeg : (modelPart.equals("front_leg") ? modeldragon.frontLeg : (modelPart.equals("rear_leg_tip") ? modeldragon.rearLegTip : (modelPart.equals("front_leg_tip") ? modeldragon.frontLegTip : (modelPart.equals("rear_foot") ? modeldragon.rearFoot : (modelPart.equals("front_foot") ? modeldragon.frontFoot : (modelPart.equals("wing") ? modeldragon.wing : (modelPart.equals("wing_tip") ? modeldragon.wingTip : null)))))))))));
        }
    }

    public String[] getModelRendererNames()
    {
        return new String[] {"head", "spine", "jaw", "body", "rear_leg", "front_leg", "rear_leg_tip", "front_leg_tip", "rear_foot", "front_foot", "wing", "wing_tip"};
    }

    public IEntityRenderer makeEntityRender(ModelBase modelBase, float shadowSize)
    {
        RenderManager rendermanager = Minecraft.getMinecraft().getRenderManager();
        RenderDragon renderdragon = new RenderDragon(rendermanager);
        renderdragon.mainModel = modelBase;
        renderdragon.shadowSize = shadowSize;
        return renderdragon;
    }
}
