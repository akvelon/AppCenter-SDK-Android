package com.microsoft.azure.mobile.utils.async;

import com.microsoft.azure.mobile.utils.HandlerUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link SimpleFuture}.
 *
 * @param <T> result type.
 */
public class DefaultSimpleFuture<T> implements SimpleFuture<T> {

    /**
     * Lock used to wait or monitor result.
     */
    private final CountDownLatch mLatch = new CountDownLatch(1);

    /**
     * Result
     */
    private T mResult;

    /**
     * Callbacks from thenAccept waiting for result
     */
    private Collection<SimpleConsumer<T>> mFunctions;

    @Override
    public T get() {
        while (true) {
            try {
                mLatch.await();
                break;
            } catch (InterruptedException ignored) {
            }
        }
        return mResult;
    }

    @Override
    public boolean isDone() {
        while (true) {
            try {
                return mLatch.await(0, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public synchronized void thenAccept(final SimpleConsumer<T> function) {
        if (isDone()) {
            HandlerUtils.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    function.apply(mResult);
                }
            });
        } else {
            if (mFunctions == null) {
                mFunctions = new LinkedList<>();
            }
            mFunctions.add(function);
        }
    }

    /**
     * Set result.
     */
    public synchronized void complete(final T value) {
        if (!isDone()) {
            mResult = value;
            mLatch.countDown();
            if (mFunctions != null) {
                HandlerUtils.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        /* No need to synchronize anymore as mFunctions cannot be modified anymore. */
                        for (SimpleConsumer<T> function : mFunctions) {
                            function.apply(value);
                        }
                        mFunctions = null;
                    }
                });
            }
        }
    }

}
