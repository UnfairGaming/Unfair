package net.optifine.entity.model;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelOcelot;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderOcelot;
import net.minecraft.entity.passive.EntityOcelot;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ModelAdapterOcelot extends ModelAdapter
{
    private static Map<String, Integer> mapPartFields = null;

    public ModelAdapterOcelot()
    {
        super(EntityOcelot.class, "ocelot", 0.4F);
    }

    public ModelBase makeModel()
    {
        return new ModelOcelot();
    }

    public ModelRenderer getModelRenderer(ModelBase model, String modelPart)
    {
        if (!(model instanceof ModelOcelot))
        {
            return null;
        }
        else
        {
            ModelOcelot modelocelot = (ModelOcelot)model;
            Map<String, Integer> map = getMapPartFields();

            if (map.containsKey(modelPart))
            {
                int i = ((Integer)map.get(modelPart)).intValue();
                return getModelRenderer(modelocelot, i);
            }
            else
            {
                return null;
            }
        }
    }

    private static ModelRenderer getModelRenderer(ModelOcelot modelocelot, int index)
    {
        return switch (index)
        {
            case 0 -> modelocelot.ocelotBackLeftLeg;
            case 1 -> modelocelot.ocelotBackRightLeg;
            case 2 -> modelocelot.ocelotFrontLeftLeg;
            case 3 -> modelocelot.ocelotFrontRightLeg;
            case 4 -> modelocelot.ocelotTail;
            case 5 -> modelocelot.ocelotTail2;
            case 6 -> modelocelot.ocelotHead;
            case 7 -> modelocelot.ocelotBody;
            default -> null;
        };
    }

    public String[] getModelRendererNames()
    {
        return new String[] {"back_left_leg", "back_right_leg", "front_left_leg", "front_right_leg", "tail", "tail2", "head", "body"};
    }

    private static Map<String, Integer> getMapPartFields()
    {
        if (mapPartFields != null)
        {
            return mapPartFields;
        }
        else
        {
            mapPartFields = new HashMap();
            mapPartFields.put("back_left_leg", Integer.valueOf(0));
            mapPartFields.put("back_right_leg", Integer.valueOf(1));
            mapPartFields.put("front_left_leg", Integer.valueOf(2));
            mapPartFields.put("front_right_leg", Integer.valueOf(3));
            mapPartFields.put("tail", Integer.valueOf(4));
            mapPartFields.put("tail2", Integer.valueOf(5));
            mapPartFields.put("head", Integer.valueOf(6));
            mapPartFields.put("body", Integer.valueOf(7));
            return mapPartFields;
        }
    }

    public IEntityRenderer makeEntityRender(ModelBase modelBase, float shadowSize)
    {
        RenderManager rendermanager = Minecraft.getMinecraft().getRenderManager();
        RenderOcelot renderocelot = new RenderOcelot(rendermanager, modelBase, shadowSize);
        return renderocelot;
    }
}
