package com.microsoft.azure.mobile;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.microsoft.azure.mobile.utils.MobileCenterLog;

import java.io.File;

/**
 * Various constants and meta information loaded from the context.
 **/
public class Constants {

    /**
     * Maximum time interval in milliseconds after which a synchronize will be triggered, regardless of queue size.
     */
    public static final int DEFAULT_TRIGGER_INTERVAL = 3 * 1000;
    /**
     * Number of metrics queue items which will trigger synchronization.
     */
    static final int DEFAULT_TRIGGER_COUNT = 50;
    /**
     * Maximum number of requests being sent for the group.
     */
    static final int DEFAULT_TRIGGER_MAX_PARALLEL_REQUESTS = 3;
    /**
     * Path where crash logs and temporary files are stored.
     */
    public static String FILES_PATH = null;
    /**
     * Flag indicates whether the host application is debuggable or not.
     */
    public static boolean APPLICATION_DEBUGGABLE = false;

    /**
     * Initializes constants from the given context. The context is used to set
     * the package name, version code, and the files path.
     *
     * @param context The context to use. Usually your Activity object.
     */
    public static void loadFromContext(Context context) {
        loadFilesPath(context);
        setDebuggableFlag(context);
    }

    /**
     * Helper method to set the files path. If an exception occurs, the files
     * path will be null!
     *
     * @param context The context to use. Usually your Activity object.
     */
    private static void loadFilesPath(Context context) {
        if (context != null) {
            try {
                /*
                 * The file shouldn't be null, but apparently it still can happen, see
                 * http://code.google.com/p/android/issues/detail?id=8886, Fixed in API 19.
                 */
                File file = context.getFilesDir();
                Constants.FILES_PATH = file.getAbsolutePath();
            } catch (Exception e) {
                MobileCenterLog.error(MobileCenter.LOG_TAG, "Exception thrown when accessing the application filesystem", e);
            }
        }
    }

    /**
     * Helper method to determine whether the host application is debuggable or not.
     *
     * @param context The context to use. Usually your Activity object.
     */
    private static void setDebuggableFlag(Context context) {
        if (context != null && context.getApplicationInfo() != null) {
            APPLICATION_DEBUGGABLE = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) > 0;
        }
    }
}
