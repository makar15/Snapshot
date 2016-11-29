package codes.evo.snapshotlib;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import codes.evo.snapshotlib.utils.BackgroundWorker;
import codes.evo.snapshotlib.utils.OrientationHelper;
import codes.evo.snapshotlib.utils.PermissionUtils;

@TargetApi(21)
public class SnapshotMakerV2 implements SnapshotMaker {
    private static final String TAG = "SnapshotMakerV2";
    private static final int PERMISSION_REQUEST_CODE = 177;

    private final BackgroundWorker.Client mBgClient;
    private final CameraManager mCameraManager;
    private final OrientationHelper mOrientationHelper;

    private ImageReader mImageReader;
    private CameraDevice mCamera;
    private CameraCaptureSession mCaptureSession;
    private CameraListener mCameraListener;

    private CameraState mCurrentState = CameraState.CLOSE;
    private boolean mIsRequestTakeImage;
    private boolean mIsRequestCloseCamera;

    private final CameraDevice.StateCallback mCameraStateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d(TAG, "Successfully opened camera");
                    mCamera = camera;
                    try {
                        createCaptureSession();
                    } catch (CameraException e) {
                        sendEventOnImageFailed(e, "Camera was disconnected. ");
                        handleState(CameraState.CLOSE);
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.e(TAG, "Camera was disconnected");
                    mCamera = camera;
                    handleState(CameraState.CLOSE);
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    String errMessage = "State error on device " + camera.getId() + ": code :" + error + ". ";
                    sendEventOnImageFailed(new CameraException(errMessage), errMessage);
                    mCamera = camera;
                    handleState(CameraState.CLOSE);
                }
            };

    private final CameraCaptureSession.StateCallback mCaptureSessionListener =
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "Finished configuring camera outputs");
                    mCaptureSession = session;
                    try {
                        CaptureRequest.Builder requester = mCamera
                                .createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        requester.addTarget(mImageReader.getSurface());
                        mCaptureSession.capture(requester.build(), null, null);
                    } catch (CameraAccessException e) {
                        sendEventOnImageFailed(e, "Failed to get actual capture request. ");
                        handleState(CameraState.CLOSE);
                    }
                    if (mOrientationHelper.canDetectOrientation()) {
                        Log.d(TAG, "Can detect orientation");
                        mOrientationHelper.enable();
                    }

                    if (mCameraListener != null) {
                        mCameraListener.onCameraOpened();
                    }
                    handleState(CameraState.OPEN);
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    String errMessage = "Configuration error on device : " + mCamera.getId() + ". ";
                    sendEventOnImageFailed(new CameraException(errMessage), errMessage);
                    handleState(CameraState.CLOSE);
                }
            };

    private final ImageReader.OnImageAvailableListener mImageCaptureListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                        String errMessage = "Do not access to external storage sources. ";
                        sendEventOnImageFailed(new CameraException(errMessage), errMessage);
                        handleState(CameraState.CLOSE);
                        return;
                    }
                    if (mCameraListener != null) {
                        Image image = reader.acquireLatestImage();
                        mCameraListener.onImageTaken(imageToByteArray(image));
                        image.close();
                    }
                    handleState(CameraState.OPEN);
                }
            };

    public SnapshotMakerV2(Context context, BackgroundWorker backgroundWorker) {
        mBgClient = backgroundWorker.getDefault();
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mOrientationHelper = new OrientationHelper(context, SensorManager.SENSOR_DELAY_NORMAL);
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
                if (mCaptureSession != null) {
                    try {
                        mCaptureSession.abortCaptures();
                        mCaptureSession.close();
                        mCaptureSession = null;
                    } catch (CameraAccessException e) {
                        sendEventOnImageFailed(e, "Camera device is no connected or has " +
                                "encountered a fatal error. ");
                    }
                }
                if (mCamera != null) {
                    mCamera.close();
                    mCamera = null;
                    if (mCameraListener != null) {
                        mCameraListener.onCameraClosed();
                    }
                }
                if (mImageReader != null) {
                    mImageReader.close();
                    mImageReader = null;
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
                try {
                    mImageReader.setOnImageAvailableListener(mImageCaptureListener, mBgClient.getHandler());
                    CaptureRequest.Builder requester =
                            mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    requester.addTarget(mImageReader.getSurface());
                    requester.set(CaptureRequest.JPEG_ORIENTATION,
                            getJpegOrientation(mOrientationHelper.getDeviceOrientation()));

                    if (mCaptureSession == null) {
                        throw new CameraException("Session has been closed. " +
                                "Failed to file actual capture request");
                    }
                    mCaptureSession.capture(requester.build(), null, null);

                } catch (CameraAccessException | CameraException e) {
                    sendEventOnImageFailed(e, "Failed to get actual capture request. ");
                    handleState(CameraState.CLOSE);
                }
                break;
        }

        mCurrentState = newState;
    }

    @Override
    public void openCamera(Context context) throws CameraException {
        if (!PermissionUtils.hasPermission(context, Manifest.permission.CAMERA)) {
            Log.e(TAG, "You have not permission : " + Manifest.permission.CAMERA);
            return;
        }

        if (mCurrentState != CameraState.CLOSE) {
            throw new CameraException("Camera is already open");
        }

        Log.d(TAG, "Open camera");
        String frontCameraId = null;
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                Integer feature = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (feature == null || feature != CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                Log.d(TAG, "Found a front-facing camera");
                frontCameraId = cameraId;

                StreamConfigurationMap streamCfgMap = characteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (streamCfgMap == null) {
                    // skip strange cameras
                    continue;
                }

                Size largestSize = Collections.max(Arrays.asList(
                        streamCfgMap.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());

                Log.d(TAG, "Capture size: " + largestSize);
                mImageReader = ImageReader.newInstance(largestSize.getWidth(),
                        largestSize.getHeight(), ImageFormat.JPEG, 2);
                break;
            }

            if (frontCameraId != null) {
                mCameraManager.openCamera(frontCameraId, mCameraStateCallback, mBgClient.getHandler());
            } else {
                handleState(CameraState.CLOSE);
                throw new CameraException("Not found a front-facing camera");
            }
        } catch (CameraAccessException e) {
            sendEventOnImageFailed(e, "Unable to list cameras or open the front camera. ");
            handleState(CameraState.CLOSE);
        }
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

    /**
     * Activity only need for request permission on create snapshots, starting with Android.M
     * You must be sure that you have permission to capture images.
     * Method requestPermissions need call once
     *
     * @param activity
     */
    @Override
    public void requestPermission(@NonNull Activity activity) {
        if (!PermissionUtils.hasPermission(activity, Manifest.permission.CAMERA)) {
            PermissionUtils.requestPermission(activity, Manifest.permission.CAMERA, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void setCameraListener(CameraListener listener) {
        mCameraListener = listener;
    }

    private void createCaptureSession() throws CameraException {
        if (mImageReader != null) {
            List<Surface> outputs = new ArrayList<>();
            outputs.add(mImageReader.getSurface());

            try {
                mCamera.createCaptureSession(outputs, mCaptureSessionListener, mBgClient.getHandler());
            } catch (CameraAccessException e) {
                sendEventOnImageFailed(e, "Failed to create a capture session. ");
                handleState(CameraState.CLOSE);
            }
        } else {
            handleState(CameraState.CLOSE);
            throw new CameraException("ImageReader didn't exist when trying capture image");
        }
    }

    private byte[] imageToByteArray(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] snapshotBytes = new byte[buffer.remaining()];
        buffer.get(snapshotBytes);
        return snapshotBytes;
    }

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private int getJpegOrientation(int deviceOrientation) throws CameraAccessException {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) {
            return 0;
        }
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCamera.getId());
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;
        deviceOrientation = -deviceOrientation;

        int jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360;
        return jpegOrientation;
    }

    private void sendEventOnImageFailed(Exception e, String errMessage) {
        Log.e(TAG, errMessage, e);
        mCameraListener.onImageFailed(e, errMessage);
    }
}
