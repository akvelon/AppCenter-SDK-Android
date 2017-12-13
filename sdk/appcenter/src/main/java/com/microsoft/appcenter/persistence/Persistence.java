package com.microsoft.appcenter.persistence;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.microsoft.appcenter.ingestion.models.Log;
import com.microsoft.appcenter.ingestion.models.json.LogSerializer;

import java.io.Closeable;
import java.util.List;

/**
 * Abstract class for Persistence service.
 */
public abstract class Persistence implements Closeable {

    /**
     * Storage capacity in number of logs.
     */
    static final int DEFAULT_CAPACITY = 300;

    /**
     * Log serializer override.
     */
    private LogSerializer mLogSerializer;

    /**
     * Writes a log to the storage with the given {@code group}.
     *
     * @param group The group of the storage for the log.
     * @param log   The log to be placed in the storage.
     * @return log identifier from persistence after saving.
     * @throws PersistenceException Exception will be thrown if Persistence cannot write a log to the storage.
     */
    public abstract long putLog(@NonNull String group, @NonNull Log log) throws PersistenceException;

    /**
     * Deletes a log with the give ID from the {@code group}.
     *
     * @param group The group of the storage for logs.
     * @param id    The ID for a set of logs.
     */
    public abstract void deleteLogs(@NonNull String group, @NonNull String id);

    /**
     * Deletes all logs for the given {@code group}.
     *
     * @param group The group of the storage for logs.
     */
    public abstract void deleteLogs(String group);

    /**
     * Gets the number of logs for the given {@code group}.
     *
     * @param group The group of the storage for logs.
     * @return The number of logs for the given {@code group}.
     */
    public abstract int countLogs(@NonNull String group);

    /**
     * Gets an array of logs for the given {@code group}.
     *
     * @param group   The group of the storage for logs.
     * @param limit   The max number of logs to be returned.
     * @param outLogs A list to receive {@link Log} objects.
     * @return An ID for {@code outLogs}. {@code null} if no logs exist.
     */
    @Nullable
    public abstract String getLogs(@NonNull String group, @IntRange(from = 0) int limit, @NonNull List<Log> outLogs);

    /**
     * Clears all associations between logs of the {@code group} and ids returned by {@link #getLogs(String, int, List)}}.
     */
    public abstract void clearPendingLogState();

    /**
     * Gets a {@link LogSerializer}.
     *
     * @return The log serializer instance.
     */
    LogSerializer getLogSerializer() {
        if (mLogSerializer == null) {
            throw new IllegalStateException("logSerializer not configured");
        }
        return mLogSerializer;
    }

    /**
     * Sets a {@link LogSerializer}.
     *
     * @param logSerializer The log serializer instance.
     */
    public void setLogSerializer(@NonNull LogSerializer logSerializer) {
        mLogSerializer = logSerializer;
    }

    /**
     * Thrown when {@link Persistence} cannot write a log to the storage.
     */
    public static class PersistenceException extends Exception {
        public PersistenceException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }
}
