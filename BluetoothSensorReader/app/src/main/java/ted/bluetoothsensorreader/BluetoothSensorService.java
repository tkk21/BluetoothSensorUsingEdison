package ted.bluetoothsensorreader;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Created by ted on 11/7/2015.
 */
public class BluetoothSensorService {
    private static final String TAG = "BluetoothSensorService";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private final BluetoothAdapter mBluetoothAdapter;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    private int mState;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    public BluetoothSensorService(Context context){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    }

    public synchronized void setState (int state){
        Log.d(TAG, "turning state from " + mState +"to " +state);
        mState = state;
    }

    public synchronized int getState (){
        return mState;
    }

    /**
     * This is called to start a bluetooth connection with another device
     * @param device
     */
    public synchronized void connect (BluetoothDevice device){
        Log.d(TAG, "Connecting to " + device);

        if (mState == STATE_CONNECTING){
            if (mConnectThread != null){
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }
        if (mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * This is called to start managing the bluetooth connection
     *
     * @param socket
     * @param device
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device){
        Log.d(TAG, "connected to " + device);
        //Optional make send button visible

        //free the connect thread since we're already connected
        if (mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }
        //We are going to use the thread, so cancel that
        if (mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        setState(STATE_CONNECTED);
    }

    //free all the threads
    public synchronized void stop(){
        Log.d(TAG, "STOP!");
        if (mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    private class ConnectThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tempSocket = null;
            try {
                tempSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "failed to create socket", e);
            }
            mmSocket = tempSocket;
        }

        public void run() {
            Log.i(TAG, "Begin mConnectThread");
            setName("ConnectThread");
            mBluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                mmSocket.connect();
            } catch (IOException e) {
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() the socket during connection failure", e2);
                }

                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothSensorService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }


    private class ConnectedThread extends Thread{
        private final BluetoothSocket connectedBluetoothSocket;
        private final InputStream connectedInputStream;
        private final OutputStream connectedOutputStream;
        private static final int MESSAGE_READ = 2;

        public ConnectedThread(BluetoothSocket socket) {
            connectedBluetoothSocket = socket;
            InputStream tmpInStream = null;
            OutputStream tmpOutStream = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpInStream = socket.getInputStream();
                tmpOutStream = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            connectedInputStream = tmpInStream;
            connectedOutputStream = tmpOutStream;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    bytes = connectedInputStream.read(buffer);

                    //convert from byte[] to string
                    //if that didn't work use BufferedReader
                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buffer);
                    int size = byteArrayInputStream.available();
                    byte[] decode = new byte[size];
                    byteArrayInputStream.read(decode, 0, size);
                    String result = new String(decode, StandardCharsets.UTF_8);


                    //TODO
                    //write to CSV

                } catch (IOException e) {
                    Log.wtf(TAG, "disconnected during connected thread", e);
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                connectedOutputStream.write(buffer);
            } catch (IOException e) {
                Log.wtf(TAG, "Failed to write", e);
            }
        }

        public void cancel() {
            try {
                connectedBluetoothSocket.close();
            } catch (IOException e) {
                Log.wtf(TAG, "Failed to close the connect socket failed", e);
            }
        }
    }
}
