package codes.evo.snapshotlib.utils;

import codes.evo.snapshotlib.SnapshotMaker;

public class SnapshotSaver implements Runnable {

    private final byte[] mSnapshotBytes;
    private final String mSnapshotName;

    private SnapshotMaker.SnapshotListener mListener;

    public SnapshotSaver(byte[] snapshotBytes, String snapshotName) {
        mSnapshotBytes = snapshotBytes;
        mSnapshotName = snapshotName;
    }

    public void setSnapshotListener(SnapshotMaker.SnapshotListener listener) {
        mListener = listener;
    }

    @Override
    public void run() {
        LocalFileStorage.saveMediaBytes(mSnapshotBytes, mSnapshotName);

        String photoPath = LocalFileStorage.getPhotoFilePath(mSnapshotName);
        if (mListener != null) {
            mListener.onImageSaved(photoPath);
        }
    }
}
