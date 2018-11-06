package com.example.biswajit.androidusb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class SerialCommReceiver extends BroadcastReceiver {
    public final String ACTION_USB_PERMISSION = "com.example.biswajit.arduinousb.USB_PERMISSION";

    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;

    Context context;

    public SerialCommReceiver(){
    }

    public SerialCommReceiver(Context ctx){
        context = ctx;
        usbManager = (UsbManager) context.getSystemService(context.USB_SERVICE);


    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
            boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
            if (granted) {
                connection = usbManager.openDevice(device);
                serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                if (serialPort != null) {
                    if (serialPort.open()) { //Set Serial Connection Parameters.
                        serialPort.setBaudRate(9600);
                        serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                        serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                        serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                        serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                        serialPort.read(mCallback);
                        Toast.makeText(context, "Serial Connection Opened", Toast.LENGTH_SHORT).show();

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
            onClickStart();
        } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
            onClickStop();

        }
    }

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = null;
            try {
                data = new String(arg0, "UTF-8");
                data.concat("/n");
                Toast.makeText(context, data, Toast.LENGTH_SHORT).show();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }


        }
    };

    public void onClickStart() {

        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (deviceVID == 1027)
                {
                    PendingIntent pi = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
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

    public void onClickSend(String command) {
        serialPort.write(command.getBytes());
        Toast.makeText(context, "Data sent " + command, Toast.LENGTH_SHORT).show();

    }

    public void onClickStop() {
        serialPort.close();
    }

}
