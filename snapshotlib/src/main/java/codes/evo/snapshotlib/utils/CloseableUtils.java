package codes.evo.snapshotlib.utils;

import android.support.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

class CloseableUtils {

    static boolean close(@Nullable Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
                return true;
            } catch (IOException e) {
                return false;
            }
        } else {
            return false;
        }
    }
}
