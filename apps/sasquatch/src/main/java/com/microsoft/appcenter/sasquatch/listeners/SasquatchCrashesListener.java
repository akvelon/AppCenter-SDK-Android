package com.microsoft.appcenter.sasquatch.listeners;

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.annotation.VisibleForTesting;
import android.support.test.espresso.idling.CountingIdlingResource;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.microsoft.appcenter.crashes.AbstractCrashesListener;
import com.microsoft.appcenter.crashes.Crashes;
import com.microsoft.appcenter.crashes.ingestion.models.ErrorAttachmentLog;
import com.microsoft.appcenter.crashes.model.ErrorReport;
import com.microsoft.appcenter.sasquatch.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import static com.microsoft.appcenter.sasquatch.activities.MainActivity.LOG_TAG;

public class SasquatchCrashesListener extends AbstractCrashesListener {

    private Context context;
    
    private String textAttachment;

    private Uri fileAttachment;

    @VisibleForTesting
    public static final CountingIdlingResource crashesIdlingResource = new CountingIdlingResource("crashes");

    public SasquatchCrashesListener(Context context) {
        this.context = context;
    }

    public String getTextAttachment() {
        return textAttachment;
    }

    public void setTextAttachment(String textAttachment) {
        this.textAttachment = textAttachment;
    }

    public Uri getFileAttachment() {
        return fileAttachment;
    }

    public void setFileAttachment(Uri fileAttachment) {
        this.fileAttachment = fileAttachment;
    }

    @Override
    public boolean shouldAwaitUserConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder
                .setTitle(R.string.crash_confirmation_dialog_title)
                .setMessage(R.string.crash_confirmation_dialog_message)
                .setPositiveButton(R.string.crash_confirmation_dialog_send_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Crashes.notifyUserConfirmation(Crashes.SEND);
                    }
                })
                .setNegativeButton(R.string.crash_confirmation_dialog_not_send_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Crashes.notifyUserConfirmation(Crashes.DONT_SEND);
                    }
                })
                .setNeutralButton(R.string.crash_confirmation_dialog_always_send_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Crashes.notifyUserConfirmation(Crashes.ALWAYS_SEND);
                    }
                });
        builder.create().show();
        return true;
    }

    @Override
    public Iterable<ErrorAttachmentLog> getErrorAttachments(ErrorReport report) {
        List<ErrorAttachmentLog> attachments = new LinkedList<>();

        /* Attach app icon to test binary. */
        if (fileAttachment != null) {
            byte[] data = getFileAttachmentData();
            String name = getFileAttachmentDisplayName();
            String mime = getFileAttachmentMimeType();
            ErrorAttachmentLog binaryLog = ErrorAttachmentLog.attachmentWithBinary(data, name, mime);
            attachments.add(binaryLog);
        }

        /* Attach some text. */
        if (!TextUtils.isEmpty(textAttachment)) {
            ErrorAttachmentLog textLog = ErrorAttachmentLog.attachmentWithText(textAttachment, "text.txt");
            attachments.add(textLog);
        }

        /* Return attachments as list. */
        return attachments.size() > 0 ? attachments : null;
    }

    @Override
    public void onBeforeSending(ErrorReport report) {
        Toast.makeText(context, R.string.crash_before_sending, Toast.LENGTH_SHORT).show();
        crashesIdlingResource.increment();
    }

    @Override
    public void onSendingFailed(ErrorReport report, Exception e) {
        Toast.makeText(context, R.string.crash_sent_failed, Toast.LENGTH_SHORT).show();
        crashesIdlingResource.decrement();
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @Override
    public void onSendingSucceeded(ErrorReport report) {
        String message = String.format("%s\nCrash ID: %s", context.getString(R.string.crash_sent_succeeded), report.getId());
        if (report.getThrowable() != null) {
            message += String.format("\nThrowable: %s", report.getThrowable().toString());
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        crashesIdlingResource.decrement();
    }

    public String getFileAttachmentDisplayName() {
        Cursor cursor = context.getContentResolver()
                .query(fileAttachment, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (!cursor.isNull(nameIndex)) {
                    return cursor.getString(nameIndex);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "";
    }

    public String getFileAttachmentSize() {
        Cursor cursor = context.getContentResolver()
                .query(fileAttachment, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (!cursor.isNull(sizeIndex)) {
                    return cursor.getString(sizeIndex);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "Unknown";
    }

    private byte[] getFileAttachmentData() {
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(fileAttachment);
            if (inputStream == null) {
                return null;
            }
            outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Couldn't read file", e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException ignore) {
            }
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException ignore) {
            }
        }
        return outputStream != null ? outputStream.toByteArray() : null;
    }

    private String getFileAttachmentMimeType() {
        String mimeType;
        if (fileAttachment.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            mimeType = context.getContentResolver().getType(fileAttachment);
        } else {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(fileAttachment.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }
        return mimeType;
    }
}
