package cn.unfair.util.animation.normal.easing;

import cn.unfair.util.animation.normal.Animation;

public class EaseInQuint extends Animation {

	public EaseInQuint(int ms, double endPoint) {
		super(ms, endPoint);
		this.reset();
	}

	@Override
	protected double getEquation(double x) {
		
	    double x1 = x / duration;
	    
	    return x1 * x1 * x1 * x1 * x1;
	}
}
