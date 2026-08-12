package net.optifine.entity.model.anim;

import net.optifine.expr.IExpression;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class RenderResolverEntity implements IRenderResolver
{
    public IExpression getParameter(String name)
    {
        RenderEntityParameterBool renderentityparameterbool = RenderEntityParameterBool.parse(name);

        if (renderentityparameterbool != null)
        {
            return renderentityparameterbool;
        }
        else
        {
            RenderEntityParameterFloat renderentityparameterfloat = RenderEntityParameterFloat.parse(name);
            return renderentityparameterfloat;
        }
    }
}
