package com.example.biswajit.androidusb;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends Activity {
    public final String ACTION_USB_PERMISSION = "com.example.biswajit.arduinousb.USB_PERMISSION";
    Button startButton, sendButton, clearButton, stopButton;
    TextView textView;
    EditText editText;

    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;

    ArrayList<String> fwUpdateCommands = new ArrayList();

    boolean git;

    final int timeout = 5000; // ms
    final int maxTries = 5;
    final int packet_to = 200;

    String serialData = "";

    ProgressDialog dialog;
    String TAG = "coelfuu";

    byte serialBytes[] = {};

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] data) {
            try {

                serialData += new String(data, "UTF-8");

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                outputStream.write(serialBytes);
                outputStream.write(data);
                serialBytes = outputStream.toByteArray();
                Log.d(TAG, "Serial data  :" + new String(serialBytes).replaceAll("\n", " "));
                Log.d(TAG, "Serial bytes :" + Arrays.toString(serialBytes));

                tvAppend(textView, serialData);
                Utils.appendLog("Serial data : " + new String(serialBytes));
                Utils.appendLog("Serial bytes : " + Arrays.toString(serialBytes));

            } catch (Exception e) {
                Log.d(TAG, "Exception " + e.getMessage());
                Utils.appendLog("Serial data Exception ");
                e.printStackTrace();
            }
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            setUiEnabled(true);
                            serialPort.setBaudRate(115200);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(mCallback);
                            tvAppend(textView, "Serial Connection Opened!\n");

                            Utils.appendLog("\n Serial Connection Opened!");

                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                onClickStart(startButton);
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                onClickStop(stopButton);

            }
        }

        ;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);

        startButton = (Button) findViewById(R.id.buttonStart);
        sendButton = (Button) findViewById(R.id.buttonSend);
        clearButton = (Button) findViewById(R.id.buttonClear);
        stopButton = (Button) findViewById(R.id.buttonStop);
        editText = (EditText) findViewById(R.id.editText);
        textView = (TextView) findViewById(R.id.textView);

        setUiEnabled(false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        registerReceiver(broadcastReceiver, filter);

        fwUpdateCommands.add("fw_upgrade");
        fwUpdateCommands.add("reset");

        dialog = new ProgressDialog(this);
        dialog.setCancelable(false);

    }

    public void setUiEnabled(boolean bool) {
        startButton.setEnabled(!bool);
        sendButton.setEnabled(bool);
        stopButton.setEnabled(bool);
        textView.setEnabled(bool);

    }

    public void onClickStart(View view) {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (deviceVID == 1027) {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        }
    }

    public void onClickSend(View view) {
        String string = editText.getText().toString();
        execCommand(string.concat("\r").getBytes(), "");
    }

    public void onClickStop(View view) {
        setUiEnabled(false);
        serialPort.close();
        Utils.appendLog("\n Close connection.");
    }

    public void onClickClear(View view) {
        textView.setText(" ");
    }

    public void onClickCMD(View view) {
        enterCMDMode();
//        execCommand("cmd\r".getBytes(), "");
    }

    public void onClickStatus(View view) {
        execCommand("status\r".getBytes(), "");
    }

    public void onClickErase(View view) {
        execCommand("erase coel\r".getBytes(), "");
    }

    public void onClickRead(View view) {
        execCommand("read-8\r".getBytes(), "");
    }

    private void enterCMDMode() {
        String message = "Attempting to enter cmd mode ...";
        if(!dialog.isShowing()){
            dialog.show();
            dialog.setMessage(message);
        }

        Log.d(TAG, message);
        execCommand("c".getBytes(), message);

        if (!waitForSerialInput("uart waiting for - \"cmd\"".getBytes(), timeout)) {
            // Are we already in CMD mode?
            dialog.setMessage("Checking if we are already in cmd mode ...");
            execCommand("\r".getBytes(), message);

            if (!waitForSerialInput("Invalid command, check manual!".getBytes(), timeout)) {
                Log.d(TAG, "ERROR: Couldn't enter cmd mode");
            } else {
                Log.d(TAG, "Already In cmd mode");
            }
        } else {
            // Try to enter CMD mode.
            long startedTime = System.currentTimeMillis();
            while (!waitForSerialInput("cmd:>".getBytes(), 100)) {
                execCommand("cmd\r".getBytes(), "");
                long elapsed = (System.currentTimeMillis() - startedTime);
                if (elapsed > 10) {
                    Log.d(TAG, "ERROR: Couldn't enter cmd mode");
                }
            }
        }

        dialog.dismiss();

    }

    // fw_upgrade command
    public void onClickFWUpgrade(View view) {
        final String message = "Attempting to enter BSL mode ...";
        if(!dialog.isShowing()) {
            dialog.show();
            dialog.setMessage(message);
        }

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                execCommand("fw_upgrade\r".getBytes(), message); //hexStringToByteArray("66775f75706772616465"); //fw_upgrade

                if (!waitForSerialInput("cmd:>".getBytes(), timeout)) {
                    showMessage("ERROR: Couldn't enter BSL mode.Couldn't enter fw_upgrade command.");
                    dialog.dismiss();
                } else {
                    onClickReset(null);
                }
            }
        }, 3000);
    }

    // reset command
    public void onClickReset(View view) {
        final String message = "Wait while the device is entering BSL mode.";
        dialog.setMessage(message);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                execCommand("reset\r".getBytes(), message);
                showMessage("Entered BSL mode.");
                setBaud9600();

            }
        }, 5000);
    }

    public void setBaud9600() {
        String message = "Changing serial baud rate.";
        dialog.setMessage(message);
        showMessage(message);
        serialPort.setBaudRate(9600);
        serialPort.setParity(UsbSerialInterface.PARITY_EVEN);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                showMessage("Baud rate changed.");
                onClickMassErase(null);

            }
        }, 50000);

    }

    // mass erase command
    public void onClickMassErase(View view) {
        String message = "Attempting to erase device's memory ...";
        dialog.setMessage(message);
        byte[] command = hexStringToByteArray("8001001564a3");

        execCommand(command, message);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                showMessage("Mass erase command done.");
                onClickPassword(null);
            }
        }, 50000);

    }

    // password command
    public void onClickPassword(View view) {
        String message = "Attempting to input password ...";
        dialog.setMessage(message);

        byte[] command = hexStringToByteArray("80210011ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff9ee6");
        execCommand(command, message);

        if (!waitForSerialInput(hexStringToByteArray("008002003b0060c4"), timeout)) {
            showMessage("ERROR: Password command failed");
            dialog.dismiss();
        } else {
            showMessage("Password command successful.");
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    onClickResetVector(null);
                }
            }, 2000);
        }

    }

    // write 0x1000 to reset vector
    public void onClickResetVector(View view) {
        String message = "write 0x1000 to reset vector";
        dialog.setMessage(message);
        byte[] command = hexStringToByteArray("80060010feff000010f8bd");

        execCommand(command, message);

        if (!waitForSerialInput("008002003b0060c4".getBytes(), timeout)) {
            showMessage("ERROR: Write command failed");
            dialog.dismiss();
        } else {
            showMessage("Write command successful.");
            onClickChangeBaud(null);
        }
    }


    // change baud rate for faster download
    public void onClickChangeBaud(View view) {
        String message = "Attempting to change baud rate to 115200 ...";
        dialog.setMessage(message);
        byte[] command = hexStringToByteArray("80020052061415");

        execCommand(command, message);

        if (!waitForSerialInput("00".getBytes(), timeout)) {
            showMessage("ERROR: Baud rate change command failed");
            dialog.dismiss();
        } else {
            setBaud115200();
        }
    }

    public void setBaud115200() {
        serialPort.setBaudRate(115200);
        serialPort.read(mCallback);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                showMessage("Baud rate changed successfully");
                onClickFWUpdate(null);
            }
        }, 50000);
        onClickFWUpdate(null);
    }

    //Firmware upgrade process start
    public void onClickFWUpdate(View view) {
        firmwareUpdate();
    }

    // boot our firmware
    public void onClickBootFW(View view) {
        byte[] command = hexStringToByteArray("80040017feff00e635");

        execCommand(command, "Booting firmware ...");

        if (!waitForSerialInput("Firmware version".getBytes(), timeout)) {
            showMessage("ERROR: Reset failed. Manual reset required.");
            dialog.dismiss();
        } else {
            showMessage("Firmware update complete!");
        }

    }

    public void onClickCR(View view) {
//        QByteArray text = QByteArray::fromHex("517420697320677265617421");
//        text.data();
//        Toast.makeText(this, new String(hexStringToByteArray("517420697320677265617421")), Toast.LENGTH_SHORT).show();

        enterCMDMode();

//        try {
//            byte[] command = hexStringToByteArray("FFE0h");
//            Log.d(TAG, "password bytes : " + Arrays.toString(command));
//            Log.d(TAG, "password string utf : " + new String(command, "UTF-8"));
//            Log.d(TAG, "password string : " + command.toString());
//        } catch (Exception e) {
//            Log.d(TAG, "R : " + e.getMessage());
//
//        }
    }

    private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ftv.append(ftext);
            }
        });
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public void execCommand(byte[] command, String message) {
        showMessage(message);
        serialData = "";
        serialBytes = "".getBytes();
        serialPort.write(command);
    }

    private void showMessage(String msg) {
        //Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        Utils.appendLog("\n" + msg);
    }

    public void firmwareUpdate() {
        dialog.setMessage("Downloading firmware...");
        Utils.appendLog("\n Downloading firmware...");

        StringBuilder sb = new StringBuilder();
        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    Toast.makeText(getApplicationContext(), "Downloading firmware...", Toast.LENGTH_SHORT).show();
                }
            });

            FileInputStream fis = new FileInputStream(Environment.getExternalStorageDirectory() + File.separator + "coelfuu.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));

            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            br.close();
        } catch (Exception e) {
            Utils.appendLog("\n" + e.getMessage());
        }

        dialog.setMessage("File read complete.");
        Utils.appendLog("\n File read complete.");

        String str = sb.toString();
        Utils.appendLog("\n File data : " + str);

        List<String> list = new ArrayList<>();

        while (!str.isEmpty()) {
            int pos = str.indexOf("@", 1);
            if (pos == -1)
                pos = str.length();
            String temp = str.substring(0, pos);
            list.add(temp);
            Utils.appendLog("\n List data : " + temp);
            str = str.replace(temp, "");
        }


        int currsize = 0;
        int reset_val_l = 0, reset_val_h = 0;

        dialog.setMessage(" Data processing...");
        Utils.appendLog("\n Data processing...");

        try {
            while (!list.isEmpty()) {
                String temp = list.get(0);
                list.remove(0);
                int indexa = temp.indexOf("\n");

                String sizestr = (temp.substring(0, indexa)).replace(temp.substring(0, 1), "");
                temp = temp.substring(indexa);
                int writeAddress = hexToInt(sizestr);


                byte[] firmware = hexStringToByteArray(temp.replaceAll("q|\\r|\\n| |\\t", ""));

                Utils.appendLog("\n firmware :" + temp.replaceAll("q|\\r|\\n| |\\t", ""));

                while (firmware.length > 0) {
                    byte[] chunk = Arrays.copyOfRange(firmware, 0, 240);
                    firmware = Arrays.copyOfRange(firmware, 240, firmware.length);
                    int length = chunk.length + 4;

                    if (writeAddress <= 0xfffe && writeAddress + chunk.length > 0xffff) {
                        reset_val_l = chunk[0xfffe - writeAddress];
                        reset_val_h = chunk[0xffff - writeAddress];
                    }


                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    byte[] message;
                    // length
                    outputStream.write(length & 0xFF);
                    outputStream.write((length >> 8) & 0xFF);
                    // cmd
                    outputStream.write(0x10);
                    // address
                    outputStream.write(writeAddress & 0xFF);
                    outputStream.write((writeAddress >> 8) & 0xFF);
                    outputStream.write((writeAddress >> 16) & 0xFF);
                    // data
                    outputStream.write(chunk);
                    message = outputStream.toByteArray();
                    // checksum
                    int crc = checksum(message);
                    outputStream.write(crc & 0xFF);
                    outputStream.write((crc >> 8) & 0xFF);
                    message = outputStream.toByteArray();

                    int tries = 0;
                    do {
                        if (++tries > maxTries) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "Response packet from BSL was not received with packet", Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Response packet from BSL received", Toast.LENGTH_SHORT).show();
                            }
                        });

                        Utils.appendLog("\n Message : " + message);
                        serialPort.write(message);

                    }
                    while (!waitForSerialInput(hexStringToByteArray("008002003b0060c4"), packet_to));

                    writeAddress += chunk.length;
                    currsize += chunk.length * 3 + chunk.length / 16;

                }
            }

            Utils.appendLog("\n write our reset vector");

            // write our reset vector
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] message = hexStringToByteArray("80060010feff00");
            outputStream.write(message);
            outputStream.write(reset_val_l);
            outputStream.write(reset_val_h);
            message = outputStream.toByteArray();
            int crc = checksum(message);
            outputStream.write(crc & 0xFF);
            outputStream.write((crc >> 8) & 0xFF);
            message = outputStream.toByteArray();

            serialPort.write(message);

            if (!waitForSerialInput(hexStringToByteArray("008002003b0060c4"), timeout)) {
                showMessage("ERROR: Download failed");
            } else {
                showMessage("Firmware download successful.");
            }

            // boot our firmware
            showMessage("Booting firmware ...");
            serialPort.write(hexStringToByteArray("80040017feff00e635"));

            if (!waitForSerialInput("Firmware version".getBytes(), timeout)) {
                showMessage("WARNING: Reset failed. Manual reset required.");
            } else {
                showMessage("Firmware update complete!");
            }

        } catch (Exception e) {
            Utils.appendLog("\n Exception  ");
            dialog.dismiss();
        }

    }

    public int checksum(byte[] arr) {
        int crc = 0xFFFF;
        int n = arr.length;
        for (int i = 3; i < n; ++i) {
            int x;
            x = ((crc >> 8) ^ arr[i]) & 0xff;
            x ^= x >> 4;
            crc = (crc << 8) ^ (x << 12) ^ (x << 5) ^ x;
        }
        return crc;
    }

    private int hexToInt(String hex) {
        int value = Integer.parseInt(hex, 16);
        return value;
    }

    private boolean waitForSerialInput(byte[] expectedBytes, int msecs) {
        Utils.appendLog("Expected serial bytes : " + Arrays.toString(expectedBytes));
        Log.d(TAG, "Expected serial bytes : " + Arrays.toString(expectedBytes));
        long startedTime = System.currentTimeMillis();
        while (!matchBytes(serialBytes, expectedBytes)) {
            long elapsed = (System.currentTimeMillis() - startedTime);
            if (elapsed > msecs) {
                return false;
            }

            try {
                Thread.sleep(msecs - elapsed);
            } catch (InterruptedException e) {
                Log.d(TAG, e.getMessage());
            }
        }
        return true;
    }

    private void writeToFile(String data) {
        try {
            File file = new File(Environment.getExternalStorageDirectory() + File.separator + "cfu.txt");
            if (!file.exists()) {
                file.createNewFile();
            }

            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fos);
            outputStreamWriter.write(data);
            outputStreamWriter.close();
            Log.d(TAG, "Write ");
        } catch (Exception e) {
            Log.e(TAG, "File write failed: " + e.getMessage());
        }
    }

    public boolean matchBytes(byte[] a, byte[] b) {
        if (ByteArrayFinder.find(a, b) == -1) {
            return false;
        }
        return true;

    }
}
