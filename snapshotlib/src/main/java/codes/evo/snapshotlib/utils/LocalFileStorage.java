package codes.evo.snapshotlib.utils;

import android.content.Context;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LocalFileStorage {

    private static final String TAG = "LocalFileStorage";
    private static final String PHOTO_EXT = ".jpg";

    private static String MEDIA_PATH;

    public static void init(Context context) {
        File mediaDirExternalStorageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        MEDIA_PATH = mediaDirExternalStorageDir == null ? null : mediaDirExternalStorageDir.getAbsolutePath();
        createNonExistingDir(MEDIA_PATH);
    }

    public static String generateUniqueName() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format((new Date()).getTime());
    }

    public static void saveMediaBytes(byte[] fileContent, String name) {
        File file = getMediaFile(name);
        StreamUtils.save(fileContent, file.getAbsolutePath());
    }

    public static String getPhotoFilePath(String name) {
        return getMediaFilePath(name + PHOTO_EXT);
    }

    private static File getMediaFile(String name) {
        return new File(getPhotoFilePath(name));
    }

    private static String getMediaFilePath(String fileName) {
        return new File(MEDIA_PATH + fileName).getPath();
    }

    private static boolean createNonExistingDir(@Nullable String dirPath) {
        if (dirPath == null) {
            return false;
        }

        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            return true;
        }

        dir.delete();
        dir.mkdirs();
        if (!dir.isDirectory()) {
            Log.e(TAG, "Cannot create directory: " + dirPath);
            return false;
        }
        return true;
    }
}
