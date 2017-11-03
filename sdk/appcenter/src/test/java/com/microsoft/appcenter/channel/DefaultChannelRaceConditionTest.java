package com.microsoft.appcenter.channel;

import android.content.Context;

import com.microsoft.appcenter.CancellationException;
import com.microsoft.appcenter.http.ServiceCall;
import com.microsoft.appcenter.http.ServiceCallback;
import com.microsoft.appcenter.ingestion.IngestionHttp;
import com.microsoft.appcenter.ingestion.models.Log;
import com.microsoft.appcenter.ingestion.models.LogContainer;
import com.microsoft.appcenter.persistence.Persistence;
import com.microsoft.appcenter.utils.HandlerUtils;
import com.microsoft.appcenter.utils.UUIDUtils;

import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import static com.microsoft.appcenter.channel.DefaultChannel.CLEAR_BATCH_SIZE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doAnswer;

public class DefaultChannelRaceConditionTest extends AbstractDefaultChannelTest {

    @Test
    public void disabledWhileSendingLogs() throws Exception {

        /* Set up mocking. */
        final Semaphore beforeCallSemaphore = new Semaphore(0);
        final Semaphore afterCallSemaphore = new Semaphore(0);
        Persistence mockPersistence = mock(Persistence.class);
        when(mockPersistence.countLogs(anyString())).thenReturn(1);
        when(mockPersistence.getLogs(anyString(), eq(1), anyListOf(Log.class))).then(getGetLogsAnswer(1));
        when(mockPersistence.getLogs(anyString(), eq(CLEAR_BATCH_SIZE), anyListOf(Log.class))).then(getGetLogsAnswer(0));
        IngestionHttp mockIngestion = mock(IngestionHttp.class);
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(final InvocationOnMock invocation) throws Throwable {
                new Thread() {

                    @Override
                    public void run() {
                        beforeCallSemaphore.acquireUninterruptibly();
                        ((Runnable) invocation.getArguments()[0]).run();
                        afterCallSemaphore.release();
                    }
                }.start();
                return null;
            }
        }).when(HandlerUtils.class);
        HandlerUtils.runOnUiThread(any(Runnable.class));

        /* Simulate enable module then disable. */
        DefaultChannel channel = new DefaultChannel(mock(Context.class), UUIDUtils.randomUUID().toString(), mockPersistence, mockIngestion, mCoreHandler);
        Channel.GroupListener listener = mock(Channel.GroupListener.class);
        channel.addGroup(TEST_GROUP, 1, BATCH_TIME_INTERVAL, MAX_PARALLEL_BATCHES, listener);
        channel.setEnabled(false);
        channel.setEnabled(true);

        /* Release call to mock ingestion. */
        beforeCallSemaphore.release();

        /* Wait for callback ingestion. */
        afterCallSemaphore.acquireUninterruptibly();

        /* Verify ingestion not sent. */
        verify(mockIngestion, never()).sendAsync(anyString(), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class));
    }

    @Test
    public void disabledWhileHandlingIngestionSuccess() throws Exception {

        /* Set up mocking. */
        final Semaphore beforeCallSemaphore = new Semaphore(0);
        final Semaphore afterCallSemaphore = new Semaphore(0);
        Persistence mockPersistence = mock(Persistence.class);
        when(mockPersistence.countLogs(anyString())).thenReturn(1);
        when(mockPersistence.getLogs(anyString(), eq(1), anyListOf(Log.class))).then(getGetLogsAnswer(1));
        when(mockPersistence.getLogs(anyString(), eq(CLEAR_BATCH_SIZE), anyListOf(Log.class))).then(getGetLogsAnswer(0));
        IngestionHttp mockIngestion = mock(IngestionHttp.class);
        when(mockIngestion.sendAsync(anyString(), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class))).then(new Answer<Object>() {

            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                new Thread() {

                    @Override
                    public void run() {
                        beforeCallSemaphore.acquireUninterruptibly();
                        ((ServiceCallback) invocation.getArguments()[3]).onCallSucceeded("");
                        afterCallSemaphore.release();
                    }
                }.start();
                return mock(ServiceCall.class);
            }
        });

        /* Simulate enable module then disable. */
        DefaultChannel channel = new DefaultChannel(mock(Context.class), UUIDUtils.randomUUID().toString(), mockPersistence, mockIngestion, mCoreHandler);
        Channel.GroupListener listener = mock(Channel.GroupListener.class);
        channel.addGroup(TEST_GROUP, 1, BATCH_TIME_INTERVAL, MAX_PARALLEL_BATCHES, listener);
        channel.setEnabled(false);
        channel.setEnabled(true);

        /* Release call to mock ingestion. */
        beforeCallSemaphore.release();

        /* Wait for callback ingestion. */
        afterCallSemaphore.acquireUninterruptibly();

        /* Verify handling success was ignored. */
        verify(listener, never()).onSuccess(any(Log.class));
        verify(listener).onFailure(any(Log.class), argThat(new ArgumentMatcher<Exception>() {

            @Override
            public boolean matches(Object argument) {
                return argument instanceof CancellationException;
            }
        }));
    }

    @Test
    public void disabledWhileHandlingIngestionFailure() throws Exception {

        /* Set up mocking. */
        final Semaphore beforeCallSemaphore = new Semaphore(0);
        final Semaphore afterCallSemaphore = new Semaphore(0);
        Persistence mockPersistence = mock(Persistence.class);
        when(mockPersistence.countLogs(anyString())).thenReturn(1);
        when(mockPersistence.getLogs(anyString(), eq(1), anyListOf(Log.class))).then(getGetLogsAnswer(1));
        when(mockPersistence.getLogs(anyString(), eq(CLEAR_BATCH_SIZE), anyListOf(Log.class))).then(getGetLogsAnswer(0));
        IngestionHttp mockIngestion = mock(IngestionHttp.class);
        final Exception mockException = new IOException();
        when(mockIngestion.sendAsync(anyString(), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class))).then(new Answer<Object>() {

            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                new Thread() {

                    @Override
                    public void run() {
                        beforeCallSemaphore.acquireUninterruptibly();
                        ((ServiceCallback) invocation.getArguments()[3]).onCallFailed(mockException);
                        afterCallSemaphore.release();
                    }
                }.start();
                return mock(ServiceCall.class);
            }
        });

        /* Simulate enable module then disable. */
        DefaultChannel channel = new DefaultChannel(mock(Context.class), UUIDUtils.randomUUID().toString(), mockPersistence, mockIngestion, mCoreHandler);
        Channel.GroupListener listener = mock(Channel.GroupListener.class);
        channel.addGroup(TEST_GROUP, 1, BATCH_TIME_INTERVAL, MAX_PARALLEL_BATCHES, listener);
        channel.setEnabled(false);
        channel.setEnabled(true);

        /* Release call to mock ingestion. */
        beforeCallSemaphore.release();

        /* Wait for callback ingestion. */
        afterCallSemaphore.acquireUninterruptibly();

        /* Verify handling error was ignored. */
        verify(listener, never()).onFailure(any(Log.class), eq(mockException));
        verify(listener).onFailure(any(Log.class), argThat(new ArgumentMatcher<Exception>() {

            @Override
            public boolean matches(Object argument) {
                return argument instanceof CancellationException;
            }
        }));
    }
}
