package codes.evo.snapshotlib;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.IOException;

import codes.evo.snapshotlib.utils.BackgroundWorker;
import codes.evo.snapshotlib.utils.OrientationHelper;

@SuppressWarnings("deprecation")
public class SnapshotMakerV1 implements SnapshotMaker {
    private static final String TAG = "SnapshotMakerV1";

    private final Context mContext;
    private final BackgroundWorker.Client mBgClient;
    private final WindowManager mWindowManager;
    private final OrientationHelper mOrientationHelper;

    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private CameraListener mCameraListener;

    private CameraState mCurrentState = CameraState.CLOSE;
    private boolean mIsRequestTakeImage;
    private boolean mIsRequestCloseCamera;

    private final SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mBgClient.post(new Runnable() {
                @Override
                public void run() {
                    int cameraCount = Camera.getNumberOfCameras();

                    for (int camId = 0; camId < cameraCount; camId++) {
                        Camera.CameraInfo info = new Camera.CameraInfo();
                        Camera.getCameraInfo(camId, info);

                        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            try {
                                mCamera = Camera.open(camId);
                            } catch (RuntimeException e) {
                                sendEventOnImageFailed(e, "Camera failed to open : " + e.getLocalizedMessage());
                                handleState(CameraState.CLOSE);
                            }
                        }
                    }
                    if (mCamera != null) {
                        try {
                            mCamera.setPreviewDisplay(mSurfaceHolder);
                            mCamera.setPreviewCallback(null);
                            mCamera.startPreview();

                            if (mOrientationHelper.canDetectOrientation()) {
                                Log.d(TAG, "Can detect orientation");
                                mOrientationHelper.enable();
                            }
                            if (mCameraListener != null) {
                                mCameraListener.onCameraOpened();
                            }
                            handleState(CameraState.OPEN);

                        } catch (IOException e) {
                            sendEventOnImageFailed(e, "Surface is unavailable or unsuitable. ");
                            handleState(CameraState.CLOSE);
                        }
                    } else {
                        destroySurface();
                        Log.e(TAG, "Unable to access the camera");
                    }
                }
            });
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    };

    private final Camera.PictureCallback mCameraPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                String errMessage = "Do not access to external storage sources. ";
                sendEventOnImageFailed(new CameraException(errMessage), errMessage);
                handleState(CameraState.CLOSE);
                return;
            }
            if (mCameraListener != null) {
                mCameraListener.onImageTaken(data);
            }
            handleState(CameraState.OPEN);
        }
    };

    public SnapshotMakerV1(Context context, BackgroundWorker backgroundWorker) {
        mContext = context;
        mBgClient = backgroundWorker.getDefault();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mOrientationHelper = new OrientationHelper(mContext, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void handleState(CameraState newState) {
        if (mCurrentState == newState) {
            return;
        }

        switch (newState) {
            case OPEN:
                if (mIsRequestCloseCamera) {
                    mCurrentState = newState;
                    closeCamera();
                    return;
                }
                if (mIsRequestTakeImage) {
                    takeImage();
                }
                break;

            case CLOSE:
                Log.d(TAG, "Close camera");
                if (mOrientationHelper.canDetectOrientation()) {
                    Log.d(TAG, "Cannot detect orientation");
                    mOrientationHelper.disable();
                }
                destroySurface();
                if (mCamera != null) {
                    mCamera.stopPreview();
                    mCamera.release();
                    mCamera = null;
                    if (mCameraListener != null) {
                        mCameraListener.onCameraClosed();
                    }
                }
                mIsRequestTakeImage = false;
                mIsRequestCloseCamera = false;
                break;

            case TAKE_IMAGE:
                if (mCurrentState != CameraState.OPEN) {
                    mIsRequestTakeImage = true;
                    return;
                }

                mIsRequestTakeImage = false;
                mBgClient.post(new Runnable() {
                    @Override
                    public void run() {
                        setupCameraParameters();
                        mCamera.takePicture(null, null, null, mCameraPictureCallback);
                    }
                });
                break;
        }

        mCurrentState = newState;
    }

    @Override
    public void openCamera(@Nullable Context context) throws CameraException {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            throw new CameraException("Not found a front-facing camera");
        }
        if (mCurrentState != CameraState.CLOSE) {
            throw new CameraException("Camera is already open");
        }

        Log.d(TAG, "Open camera");
        mSurfaceView = new SurfaceView(mContext);
        mWindowManager.addView(mSurfaceView, initLayoutParams());
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(mSurfaceHolderCallback);
    }

    @Override
    public void takeImage() {
        handleState(CameraState.TAKE_IMAGE);
    }

    @Override
    public void closeCamera() {
        if (mCurrentState == CameraState.OPEN) {
            handleState(CameraState.CLOSE);
            return;
        }
        if (mCurrentState == CameraState.TAKE_IMAGE) {
            mIsRequestCloseCamera = true;
        }
    }

    @Override
    public void requestPermission(@Nullable Activity activity) {
    }

    @Override
    public void setCameraListener(CameraListener listener) {
        mCameraListener = listener;
    }

    private void destroySurface() {
        Log.d(TAG, "Destroy surface");

        if (mSurfaceView != null) {
            mWindowManager.removeView(mSurfaceView);
            mSurfaceHolder.removeCallback(mSurfaceHolderCallback);
            mSurfaceHolder = null;
            mSurfaceView = null;
        }
    }

    private WindowManager.LayoutParams initLayoutParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.width = 1;
        params.height = 1;
        params.x = 0;
        params.y = 0;

        return params;
    }

    private void setupCameraParameters() {
        Camera.Parameters parameters = mCamera.getParameters();
        Camera.Size pictureSize = getBiggestPictureSize(parameters);

        int deviceOrientation = mOrientationHelper.getDeviceOrientation();
        int jpegOrientation = getJpegOrientation(deviceOrientation);
        parameters.setRotation(jpegOrientation);

        int orientation = mContext.getResources().getConfiguration().orientation;
        if (pictureSize != null) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                parameters.setPictureSize(pictureSize.width, pictureSize.height);
            } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                parameters.setPictureSize(pictureSize.height, pictureSize.width);
            }
        }
        mCamera.setParameters(parameters);
    }

    private int getJpegOrientation(int deviceOrientation) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) {
            return 0;
        }
        Camera.CameraInfo info = new Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_FRONT, info);
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;
        int cameraRotationOffset = info.orientation;

        int jpegOrientation = (cameraRotationOffset - deviceOrientation + 360) % 360;
        return jpegOrientation;
    }

    private Camera.Size getBiggestPictureSize(Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPictureSizes()) {
            if (result == null) {
                result = size;
            } else {
                int resultArea = result.width * result.height;
                int newArea = size.width * size.height;

                if (newArea > resultArea) {
                    result = size;
                }
            }
        }
        return result;
    }

    private void sendEventOnImageFailed(Exception e, String errMessage) {
        Log.e(TAG, errMessage, e);
        mCameraListener.onImageFailed(e, errMessage);
    }
}
