package cn.unfair.util.animation.normal.easing;

import cn.unfair.util.animation.normal.Animation;

public class EaseInOutQuart extends Animation {

	public EaseInOutQuart(int ms, double endPoint) {
		super(ms, endPoint);
		this.reset();
	}

	@Override
	protected double getEquation(double x) {
		
	    double x1 = x / (duration / 2);
	    
	    if (x1 < 1) {
	    	return 0.5 * x1 * x1 * x1 * x1;
	    }
	    
	    x1 -= 2;
	    
	    return -0.5 * (x1 * x1 * x1 * x1 - 2);
	}
}
