package cn.unfair.util.font;

import java.awt.*;

@FunctionalInterface
public interface GradientApplier {
    Color colour(int i);
}
