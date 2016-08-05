package sensors.ibluez;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Created by cost on 7/31/16.
 */
class ConnectThread extends Thread {
    private final BluetoothSocket mmSocket;

    private final BluetoothDevice mmDevice;

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private static final UUID MY_UUID =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    public ConnectedThread mConnectedThread;

    public Handler mHandler;

    public FallbackBluetoothSocket fallbackSocket;

    public ConnectThread(BluetoothDevice device, Handler handler) {
        // Use a temporary object that is later assigned to mmSocket,
        // because mmSocket is final
        mHandler = handler;
        BluetoothSocket tmp = null;
        mmDevice = device;

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Get a BluetoothSocket to connect with the given BluetoothDevice
        try {
            // MY_UUID is the app's UUID string, also used by the server code
            tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            sendMessageToMainActivity("Opening socket");
        } catch (IOException e) {
            sendErrorMessageToMainActivity("Could not get a socket\n");
        }
        mmSocket = tmp;
    }

    public void run() {
//        sendMessageToMainActivity("\nRunning ConnectThread...\n");
        // Cancel discovery because it will slow down the connection
        mBluetoothAdapter.cancelDiscovery();
        try {
            // Connect the device through the socket. This will block
            // until it succeeds or throws an exception
            mmSocket.connect();
        } catch (IOException connectException) {
//            sendErrorMessageToMainActivity("Error: " + connectException.toString());
            // Unable to connect; close the socket and get out
//            sendMessageToMainActivity("Unable to connect");

            try {
                mmSocket.close();
                sendMessageToMainActivity("Closing socket");
                fallbackSocket = new FallbackBluetoothSocket(mmSocket);
                sendMessageToMainActivity("Opening new socket...");
                fallbackSocket.connect();
                sendMessageToMainActivity("Connection successful");
                // Do work to manage the connection (in a separate thread)
                manageConnectedSocket(fallbackSocket);
            } catch (IOException closeException) {
                sendErrorMessageToMainActivity("Unable to open socket: Is iShadow on?");
                return;
            }
        }
    }

    public void manageConnectedSocket(FallbackBluetoothSocket mmSocket)
    {
        mConnectedThread = new ConnectedThread(mmSocket, mHandler);
        sendMessageToMainActivity("Starting ConnectedThread...");
        mConnectedThread.start();
    }

    /** Will cancel an in-progress connection, and close the socket */
    public void cancel() {
        try {
            mmSocket.close();
            fallbackSocket.close();
        } catch (IOException e) { }
    }

    public void sendMessageToMainActivity(String message) {
        byte[] buffer;  // buffer store for the stream
        int bytes;
        // Read from the InputStream
        bytes = message.getBytes().length;
        buffer = message.getBytes();
        // Send the obtained bytes to the UI activity
        mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                .sendToTarget();
    }

    public void sendErrorMessageToMainActivity(String message) {
        byte[] buffer;  // buffer store for the stream
        int bytes;
        // Read from the InputStream
        bytes = message.getBytes().length;
        buffer = message.getBytes();
        // Send the obtained bytes to the UI activity
        mHandler.obtainMessage(Constants.MESSAGE_ERROR, bytes, -1, buffer)
                .sendToTarget();
    }
}

 class FallbackBluetoothSocket {

    private BluetoothSocket fallbackSocket;

    public FallbackBluetoothSocket(BluetoothSocket tmp) throws IOException {
        fallbackSocket = tmp;
        try {
            Class<?> clazz = tmp.getRemoteDevice().getClass();
            Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};
            Method m = clazz.getMethod("createRfcommSocket", paramTypes);
            Object[] params = new Object[]{Integer.valueOf(1)};
            fallbackSocket = (BluetoothSocket) m.invoke(tmp.getRemoteDevice(), params);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

     public InputStream getInputStream() throws IOException {
         return fallbackSocket.getInputStream();
     }

     public OutputStream getOutputStream() throws IOException {
         return fallbackSocket.getOutputStream();
     }


     public void connect() throws IOException {
         fallbackSocket.connect();
     }


     public void close() throws IOException {
         fallbackSocket.close();
     }
}