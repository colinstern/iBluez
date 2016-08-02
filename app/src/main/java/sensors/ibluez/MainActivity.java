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
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class MainActivity extends Activity {

    // Intent request codes
    private static final int REQUEST_ENABLE_BT = 3;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mArrayAdapter;

    private ConnectThread mmConnectThread;

    private BluetoothDevice imFeelingLuckyDevice; //total guess - is the first device the microcontroller?

    private String mConnectedDeviceName;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView textview = (TextView) findViewById(R.id.textview);

        /**Get Bluetooth adapter
         *
         */
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
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

        /**Query paired devices
         *
         */
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
// If there are paired devices
//        mArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                imFeelingLuckyDevice = device;
                if (imFeelingLuckyDevice == null) {
                    sendMessage("chosen device is null\n");
                }
                else {
                    textview.append("Chosen device is " + imFeelingLuckyDevice.getName() + "\n");
                }
                // Add the name and address to an array adapter to show in a ListView
//                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                textview.append("Paired with " + device.getName() + "\n" + device.getAddress() + "\n");
            }
        }

        /**Discover devices
         *
         */
// Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        /**
         * Enable discoverability
         */
        Intent discoverableIntent = new
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);

        mBluetoothAdapter.startDiscovery();




/**
 * The Handler that gets information back from the BluetoothService
 */
         final Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
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
        mmConnectThread = new ConnectThread(imFeelingLuckyDevice, mHandler);
        sendMessage("Starting ConnectThread\n");
        mmConnectThread.start();
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

    public void sendMessage(String message) {
        final TextView textview = (TextView) findViewById(R.id.textview);
        textview.append(message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(mReceiver);
    }
}
