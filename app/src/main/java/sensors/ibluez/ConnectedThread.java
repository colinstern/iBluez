package sensors.ibluez;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * This class handles the connection with iShadow and receives and saves data.
 * Created by cost on 7/31/16.
 */
class ConnectedThread extends Thread {
    private final FallbackBluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private final Handler mHandler;
    FileOutputStream outputStream;
    Context context;
    /** Temporary way to hold the text read from a packet */
    public String writeString = "";

    public ConnectedThread(FallbackBluetoothSocket socket, Handler handler) {
        mHandler = handler;
        mmSocket = socket;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the input and output streams, using temp objects because
        // member streams are final
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) {
            sendMessageToMainActivity("Unable to open input stream: " + e.toString());
        }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }

    public void run() {
//        sendMessageToMainActivity("Running ConnectedThread");
        byte[] buffer = new byte[1024];  // buffer store for the stream
        int bytes; // bytes returned from read()
        if (!isExternalStorageWritable()) {
            sendMessageToMainActivity("External storage is not writable!");
        }
        File storageDir = getStorageDir();
        String filenamePrefix = "data_";
        int i = 0;
        File file = makeNewFile(storageDir, filenamePrefix + i);

        /*Increment filename suffix until an unused filename is found*/
        while (file.exists()) {
            i++;
            file = makeNewFile(storageDir, filenamePrefix + i);
        }
        String filename = filenamePrefix + i;
        sendMessageToMainActivity("Created new file: " + filename);

        try {
            try {
                outputStream = new FileOutputStream(file);
            } catch (FileNotFoundException e) {

            }

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    sendMessageToMainActivity("Ready to read data");

                    /** Clean out buffer */
                    buffer = new byte[1024];
                    bytes = mmInStream.read(buffer);

                    /** Keep filling the buffer until it finds the stop character */
                    while (!(new String(buffer, "UTF-8").contains("$"))) {
                        byte [] append = new byte[1024];
                        bytes = mmInStream.read(append);

                        byte[] combined = new byte[buffer.length + append.length];

                        System.arraycopy(buffer,0,combined,0         ,buffer.length);
                        System.arraycopy(append,0,combined,buffer.length,append.length);

                        buffer = combined;
                    }

//                    sendMessageToMainActivity("Data read");
                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                    sendMessageToMainActivity("Read " + bytes + " bytes");
                    
                    if (parse(buffer)) {
                        /* Data matches the hash, so we write it */
                        outputStream.write(writeString.getBytes());
                        write("O".getBytes()); //OK
                        sendMessageToMainActivity("Packet successfully received");
                    } else {
                        /* Data is (probably) corrupted, so we ask for it to be resent */
                        sendMessageToMainActivity("Asking for retransmit of packet");
                        write("R".getBytes()); //RETRANSMIT
                    }
                } catch (IOException e) {
//                sendMessageToMainActivity("Unable to read from inputStream!");
                sendMessageToMainActivity("Connection terminated.");
                    break;
                }
            }
            outputStream.close();
            printSavedData(storageDir, filename);
        } catch (IOException e) {
            sendMessageToMainActivity("IO error! " + e.toString());
            e.getStackTrace();
        }
    }

    /**
     * Creates a new file.
     * @param dir Directory to create file in.
     * @param filename Name of file.
     * @return
     */
    public File makeNewFile(File dir, String filename) {
        return new File(dir, filename);
    }

    /**
     * Read and print data from a file.
     * @param dir The directory of the file.
     * @param filename The name of the file to open.
     */
    public void printSavedData(File dir, String filename) {
        try {
            File myDir = new File(dir.getAbsolutePath());
            String s = "";

//                FileWriter fw = new FileWriter(myDir + "/Test.txt");
//                fw.write("Hello World");
//                fw.close();

            BufferedReader br = new BufferedReader(new FileReader(myDir + "/" + filename));
            sendMessageToMainActivity("Reading file...");

            while (true) {
                try {
                    s = br.readLine();
                    sendMessageToMainActivity(s);
                } catch (Exception e){
                    sendMessageToMainActivity("Finished reading data.\n");
                    break;
                }
            }
            // Set TextView text here using tv.setText(s);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if the packet was correctly transmitted.
     * @param buffer The byte array just read, aka the "packet".
     * @return Whether the hash of the data matches the hash in the packet header.
     */
    private boolean parse(byte[] buffer) {
        String bufString = "";
        try {
            bufString = new String(buffer, "UTF-8");
        } catch (UnsupportedEncodingException e){
            sendMessageToMainActivity(e.toString());
        }

        String[] tokens = bufString.split("\\|"); //pipe is escaped because it is a regex

        if (tokens.length > 3) {
            sendMessageToMainActivity("Packet: " + tokens[1]);
            /** The pipe is included because it is used for hash calculation on the microcontroller */
            int newHash = hash(tokens[3]+"|");
            sendMessageToMainActivity("New hash: " + new Integer(newHash).toString() + " Original hash: " + tokens[2]);

            try {
                if (Integer.parseInt(tokens[2]) == newHash) {
                    writeString = tokens[3];
                writeString = writeString.replace('$', Character.MIN_VALUE); //remove the $
                    return true;
                } else {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            return false; //the packet was misformed and we couldn't even find the header so we need it to be resent
        }
    }

    /**
     * Makes a hash of the input string by adding up the ASCII values of its characters.
     * @param data The input string.
     * @return The hash.
     */
    private int hash(String data) {
        char[] chars = data.toCharArray();
        int result = 0;
        for (char c: chars)
            result += (int) c;
        return result;
    }

    /* Call this from the main activity to send data to the remote device */
    public void write(byte[] bytes) {
        try {
            mmOutStream.write(bytes);
        } catch (IOException e) { }
    }

    /* Call this from the main activity to shutdown the connection */
    public void cancel() {
        try {
            mmSocket.close();
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

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public File getStorageDir() {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "iShadow Received Data");
        if (!file.mkdirs()) {
//            sendMessageToMainActivity("Directory not created");
        }
        return file;
    }
}