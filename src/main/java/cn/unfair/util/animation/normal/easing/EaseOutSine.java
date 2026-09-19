package cn.unfair.util.animation.normal.easing;

import cn.unfair.util.animation.normal.Animation;

public class EaseOutSine extends Animation {

	public EaseOutSine(int ms, double endPoint) {
		super(ms, endPoint);
		this.reset();
	}
	
	@Override
	protected double getEquation(double x) {
	    return Math.sin(x / duration * (Math.PI / 2));
	}
}
