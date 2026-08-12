package net.optifine.entity.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelHorse;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderHorse;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.passive.EntityHorse;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ModelAdapterHorse extends ModelAdapter
{
    private static Map<String, Integer> mapPartFields = null;

    public ModelAdapterHorse()
    {
        super(EntityHorse.class, "horse", 0.75F);
    }

    protected ModelAdapterHorse(Class entityClass, String name, float shadowSize)
    {
        super(entityClass, name, shadowSize);
    }

    public ModelBase makeModel()
    {
        return new ModelHorse();
    }

    public ModelRenderer getModelRenderer(ModelBase model, String modelPart)
    {
        if (!(model instanceof ModelHorse))
        {
            return null;
        }
        else
        {
            ModelHorse modelhorse = (ModelHorse)model;
            Map<String, Integer> map = getMapPartFields();

            if (map.containsKey(modelPart))
            {
                int i = map.get(modelPart);
                return getModelRenderer(modelhorse, i);
            }
            else
            {
                return null;
            }
        }
    }

    private static ModelRenderer getModelRenderer(ModelHorse modelhorse, int index)
    {
        return switch (index)
        {
            case 0 -> modelhorse.head;
            case 1 -> modelhorse.field_178711_b;
            case 2 -> modelhorse.field_178712_c;
            case 3 -> modelhorse.horseLeftEar;
            case 4 -> modelhorse.horseRightEar;
            case 5 -> modelhorse.muleLeftEar;
            case 6 -> modelhorse.muleRightEar;
            case 7 -> modelhorse.neck;
            case 8 -> modelhorse.horseFaceRopes;
            case 9 -> modelhorse.mane;
            case 10 -> modelhorse.body;
            case 11 -> modelhorse.tailBase;
            case 12 -> modelhorse.tailMiddle;
            case 13 -> modelhorse.tailTip;
            case 14 -> modelhorse.backLeftLeg;
            case 15 -> modelhorse.backLeftShin;
            case 16 -> modelhorse.backLeftHoof;
            case 17 -> modelhorse.backRightLeg;
            case 18 -> modelhorse.backRightShin;
            case 19 -> modelhorse.backRightHoof;
            case 20 -> modelhorse.frontLeftLeg;
            case 21 -> modelhorse.frontLeftShin;
            case 22 -> modelhorse.frontLeftHoof;
            case 23 -> modelhorse.frontRightLeg;
            case 24 -> modelhorse.frontRightShin;
            case 25 -> modelhorse.frontRightHoof;
            case 26 -> modelhorse.muleLeftChest;
            case 27 -> modelhorse.muleRightChest;
            case 28 -> modelhorse.horseSaddleBottom;
            case 29 -> modelhorse.horseSaddleFront;
            case 30 -> modelhorse.horseSaddleBack;
            case 31 -> modelhorse.horseLeftSaddleRope;
            case 32 -> modelhorse.horseLeftSaddleMetal;
            case 33 -> modelhorse.horseRightSaddleRope;
            case 34 -> modelhorse.horseRightSaddleMetal;
            case 35 -> modelhorse.horseLeftFaceMetal;
            case 36 -> modelhorse.horseRightFaceMetal;
            case 37 -> modelhorse.horseLeftRein;
            case 38 -> modelhorse.horseRightRein;
            default -> null;
        };
    }

    public String[] getModelRendererNames()
    {
        return new String[] {"head", "upper_mouth", "lower_mouth", "horse_left_ear", "horse_right_ear", "mule_left_ear", "mule_right_ear", "neck", "horse_face_ropes", "mane", "body", "tail_base", "tail_middle", "tail_tip", "back_left_leg", "back_left_shin", "back_left_hoof", "back_right_leg", "back_right_shin", "back_right_hoof", "front_left_leg", "front_left_shin", "front_left_hoof", "front_right_leg", "front_right_shin", "front_right_hoof", "mule_left_chest", "mule_right_chest", "horse_saddle_bottom", "horse_saddle_front", "horse_saddle_back", "horse_left_saddle_rope", "horse_left_saddle_metal", "horse_right_saddle_rope", "horse_right_saddle_metal", "horse_left_face_metal", "horse_right_face_metal", "horse_left_rein", "horse_right_rein"};
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
            mapPartFields.put("head", 0);
            mapPartFields.put("upper_mouth", 1);
            mapPartFields.put("lower_mouth", 2);
            mapPartFields.put("horse_left_ear", 3);
            mapPartFields.put("horse_right_ear", 4);
            mapPartFields.put("mule_left_ear", 5);
            mapPartFields.put("mule_right_ear", 6);
            mapPartFields.put("neck", 7);
            mapPartFields.put("horse_face_ropes", 8);
            mapPartFields.put("mane", 9);
            mapPartFields.put("body", 10);
            mapPartFields.put("tail_base", 11);
            mapPartFields.put("tail_middle", 12);
            mapPartFields.put("tail_tip", 13);
            mapPartFields.put("back_left_leg", 14);
            mapPartFields.put("back_left_shin", 15);
            mapPartFields.put("back_left_hoof", 16);
            mapPartFields.put("back_right_leg", 17);
            mapPartFields.put("back_right_shin", 18);
            mapPartFields.put("back_right_hoof", 19);
            mapPartFields.put("front_left_leg", 20);
            mapPartFields.put("front_left_shin", 21);
            mapPartFields.put("front_left_hoof", 22);
            mapPartFields.put("front_right_leg", 23);
            mapPartFields.put("front_right_shin", 24);
            mapPartFields.put("front_right_hoof", 25);
            mapPartFields.put("mule_left_chest", 26);
            mapPartFields.put("mule_right_chest", 27);
            mapPartFields.put("horse_saddle_bottom", 28);
            mapPartFields.put("horse_saddle_front", 29);
            mapPartFields.put("horse_saddle_back", 30);
            mapPartFields.put("horse_left_saddle_rope", 31);
            mapPartFields.put("horse_left_saddle_metal", 32);
            mapPartFields.put("horse_right_saddle_rope", 33);
            mapPartFields.put("horse_right_saddle_metal", 34);
            mapPartFields.put("horse_left_face_metal", 35);
            mapPartFields.put("horse_right_face_metal", 36);
            mapPartFields.put("horse_left_rein", 37);
            mapPartFields.put("horse_right_rein", 38);
            return mapPartFields;
        }
    }

    public IEntityRenderer makeEntityRender(ModelBase modelBase, float shadowSize)
    {
        RenderManager rendermanager = Minecraft.getMinecraft().getRenderManager();
        RenderHorse renderhorse = new RenderHorse(rendermanager, (ModelHorse)modelBase, shadowSize);
        return renderhorse;
    }
}
