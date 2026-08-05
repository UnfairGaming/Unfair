package org.lwjgl.input;

public interface Controller {

    int getAxisCount();

    String getAxisName(int arg0);

    float getAxisValue(int arg0);

    int getButtonCount();

    String getButtonName(int arg0);

    float getDeadZone(int arg0);

    int getIndex();

    String getName();

    float getPovX();

    float getPovY();

    float getRXAxisDeadZone();

    void setRXAxisDeadZone(float arg0);

    float getRXAxisValue();

    float getRYAxisDeadZone();

    void setRYAxisDeadZone(float arg0);

    float getRYAxisValue();

    float getRZAxisDeadZone();

    void setRZAxisDeadZone(float arg0);

    float getRZAxisValue();

    int getRumblerCount();

    String getRumblerName(int arg0);

    float getXAxisDeadZone();

    void setXAxisDeadZone(float arg0);

    float getXAxisValue();

    float getYAxisDeadZone();

    void setYAxisDeadZone(float arg0);

    float getYAxisValue();

    float getZAxisDeadZone();

    void setZAxisDeadZone(float arg0);

    float getZAxisValue();

    boolean isButtonPressed(int arg0);

    void poll();

    void setDeadZone(int arg0, float arg1);

    void setRumblerStrength(int arg0, float arg1);
}
