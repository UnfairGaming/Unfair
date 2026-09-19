package cn.unfair.util.animation.normal.easing;

import cn.unfair.util.animation.normal.Animation;

public class EaseInQuad extends Animation {

	public EaseInQuad(int ms, double endPoint) {
		super(ms, endPoint);
		this.reset();
	}

	@Override
	protected double getEquation(double x) {
	    double x1 = x / duration;
	    return x1 * x1;
	}
}
