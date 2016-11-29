package codes.evo.snapshot;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import codes.evo.snapshotlib.CameraException;
import codes.evo.snapshotlib.SnapshotMaker;
import codes.evo.snapshotlib.SnapshotMakerCompat;
import codes.evo.snapshotlib.utils.BackgroundWorker;
import codes.evo.snapshotlib.utils.LocalFileStorage;
import codes.evo.snapshotlib.utils.SnapshotSaver;

public class ExampleActivity extends AppCompatActivity {

    private static final String TAG = "ExampleActivity";

    // To start a task, you can use BackgroundWorker.Client or run a background thread in another way
    private BackgroundWorker.Client mBgClient;
    private SnapshotMaker mSnapshotMaker;
    private String mSnapshotName;
    private int mCounter = 0;

    private final SnapshotMaker.CameraListener mCameraListener = new SnapshotMaker.CameraListener() {

        @Override
        public void onImageTaken(byte[] result) {
            showToast(ExampleActivity.this, "onImageTaken");
            // You can use SnapshotSaver or handle an array of bytes (image) another way at pleasure
            SnapshotSaver saver = new SnapshotSaver(result, mSnapshotName);
            saver.setSnapshotListener(mSnapshotListener);
            mBgClient.post(saver);
        }

        @Override
        public void onCameraOpened() {
            showToast(ExampleActivity.this, "onCameraOpened");
        }

        @Override
        public void onCameraClosed() {
            showToast(ExampleActivity.this, "onCameraClosed");
        }

        @Override
        public void onImageFailed(Exception e, String errMessage) {
            showToast(ExampleActivity.this, "onImageFailed : " + errMessage);
        }
    };

    private final SnapshotMaker.SnapshotListener mSnapshotListener =
            new SnapshotMaker.SnapshotListener() {
                @Override
                public void onImageSaved(String photoPath) {
                    showToast(ExampleActivity.this, "onImageSaved to : " + photoPath);
                    // Then you can send the image to the cloud, or some other action
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example);

        // If you'll use our SnapshotSaver, you must initialize LocalFileStorage to save the snapshots on sdCard
        LocalFileStorage.init(this);

        BackgroundWorker backgroundWorker = new BackgroundWorker();
        mBgClient = backgroundWorker.getDefault();
        mSnapshotMaker = SnapshotMakerCompat.get(this, backgroundWorker);
        mSnapshotMaker.requestPermission(this);
        mSnapshotMaker.setCameraListener(mCameraListener);

        Button open = (Button) findViewById(R.id.open_btn);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    mSnapshotMaker.openCamera(ExampleActivity.this);
                } catch (CameraException e) {
                    Log.e(TAG, "Unable to access the camera", e);
                }
            }
        });

        final String name = "/test";
        Button capture = (Button) findViewById(R.id.capture_btn);
        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSnapshotMaker.takeImage();
                mSnapshotName = name + String.valueOf(mCounter);
                mCounter++;
            }
        });

        Button close = (Button) findViewById(R.id.close_btn);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSnapshotMaker.closeCamera();
            }
        });
    }

    public static void showToast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }
}
