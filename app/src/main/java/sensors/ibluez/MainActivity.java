package sensors.ibluez;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import java.util.Set;

public class MainActivity extends Activity {
    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mArrayAdapter;

    private ConnectThread mmConnectThread;

    private BluetoothDevice imFeelingLuckyDevice; //total guess - is the first device the microcontroller?

    private String mConnectedDeviceName;

    private boolean mDisconnectShown;

    private BluetoothAdapter mBluetoothAdapter;

    private static boolean openContextMenuOnce = true;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MyApplication.setContext(this);

        /**
         * GUI setup
         */
        setContentView(R.layout.gui_main);

        final TextView textview = (TextView) findViewById(R.id.textview_new);
        textview.setMovementMethod(new ScrollingMovementMethod());

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        /* Set an OnMenuItemClickListener to handle menu item clicks */
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()){
                    case R.id.action_disconnect:
                        sendMessage("Disconnecting...\n");
                        if (mmConnectThread != null) {
                            mmConnectThread.cancel();
                        }
                        else {
                            sendMessage("No connection to close!\n");
                        }
                        sendMessage("Disconnected.\n");
                        return true;
                    case R.id.action_discoverable:
                        ensureDiscoverable();
                        sendMessage("Now discoverable\n");
                        return true;
                    case R.id.action_connect:
                        if (openContextMenuOnce) {
                            openContextMenuOnce = !openContextMenuOnce;
                            // Launch the DeviceListActivity to see devices and do scan
                            Intent serverIntent = new Intent(MyApplication.getAppContext(), DeviceListActivity.class);
                            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                        }
                        else {
                            sendMessage("Cannot connect to more than one device. Try restarting the app.\n");
                        }
                        return true;
                }
                return false;
            }
        });

        toolbar.inflateMenu(R.menu.toolbar_menu);

        /**Get Bluetooth adapter
         *
         */
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
           textview.append("Device does not support bluetooth");
            return;
        }

        /**Enable bluetooth
         *
         */
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        /**Discover devices
         * Register the BroadcastReceiver
         */
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);
    }

    /**
     * The Handler that gets information back from the BluetoothService
     */
    final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            final TextView textview = (TextView) findViewById(R.id.textview_new);
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case Constants.STATE_CONNECTED:
//                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            break;
                        case Constants.STATE_CONNECTING:
//                            setStatus(R.string.title_connecting);
                            break;
                        case Constants.STATE_LISTEN:
                        case Constants.STATE_NONE:
//                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    textview.append("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    textview.append("\n" + mConnectedDeviceName + ":  " + readMessage);
                    scrollToBottom();
                    break;
                case Constants.MESSAGE_ERROR:
                    byte[] errorBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String errorMessage = new String(errorBuf, 0, msg.arg1);
                    textview.append("\n" + mConnectedDeviceName + ":  " + errorMessage );
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    textview.append("Connected to "
                            + mConnectedDeviceName.toString());
                    Toast.makeText(MainActivity.this, "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_TOAST:
                    Toast.makeText(MainActivity.this, msg.getData().getString(Constants.TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    /**
     * Start ConnectThread
     */
    public void connect(Intent data) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device

        mmConnectThread = new ConnectThread(device, mHandler);
//        sendMessage("Starting ConnectThread\n");
        mmConnectThread.start();
    }

    /*
    Handles the response of DeviceListActivity
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connect(data);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connect(data);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Toast.makeText(this,"Bluetooth not enabled, leaving...",
                            Toast.LENGTH_SHORT).show();
                }
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
//                    mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                sendMessage("Discovered " + device.getName() + "\n" + device.getAddress());
            }
        }
    };

    /**
     * Write a message on the TextView.
     * @param message
     */
    public void sendMessage(String message) {
        final TextView textview = (TextView) findViewById(R.id.textview_new);
        textview.append(message);
        scrollToBottom();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(mReceiver);
    }


    /**
     * Scrolls to the bottom of the TextView.
     */
    private void scrollToBottom()
    {

        final TextView mTextStatus = (TextView) findViewById(R.id.textview_new);
        final ScrollView mScrollView = (ScrollView) findViewById(R.id.scrollview);
        mScrollView.post(new Runnable()
        {
            public void run()
            {
                mScrollView.smoothScrollTo(0, mTextStatus.getBottom());
            }
        });
    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

}
