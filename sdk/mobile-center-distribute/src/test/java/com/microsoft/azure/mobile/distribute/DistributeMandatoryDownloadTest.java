package com.microsoft.azure.mobile.distribute;

import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;

import com.microsoft.azure.mobile.utils.HandlerUtils;
import com.microsoft.azure.mobile.utils.storage.StorageHelper.PreferencesStorage;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;

import java.util.concurrent.Semaphore;

import static com.microsoft.azure.mobile.distribute.DistributeConstants.CHECK_PROGRESS_TIME_INTERVAL_IN_MILLIS;
import static com.microsoft.azure.mobile.distribute.DistributeConstants.DOWNLOAD_STATE_INSTALLING;
import static com.microsoft.azure.mobile.distribute.DistributeConstants.HANDLER_TOKEN_CHECK_PROGRESS;
import static com.microsoft.azure.mobile.distribute.DistributeConstants.MEBIBYTE_IN_BYTES;
import static com.microsoft.azure.mobile.distribute.DistributeConstants.PREFERENCE_KEY_DOWNLOAD_STATE;
import static com.microsoft.azure.mobile.distribute.DistributeConstants.PREFERENCE_KEY_RELEASE_DETAILS;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@PrepareForTest({SystemClock.class, HandlerUtils.class})
public class DistributeMandatoryDownloadTest extends AbstractDistributeAfterDownloadTest {

    @Mock
    private ProgressDialog mProgressDialog;

    @Mock
    private Handler mHandler;

    @Before
    public void setUpDownload() throws Exception {

        /* Mock some dialog methods. */
        whenNew(ProgressDialog.class).withAnyArguments().thenReturn(mProgressDialog);
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                when(mProgressDialog.isIndeterminate()).thenReturn((Boolean) invocation.getArguments()[0]);
                return null;
            }
        }).when(mProgressDialog).setIndeterminate(anyBoolean());
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Mockito.when(mDialog.isShowing()).thenReturn(true);
                return null;
            }
        }).when(mDialog).show();
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Mockito.when(mDialog.isShowing()).thenReturn(false);
                return null;
            }
        }).when(mDialog).hide();

        /* Mock time for Handler.post. */
        mockStatic(SystemClock.class);
        when(SystemClock.uptimeMillis()).thenReturn(1L);

        /* Mock Handler. */
        mockStatic(HandlerUtils.class);
        when(mHandler.postAtTime(any(Runnable.class), eq(HANDLER_TOKEN_CHECK_PROGRESS), anyLong())).then(new Answer<Boolean>() {

            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                ((Runnable) invocation.getArguments()[0]).run();
                return true;
            }
        });
        when(HandlerUtils.getMainHandler()).thenReturn(mHandler);
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ((Runnable) invocation.getArguments()[0]).run();
                return null;
            }
        }).when(HandlerUtils.class);
        HandlerUtils.runOnUiThread(any(Runnable.class));

        /* Set up common download test. */
        setUpDownload(true);
    }

    @NonNull
    private Cursor mockProgressCursor(long progress) {
        Cursor cursor = mock(Cursor.class);
        when(mDownloadManager.query(any(DownloadManager.Query.class))).thenReturn(cursor);
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)).thenReturn(0);
        when(cursor.getInt(0)).thenReturn(DownloadManager.STATUS_RUNNING);
        when(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).thenReturn(1);
        when(cursor.getLong(1)).thenReturn(progress);
        when(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).thenReturn(2);
        when(cursor.getLong(2)).thenReturn(progress > 0 ? (long) (100 * MEBIBYTE_IN_BYTES) : -1);
        return cursor;
    }

    @Test
    public void longMandatoryDownloadAndInstallAcrossRestarts() throws Exception {

        /* Dialog shown upon clicking on download. */
        verify(mProgressDialog).setCancelable(false);
        verify(mProgressDialog).show();

        /* Mock initial progress where file size is still unknown. */
        Cursor cursor = mockProgressCursor(-1);
        waitDownloadTask();
        waitCheckDownloadTask();
        verify(cursor).close();
        verify(mHandler).postAtTime(any(Runnable.class), eq(HANDLER_TOKEN_CHECK_PROGRESS), eq(CHECK_PROGRESS_TIME_INTERVAL_IN_MILLIS + 1));
        verify(mProgressDialog, never()).setProgress(anyInt());

        /* Mock some progress. */
        mockProgressCursor((long) (17 * MEBIBYTE_IN_BYTES));
        waitCheckDownloadTask();
        verify(mProgressDialog).setProgress(17);

        /* Mock further progress. */
        mockProgressCursor((long) (42 * MEBIBYTE_IN_BYTES));
        waitCheckDownloadTask();
        verify(mProgressDialog).setProgress(42);

        /* Pause hides dialog and pauses updates. */
        Distribute.getInstance().onActivityPaused(mActivity);
        verify(mProgressDialog).hide();
        verify(mHandler).removeCallbacksAndMessages(HANDLER_TOKEN_CHECK_PROGRESS);

        /* Unblock current task that will not reschedule a new one. */
        waitCheckDownloadTask();

        /* Check no more timer and progress update while paused. */
        verify(mProgressDialog).setProgress(42);
        verify(mHandler, times(3)).postAtTime(any(Runnable.class), eq(HANDLER_TOKEN_CHECK_PROGRESS), eq(CHECK_PROGRESS_TIME_INTERVAL_IN_MILLIS + 1));

        /* Reusing dialog on resume. */
        Distribute.getInstance().onActivityResumed(mActivity);
        verify(mProgressDialog, times(2)).show();

        /* On restart progress is restored. */
        mProgressDialog = mock(ProgressDialog.class);
        whenNew(ProgressDialog.class).withAnyArguments().thenReturn(mProgressDialog);
        restartProcessAndSdk();

        /* Unblock the previous task now that we are paused. */
        waitCheckDownloadTask();

        /* Resume shows a new dialog as process restarted. */
        Distribute.getInstance().onActivityResumed(mActivity);
        verify(mProgressDialog).show();
        waitCheckDownloadTask();
        verify(mProgressDialog).setProgress(42);

        /* Download eventually completes: show install U.I. */
        completeDownload();
        mockSuccessCursor();
        Intent installIntent = mockInstallIntent();
        waitCheckDownloadTask();
        waitCheckDownloadTask();
        verify(mContext).startActivity(installIntent);
        verifyStatic();
        PreferencesStorage.putInt(PREFERENCE_KEY_DOWNLOAD_STATE, DOWNLOAD_STATE_INSTALLING);
        verifyNoMoreInteractions(mNotificationManager);

        /* Showing install U.I. pauses app. */
        Distribute.getInstance().onActivityPaused(mActivity);

        /* We also display mandatory install dialog if user goes back to app. */
        Distribute.getInstance().onActivityResumed(mActivity);

        /* Check dialog shown. */
        ArgumentCaptor<DialogInterface.OnClickListener> clickListener = ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        verify(mDialogBuilder).setPositiveButton(eq(R.string.mobile_center_distribute_install), clickListener.capture());
        clickListener.getValue().onClick(mDialog, DialogInterface.BUTTON_POSITIVE);
        waitCheckDownloadTask();
        verify(mContext, times(2)).startActivity(installIntent);

        /* Showing install U.I. pauses app. */
        Distribute.getInstance().onActivityPaused(mActivity);

        /* Pause/resume leave existing dialog intact. */
        Distribute.getInstance().onActivityResumed(mActivity);
        verify(mDialogBuilder).setPositiveButton(eq(R.string.mobile_center_distribute_install), clickListener.capture());

        /* If we restart the app process, it will display install U.I. again skipping dialog. */
        restartProcessAndSdk();
        Distribute.getInstance().onActivityResumed(mActivity);
        waitCheckDownloadTask();
        verify(mContext, times(3)).startActivity(installIntent);

        /* Eventually discard download only if application updated. */
        PackageInfo packageInfo = mock(PackageInfo.class);
        packageInfo.lastUpdateTime = Long.MAX_VALUE;
        when(mPackageManager.getPackageInfo(mContext.getPackageName(), 0)).thenReturn(packageInfo);
        restartProcessAndSdk();
        Distribute.getInstance().onActivityResumed(mActivity);
        verify(mDownloadManager).remove(DOWNLOAD_ID);

        /* Check no more dialog displayed. */
        verify(mDialogBuilder).setPositiveButton(eq(R.string.mobile_center_distribute_install), clickListener.capture());

        /* And that we don't prompt install anymore. */
        verify(mContext, times(3)).startActivity(installIntent);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void disabledBeforeClickOnDialogInstall() throws Exception {

        /* Unblock download. */
        waitDownloadTask();

        /* Complete download. */
        completeDownload();
        mockSuccessCursor();
        Intent installIntent = mockInstallIntent();
        waitCheckDownloadTask();
        waitCheckDownloadTask();
        verify(mContext).startActivity(installIntent);

        /* Cancel install to go back to app. */
        Distribute.getInstance().onActivityPaused(mActivity);
        Distribute.getInstance().onActivityResumed(mActivity);

        /* Verify install dialog shown. */
        ArgumentCaptor<DialogInterface.OnClickListener> clickListener = ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        verify(mDialogBuilder).setPositiveButton(eq(R.string.mobile_center_distribute_install), clickListener.capture());

        /* Disable SDK. */
        Distribute.setEnabled(false);

        /* Click. */
        clickListener.getValue().onClick(mDialog, DialogInterface.BUTTON_POSITIVE);

        /* Verify disabled. */
        verify(mDownloadManager).remove(DOWNLOAD_ID);
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);
    }

    @Test
    public void startActivityButDisabledAfterCheckpoint() throws Exception {

        /* Simulate async task. */
        waitDownloadTask();

        /* Process download completion. */
        mockSuccessCursor();
        final Intent installIntent = mockInstallIntent();
        final Semaphore beforeStartingActivityLock = new Semaphore(0);
        final Semaphore disabledLock = new Semaphore(0);
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                beforeStartingActivityLock.release();
                disabledLock.acquireUninterruptibly();
                return null;
            }
        }).when(mContext).startActivity(installIntent);

        /* Complete download, unblock the first check progress. */
        completeDownload();

        /* Disable between check notification and start activity. Also unblock the initial check progress. */
        mCheckDownloadBeforeSemaphore.release(2);
        beforeStartingActivityLock.acquireUninterruptibly();
        Distribute.setEnabled(false);
        disabledLock.release();
        mCheckDownloadAfterSemaphore.acquireUninterruptibly(2);

        /* Verify start activity and complete workflow skipped, e.g. clean behavior happened only once. */
        verify(mContext).startActivity(installIntent);
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);
        verifyStatic(never());
        PreferencesStorage.putInt(PREFERENCE_KEY_DOWNLOAD_STATE, DOWNLOAD_STATE_INSTALLING);
        verifyZeroInteractions(mNotificationManager);
    }

    @Test
    public void jsonCorruptedWhenRestarting() throws Exception {

        /* Simulate async task. */
        waitDownloadTask();

        /* Make JSON parsing fail. */
        when(ReleaseDetails.parse(anyString())).thenThrow(new JSONException("mock"));
        verifyWithInvalidOrMissingCachedJson();
    }

    @Test
    public void jsonMissingWhenRestarting() throws Exception {

        /* Simulate async task. */
        waitDownloadTask();

        /* Make JSON disappear for some reason (should not happen for real). */
        PreferencesStorage.remove(PREFERENCE_KEY_RELEASE_DETAILS);
        verifyWithInvalidOrMissingCachedJson();
    }

    private void verifyWithInvalidOrMissingCachedJson() throws Exception {
        restartProcessAndSdk();
        Distribute.getInstance().onActivityResumed(mActivity);
        mockProgressCursor(-1);

        /* Unblock the mock task before restart sdk (unit test limitation). */
        waitCheckDownloadTask();

        /* Unblock the task that is scheduled after restart to check sanity. */
        waitCheckDownloadTask();

        /* Verify JSON removed. */
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_RELEASE_DETAILS);

        /* In that case the SDK will think its not mandatory but anyway this case never happens. */
        mockSuccessCursor();
        Intent intent = mockInstallIntent();
        completeDownload();
        waitCheckDownloadTask();
        verify(mContext).startActivity(intent);
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);
    }
}
