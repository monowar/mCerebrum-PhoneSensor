package org.md2k.phonesensor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import org.md2k.datakitapi.source.METADATA;
import org.md2k.datakitapi.source.datasource.DataSource;
import org.md2k.datakitapi.source.datasource.DataSourceType;
import org.md2k.phonesensor.phone.sensors.Accelerometer;
import org.md2k.phonesensor.phone.sensors.AmbientLight;
import org.md2k.phonesensor.phone.sensors.Compass;
import org.md2k.phonesensor.phone.sensors.Gyroscope;
import org.md2k.phonesensor.phone.sensors.PhoneSensorDataSource;
import org.md2k.phonesensor.phone.sensors.PhoneSensorDataSources;
import org.md2k.utilities.Apps;
import org.md2k.utilities.Report.Log;
import org.md2k.utilities.UI.AlertDialogs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import pl.charmas.android.reactivelocation.ReactiveLocationProvider;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Copyright (c) 2015, The University of Memphis, MD2K Center
 * - Syed Monowar Hossain <monowar.hossain@gmail.com>
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p/>
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * <p/>
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
public class PrefsFragmentPhoneSensorSettings extends PreferenceFragment {
    private static final String TAG = PrefsFragmentPhoneSensorSettings.class.getSimpleName();
    public static final int REQUEST_CHECK_SETTINGS = 1000;
    PhoneSensorDataSources phoneSensorDataSources;
    ArrayList<DataSource> defaultConfig;
    Preference.OnPreferenceChangeListener onPreferenceChangeListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            phoneSensorDataSources.find(preference.getKey()).setEnabled((Boolean) newValue);
            updatePreferenceScreen();
            return false;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readConfiguration();
        readDefaultConfiguration();
        addPreferencesFromResource(R.xml.pref_phonesensor_platform);
        createPreferenceScreen();
        setBackButton();
        setSaveButton();
        checkPlayServices();
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(getActivity());
        if (result != ConnectionResult.SUCCESS) {
            if (googleAPI.isUserResolvableError(result)) {
                googleAPI.getErrorDialog(getActivity(), result,
                        9000, new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                getActivity().finish();
                            }
                        }).show();
            }
            return false;
        } else{
            register();
        }


        return true;
    }

    private static final long INTERVAL = 5000L;
    private Subscription updatableLocationSubscription;
    private Observable<LocationSettingsResult> locationUpdatesObservable;

    void register() {
        locationPermission();
        updatableLocationSubscription = locationUpdatesObservable.subscribe(new Observer<LocationSettingsResult>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(LocationSettingsResult locationSettingsResult) {
                try {
                    Status status = locationSettingsResult.getStatus();
                    switch (status.getStatusCode()){
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            status.startResolutionForResult(getActivity(), REQUEST_CHECK_SETTINGS);
                            break;
                        case LocationSettingsStatusCodes.SUCCESS:
                            unregister();
                            break;
                        default:
                            Toast.makeText(getActivity(), "!PERMISSION DENIED !!! Could not continue...", Toast.LENGTH_SHORT).show();
                            unregister();
                            getActivity().finish();
                            break;
                    }
                }catch (Exception e){
                    Toast.makeText(getActivity(), "!PERMISSION DENIED !!! Could not continue...", Toast.LENGTH_SHORT).show();
                    unregister();
                    getActivity().finish();
                }

            }
        });

    }

    public void unregister() {
        if (updatableLocationSubscription != null && !updatableLocationSubscription.isUnsubscribed())
            updatableLocationSubscription.unsubscribe();
    }

    void locationPermission() {
        ReactiveLocationProvider locationProvider = new ReactiveLocationProvider(getActivity());
        final LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(INTERVAL);
        locationUpdatesObservable = locationProvider
                .checkLocationSettings(
                        new LocationSettingsRequest.Builder()
                                .addLocationRequest(locationRequest)
                                .setAlwaysShow(true)  //Refrence: http://stackoverflow.com/questions/29824408/google-play-services-locationservices-api-new-option-never
                                .build()
                );
    }

    void readDefaultConfiguration() {
        try {
            defaultConfig = Configuration.readDefault();
        } catch (FileNotFoundException e) {
            defaultConfig = null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 9000:
                if (resultCode == Activity.RESULT_OK) {
                    register();
                }
                break;
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        // All required changes were successfully made
                        unregister();
                        break;
                    case Activity.RESULT_CANCELED:
                        // The user was asked to change settings, but chose not to
                        Toast.makeText(getActivity(), "!PERMISSION DENIED !!! Could not continue...", Toast.LENGTH_SHORT).show();
                        unregister();
                        getActivity().finish();
                        break;
                    default:
                        break;
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        assert v != null;
        ListView lv = (ListView) v.findViewById(android.R.id.list);
        lv.setPadding(0, 0, 0, 0);
        return v;
    }

    void updateDefaultConfig() {
        for (int i = 0; i < phoneSensorDataSources.getPhoneSensorDataSources().size(); i++) {
            phoneSensorDataSources.getPhoneSensorDataSources().get(i).setEnabled(false);
        }
        assert defaultConfig != null;
        for (int i = 0; i < defaultConfig.size(); i++) {
            String type = defaultConfig.get(i).getType();
            String freq = defaultConfig.get(i).getMetadata().get(METADATA.FREQUENCY);
            phoneSensorDataSources.find(type).setEnabled(true);
            phoneSensorDataSources.find(type).setFrequency(freq);
        }
    }

    void setDefaultSettings() {
        final CheckBoxPreference checkBoxPreference = (CheckBoxPreference) findPreference("key_default_settings");
        if (defaultConfig == null) {
            checkBoxPreference.setEnabled(false);
            checkBoxPreference.setChecked(false);
            checkBoxPreference.setSummary("not available");
        } else {
            checkBoxPreference.setEnabled(true);
            checkBoxPreference.setChecked(false);
            checkBoxPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    boolean checked = ((CheckBoxPreference) preference).isChecked();
                    Log.d(TAG, "checked=" + checked);
                    if (checked) {
                        PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("dataSourceType");
                        preferenceCategory.setEnabled(false);
                        updateDefaultConfig();
                        updatePreferenceScreen();

                    } else {
                        PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("dataSourceType");
                        preferenceCategory.setEnabled(true);
                    }
                    return false;
                }
            });
        }
    }

    void createPreferenceScreen() {
        setDefaultSettings();
        addPreferenceScreenSensors();
        updatePreferenceScreen();
    }

    void readConfiguration() {
        phoneSensorDataSources = new PhoneSensorDataSources(getActivity());
    }

    boolean isSensorSupported(String dataSourceType) {
        SensorManager mSensorManager;
        Sensor mSensor = null;
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        switch (dataSourceType) {
            case DataSourceType.ACCELEROMETER:
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                break;
            case (DataSourceType.GYROSCOPE):
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                break;
            case (DataSourceType.AMBIENT_TEMPERATURE):
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
                break;
            case (DataSourceType.COMPASS):
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
                break;
            case (DataSourceType.AMBIENT_LIGHT):
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
                break;
            case (DataSourceType.PRESSURE):
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
                break;
            case (DataSourceType.PROXIMITY):
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
                break;
            case DataSourceType.LOCATION:
                return getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
            default:
                return true;

        }
        return mSensor != null;
    }

    private SwitchPreference createSwitchPreference(String dataSourceType) {
        SwitchPreference switchPreference = new SwitchPreference(getActivity());
        switchPreference.setKey(dataSourceType);
        String title = dataSourceType;
        title = title.replace("_", " ");
        title = title.substring(0, 1).toUpperCase() + title.substring(1).toLowerCase();
        switchPreference.setTitle(title);
        switchPreference.setOnPreferenceChangeListener(onPreferenceChangeListener);
        switchPreference.setEnabled(isSensorSupported(dataSourceType));
        switch (dataSourceType) {
            case (DataSourceType.ACCELEROMETER):

                switchPreference.setOnPreferenceClickListener(alertDialogFrequency(Accelerometer.frequencyOptions));
                break;
            case (DataSourceType.GYROSCOPE):
                switchPreference.setOnPreferenceClickListener(alertDialogFrequency(Gyroscope.frequencyOptions));
                break;
            case (DataSourceType.AMBIENT_TEMPERATURE):
//                switchPreference.setOnPreferenceClickListener(alertDialogFrequency(AmbientTemperature.frequencyOptions));
                break;
            case (DataSourceType.COMPASS):
                switchPreference.setOnPreferenceClickListener(alertDialogFrequency(Compass.frequencyOptions));
                break;
            case (DataSourceType.AMBIENT_LIGHT):
                switchPreference.setOnPreferenceClickListener(alertDialogFrequency(AmbientLight.frequencyOptions));
                break;
            case (DataSourceType.PRESSURE):
//                switchPreference.setOnPreferenceClickListener(alertDialogFrequency(Pressure.frequencyOptions));
                break;
            case (DataSourceType.PROXIMITY):
//                switchPreference.setOnPreferenceClickListener(alertDialogFrequency(Proximity.frequencyOptions));
                break;
        }
        return switchPreference;
    }

    private Preference.OnPreferenceClickListener alertDialogFrequency(final String[] frequencies) {
        for (int i = 0; i < frequencies.length; i++)
            frequencies[i] = frequencies[i] + " Hz";
        return new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                SwitchPreference switchPreference = (SwitchPreference) preference;
                Log.d(TAG, "preferencekey=" + preference.getKey());
                phoneSensorDataSources.find(preference.getKey()).setEnabled(switchPreference.isChecked());
                if (switchPreference.isChecked()) {
                    int curSelected = 0;
                    String freq = phoneSensorDataSources.find(preference.getKey()).getFrequency();
                    if (freq != null) {
                        for (int i = 0; i < frequencies.length; i++)
                            if (frequencies[i].equals(freq + " Hz")) {
                                curSelected = i;
                                break;
                            }
                    }
                    try {
                        AlertDialogs.AlertDialogSingleChoice(getActivity(), "Select Frequency", frequencies, curSelected, "Select", "Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which >= 0) {
                                    Log.d(TAG, "preferencekey=" + preference.getKey() + " which=" + which);
                                    String freq[] = frequencies[which].split(" ");
                                    phoneSensorDataSources.find(preference.getKey()).setFrequency(freq[0]);
                                    updatePreferenceScreen();
                                }
                            }
                        });
                    } catch (Exception ignored) {

                    }
                }
                return false;
            }
        };
    }

    protected void addPreferenceScreenSensors() {
        String dataSourceType;
        PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("dataSourceType");
        preferenceCategory.removeAll();
        for (int i = 0; i < phoneSensorDataSources.getPhoneSensorDataSources().size(); i++) {
            dataSourceType = phoneSensorDataSources.getPhoneSensorDataSources().get(i).getDataSourceType();
            SwitchPreference switchPreference = createSwitchPreference(dataSourceType);
            preferenceCategory.addPreference(switchPreference);
        }
    }

    void updatePreferenceScreen() {
        PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("dataSourceType");
        PhoneSensorDataSource phoneSensorDataSource;
        for (int i = 0; i < phoneSensorDataSources.getPhoneSensorDataSources().size(); i++) {
            phoneSensorDataSource = phoneSensorDataSources.getPhoneSensorDataSources().get(i);
            SwitchPreference switchPreference = (SwitchPreference) preferenceCategory.findPreference(phoneSensorDataSource.getDataSourceType());
            switchPreference.setChecked(phoneSensorDataSource.isEnabled());
            if (!isSensorSupported(phoneSensorDataSource.getDataSourceType()))
                switchPreference.setSummary("Not Supported");
            else {
                try {
                    Double.parseDouble(phoneSensorDataSource.getFrequency());
                    switchPreference.setSummary(phoneSensorDataSource.getFrequency() + " Hz");
                } catch (NumberFormatException nfe) {
                    switchPreference.setSummary(phoneSensorDataSource.getFrequency());
                }
            }
        }
    }

    private void setBackButton() {
        final Button button = (Button) getActivity().findViewById(R.id.button_2);
        button.setText(R.string.button_close);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getActivity().finish();
            }
        });
    }

    private void setSaveButton() {
        final Button button = (Button) getActivity().findViewById(R.id.button_1);
        button.setText("Save");
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Apps.isServiceRunning(getActivity(), ServicePhoneSensor.class.getName())) {
                    AlertDialogs.AlertDialog(getActivity(), "Save and Restart?", "Save configuration file and restart PhoneSensor App?", R.drawable.ic_info_teal_48dp, "Yes", "Cancel", null, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                                case DialogInterface.BUTTON_POSITIVE:
                                    Intent intent = new Intent(getActivity(), ServicePhoneSensor.class);
                                    getActivity().stopService(intent);
                                    saveConfigurationFile();
                                    intent = new Intent(getActivity(), ServicePhoneSensor.class);
                                    getActivity().startService(intent);
                                    getActivity().finish();
                                    break;

                                case DialogInterface.BUTTON_NEGATIVE:
                                    Toast.makeText(getActivity(), "Configuration file is not saved.", Toast.LENGTH_LONG).show();
                                    getActivity().finish();
                                    break;
                            }
                        }
                    });
                } else {
                    saveConfigurationFile();
                    getActivity().finish();
                }
            }
        });
    }

    void saveConfigurationFile() {
        try {
            phoneSensorDataSources.writeDataSourceToFile();
            Toast.makeText(getActivity(), "Configuration file is saved.", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(getActivity(), "!!!Error:" + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}
