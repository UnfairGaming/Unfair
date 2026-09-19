package cn.unfair.util.animation.normal.easing;

import cn.unfair.util.animation.normal.Animation;

public class EaseInOutExpo extends Animation {

	public EaseInOutExpo(int ms, double endPoint) {
		super(ms, endPoint);
		this.reset();
	}

	@Override
	protected double getEquation(double x) {
		
	    if (x == 0) {
	    	return 0;
	    }
	    
	    if (x == duration) {
	    	return 1;
	    }
	    
	    double x1 = x / (duration / 2);
	    
	    if (x1 < 1) {
	    	return 0.5 * Math.pow(2, 10 * (x1 - 1));
	    }
	    
	    x1--;
	    
	    return 0.5 * (-Math.pow(2, -10 * x1) + 2);
	}

}
