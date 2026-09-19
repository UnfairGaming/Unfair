package cn.unfair.util.animation.normal.easing;

import cn.unfair.util.animation.normal.Animation;

public class EaseInOutCirc extends Animation {

	public EaseInOutCirc(int ms, double endPoint) {
		super(ms, endPoint);
		this.reset();
	}

	@Override
	protected double getEquation(double x) {
	    double x1 = x / duration - 1;
	    return Math.sqrt(1 - x1 * x1);
	}
}
