package com.microsoft.appcenter.utils;

import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.microsoft.appcenter.utils.storage.StorageHelper;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;


@SuppressWarnings("unused")
public class IdHelperAndroidTest {

    private static final String TAG = "IdHelper";

    @BeforeClass
    public static void setUpClass() {
        StorageHelper.initialize(InstrumentationRegistry.getTargetContext());
    }


    @Test
    public void getInstallId() {
        Log.i(TAG, "Testing installId-shortcut");
        UUID expected = UUIDUtils.randomUUID();
        StorageHelper.PreferencesStorage.putString(PrefStorageConstants.KEY_INSTALL_ID, expected.toString());

        UUID actual = IdHelper.getInstallId();
        assertEquals(expected, actual);

        String wrongUUID = "1234567";
        StorageHelper.PreferencesStorage.putString(PrefStorageConstants.KEY_INSTALL_ID, expected.toString());

        actual = IdHelper.getInstallId();
        assertNotEquals(wrongUUID, actual);
        assertNotNull(actual);
    }
}