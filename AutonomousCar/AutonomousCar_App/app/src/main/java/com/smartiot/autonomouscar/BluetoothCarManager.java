package com.smartiot.autonomouscar;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothCarManager {

    private static final String TAG = "BluetoothCarManager";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isConnected = false;
    private Thread readThread;

    public interface BluetoothCallback {
        void onConnected(String deviceName);
        void onConnectionFailed(String error);
        void onDisconnected();
        void onDataReceived(String telemetry);
    }

    private BluetoothCallback callback;

    public BluetoothCarManager() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void setCallback(BluetoothCallback callback) {
        this.callback = callback;
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(String macAddress) {
        new Thread(() -> {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothAdapter.cancelDiscovery();
                socket.connect();

                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
                isConnected = true;

                mainHandler.post(() -> {
                    if (callback != null) callback.onConnected(device.getName());
                });

                startListening();

            } catch (Exception e) {
                Log.e(TAG, "Lỗi kết nối Bluetooth: ", e);
                disconnect();
                mainHandler.post(() -> {
                    if (callback != null) callback.onConnectionFailed(e.getMessage());
                });
            }
        }).start();
    }

    private void startListening() {
        readThread = new Thread(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            while (isConnected) {
                try {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        mainHandler.post(() -> {
                            if (callback != null) callback.onDataReceived(line.trim());
                        });
                    }
                } catch (Exception e) {
                    break;
                }
            }
            disconnect();
        });
        readThread.start();
    }

    public void sendCommand(String cmd) {
        if (isConnected && outputStream != null) {
            try {
                outputStream.write((cmd + "\n").getBytes());
                outputStream.flush();
            } catch (Exception e) {
                Log.e(TAG, "Lỗi gửi lệnh Bluetooth: ", e);
            }
        }
    }

    public void disconnect() {
        isConnected = false;
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (socket != null) socket.close();
        } catch (Exception ignored) {}

        mainHandler.post(() -> {
            if (callback != null) callback.onDisconnected();
        });
    }

    public boolean isConnected() {
        return isConnected;
    }
}
