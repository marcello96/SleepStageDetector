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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.sleepstage.detector.R;
import com.sleepstage.detector.helpers.CustomBluetoothProfile;
import com.sleepstage.detector.model.HeartRateMeasurement;

import net.danlew.android.joda.JodaTimeAndroid;

import org.joda.time.LocalDateTime;

import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import static com.sleepstage.detector.helpers.HeartRateUtil.extractHeartRate;

public class MainActivity extends Activity {

    boolean isListeningHeartRate = false;

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    BluetoothAdapter bluetoothAdapter;
    BluetoothGatt bluetoothGatt;
    BluetoothDevice bluetoothDevice;

    Button btnStartConnecting, btnGetHeartRate;
    EditText txtPhysicalAddress;
    TextView txtState, txtByte;
    private String mDeviceName;
    private String mDeviceAddress;
    private Timer hrScheduler;

    private static final int INTERVAL_SEC = 20;

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
        txtPhysicalAddress.setText(mDeviceAddress);

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
        txtPhysicalAddress = findViewById(R.id.txtPhysicalAddress);
        txtState = findViewById(R.id.txtState);
        txtByte = findViewById(R.id.txtByte);
    }

    void initializeEvents() {
        btnStartConnecting.setOnClickListener(v -> startConnecting());
        btnGetHeartRate.setOnClickListener(v -> handleHrBtnClick());
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
        }
        isListeningHeartRate = true;
        startHeartRateScanning();
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
        runOnUiThread(() -> txtByte.setText("..."));
        BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.HeartRate.service)
                .getCharacteristic(CustomBluetoothProfile.HeartRate.controlCharacteristic);
        bchar.setValue(new byte[]{21, 2, 1});
        bluetoothGatt.writeCharacteristic(bchar);
    }

    void listenHeartRate() {
        BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.HeartRate.service)
                .getCharacteristic(CustomBluetoothProfile.HeartRate.measurementCharacteristic);
        bluetoothGatt.setCharacteristicNotification(bchar, true);
        BluetoothGattDescriptor descriptor = bchar.getDescriptor(CustomBluetoothProfile.HeartRate.descriptor);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(descriptor);
        isListeningHeartRate = true;
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
            //listenHeartRate();
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
        runOnUiThread(() -> txtByte.setText(String.format("%d bpm", heartRateData.getValue())));
    }
}
