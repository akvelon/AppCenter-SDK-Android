package com.microsoft.azure.mobile;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.microsoft.azure.mobile.channel.Channel;
import com.microsoft.azure.mobile.ingestion.models.json.LogFactory;
import com.microsoft.azure.mobile.utils.HandlerUtils;
import com.microsoft.azure.mobile.utils.MobileCenterLog;
import com.microsoft.azure.mobile.utils.async.DefaultMobileCenterFuture;
import com.microsoft.azure.mobile.utils.async.MobileCenterFuture;
import com.microsoft.azure.mobile.utils.storage.StorageHelper;

import java.util.Map;

import static com.microsoft.azure.mobile.Constants.DEFAULT_TRIGGER_COUNT;
import static com.microsoft.azure.mobile.Constants.DEFAULT_TRIGGER_INTERVAL;
import static com.microsoft.azure.mobile.Constants.DEFAULT_TRIGGER_MAX_PARALLEL_REQUESTS;
import static com.microsoft.azure.mobile.MobileCenter.LOG_TAG;
import static com.microsoft.azure.mobile.utils.PrefStorageConstants.KEY_ENABLED;

public abstract class AbstractMobileCenterService implements MobileCenterService {

    /**
     * Separator for preference key.
     */
    private static final String PREFERENCE_KEY_SEPARATOR = "_";

    /**
     * Channel instance.
     */
    protected Channel mChannel;

    /**
     * Background thread handler.
     */
    private MobileCenterHandler mHandler;

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

    /**
     * Help implementing static isEnabled() for services with future.
     *
     * @return future with result being <code>true</code> if enabled, <code>false</code> otherwise.
     */
    protected synchronized MobileCenterFuture<Boolean> isInstanceEnabledAsync() {
        final DefaultMobileCenterFuture<Boolean> future = new DefaultMobileCenterFuture<>();
        postAsyncGetter(new Runnable() {

            @Override
            public void run() {
                future.complete(true);
            }
        }, future, false);
        return future;
    }

    /**
     * Help implementing static setEnabled() for services with future.
     *
     * @param enabled true to enable, false to disable.
     * @return future with null result to monitor when the operation completes.
     */
    protected final synchronized MobileCenterFuture<Void> setInstanceEnabledAsync(final boolean enabled) {

        /*
         * We need to execute this while the service is disabled to enable it again,
         * but not if core disabled... Hence the parameters in post.
         */
        final DefaultMobileCenterFuture<Void> future = new DefaultMobileCenterFuture<>();
        final Runnable coreDisabledRunnable = new Runnable() {

            @Override
            public void run() {
                MobileCenterLog.error(LOG_TAG, "Mobile Center SDK is disabled.");
                future.complete(null);
            }
        };
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                setInstanceEnabled(enabled);
                future.complete(null);
            }
        };
        if (!post(runnable, coreDisabledRunnable, runnable)) {
            future.complete(null);
        }
        return future;
    }

    @Override
    public synchronized boolean isInstanceEnabled() {
        return StorageHelper.PreferencesStorage.getBoolean(getEnabledPreferenceKey(), true);
    }

    @Override
    public synchronized void setInstanceEnabled(boolean enabled) {

        /* Nothing to do if state does not change. */
        if (enabled == isInstanceEnabled()) {
            MobileCenterLog.info(getLoggerTag(), String.format("%s service has already been %s.", getServiceName(), enabled ? "enabled" : "disabled"));
            return;
        }

        /* Initialize channel group. */
        String groupName = getGroupName();
        if (groupName != null) {

            /* Register service to channel on enabling. */
            if (enabled) {
                mChannel.addGroup(groupName, getTriggerCount(), getTriggerInterval(), getTriggerMaxParallelRequests(), getChannelListener());
            }

            /* Otherwise, clear all persisted logs and remove a group for the service. */
            else {
                mChannel.clear(groupName);
                mChannel.removeGroup(groupName);
            }
        }

        /* Save new state. */
        StorageHelper.PreferencesStorage.putBoolean(getEnabledPreferenceKey(), enabled);
        MobileCenterLog.info(getLoggerTag(), String.format("%s service has been %s.", getServiceName(), enabled ? "enabled" : "disabled"));

        /* Allow sub-class to handle state change. */
        applyEnabledState(enabled);
    }

    protected synchronized void applyEnabledState(boolean enabled) {

        /* Optional callback to react to enabled state change. */
    }

    @Override
    public final synchronized void onStarting(@NonNull MobileCenterHandler handler) {

        /*
         * The method is final just to avoid a sub-class start using the handler now,
         * it is not supported and could cause null pointer exception.
         */
        mHandler = handler;
    }

    @Override
    public synchronized void onStarted(@NonNull Context context, @NonNull String appSecret, @NonNull Channel channel) {
        String groupName = getGroupName();
        boolean enabled = isInstanceEnabled();
        if (groupName != null) {
            channel.removeGroup(groupName);

            /* Add a group to the channel if the service is enabled */
            if (enabled)
                channel.addGroup(groupName, getTriggerCount(), getTriggerInterval(), getTriggerMaxParallelRequests(), getChannelListener());

            /* Otherwise, clear all persisted logs for the service. */
            else
                channel.clear(groupName);
        }
        mChannel = channel;
        if (enabled) {
            applyEnabledState(true);
        }
    }

    @Override
    public Map<String, LogFactory> getLogFactories() {
        return null;
    }

    /**
     * Gets a name of group for the service.
     *
     * @return The group name.
     */
    protected abstract String getGroupName();

    /**
     * Gets a tag of the logger.
     *
     * @return The tag of the logger.
     */
    protected abstract String getLoggerTag();

    @SuppressWarnings("WeakerAccess")
    @NonNull
    protected String getEnabledPreferenceKey() {
        return KEY_ENABLED + PREFERENCE_KEY_SEPARATOR + getServiceName();
    }

    /**
     * Gets a number of logs which will trigger synchronization.
     *
     * @return A number of logs.
     */
    protected int getTriggerCount() {
        return DEFAULT_TRIGGER_COUNT;
    }

    /**
     * Gets a maximum time interval in milliseconds after which a synchronize will be triggered, regardless of queue size
     *
     * @return A maximum time interval in milliseconds.
     */
    @SuppressWarnings("WeakerAccess")
    protected int getTriggerInterval() {
        return DEFAULT_TRIGGER_INTERVAL;
    }

    /**
     * Gets a maximum number of requests being sent for the group.
     *
     * @return A maximum number of requests.
     */
    @SuppressWarnings("WeakerAccess")
    protected int getTriggerMaxParallelRequests() {
        return DEFAULT_TRIGGER_MAX_PARALLEL_REQUESTS;
    }

    /**
     * Gets a listener which will be called when channel completes synchronization.
     *
     * @return A listener for channel
     */
    @SuppressWarnings({"WeakerAccess", "SameReturnValue"})
    protected Channel.GroupListener getChannelListener() {
        return null;
    }

    /**
     * Post a command in background.
     *
     * @param runnable command.
     */
    protected synchronized void post(Runnable runnable) {
        post(runnable, null, null);
    }

    /**
     * Post a command in background.
     *
     * @param runnable                command.
     * @param coreDisabledRunnable    optional alternate command if core is disabled.
     * @param serviceDisabledRunnable optional alternate command if this service is disabled.
     * @return false if core not configured (no handler ready yet), true otherwise.
     */
    protected synchronized boolean post(final Runnable runnable, final Runnable coreDisabledRunnable, final Runnable serviceDisabledRunnable) {
        if (mHandler == null) {
            MobileCenterLog.error(LOG_TAG, getServiceName() + " needs to be started before it can be used.");
            return false;
        } else {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (isInstanceEnabled()) {
                        runnable.run();
                    } else if (serviceDisabledRunnable != null) {
                        serviceDisabledRunnable.run();
                    } else {
                        MobileCenterLog.info(LOG_TAG, getServiceName() + " service disabled, discarding calls.");
                    }
                }
            }, coreDisabledRunnable);
            return true;
        }
    }

    /**
     * Helper method to handle getter methods in services.
     *
     * @param runnable                    command to run if service is enabled.
     * @param future                      future to complete the result of the async operation.
     * @param valueIfDisabledOrNotStarted value to use for the future async operation result if service is disabled or not started or MobileCenter not started.
     * @param <T>                         getter value type.
     */
    protected synchronized <T> void postAsyncGetter(final Runnable runnable, final DefaultMobileCenterFuture<T> future, final T valueIfDisabledOrNotStarted) {
        Runnable disabledOrNotStartedRunnable = new Runnable() {

            @Override
            public void run() {

                /* Same runnable is used whether Mobile Center or the service is disabled or not started. */
                future.complete(valueIfDisabledOrNotStarted);
            }
        };
        if (!post(new Runnable() {

            @Override
            public void run() {
                runnable.run();
            }
        }, disabledOrNotStartedRunnable, disabledOrNotStartedRunnable)) {

            /* MobileCenter is not configured if we reach this. */
            disabledOrNotStartedRunnable.run();
        }
    }

    /**
     * Like {{@link #post(Runnable)}} but also post back in U.I. thread.
     * Use this for example to manage life cycle callbacks to make sure SDK is started and that
     * every operation runs in order.
     *
     * This method will not SDK is disabled, the purpose is for internal commands, not APIs.
     *
     * @param runnable command to run.
     */
    protected synchronized void postOnUiThread(final Runnable runnable) {

        /*
         * We don't try to optimize with if channel if not null as there could be race conditions:
         * If onResume was queued, then onStarted called, onResume will be next in queue and thus
         * onPause could be called between the queued onStarted and the queued onResume.
         */
        post(new Runnable() {

            @Override
            public void run() {

                /* And make sure we run the original command on U.I. thread. */
                HandlerUtils.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        runIfEnabled(runnable);
                    }
                });
            }
        }, new Runnable() {

            @Override
            public void run() {

                /* Avoid logging SDK disabled by providing an empty command. */
            }
        }, null);
    }

    /**
     * Run the command only if service is enabled.
     * The method is top level just because code coverage when using synchronized.
     *
     * @param runnable command to run.
     */
    private synchronized void runIfEnabled(Runnable runnable) {
        if (isInstanceEnabled()) {
            runnable.run();
        }
    }
}
