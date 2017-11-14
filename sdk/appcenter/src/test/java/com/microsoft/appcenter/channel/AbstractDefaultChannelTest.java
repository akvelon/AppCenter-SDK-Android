package com.microsoft.appcenter.channel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.microsoft.appcenter.http.ServiceCallback;
import com.microsoft.appcenter.ingestion.models.Device;
import com.microsoft.appcenter.ingestion.models.Log;
import com.microsoft.appcenter.utils.DeviceInfoHelper;
import com.microsoft.appcenter.utils.HandlerUtils;
import com.microsoft.appcenter.utils.IdHelper;
import com.microsoft.appcenter.utils.AppCenterLog;
import com.microsoft.appcenter.utils.UUIDUtils;

import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;

import java.util.ArrayList;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@SuppressWarnings("WeakerAccess")
@PrepareForTest({DefaultChannel.class, IdHelper.class, DeviceInfoHelper.class, AppCenterLog.class, HandlerUtils.class})
public class AbstractDefaultChannelTest {

    static final String TEST_GROUP = "group_test";

    static final long BATCH_TIME_INTERVAL = 500;

    static final int MAX_PARALLEL_BATCHES = 3;

    @Rule
    public PowerMockRule mPowerMockRule = new PowerMockRule();

    @Mock
    protected Handler mHandler;

    @Mock
    protected Handler mCoreHandler;

    static Answer<String> getGetLogsAnswer() {
        return getGetLogsAnswer(-1);
    }

    static Answer<String> getGetLogsAnswer(final int size) {
        return new Answer<String>() {

            @Override
            @SuppressWarnings("unchecked")
            public String answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[2] instanceof ArrayList) {
                    ArrayList logs = (ArrayList) args[2];
                    int length = size >= 0 ? size : (int) args[1];
                    for (int i = 0; i < length; i++) {
                        logs.add(mock(Log.class));
                    }
                }
                return UUIDUtils.randomUUID().toString();
            }
        };
    }

    static Answer<Object> getSendAsyncAnswer() {
        return getSendAsyncAnswer(null);
    }

    static Answer<Object> getSendAsyncAnswer(final Exception e) {
        return new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[3] instanceof ServiceCallback) {
                    if (e == null)
                        ((ServiceCallback) invocation.getArguments()[3]).onCallSucceeded("");
                    else
                        ((ServiceCallback) invocation.getArguments()[3]).onCallFailed(e);
                }
                return null;
            }
        };
    }

    @Before
    public void setUp() throws Exception {
        mockStatic(AppCenterLog.class);
        mockStatic(IdHelper.class, new Returns(UUIDUtils.randomUUID()));
        mockStatic(DeviceInfoHelper.class);
        when(DeviceInfoHelper.getDeviceInfo(any(Context.class))).thenReturn(mock(Device.class));
        whenNew(Handler.class).withParameterTypes(Looper.class).withArguments(Looper.getMainLooper()).thenReturn(mHandler);
        when(mCoreHandler.post(any(Runnable.class))).then(new Answer<Boolean>() {

            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                ((Runnable) invocation.getArguments()[0]).run();
                return true;
            }
        });
        mockStatic(HandlerUtils.class);
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ((Runnable) invocation.getArguments()[0]).run();
                return null;
            }
        }).when(HandlerUtils.class);
        HandlerUtils.runOnUiThread(any(Runnable.class));
    }
}
