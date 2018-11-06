package com.example.biswajit.androidusb;

import android.app.Activity;
import android.app.PendingIntent;
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


public class MainAct extends Activity {
    public final String ACTION_USB_PERMISSION = "com.example.biswajit.arduinousb.USB_PERMISSION";
    Button startButton, sendButton, clearButton, stopButton;
    TextView textView;
    EditText editText;

    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;

    ArrayList<String> fwUpdateCommands = new ArrayList();

    final int timeout = 5000; // ms
    final int maxProgress = 100;
    final int infProgress = -1;
    final int maxTries = 5;
    final int packet_to = 200;

    boolean terminator;

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = null;
            try {
                data = new String(arg0, "UTF-8");
                data.concat("/n");

//                Handler handler = new Handler();
//                handler.postDelayed(new Runnable() {
//                    public void run() {
//                        // yourMethod();
//                    }
//                }, 5000);

                if (data.contains("Invalid command, check manual!")) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            //Toast.makeText(MainActivity.this, "Invalid command", Toast.LENGTH_SHORT).show();
                        }
                    });

                }

                tvAppend(textView, data);
                Utils.appendLog("\n Serial data : " + data);

                if (data.equalsIgnoreCase(new String(hexStringToByteArray("008002003b0060c4")))) {
                    terminator = true;
                }
            } catch (Exception e) {
                Utils.appendLog("\n Serial data Exception ");
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
//                            serialPort.setBaudRate(9600);
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
        serialPort.write(string.concat("\n\r").getBytes());
        tvAppend(textView, "  " + string + "\n");
    }

    public void onClickStop(View view) {
        setUiEnabled(false);
        serialPort.close();
    }

    public void onClickClear(View view) {
        Utils.appendLog("\n Click clear button!");
        textView.setText(" ");

    }

    public void onClickCMD(View view) {
        Utils.appendLog("\n Click CMD button!");
        serialPort.write("cmd\n\r".getBytes());
    }

    public void onClickStatus(View view) {
        Utils.appendLog("\n Click status button!");
        serialPort.write("status\n\r".getBytes());
    }

    public void onClickErase(View view) {
        Utils.appendLog("\n Click erase button!");
        serialPort.write("erase coel\n\r".getBytes());
    }

    public void onClickRead(View view) {
        Utils.appendLog("\n Click read button!");
        serialPort.write("read-8\n\r".getBytes());
    }

    public void onClickFWUpgrade(View view) {
        Utils.appendLog("\n Click fw_up button!");
        execCommand("fw_upgrade", "Attempting to enter BSL mode ...", "Enter fw_upgrade command.");
    }

    public void onClickReset(View view) {
        Utils.appendLog("\n Click reset button!");
        execCommand("reset", "Attempting to enter RESET ...", "Entered BSL mode");
    }

    public void onClickC1(View view) {
//        serialPort.setBaudRate(9600);
//        serialPort.setParity(UsbSerialInterface.PARITY_EVEN);
        byte[] data = hexStringToByteArray("8001001564a3");
        execCommand(data.toString(), "Attempting to erase device's memory ...", "Mass erase command done.");
    }

    public void onClickC2(View view) {
        byte[] data = hexStringToByteArray("80210011ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff9ee6");
        execCommand(data.toString(), "Attempting to input password ...", "Password command successful.");
    }

    public void onClickC3(View view) {
        byte[] data = hexStringToByteArray("80060010feff000010f8bd");
        execCommand(data.toString(), "write 0x1000 to reset vector", "Write command done");
    }

    public void onClickC4(View view) {
        byte[] data = hexStringToByteArray("80020052061415");
        execCommand(data.toString(), "Attempting to change baud rate to 115200 ...", "Baud rate changed.");
    }

    public void onClickC5(View view) {
        firmwareUpdate();
    }

    public void onClickC6(View view) {
        byte[] data = hexStringToByteArray("80040017feff00e635");
        execCommand(data.toString(), "Booting firmware ...", "Firmware update complete!");
        Toast.makeText(this, "Firmware update complete!", Toast.LENGTH_SHORT).show();
    }

    public void onClickCReserve(View view) {
        firmwareUpdate();
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

    public void execCommand(final String command, String preMessage, final String postMessage) {
        Utils.appendLog("\n" + preMessage);
        Toast.makeText(this, preMessage, Toast.LENGTH_SHORT).show();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), postMessage, Toast.LENGTH_SHORT).show();
                Utils.appendLog("\n" + postMessage);
                //editText.setText(command);
                serialPort.write(command.concat("\n\r").getBytes());
            }
        }, timeout);
    }


    public void firmwareUpdate() {
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

                    byte[] message = new byte[1];
                    // header
                    message[0] += 12;
                    // length
                    message[0] += length & 0xFF;
                    message[0] += (length >> 8) & 0xFF;
                    // cmd
                    message[0] += 0x10;
                    // address
                    message[0] += writeAddress & 0xFF;
                    message[0] += (writeAddress >> 8) & 0xFF;
                    message[0] += (writeAddress >> 16) & 0xFF;
                    // data
                    message[0] += chunk[0];
                    // checksum
                    int crc = checksum(message);
                    message[0] += crc & 0xFF;
                    message[0] += (crc >> 8) & 0xFF;

                    Utils.appendLog("\n Message : " + message);

                    int tries = 0;
                    do {
                        if (++tries > maxTries) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainAct.this, "Response packet from BSL was not received with packet", Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainAct.this, "Response packet from BSL received", Toast.LENGTH_SHORT).show();
                            }
                        });

                        Utils.appendLog("\n write  : " + message);
                        serialPort.write(message.toString().concat("\n\r").getBytes());

                    } while (!terminator);

                    writeAddress += chunk.length;
                    currsize += chunk.length * 3 + chunk.length / 16;

                }
            }

            Utils.appendLog("\n write our reset vector");
            // write our reset vector
            byte[] message = hexStringToByteArray("80060010feff00");
            message[0] += reset_val_l;
            message[0] += reset_val_h;
            int crc = checksum(message);
            message[0] += crc & 0xFF;
            message[0] += (crc >> 8) & 0xFF;

            final String msg = message.toString();

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Utils.appendLog("\n Firmware download success");
                    Toast.makeText(getApplicationContext(), "Firmware download successful.", Toast.LENGTH_SHORT).show();
                    serialPort.write(msg.concat("\n\r").getBytes());

                }
            }, timeout);
        } catch (Exception e) {
            Utils.appendLog("\n Exception  ");
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

    public byte[] intToByteArray(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value};
    }

    private byte[] intToBytes(final int i) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(i);
        return bb.array();
    }

    private int hexToInt(String hex) {
        int value = Integer.parseInt(hex, 16);
        return value;
    }

    byte[] toPrimitives(byte[] oBytes) {
        byte[] bytes = new byte[oBytes.length];
        for (int i = 0; i < oBytes.length; i++) {
            bytes[i] = oBytes[i];
        }
        return bytes;

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
            Log.d("bk", "Write ");
        } catch (Exception e) {
            Log.e("bk", "File write failed: " + e.getMessage().toString());
        }
    }

    private void testByte() {
        byte[] a = new byte[1];
        a[0] += 0x80;
        a[0] += 2;
        for (int i = 0; i < a.length; i++) {
            Log.d("bk", "" + a[i]);
        }

    }
}
