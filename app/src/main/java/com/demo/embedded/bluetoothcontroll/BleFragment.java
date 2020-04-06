package com.demo.embedded.bluetoothcontroll;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;


import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import okio.Buffer;

import com.demo.embedded.bluetoothcontroll.ble.DiscoverHelp;
import com.jakewharton.rx.ReplayingShare;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;


public class BleFragment extends Fragment {

    ImageButton btnDevice1;
    TextView stDevice1;

    public static final int STATE_ON = 1;
    public static final int STATE_OFF = 0;
    int device1State = STATE_OFF;
    int device2State = STATE_OFF;
    // Debugging
    private static final String TAG = BleFragment.class.getSimpleName();

    // Intent request codes
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 99;

    public static final String BLE_MAC = "24:6F:28:24:CB:22";//"EA:0A:5E:A3:61:49";//
    public static final String SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb";//"6E400001-B5A3-F393-E0A9-E50E24DCCA9E";//
    public static final String TX_UUID_NOTIFY = "0000ffe1-0000-1000-8000-00805f9b34fb";//"6E400003-B5A3-F393-E0A9-E50E24DCCA9E";// UART TX
    public static final String RX_UUID_WRITE = "0000ffe1-0000-1000-8000-00805f9b34fb";//"6E400002-B5A3-F393-E0A9-E50E24DCCA9E"; //UART RX

    private PublishSubject<Boolean> disconnectTriggerSubject = PublishSubject.create();
    private Observable<RxBleConnection> connectionObservable;
    private RxBleDevice bleDevice;
    private final CompositeDisposable disposables = new CompositeDisposable();
    RxBleClient rxBleClient;
    private Scheduler mainThreadScheduler = AndroidSchedulers.mainThread();
    private Scheduler backgroundThread = Schedulers.io();

    int width_px;
    int active_color;
    int deactive_color;
    LinearLayout lay_shape1;
    LayerDrawable bgDrawable1;
    GradientDrawable shape1;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;


    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;
    //
    private Buffer buffer = new Buffer();
    private Buffer extantBuffer = new Buffer();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        //
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(getActivity(), R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        FragmentActivity activity = getActivity();
        if (mBluetoothAdapter == null && activity != null) {
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE_REQUIRED_PERMISSIONS: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getActivity(), "Location granted", Toast.LENGTH_SHORT).show();
                    createConnection();
                } else {
                    Toast.makeText(getActivity(), "The app need permission to connect with BLE device . Please consider granting it this permission", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mBluetoothAdapter == null) {
            return;
        }
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (rxBleClient == null) {
            setupInit();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_control_basic, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        btnDevice1 = (ImageButton) rootView.findViewById(R.id.btn1);
        stDevice1 = (TextView) rootView.findViewById(R.id.textview1);


        // To change the stroke color
        width_px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                2f, getResources().getDisplayMetrics());
        active_color = getResources().getColor(R.color.active);
        deactive_color = getResources().getColor(R.color.deactive);
        lay_shape1 = (LinearLayout) rootView.findViewById(R.id.shape1);
        bgDrawable1 = (LayerDrawable) lay_shape1.getBackground();
        shape1 = (GradientDrawable) bgDrawable1
                .findDrawableByLayerId(R.id.shape_active);
        btn1Off();
    }

    void btn1On() {
        shape1.setStroke(width_px, active_color);
        stDevice1.setTextColor(active_color);
        btnDevice1.setImageResource(R.drawable.bt_bulb_on);
    }

    void btn1Off() {
        shape1.setStroke(width_px, deactive_color);
        stDevice1.setTextColor(deactive_color);
        btnDevice1.setImageResource(R.drawable.bt_bulb_off);
    }

    /**
     * Set up the UI and background operations
     */
    private void setupInit() {
        Log.d(TAG, "setupInit()");

        // Initialize the array adapter for the conversation thread
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        btnDevice1.setOnClickListener(view -> sendCmd1());
        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer();
        //
        rxBleClient = RxBleClient.create(getActivity().getApplicationContext());
        bleDevice = rxBleClient.getBleDevice(BLE_MAC);
        connectionObservable = prepareConnectionObservable();
    }

    void sendCmd1() {
        if (device1State == STATE_ON)
            sendData(Constants.CMD_RF_1_OFF, true);
        else if (device1State == STATE_OFF)
            sendData(Constants.CMD_RF_1_ON, true);
    }


    void upateBtn1(boolean state) {
        if (state == true) {

        }
    }

    void updateReceive(String data) {
        Log.d(TAG, data);
        if (data.startsWith(Constants.REC_1_ON)) {
            device1State = STATE_ON;
            btn1On();
        } else if (data.startsWith(Constants.REC_1_OFF)) {
            device1State = STATE_OFF;
            btn1Off();
        }

    }

    void sendData(String text, boolean crlf) {
        if (!TextUtils.isEmpty(text)) {
            if (crlf)
                sendMessage(text + "\r\n");
            else
                sendMessage(text);
        }

    }

    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (!isConnected()) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            writeData(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        }
    }


    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        MainActivity activity = (MainActivity) getActivity();
        if (null == activity) {
            return;
        }
        activity.setActionBarSubTitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        MainActivity activity = (MainActivity) getActivity();
        if (null == activity) {
            return;
        }
        activity.setActionBarSubTitle(subTitle);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupInit();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    FragmentActivity activity = getActivity();
                    if (activity != null) {
                        Toast.makeText(activity, R.string.bt_not_enabled_leaving,
                                Toast.LENGTH_SHORT).show();
                        activity.finish();
                    }
                }
        }
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect_scan: {
                checkCanScan();
                return true;
            }
            case R.id.action_sync: {
                sendData("Sync", true);
                return true;
            }
        }
        return false;
    }

    private void recursiveCheckCharacter(byte[] cmd, byte splitValue) {
        boolean isMatch = false;
        for (byte b : cmd) {
            if (isMatch) {
                extantBuffer.writeByte(b);
            } else {
                buffer.writeByte(b);
                if (b == splitValue) {
                    isMatch = true;
                }
            }
        }
        if (isMatch) {
            byte[] sendBytes = buffer.readByteArray();
            String readMessage = new String(sendBytes, StandardCharsets.UTF_8).trim();//Trim will remove all CRLF and space
            updateReceive(readMessage);
            isMatch = false;
            byte[] extantBytes = extantBuffer.readByteArray();
            if (extantBytes.length > 0) recursiveCheckCharacter(extantBytes, splitValue);
        }
    }

    private void checkCanScan() {
        if (hasPermissions(Manifest.permission.ACCESS_FINE_LOCATION)) {
            createConnection();
        } else {
            requestPemission(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPermissions(String permission) {

        if (ContextCompat.checkSelfPermission(getActivity(), permission)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return true;
    }

    private void requestPemission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] requestPem = new String[]{permission};
            requestPermissions(requestPem, REQUEST_CODE_REQUIRED_PERMISSIONS);
        }
    }

    private Observable<RxBleConnection> prepareConnectionObservable() {
        return bleDevice
                .establishConnection(false)
                .takeUntil(disconnectTriggerSubject)
                .compose(ReplayingShare.instance());
    }

    private boolean isConnected() {
        return bleDevice.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }

    private void triggerDisconnect() {
        disconnectTriggerSubject.onNext(true);
    }

    private void createConnection() {
        if (isConnected()) {
            Log.d(TAG, "BLE Disconnection");
            triggerDisconnect();
        } else {
            Log.d(TAG, "Create connection");
            final Disposable connectionDisposable = connectionObservable
                    .flatMapSingle(RxBleConnection::discoverServices)
                    .subscribeOn(backgroundThread)//Nen dung nhu vay de kiem soat luong rieng khi can subscrible
                    .observeOn(mainThreadScheduler)//Dung false cho flowable create
                    .doOnSubscribe(__ -> {
                        Log.i(TAG, "Discovery Connection...\n");
                        setStatus("Discovery Connection...");
                    })
                    .subscribe(
                            (deviceServices -> {
                                Log.i(TAG, "Discovery Connection Success!\n");
                                Log.i(TAG, "Parser Service");
                                for (BluetoothGattService service : deviceServices.getBluetoothGattServices()) {
                                    // Add service
                                    Log.i(TAG, "ServiceType:" + DiscoverHelp.getServiceType(service) + " for " + service.getUuid().toString());
                                    final List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                                    for (BluetoothGattCharacteristic characteristic : characteristics) {
                                        Log.i(TAG, "\t characteristic:" + DiscoverHelp.describeProperties(characteristic) + " for " + characteristic.getUuid().toString());
                                    }
                                }
                                Log.d(TAG, "Connection has been established!\n");
                                setStatus("Connection established!");
                                enableNotifyData();
                            })
                            , throwable -> {
                                Log.d(TAG, "Discovery Connection Error:" + throwable.getMessage() + "\n");
                                setStatus("Discovery Connection Error!");
                            }

                    );

            disposables.add(connectionDisposable);
        }
    }

    public void enableNotifyData() {

        if (isConnected()) {
            final Disposable disposable = connectionObservable
                    .flatMap(rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(TX_UUID_NOTIFY)))
                    .doOnNext(notificationObservable -> Log.i(TAG, "[I]>>Notification Has Been SetUp"))
                    .flatMap(notificationObservable -> notificationObservable)
                    .subscribeOn(backgroundThread)//Nen dung nhu vay de kiem soat luong rieng khi can subscrible
                    .observeOn(mainThreadScheduler)//Dung false cho flowable create
                    .subscribe(this::onNotificationReceived, this::onNotificationSetupFailure);

            disposables.add(disposable);
        }

    }

    private void onNotificationReceived(byte[] bytes) {
        String dataString = new String(bytes);
        Log.d(TAG, "[REC]>>" + dataString);
        // construct a string from the valid bytes in the buffer
        recursiveCheckCharacter(bytes, (byte) 13);//LF

    }

    private void onNotificationSetupFailure(Throwable throwable) {
        //noinspection ConstantConditions
        Log.d(TAG, "[I]>>" + "Notifications error: " + throwable);
    }

    public void writeData(byte[] payload) {

        if (isConnected()) {
            final Disposable disposable = connectionObservable
                    .firstOrError()
                    .flatMap(rxBleConnection -> rxBleConnection.writeCharacteristic(UUID.fromString(RX_UUID_WRITE), payload))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            bytes -> onWriteSuccess(),
                            this::onWriteFailure
                    );

            disposables.add(disposable);
        } else {
            Log.d(TAG, "[E]>>" + "BLE device not connect");
        }
    }

    private void onWriteSuccess() {

        Log.d(TAG, "[I]>>" + "Write success");
    }

    private void onWriteFailure(Throwable throwable) {

        Log.d(TAG, "[I]>>" + "Write error: " + throwable.getMessage());
    }
}
