package cn.unfair.util.animation;

import cn.unfair.util.animation.simple.SimpleAnimation;

import java.awt.*;

public class ColorAnimation {

	private SimpleAnimation[] animation = new SimpleAnimation[3];

	public ColorAnimation() {
		
		for(int i = 0; i < animation.length; i++) {
			animation[i] = new SimpleAnimation();
		}
	}
	
	public Color getColor(Color color, int speed) {
		
		animation[0].setAnimation(color.getRed(), speed);
		animation[1].setAnimation(color.getGreen(), speed);
		animation[2].setAnimation(color.getBlue(), speed);
		
		return new Color((int) animation[0].getValue(), (int) animation[1].getValue(), (int) animation[2].getValue(), color.getAlpha());
	}
	
	public Color getColor(Color color) {
		return getColor(color, 12);
	}
	
	public void setColor(Color color) {
		animation[0].setValue(color.getRed());
		animation[1].setValue(color.getGreen());
		animation[2].setValue(color.getBlue());
	}
}
