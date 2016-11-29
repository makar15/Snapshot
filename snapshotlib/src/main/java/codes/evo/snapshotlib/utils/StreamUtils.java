package codes.evo.snapshotlib.utils;

import android.support.annotation.WorkerThread;

import java.io.FileOutputStream;
import java.io.IOException;

class StreamUtils {

    @WorkerThread
    static void save(byte[] data, String path) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            out.write(data);
        } catch (IOException ignored) {
        } finally {
            CloseableUtils.close(out);
        }
    }
}
