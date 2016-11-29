package codes.evo.snapshotlib.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class BackgroundWorker {

    private static final String TAG = "BackgroundWorker";

    private HandlerThread mThread;
    private Client mDefaultClient = new Client();
    private List<Client> mClients = new ArrayList<>();

    public BackgroundWorker() {
        addClient(mDefaultClient);
    }

    public Client getDefault() {
        return mDefaultClient;
    }

    public synchronized void addClient(Client client) {
        if (mClients.contains(client)) {
            throw new RuntimeException("Client is already attached to the worker");
        }

        initThreadIfNeeded();

        mClients.add(client);
        client.setWorker(this);
        client.attach(mThread.getLooper());
    }

    public synchronized void removeClient(Client client) {
        if (!mClients.contains(client)) {
            throw new RuntimeException("Client wasn't attached to the worker");
        }
        mClients.remove(client);
        client.setWorker(null);
        client.detach();
    }

    /**
     * Initializes a worker thread if needed. Return a boolean indicating if a new thread was created
     */
    private synchronized boolean initThreadIfNeeded() {
        if (mThread != null && mThread.isAlive()) {
            return false;
        }
        Log.d(TAG, "Starting ApplicationWorker thread");

        mThread = new HandlerThread("ApplicationWorker", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        mThread.start();
        mThread.setUncaughtExceptionHandler(new SilentUEH());

        for (Client cli : mClients) {
            cli.attach(mThread.getLooper());
        }
        return true;
    }

    public static class Client {

        private BackgroundWorker mWorker;
        private Handler mHandler;

        public Client() {
        }

        public Handler getHandler() {
            checkState();
            return mHandler;
        }

        /**
         * Called by Handler when it's time to process a message
         *
         * @param msg message for processing
         * @return boolean indicating whether to skip default handling
         */
        public boolean handleMessage(Message msg) {
            return false;
        }

        /**
         * Mimics
         */

        public void post(Runnable r) {
            checkState();
            mHandler.post(r);
        }

        public void postDelayed(Runnable r, long millis) {
            checkState();
            mHandler.postDelayed(r, millis);
        }

        public void removeCallbacks(Runnable r) {
            checkState();
            mHandler.removeCallbacks(r);
        }

        public void removeMessages(int what) {
            checkState();
            mHandler.removeMessages(what);
        }

        /**
         * Internals
         */

        private void checkState() {
            if (mWorker == null || mHandler == null) {
                throw new RuntimeException("Client wasn't registered or is detached");
            }
            mWorker.initThreadIfNeeded();
        }

        void attach(Looper looper) {
            mHandler = new Handler(looper) {
                @Override
                public void handleMessage(Message msg) {
                    if (!Client.this.handleMessage(msg)) {
                        super.handleMessage(msg);
                    }
                }
            };
        }

        void detach() {
            mHandler = null;
        }

        void setWorker(BackgroundWorker worker) {
            mWorker = worker;
        }
    }

    static class SilentUEH implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            Log.e(TAG, "ApplicationWorker thread dead", ex);
        }
    }
}
