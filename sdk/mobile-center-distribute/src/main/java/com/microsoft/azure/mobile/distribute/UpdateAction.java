package com.microsoft.azure.mobile.distribute;

import android.content.DialogInterface;
import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Constants to use for {@link Distribute#handleUpdateAction}.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({
        UpdateAction.UPDATE,
        UpdateAction.POSTPONE
})
public @interface UpdateAction {

    /**
     * Action to trigger the download of the release.
     */
    int UPDATE = DialogInterface.BUTTON_POSITIVE;

    /**
     * Action to postpone optional updates for 1 day.
     */
    int POSTPONE = DialogInterface.BUTTON_NEGATIVE;
}
