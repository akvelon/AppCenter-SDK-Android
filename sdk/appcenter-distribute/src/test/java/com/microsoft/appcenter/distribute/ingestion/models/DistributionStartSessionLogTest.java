package com.microsoft.appcenter.distribute.ingestion.models;

import com.microsoft.appcenter.test.TestUtils;
import com.microsoft.appcenter.utils.UUIDUtils;

import org.junit.Test;

import java.util.UUID;

import static com.microsoft.appcenter.test.TestUtils.checkEquals;
import static com.microsoft.appcenter.test.TestUtils.checkNotEquals;

@SuppressWarnings("unused")
public class DistributionStartSessionLogTest {

    @Test
    public void compareDifferentType() {
        TestUtils.compareSelfNullClass(new DistributionStartSessionLog());
    }

    @Test
    public void compare() {

        /* Empty objects. */
        DistributionStartSessionLog a = new DistributionStartSessionLog();
        DistributionStartSessionLog b = new DistributionStartSessionLog();
        checkEquals(a, b);
        checkEquals(a.getType(), DistributionStartSessionLog.TYPE);

        /* With session ID. */
        UUID sid = UUIDUtils.randomUUID();
        a.setSid(sid);
        checkNotEquals(a, b);
        b.setSid(sid);
        checkEquals(a, b);
    }
}