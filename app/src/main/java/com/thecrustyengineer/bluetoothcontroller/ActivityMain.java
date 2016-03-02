package com.thecrustyengineer.bluetoothcontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class ActivityMain extends AppCompatActivity implements OnClickListener, AdapterView.OnItemClickListener {

    private static boolean SEARCH_DEVICE_ENABLED;
    private static boolean LED_STATE_CHANGED;

    private BluetoothAdapter bluetoothAdapter;
    private Set<BluetoothDevice> pairedBluetoothDevices;
    private ArrayList<BluetoothDevice> listBluetoothDevices;
    private ArrayList<String> listDeviceName;
    private ArrayAdapter<String> arrayAdapterDevice;
    private TransmissionThread transmissionThread;

    private ToggleButton toggleButtonAdapterSwitch;
    private ToggleButton toggleButtonLEDSwitch;
    private Button buttonSearchDevices;
    private Button buttonDisconnect;
    private ListView listViewDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listBluetoothDevices = new ArrayList<>();
        listDeviceName = new ArrayList<>();

        toggleButtonAdapterSwitch = (ToggleButton)findViewById(R.id.activity_main_toggleButton_adapter_switch);
        toggleButtonLEDSwitch = (ToggleButton)findViewById(R.id.activity_main_toggleButton_LED);
        buttonSearchDevices = (Button)findViewById(R.id.activity_main_button_search);
        buttonDisconnect = (Button)findViewById(R.id.activity_main_button_disconnect);
        listViewDevices = (ListView)findViewById(R.id.activity_main_listView_devices);

        toggleButtonAdapterSwitch.setOnClickListener(this);
        toggleButtonLEDSwitch.setOnClickListener(this);
        buttonSearchDevices.setOnClickListener(this);
        buttonDisconnect.setOnClickListener(this);

        arrayAdapterDevice = new ArrayAdapter<>(this,  android.R.layout.simple_list_item_1, listDeviceName);
        listViewDevices.setAdapter(arrayAdapterDevice);
        listViewDevices.setOnItemClickListener(this);

        //For API 18 and above, instance of BluetoothManager is obtained from system,
        //then, BluetoothAdapter instance obtained from it using getAdapter method.
        //For API 17 and below, BluetoothAdapter instance directly obtained using getDefaultAdapter() method.
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
            BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
        }else{
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        //Checking to see if device has a BluetoothAdapter
        if(bluetoothAdapter == null){
            Toast.makeText(this, "Oops!! This device doesn't seem to have a bluetooth adapter.", Toast.LENGTH_SHORT).show();
        }else{
            transmissionThread = new TransmissionThread();

            //Register broadcast receiver for the intent generated when a bluetooth device is found
            registerReceiver(broadcastReceiverAction, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        }
    }

    @Override
    protected void onResume(){
        super.onResume();

        if(bluetoothAdapter == null){
            toggleButtonAdapterSwitch.setEnabled(false);

            SEARCH_DEVICE_ENABLED = false;
        }else{
            if(bluetoothAdapter.isEnabled()){
                toggleButtonAdapterSwitch.setChecked(true);

                SEARCH_DEVICE_ENABLED = true;
            }else{
                toggleButtonAdapterSwitch.setChecked(false);

                SEARCH_DEVICE_ENABLED = false;
            }
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        unregisterReceiver(broadcastReceiverAction);
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.activity_main_toggleButton_adapter_switch:{
                //BluetoothAdapter.isEnabled() returns true if bluetooth is switched on, false if not.

                if(bluetoothAdapter.isEnabled()){
                    if(bluetoothAdapter.isDiscovering()){
                        bluetoothAdapter.cancelDiscovery();
                    }else if(transmissionThread.isConnected()){
                        transmissionThread.interrupt();
                    }
                    bluetoothAdapter.disable();

                    SEARCH_DEVICE_ENABLED = false;

                    listDeviceName.clear();
                    listBluetoothDevices.clear();
                    arrayAdapterDevice.notifyDataSetChanged();

                    Toast.makeText(this, "Adapter disabled", Toast.LENGTH_SHORT).show();
                }else{
                    Intent intentTurnOnBluetoothAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(intentTurnOnBluetoothAdapter, 1);

                    SEARCH_DEVICE_ENABLED = true;

                    Toast.makeText(this, "Adapter enabled", Toast.LENGTH_SHORT).show();
                }

                break;
            }
            case R.id.activity_main_button_search:{
                if(SEARCH_DEVICE_ENABLED && !transmissionThread.isConnected()){
                    if(bluetoothAdapter.isDiscovering()){
                        bluetoothAdapter.cancelDiscovery();
                    }

                    //Gets the list of bluetooth devices that are already paired with the android device
                    pairedBluetoothDevices = bluetoothAdapter.getBondedDevices();
                    listBluetoothDevices.clear();
                    listDeviceName.clear();

                    if(pairedBluetoothDevices.size() > 0){
                        for(BluetoothDevice device : pairedBluetoothDevices){
                            listBluetoothDevices.add(device);
                            listDeviceName.add("P)" + device.getName());
                        }
                        arrayAdapterDevice.notifyDataSetChanged();
                    }

                    //Sets the bluetooth adapter to start discovering nearby bluetooth devices that aren't paired.
                    //Once started, the bluetooth adapter must not be interacted with without cancelling the search
                    //using BluetoothAdapter.cancelDiscovery(). Discovered devices will be retured via intents to
                    //broadcast receiver with the Bluetoothdevice.ACTION_FOUND filter
                    bluetoothAdapter.startDiscovery();
                }

                break;
            }
            case R.id.activity_main_toggleButton_LED:{
                if(transmissionThread.isConnected()){
                    LED_STATE_CHANGED = true;
                }

                break;
            }
            case R.id.activity_main_button_disconnect:{
                if(transmissionThread.isConnected()) {
                    transmissionThread.interrupt();

                    Log.d("Thread interrupted", Boolean.toString(transmissionThread.isInterrupted()));
                }

                break;
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        //When a list item is clicked, an attempt to connect to the corresponding BluetoothDevice in the list
        //is made, via the TransmissionThread instance. If BluetoothAdapter is in discovery mode, no connection
        //attempt to the selected device is made.

        if(bluetoothAdapter.isDiscovering()){
            Toast.makeText(this, "Unable to connect! Devices being discovered by adapter.", Toast.LENGTH_SHORT).show();
        }else if((transmissionThread.getBluetoothSocket() != null) && (transmissionThread.isConnected())){
            Toast.makeText(this, "Unable to connect! Device already connected to adapter.", Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(this, "Connecting to " + listBluetoothDevices.get(position).getName().toString(), Toast.LENGTH_SHORT).show();

            transmissionThread = new TransmissionThread();
            transmissionThread.setupStream(listBluetoothDevices.get(position));
            transmissionThread.start();
        }
    }

    private final BroadcastReceiver broadcastReceiverAction = new BroadcastReceiver() {
        //Intents received by this receiver contain a BluetoothDevice that has been discovered by the adapter
        //in discovery mode.

        @Override
        public void onReceive(Context context, Intent intent) {
            if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())){
                listBluetoothDevices.add((BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
                listDeviceName.add(((BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).getName());
                arrayAdapterDevice.notifyDataSetChanged();
            }
        }
    };

    private class TransmissionThread extends Thread{

        private BluetoothSocket bluetoothSocket;
        private OutputStream outputStream;
        private PrintStream printStream;
        private InputStream inputStream;
        private InputStreamReader inputStreamReader;
        private BufferedReader bufferedReader;

        public void run(){
            try{
                //Connect the BluetoothSocket instance to the bluetooth device

                bluetoothSocket.connect();
            }catch(IOException ioe){

            }

            //Open the input and output streams.
            try{
                outputStream = bluetoothSocket.getOutputStream();
                printStream = new PrintStream(outputStream);
                inputStream = bluetoothSocket.getInputStream();
                inputStreamReader = new InputStreamReader(inputStream);
                bufferedReader = new BufferedReader(inputStreamReader);
            }catch(IOException ioe){

            }

            SEARCH_DEVICE_ENABLED = false;
            LED_STATE_CHANGED = false;

            try{
                while(!Thread.currentThread().isInterrupted()){
                    if(LED_STATE_CHANGED){
                        sendData(Boolean.toString(toggleButtonLEDSwitch.isChecked()));
                        LED_STATE_CHANGED = false;
                    }

                    Thread.sleep(10);
                }
            }catch(InterruptedException ie){
                close();
            }
        }

        private void sendData(String data){
            //Send data to the bluetooth device using the output stream.

            printStream.print(data);
        }

        private String getData(){
            String data = "";

            try {
                //Get data from the bluetooth device using the input stream.

                data = bufferedReader.readLine();
            } catch(IOException e) {
                e.printStackTrace();
            }

            return data;
        }

        private void close(){
            //method to handle the closing of all input output streams and buffers

            printStream.flush();
            printStream.close();
            try{
                outputStream.close();
                bufferedReader.close();
                inputStreamReader.close();
                inputStream.close();
                bluetoothSocket.close();
            }catch(IOException ioe){

            }

            SEARCH_DEVICE_ENABLED = true;

            Thread.currentThread().interrupt();
        }

        private boolean isConnected(){
            //Return a boolean value for the connection status of the bluetooth socket

            if(bluetoothSocket == null){
                return false;
            }else{
                return bluetoothSocket.isConnected();
            }
        }

        private void setupStream(BluetoothDevice bluetoothDevice){
            //Attempt to create a BluetoothSocket to communicate with the bluetooth device in question
            try{
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));
            }catch(IOException ioe){

            }
        }

        private BluetoothSocket getBluetoothSocket(){
            return bluetoothSocket;
        }
    }
}
