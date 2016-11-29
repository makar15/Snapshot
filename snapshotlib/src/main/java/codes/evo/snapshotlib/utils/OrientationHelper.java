package codes.evo.snapshotlib.utils;

import android.content.Context;
import android.view.OrientationEventListener;

public class OrientationHelper extends OrientationEventListener {

    private int mDeviceOrientation = 0;

    public OrientationHelper(Context context) {
        super(context);
    }

    public OrientationHelper(Context context, int rate) {
        super(context, rate);
    }

    public int getDeviceOrientation() {
        return mDeviceOrientation;
    }

    @Override
    public void onOrientationChanged(int orientation) {
        mDeviceOrientation = orientation;
    }

    @Override
    public boolean canDetectOrientation() {
        return super.canDetectOrientation();
    }

    @Override
    public void enable() {
        super.enable();
    }

    @Override
    public void disable() {
        super.disable();
    }
}
