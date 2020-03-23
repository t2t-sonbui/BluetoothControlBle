package com.demo.embedded.bluetoothcontroll;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

import okio.Buffer;


public class ControlFragment extends Fragment {

    ImageButton btnDevice1;
    TextView stDevice1;

    public static final int STATE_ON = 1;
    public static final int STATE_OFF = 0;
    int device1State = STATE_OFF;
    int device2State = STATE_OFF;
    // Debugging
    private static final String TAG = ControlFragment.class.getSimpleName();

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_ENABLE_BT = 3;
    // Shape change ui

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

    /**
     * Member object for the chat services
     */
    private BluetoothSPPService mChatService = null;
    //
    private Buffer buffer = new Buffer();
    private Buffer extantBuffer = new Buffer();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
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
        } else if (mChatService == null) {
            setupInit();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothSPPService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
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
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothSPPService(activity, mHandler);
        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer();
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
        if (mChatService.getState() != BluetoothSPPService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

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

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothSPPService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            // mLogText.setText("");
                            break;
                        case BluetoothSPPService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothSPPService.STATE_LISTEN:
                        case BluetoothSPPService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    // mLogText.append(writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    int size = msg.arg1;
                    String readMessage = new String(readBuf, 0, size);
                    Log.d(TAG, "readMessage:" + readMessage);
                    byte[] dataBuf = Arrays.copyOf(readBuf, size);
                    // construct a string from the valid bytes in the buffer
                    recursiveCheckCharacter(dataBuf, (byte) 13);//LF

                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data);
                }
                break;
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

    /**
     * Establish connection with other device
     *
     * @param data An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     */
    private void connectDevice(Intent data) {
        // Get the device MAC address
        Bundle extras = data.getExtras();
        if (extras == null) {
            return;
        }
        String address = extras.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
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
}
