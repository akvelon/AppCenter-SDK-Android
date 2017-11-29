package com.microsoft.appcenter.sasquatch.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Toast;

import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.AppCenterService;
import com.microsoft.appcenter.analytics.Analytics;
import com.microsoft.appcenter.analytics.AnalyticsPrivateHelper;
import com.microsoft.appcenter.crashes.Crashes;
import com.microsoft.appcenter.distribute.Distribute;
import com.microsoft.appcenter.push.Push;
import com.microsoft.appcenter.sasquatch.R;
import com.microsoft.appcenter.utils.PrefStorageConstants;
import com.microsoft.appcenter.utils.async.AppCenterFuture;
import com.microsoft.appcenter.utils.storage.StorageHelper;

import java.lang.reflect.Method;
import java.util.UUID;

import static com.microsoft.appcenter.sasquatch.activities.MainActivity.APP_SECRET_KEY;
import static com.microsoft.appcenter.sasquatch.activities.MainActivity.FILE_ATTACHMENT_KEY;
import static com.microsoft.appcenter.sasquatch.activities.MainActivity.FIREBASE_ENABLED_KEY;
import static com.microsoft.appcenter.sasquatch.activities.MainActivity.LOG_URL_KEY;
import static com.microsoft.appcenter.sasquatch.activities.MainActivity.TEXT_ATTACHMENT_KEY;
import static com.microsoft.appcenter.sasquatch.activities.MainActivity.sCrashesListener;

public class SettingsActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SettingsActivity";

    private static final int FILE_ATTACHMENT_DIALOG_ID = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

        private static final String UUID_FORMAT_REGEX = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings);
            initCheckBoxSetting(R.string.appcenter_state_key, R.string.appcenter_state_summary_enabled, R.string.appcenter_state_summary_disabled, new HasEnabled() {

                @Override
                public void setEnabled(boolean enabled) {
                    AppCenter.setEnabled(enabled);
                }

                @Override
                public boolean isEnabled() {
                    return AppCenter.isEnabled().get();
                }
            });

            /* Analytics. */
            initCheckBoxSetting(R.string.appcenter_analytics_state_key, R.string.appcenter_analytics_state_summary_enabled, R.string.appcenter_analytics_state_summary_disabled, new HasEnabled() {

                @Override
                public void setEnabled(boolean enabled) {
                    Analytics.setEnabled(enabled);
                }

                @Override
                public boolean isEnabled() {
                    return Analytics.isEnabled().get();
                }
            });
            initCheckBoxSetting(R.string.appcenter_auto_page_tracking_key, R.string.appcenter_auto_page_tracking_enabled, R.string.appcenter_auto_page_tracking_disabled, new HasEnabled() {

                @Override
                public boolean isEnabled() {
                    return AnalyticsPrivateHelper.isAutoPageTrackingEnabled();
                }

                @Override
                public void setEnabled(boolean enabled) {
                    AnalyticsPrivateHelper.setAutoPageTrackingEnabled(enabled);
                }
            });

            /* Crashes. */
            initCheckBoxSetting(R.string.appcenter_crashes_state_key, R.string.appcenter_crashes_state_summary_enabled, R.string.appcenter_crashes_state_summary_disabled, new HasEnabled() {

                @Override
                public void setEnabled(boolean enabled) {
                    Crashes.setEnabled(enabled);
                }

                @Override
                public boolean isEnabled() {
                    return Crashes.isEnabled().get();
                }
            });
            initChangeableSetting(R.string.appcenter_crashes_text_attachment_key, getCrashesTextAttachmentSummary(), new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setKeyValue(TEXT_ATTACHMENT_KEY, (String) newValue);
                    sCrashesListener.setTextAttachment((String) newValue);
                    preference.setSummary(getCrashesTextAttachmentSummary());
                    return true;
                }
            });
            initClickableSetting(R.string.appcenter_crashes_file_attachment_key, getCrashesFileAttachmentSummary(), new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                    } else {
                        intent = new Intent(Intent.ACTION_GET_CONTENT);
                    }
                    intent.setType("*/*");
                    startActivityForResult(Intent.createChooser(intent, "Select attachment file"), FILE_ATTACHMENT_DIALOG_ID);
                    return true;
                }
            });

            /* Distribute. */
            initCheckBoxSetting(R.string.appcenter_distribute_state_key, R.string.appcenter_distribute_state_summary_enabled, R.string.appcenter_distribute_state_summary_disabled, new HasEnabled() {

                @Override
                public void setEnabled(boolean enabled) {
                    Distribute.setEnabled(enabled);
                }

                @Override
                public boolean isEnabled() {
                    return Distribute.isEnabled().get();
                }
            });

            /* Push. */
            initCheckBoxSetting(R.string.appcenter_push_state_key, R.string.appcenter_push_state_summary_enabled, R.string.appcenter_push_state_summary_disabled, new HasEnabled() {

                @Override
                public void setEnabled(boolean enabled) {
                    Push.setEnabled(enabled);
                }

                @Override
                public boolean isEnabled() {
                    return Push.isEnabled().get();
                }
            });
            initCheckBoxSetting(R.string.appcenter_push_firebase_state_key, R.string.appcenter_push_firebase_summary_enabled, R.string.appcenter_push_firebase_summary_disabled, new HasEnabled() {

                @Override
                @SuppressWarnings("unchecked")
                public void setEnabled(boolean enabled) {
                    try {
                        if (enabled) {
                            Push.enableFirebaseAnalytics(getActivity());
                        } else {
                            try {
                                Class firebaseAnalyticsClass = Class.forName("com.google.firebase.analytics.FirebaseAnalytics");
                                Object analyticsInstance = firebaseAnalyticsClass.getMethod("getInstance").invoke(null, getActivity());
                                firebaseAnalyticsClass.getMethod("setAnalyticsCollectionEnabled").invoke(analyticsInstance, true);
                            } catch (Exception ignored) {
                                /* Nothing to handle; this is reached if Firebase isn't being used. */
                            }
                        }
                        MainActivity.sSharedPreferences.edit().putBoolean(FIREBASE_ENABLED_KEY, enabled).apply();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public boolean isEnabled() {
                    return isFirebaseEnabled();
                }
            });

            /* Real User Measurements. */
            try {
                @SuppressWarnings("unchecked")
                Class<? extends AppCenterService> rum = (Class<? extends AppCenterService>) Class.forName("com.microsoft.appcenter.rum.RealUserMeasurements");
                final Method isEnabled = rum.getMethod("isEnabled");
                final Method setEnabled = rum.getMethod("setEnabled", boolean.class);
                initCheckBoxSetting(R.string.appcenter_rum_state_key, R.string.appcenter_rum_state_summary_enabled, R.string.appcenter_rum_state_summary_disabled, new HasEnabled() {

                    @Override
                    public void setEnabled(boolean enabled) {
                        try {
                            setEnabled.invoke(null, enabled);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public boolean isEnabled() {
                        try {
                            return ((AppCenterFuture<Boolean>) isEnabled.invoke(null)).get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } catch (Exception e) {
                getPreferenceScreen().removePreference(findPreference(getString(R.string.real_user_measurements_key)));
            }
            initCheckBoxSetting(R.string.appcenter_auto_page_tracking_key, R.string.appcenter_auto_page_tracking_enabled, R.string.appcenter_auto_page_tracking_disabled, new HasEnabled() {

                @Override
                public boolean isEnabled() {
                    return AnalyticsPrivateHelper.isAutoPageTrackingEnabled();
                }

                @Override
                public void setEnabled(boolean enabled) {
                    AnalyticsPrivateHelper.setAutoPageTrackingEnabled(enabled);
                }
            });

            /* Application Information. */
            initClickableSetting(R.string.install_id_key, String.valueOf(AppCenter.getInstallId().get()), new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    final EditText input = new EditText(getActivity());
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    input.setText(String.valueOf(AppCenter.getInstallId().get()));

                    new AlertDialog.Builder(getActivity()).setTitle(R.string.install_id_title).setView(input)
                            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (input.getText().toString().matches(UUID_FORMAT_REGEX)) {
                                        UUID uuid = UUID.fromString(input.getText().toString());
                                        StorageHelper.PreferencesStorage.putString(PrefStorageConstants.KEY_INSTALL_ID, uuid.toString());
                                        Toast.makeText(getActivity(), String.format(getActivity().getString(R.string.install_id_changed_format), uuid.toString()), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(getActivity(), R.string.install_id_invalid, Toast.LENGTH_SHORT).show();
                                    }
                                    preference.setSummary(String.valueOf(AppCenter.getInstallId().get()));
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .create().show();
                    return true;
                }
            });
            initClickableSetting(R.string.app_secret_key, MainActivity.sSharedPreferences.getString(APP_SECRET_KEY, getString(R.string.app_secret)), new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    final EditText input = new EditText(getActivity());
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    input.setText(MainActivity.sSharedPreferences.getString(APP_SECRET_KEY, getString(R.string.app_secret)));

                    new AlertDialog.Builder(getActivity()).setTitle(R.string.app_secret_title).setView(input)
                            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String appSecret = input.getText().toString();
                                    if (!TextUtils.isEmpty(appSecret)) {
                                        setKeyValue(APP_SECRET_KEY, appSecret);
                                        Toast.makeText(getActivity(), String.format(getActivity().getString(R.string.app_secret_changed_format), appSecret), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(getActivity(), R.string.app_secret_invalid, Toast.LENGTH_SHORT).show();
                                    }
                                    preference.setSummary(MainActivity.sSharedPreferences.getString(APP_SECRET_KEY, null));
                                }
                            })
                            .setNeutralButton(R.string.reset, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String defaultAppSecret = getString(R.string.app_secret);
                                    setKeyValue(APP_SECRET_KEY, defaultAppSecret);
                                    Toast.makeText(getActivity(), String.format(getActivity().getString(R.string.app_secret_changed_format), defaultAppSecret), Toast.LENGTH_SHORT).show();
                                    preference.setSummary(MainActivity.sSharedPreferences.getString(APP_SECRET_KEY, null));
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .create().show();
                    return true;
                }
            });

            /* Miscellaneous. */
            initClickableSetting(R.string.clear_crash_user_confirmation_key, new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    StorageHelper.PreferencesStorage.remove(Crashes.PREF_KEY_ALWAYS_SEND);
                    Toast.makeText(getActivity(), R.string.clear_crash_user_confirmation_toast, Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
            String defaultLogUrl = getString(R.string.log_url);
            final String defaultLogUrlDisplay = TextUtils.isEmpty(defaultLogUrl) ? getString(R.string.log_url_set_to_production) : defaultLogUrl;
            initClickableSetting(R.string.log_url_key, MainActivity.sSharedPreferences.getString(LOG_URL_KEY, defaultLogUrlDisplay), new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    final EditText input = new EditText(getActivity());
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    input.setText(MainActivity.sSharedPreferences.getString(LOG_URL_KEY, null));
                    input.setHint(R.string.log_url_set_to_production);

                    new AlertDialog.Builder(getActivity()).setTitle(R.string.log_url_title).setView(input)
                            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (Patterns.WEB_URL.matcher(input.getText().toString()).matches()) {
                                        String url = input.getText().toString();
                                        setKeyValue(LOG_URL_KEY, url);
                                        toastUrlChange(url);
                                    } else if (input.getText().toString().isEmpty()) {
                                        setDefaultUrl();
                                    } else {
                                        Toast.makeText(getActivity(), R.string.log_url_invalid, Toast.LENGTH_SHORT).show();
                                    }
                                    preference.setSummary(MainActivity.sSharedPreferences.getString(LOG_URL_KEY, defaultLogUrlDisplay));
                                }
                            })
                            .setNeutralButton(R.string.reset, new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    setDefaultUrl();
                                    preference.setSummary(MainActivity.sSharedPreferences.getString(LOG_URL_KEY, defaultLogUrlDisplay));
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .create().show();
                    return true;
                }

                private void setDefaultUrl() {
                    setKeyValue(LOG_URL_KEY, null);
                    toastUrlChange(getString(R.string.log_url));
                }

                private void toastUrlChange(String url) {
                    if (TextUtils.isEmpty(url)) {
                        url = getString(R.string.log_url_production);
                    }
                    Toast.makeText(getActivity(), String.format(getActivity().getString(R.string.log_url_changed_format), url), Toast.LENGTH_SHORT).show();
                }
            });

            /* Register preference change listener. */
            getPreferenceManager().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();

            /* Unregister preference change listener. */
            getPreferenceManager().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

            /* Update other preferences. */
            final BaseAdapter adapter = (BaseAdapter) getPreferenceScreen().getRootAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                Preference preference = (Preference) adapter.getItem(i);
                if (preference.getOnPreferenceChangeListener() != null && !key.equals(preference.getKey())) {
                    preference.getOnPreferenceChangeListener().onPreferenceChange(preference, null);
                }
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == FILE_ATTACHMENT_DIALOG_ID) {
                Uri fileAttachment = resultCode == RESULT_OK && data != null ? data.getData() : null;
                setKeyValue(FILE_ATTACHMENT_KEY, fileAttachment != null ? fileAttachment.toString() : null);
                MainActivity.sCrashesListener.setFileAttachment(fileAttachment);
                Preference preference = getPreferenceManager().findPreference(getString(R.string.appcenter_crashes_file_attachment_key));
                if (preference != null) {
                    preference.setSummary(getCrashesFileAttachmentSummary());
                }
            }
        }

        private void initCheckBoxSetting(int key, final int enabledSummary, final int disabledSummary, final HasEnabled hasEnabled) {
            Preference preference = getPreferenceManager().findPreference(getString(key));
            if (preference == null) {
                Log.w(LOG_TAG, "Couldn't find preference for key: " + key);
                return;
            }
            preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue != null) {
                        hasEnabled.setEnabled((Boolean) newValue);
                    }
                    boolean enabled = hasEnabled.isEnabled();
                    if (((CheckBoxPreference) preference).isChecked() != enabled) {
                        preference.setSummary(enabled ? enabledSummary : disabledSummary);
                        ((CheckBoxPreference) preference).setChecked(enabled);
                        return true;
                    }
                    return false;
                }
            });
            boolean enabled = hasEnabled.isEnabled();
            preference.setSummary(enabled ? enabledSummary : disabledSummary);
            ((CheckBoxPreference) preference).setChecked(enabled);
        }

        @SuppressWarnings("SameParameterValue")
        private void initClickableSetting(int key, Preference.OnPreferenceClickListener clickListener) {
            initClickableSetting(key, null, clickListener);
        }

        @SuppressWarnings("SameParameterValue")
        private void initClickableSetting(int key, String summary, Preference.OnPreferenceClickListener clickListener) {
            Preference preference = getPreferenceManager().findPreference(getString(key));
            if (preference == null) {
                Log.w(LOG_TAG, "Couldn't find preference for key: " + key);
                return;
            }
            preference.setOnPreferenceClickListener(clickListener);
            if (summary != null) {
                preference.setSummary(summary);
            }
        }

        private void initChangeableSetting(int key, String summary, Preference.OnPreferenceChangeListener changeListener) {
            Preference preference = getPreferenceManager().findPreference(getString(key));
            if (preference == null) {
                Log.w(LOG_TAG, "Couldn't find preference for key: " + key);
                return;
            }
            preference.setOnPreferenceChangeListener(changeListener);
            if (summary != null) {
                preference.setSummary(summary);
            }
        }

        private void setKeyValue(String key, String value) {
            SharedPreferences.Editor editor = MainActivity.sSharedPreferences.edit();
            if (value == null) {
                editor.remove(key);
            } else {
                editor.putString(key, value);
            }
            editor.apply();
        }

        private boolean isFirebaseEnabled() {
            return MainActivity.sSharedPreferences.getBoolean(FIREBASE_ENABLED_KEY, false);
        }

        private String getCrashesTextAttachmentSummary() {
            String textAttachment = MainActivity.sCrashesListener.getTextAttachment();
            if (!TextUtils.isEmpty(textAttachment)) {
                return getString(R.string.appcenter_crashes_text_attachment_summary, textAttachment.length());
            }
            return getString(R.string.appcenter_crashes_text_attachment_summary_empty);
        }

        private String getCrashesFileAttachmentSummary() {
            Uri fileAttachment = MainActivity.sCrashesListener.getFileAttachment();
            if (fileAttachment != null) {
                String name = MainActivity.sCrashesListener.getFileAttachmentDisplayName();
                String size = MainActivity.sCrashesListener.getFileAttachmentSize();
                return getString(R.string.appcenter_crashes_file_attachment_summary, name, size);
            }
            return getString(R.string.appcenter_crashes_file_attachment_summary_empty);
        }


        private interface HasEnabled {
            boolean isEnabled();

            void setEnabled(boolean enabled);
        }
    }
}
