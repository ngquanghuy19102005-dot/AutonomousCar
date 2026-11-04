package com.smartiot.autonomouscar;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_CODE_SPEECH_INPUT = 100;

    private BluetoothCarManager btManager;
    private AiCarCopilotHelper copilotHelper;

    // UI
    private EditText etMacAddress;
    private Button btnConnectBt, btnDisconnectBt;
    private TextView tvBtStatus;

    // Radar & Telemetry
    private TextView tvDistFront, tvDistLeft, tvDistRight;
    private ProgressBar pbDistFront, pbDistLeft, pbDistRight;
    private TextView tvCurrentMode, tvSpeedDisplay;
    private SeekBar seekSpeed;

    // Mode
    private Switch switchAutoAvoidance;

    // D-Pad
    private ImageButton btnForward, btnBackward, btnLeft, btnRight, btnStop;

    // Voice & AI
    private Button btnVoiceControl, btnAskCopilot;
    private TextView tvCopilotAdvice;

    // Telemetry values
    private int distF = 250, distL = 250, distR = 250;
    private int carSpeed = 220;
    private String carMode = "MANUAL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btManager = new BluetoothCarManager();
        copilotHelper = new AiCarCopilotHelper();

        initViews();
        setupEvents();
        setupBluetoothCallback();
        setupCopilotCallback();
    }

    private void initViews() {
        etMacAddress = findViewById(R.id.etMacAddress);
        btnConnectBt = findViewById(R.id.btnConnectBt);
        btnDisconnectBt = findViewById(R.id.btnDisconnectBt);
        tvBtStatus = findViewById(R.id.tvBtStatus);

        tvDistFront = findViewById(R.id.tvDistFront);
        tvDistLeft = findViewById(R.id.tvDistLeft);
        tvDistRight = findViewById(R.id.tvDistRight);

        pbDistFront = findViewById(R.id.pbDistFront);
        pbDistLeft = findViewById(R.id.pbDistLeft);
        pbDistRight = findViewById(R.id.pbDistRight);

        tvCurrentMode = findViewById(R.id.tvCurrentMode);
        tvSpeedDisplay = findViewById(R.id.tvSpeedDisplay);
        seekSpeed = findViewById(R.id.seekSpeed);

        switchAutoAvoidance = findViewById(R.id.switchAutoAvoidance);

        btnForward = findViewById(R.id.btnForward);
        btnBackward = findViewById(R.id.btnBackward);
        btnLeft = findViewById(R.id.btnLeft);
        btnRight = findViewById(R.id.btnRight);
        btnStop = findViewById(R.id.btnStop);

        btnVoiceControl = findViewById(R.id.btnVoiceControl);
        btnAskCopilot = findViewById(R.id.btnAskCopilot);
        tvCopilotAdvice = findViewById(R.id.tvCopilotAdvice);

        seekSpeed.setMax(255);
        seekSpeed.setProgress(carSpeed);
        tvSpeedDisplay.setText("Tốc độ PWM: " + carSpeed + "/255");
    }

    private void setupEvents() {
        btnConnectBt.setOnClickListener(v -> {
            String mac = etMacAddress.getText().toString().trim();
            if (!mac.isEmpty()) {
                tvBtStatus.setText("Đang kết nối tới " + mac + "...");
                btManager.connectToDevice(mac);
            } else {
                Toast.makeText(this, "Vui lòng nhập địa chỉ MAC Bluetooth!", Toast.LENGTH_SHORT).show();
            }
        });

        btnDisconnectBt.setOnClickListener(v -> btManager.disconnect());

        switchAutoAvoidance.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                btManager.sendCommand("A");
                tvCurrentMode.setText("TỰ HÀNH TRÁNH VẬT CẢN (AUTO)");
                tvCurrentMode.setTextColor(0xFF00C853);
            } else {
                btManager.sendCommand("M");
                tvCurrentMode.setText("ĐIỀU KHIỂN THỦ CÔNG (BLUETOOTH)");
                tvCurrentMode.setTextColor(0xFF00B4D8);
            }
        });

        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 80) progress = 80;
                carSpeed = progress;
                tvSpeedDisplay.setText("Tốc độ PWM: " + carSpeed + "/255");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                btManager.sendCommand("V" + carSpeed);
            }
        });

        setupTouchButton(btnForward, "F");
        setupTouchButton(btnBackward, "B");
        setupTouchButton(btnLeft, "L");
        setupTouchButton(btnRight, "R");

        btnStop.setOnClickListener(v -> btManager.sendCommand("S"));

        btnVoiceControl.setOnClickListener(v -> startVoiceRecognition());

        btnAskCopilot.setOnClickListener(v -> {
            tvCopilotAdvice.setText("🤖 Copilot đang phân tích không gian xung quanh...");
            tvCopilotAdvice.setVisibility(android.view.View.VISIBLE);
            copilotHelper.askCopilot(distF, distL, distR, carSpeed, carMode, "Tình trạng đường đi hiện tại thế nào?");
        });
    }

    private void setupTouchButton(ImageButton btn, String cmd) {
        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    btManager.sendCommand(cmd);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    btManager.sendCommand("S");
                    return true;
            }
            return false;
        });
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói khẩu lệnh: Tiến, Lùi, Trái, Phải, Dừng lại...");
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            Toast.makeText(this, "Thiết bị không hỗ trợ nhận diện giọng nói", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                handleVoiceCommand(result.get(0).toLowerCase());
            }
        }
    }

    private void handleVoiceCommand(String command) {
        Toast.makeText(this, "Khẩu lệnh: " + command, Toast.LENGTH_SHORT).show();
        if (command.contains("tiến") || command.contains("tới") || command.contains("đi thẳng")) {
            btManager.sendCommand("F");
        } else if (command.contains("lùi") || command.contains("de")) {
            btManager.sendCommand("B");
        } else if (command.contains("trái")) {
            btManager.sendCommand("L");
        } else if (command.contains("phải")) {
            btManager.sendCommand("R");
        } else if (command.contains("dừng") || command.contains("thắng") || command.contains("stop")) {
            btManager.sendCommand("S");
        } else if (command.contains("tự hành") || command.contains("tự lái")) {
            switchAutoAvoidance.setChecked(true);
        }
    }

    private void setupBluetoothCallback() {
        btManager.setCallback(new BluetoothCarManager.BluetoothCallback() {
            @Override
            public void onConnected(String deviceName) {
                tvBtStatus.setText("● Đã kết nối: " + deviceName);
                tvBtStatus.setTextColor(0xFF00E676);
            }

            @Override
            public void onConnectionFailed(String error) {
                tvBtStatus.setText("● Lỗi kết nối: " + error);
                tvBtStatus.setTextColor(0xFFFF5252);
            }

            @Override
            public void onDisconnected() {
                tvBtStatus.setText("● Đã ngắt kết nối Bluetooth");
                tvBtStatus.setTextColor(0xFF778DA9);
            }

            @Override
            public void onDataReceived(String telemetry) {
                parseTelemetry(telemetry);
            }
        });
    }

    private void parseTelemetry(String raw) {
        if (raw.startsWith("D:")) {
            try {
                String body = raw.substring(2);
                String[] parts = body.split(",");
                if (parts.length >= 3) {
                    distF = Integer.parseInt(parts[0].trim());
                    distL = Integer.parseInt(parts[1].trim());
                    distR = Integer.parseInt(parts[2].trim());

                    if (parts.length >= 5) {
                        carMode = parts[4].trim();
                        tvCurrentMode.setText(carMode.equalsIgnoreCase("AUTO") ? "TỰ HÀNH TRÁNH VẬT CẢN (AUTO)" : "ĐIỀU KHIỂN THỦ CÔNG (BLUETOOTH)");
                    }

                    tvDistFront.setText(distF >= 200 ? "> 200 cm" : distF + " cm");
                    tvDistLeft.setText(distL >= 200 ? "> 200 cm" : distL + " cm");
                    tvDistRight.setText(distR >= 200 ? "> 200 cm" : distR + " cm");

                    pbDistFront.setProgress(Math.min(distF, 200));
                    pbDistLeft.setProgress(Math.min(distL, 200));
                    pbDistRight.setProgress(Math.min(distR, 200));

                    tvDistFront.setTextColor(distF < 35 ? 0xFFFF1744 : 0xFFFFFFFF);
                    tvDistLeft.setTextColor(distL < 35 ? 0xFFFF1744 : 0xFFFFFFFF);
                    tvDistRight.setTextColor(distR < 35 ? 0xFFFF1744 : 0xFFFFFFFF);
                }
            } catch (Exception ignored) {}
        }
    }

    private void setupCopilotCallback() {
        copilotHelper.setCallback(new AiCarCopilotHelper.CopilotCallback() {
            @Override
            public void onResponse(String advice) {
                tvCopilotAdvice.setText("🤖 Copilot: " + advice);
                tvCopilotAdvice.setVisibility(android.view.View.VISIBLE);
            }

            @Override
            public void onError(String err) {
                tvCopilotAdvice.setText("Copilot: " + err);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btManager != null) btManager.disconnect();
    }
}
