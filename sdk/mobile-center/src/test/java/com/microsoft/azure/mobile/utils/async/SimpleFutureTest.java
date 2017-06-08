package com.microsoft.azure.mobile.utils.async;

import com.microsoft.azure.mobile.utils.HandlerUtils;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

public class SimpleFutureTest {

    @Rule
    public PowerMockRule mPowerMockRule = new PowerMockRule();

    @Test
    public void getWithInterruption() throws InterruptedException {
        final DefaultSimpleFuture<Boolean> future = new DefaultSimpleFuture<>();
        final AtomicReference<Boolean> result = new AtomicReference<>();
        Thread thread = new Thread() {

            @Override
            public void run() {
                result.set(future.get());
            }
        };
        thread.start();
        thread.interrupt();
        future.complete(true);
        thread.join();
        assertEquals(true, result.get());
    }

    @Test
    @PrepareForTest(DefaultSimpleFuture.class)
    public void isDoneWithInterruption() throws Exception {
        CountDownLatch latch = mock(CountDownLatch.class);
        whenNew(CountDownLatch.class).withAnyArguments().thenReturn(latch);
        when(latch.await(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException()).thenReturn(true);
        final DefaultSimpleFuture<Boolean> future = new DefaultSimpleFuture<>();
        final AtomicReference<Boolean> result = new AtomicReference<>();
        Thread thread = new Thread() {

            @Override
            public void run() {
                result.set(future.isDone());
            }
        };
        thread.start();
        thread.join();
        assertEquals(true, result.get());
    }

    @Test
    public void completeTwiceIgnored() {
        DefaultSimpleFuture<Integer> future = new DefaultSimpleFuture<>();
        future.complete(1);
        future.complete(2);
        assertEquals(Integer.valueOf(1), future.get());
    }

    @Test
    @PrepareForTest(HandlerUtils.class)
    public void multipleCallbacks() {
        mockStatic(HandlerUtils.class);
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ((Runnable) invocation.getArguments()[0]).run();
                return null;
            }
        }).when(HandlerUtils.class);
        HandlerUtils.runOnUiThread(any(Runnable.class));
        DefaultSimpleFuture<Integer> future = new DefaultSimpleFuture<>();

        @SuppressWarnings("unchecked")
        SimpleConsumer<Integer> function = mock(SimpleConsumer.class);
        future.thenAccept(function);
        future.thenAccept(function);
        future.complete(1);
        verify(function, times(2)).accept(1);

        /* Works also after completion. */
        future.thenAccept(function);
        future.thenAccept(function);
        verify(function, times(4)).accept(1);
    }
}
