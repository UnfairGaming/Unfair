package cn.unfair.util.animation.normal.easing;

import cn.unfair.util.animation.normal.Animation;
import cn.unfair.util.animation.normal.Direction;

public class EaseElasticAnimation extends Animation {

    float easeAmount;
    float smooth;
    boolean reallyElastic;

    public EaseElasticAnimation(int ms, double endPoint, float elasticity, float smooth, boolean moreElasticity) {
        super(ms, endPoint);
        this.easeAmount = elasticity;
        this.smooth = smooth;
        this.reallyElastic = moreElasticity;
        this.reset();
    }

    public EaseElasticAnimation(int ms, double endPoint, float elasticity, float smooth, boolean moreElasticity, Direction direction) {
        super(ms, endPoint, direction);
        this.easeAmount = elasticity;
        this.smooth = smooth;
        this.reallyElastic = moreElasticity;
        this.reset();
    }

    @Override
    protected double getEquation(double x) {
    	
        double x1 = Math.pow(x / duration, smooth);
        double elasticity = easeAmount * .1f;
        
        return Math.pow(2, -10 * (reallyElastic ? Math.sqrt(x1) : x1)) * Math.sin((x1 - (elasticity / 4)) * ((2 * Math.PI) / elasticity)) + 1;
    }
}
