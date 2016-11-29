package codes.evo.snapshotlib;

import android.app.Activity;
import android.content.Context;

public interface SnapshotMaker {

    enum CameraState {
        OPEN,
        CLOSE,
        TAKE_IMAGE
    }

    interface SnapshotListener {

        void onImageSaved(String photoPath);
    }

    interface CameraListener {

        void onImageTaken(byte[] result);

        void onImageFailed(Exception e, String errMessage);

        void onCameraOpened();

        void onCameraClosed();
    }

    /**
     * Context only need for check has permission on create snapshots, starting with Android.M
     *
     * @param context
     * @throws CameraException
     */
    void openCamera(Context context) throws CameraException;

    void takeImage();

    void closeCamera();

    void requestPermission(Activity activity);

    void setCameraListener(CameraListener listener);
}
