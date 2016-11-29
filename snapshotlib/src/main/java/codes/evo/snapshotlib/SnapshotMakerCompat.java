package codes.evo.snapshotlib;

import android.content.Context;
import android.os.Build;

import codes.evo.snapshotlib.utils.BackgroundWorker;

public class SnapshotMakerCompat {

    public static SnapshotMaker get(Context context, BackgroundWorker backgroundWorker) {

        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
            return new SnapshotMakerV1(context, backgroundWorker);
        } else {
            return new SnapshotMakerV2(context, backgroundWorker);
        }
    }
}
