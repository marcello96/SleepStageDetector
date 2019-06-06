package com.sleepstage.detector.activities;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.sleepstage.detector.R;
import com.sleepstage.detector.helpers.CustomBluetoothProfile;
import com.sleepstage.detector.helpers.SleepPhases;
import com.sleepstage.detector.model.HeartRateMeasurement;

import net.danlew.android.joda.JodaTimeAndroid;

import org.joda.time.LocalDateTime;

import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import static com.sleepstage.detector.helpers.HeartRateUtil.extractHeartRate;
import static com.sleepstage.detector.helpers.HeartRateUtil.getHealthRateWriteBytes;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;

public class MainActivity extends Activity {

    public enum SleepStagesPlaylists
    {
        WAKEFULNESS("spotify:user:spotify:playlist:37i9dQZF1DXauOWFg72pbl"),
        AROUSAL("spotify:album:5z18I6RGoc5WJk6E9y55l0"),
        NREM1("spotify:album:6twkMgxOvXOxQsu9kBBCkW"),
        NREM2("spotify:album:1BIjAmzQzs7Tw3JwocYxQl"),
        NREM3("spotify:album:7grtCQtTTieR4VZQcYT4cB"),
        REM("spotify:album:3YoAppcd7ZVCGcp6sAsSas");


        private String url;

        SleepStagesPlaylists(String envUrl) {
            this.url = envUrl;
        }

        public String getUrl() {
            return url;
        }
    }

    private static final String CLIENT_ID = "38ee40066478433d8a23926c99173d62";
    private static final String REDIRECT_URI = "http://example.com/callback";
    private SpotifyAppRemote mSpotifyAppRemote;

    boolean isListeningHeartRate = false;

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    BluetoothAdapter bluetoothAdapter;
    BluetoothGatt bluetoothGatt;
    BluetoothDevice bluetoothDevice;

    Button btnStartConnecting, btnGetHeartRate, btnStartMusic;
    EditText txtPhysicalAddress;
    TextView txtState, txtByte, txtSleepStage;
    private String mDeviceName;
    private String mDeviceAddress;
    private Timer hrScheduler;

    public static final SleepPhases sleepPhase = new SleepPhases();
    public static SleepPhases.SleepStages currentSleepStage = SleepPhases.SleepStages.UNDEFINED;
    public static boolean isMusicOn = false;

    private static final int INTERVAL_SEC = 15;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        JodaTimeAndroid.init(this);

        initializeObjects();
        initilaizeComponents();
        initializeEvents();

        getBoundedDevice();
    }

    void getBoundedDevice() {

        mDeviceName = getIntent().getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = getIntent().getStringExtra(EXTRAS_DEVICE_ADDRESS);
        //txtPhysicalAddress.setText(mDeviceAddress);
        txtPhysicalAddress.setText("E2:BA:51:35:20:39");


        Set<BluetoothDevice> boundedDevice = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice bd : boundedDevice) {
            if (bd.getName().contains("Sleep Stage Detector")) {
                txtPhysicalAddress.setText(bd.getAddress());
            }
        }
    }

    void initializeObjects() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        hrScheduler = new Timer();
    }

    void initilaizeComponents() {
        btnStartConnecting = findViewById(R.id.btnStartConnecting);
        btnGetHeartRate = findViewById(R.id.btnGetHeartRate);
        btnStartMusic = findViewById(R.id.btnStartMusic);
        txtPhysicalAddress = findViewById(R.id.txtPhysicalAddress);
        txtState = findViewById(R.id.txtState);
        txtByte = findViewById(R.id.txtByte);
        txtSleepStage = findViewById(R.id.txtSleepStage);
    }

    void initializeEvents() {
        btnStartConnecting.setOnClickListener(v -> startConnecting());
        btnGetHeartRate.setOnClickListener(v -> handleHrBtnClick());
        btnStartMusic.setOnClickListener(v -> handleStartMusicBtnClick());
    }

    void startConnecting() {

        String address = txtPhysicalAddress.getText().toString();
        bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);

        bluetoothGatt = bluetoothDevice.connectGatt(this, true, bluetoothGattCallback);
    }

    void stateConnected() {
        bluetoothGatt.discoverServices();
        runOnUiThread(() -> txtState.setText("Connected"));
    }

    void stateDisconnected() {
        bluetoothGatt.disconnect();
        runOnUiThread(() -> txtState.setText("Disconnected"));
    }

    void handleHrBtnClick() {
        if (isListeningHeartRate) {
            isListeningHeartRate = false;
            stopHeartRateScanning();
            btnGetHeartRate.setText(R.string.start_measurement);
        }
        isListeningHeartRate = true;
        startHeartRateScanning();
        btnGetHeartRate.setText(R.string.stop_measurement);
    }

    void handleStartMusicBtnClick() {
        ConnectionParams connectionParams =
                new ConnectionParams.Builder(CLIENT_ID)
                        .setRedirectUri(REDIRECT_URI)
                        .showAuthView(true)
                        .build();

        SpotifyAppRemote.connect(this, connectionParams,
                new Connector.ConnectionListener() {

                    @Override
                    public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                        mSpotifyAppRemote = spotifyAppRemote;
                        Log.d("MainActivity", "Connected! Yay!");
                        btnStartMusic.setText("Music Started");
                        isMusicOn = true;
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        Log.e("MainActivity", throwable.getMessage(), throwable);

                        // Something went wrong when attempting to connect! Handle errors here
                    }
                });
    }

    private void playlistPlay(String playlistLink) {
        mSpotifyAppRemote.getPlayerApi().play(playlistLink);
    }

    void startHeartRateScanning() {
        hrScheduler.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                scanHeartRate();
            }
        }, 0, 1000 * INTERVAL_SEC);
    }

    void stopHeartRateScanning() {
        hrScheduler.cancel();
    }

    void scanHeartRate() {
        //runOnUiThread(() -> txtByte.append("..."));
        BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.HeartRate.service)
                .getCharacteristic(CustomBluetoothProfile.HeartRate.controlCharacteristic);
        bchar.setValue(getHealthRateWriteBytes());
        bluetoothGatt.writeCharacteristic(bchar);
    }

    void listenHeartRate() {
        BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.HeartRate.service)
                .getCharacteristic(CustomBluetoothProfile.HeartRate.measurementCharacteristic);
        bluetoothGatt.setCharacteristicNotification(bchar, true);
        BluetoothGattDescriptor descriptor = bchar.getDescriptor(CustomBluetoothProfile.HeartRate.descriptor);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(descriptor);
    }

    final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                stateConnected();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                stateDisconnected();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            listenHeartRate();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            processHeartRateResponse(characteristic.getValue());
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            processHeartRateResponse(characteristic.getValue());
        }
    };

    void processHeartRateResponse(byte[] bytes) {
        // TODO heart rate
        HeartRateMeasurement heartRateData = new HeartRateMeasurement(extractHeartRate(bytes), LocalDateTime.now());
        runOnUiThread(() -> txtByte.append(String.format("%d bpm\n", heartRateData.getValue())));

        // Sleep stage
        if (isMusicOn) {
            // Ustawic dzielnik
            float RRI = 60.0f/heartRateData.getValue()*1000.0f;
            Log.d("MainActivity", "RRI: " + RRI);
            SleepPhases.SleepStages calculatedSleepStage = sleepPhase.calculateSleepPhase(RRI);

            if (!currentSleepStage.equals(calculatedSleepStage)) {
                currentSleepStage = calculatedSleepStage;
                txtSleepStage.setText(String.format("Current sleep stage: %s", currentSleepStage.toString()));
                SleepStagesPlaylists sitUrl = SleepStagesPlaylists.valueOf(currentSleepStage.toString());
                playlistPlay(sitUrl.getUrl());
            }
        }
    }
}
