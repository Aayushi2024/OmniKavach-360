package com.example.rakshak360;

import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CyberScanActivity extends AppCompatActivity implements SensorEventListener, SurfaceHolder.Callback {

    private SurfaceView cameraPreview;
    private SurfaceHolder surfaceHolder;
    private Camera mCamera;
    private View glareFilter;

    private TextView tvTierMode, tvConsoleLogs, tvSpeedWarning, tvMasterWarning;
    private TextView tvEmf, tvAudio, tvBle, tvLan;
    private Button btnMasterScan, btnGlintTest;
    private Vibrator vibrator;

    private StringBuilder logs = new StringBuilder("<b>> Omni-Kavach Core Initialized.</b><br>");
    private long aiThrottleMs = 800;
    private int networkThreads = 5;

    private ImageLabeler imageLabeler;
    private boolean isProcessingFrame = false;
    private long lastFrameProcessTime = 0;
    private int previewWidth = 0, previewHeight = 0;

    private SensorManager sensorManager;
    private Sensor emfSensor, accelSensor;
    private float lastX, lastY, lastZ;
    private long lastAccelTime = 0;
    private boolean isMovingTooFast = false;
    private boolean isAlerting = false;

    private AudioRecord audioRecord;
    private boolean isAudioRecording = false;
    private Thread audioThread;

    private boolean isNetworkScanning = false;
    private ExecutorService networkExecutor;

    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private boolean isMdnsScanning = false;

    private BluetoothLeScanner bleScanner;
    private boolean isBleScanning = false;
    private int bleBugCount = 0;

    // 🟢 NEW: HashSet to store Unique MAC Addresses for BLE devices
    private HashSet<String> detectedBleDevices = new HashSet<>();

    private boolean isOmniScanActive = false;

    // GLINT TEST VARIABLES
    private boolean isStrobeActive = false;
    private boolean flashState = false;
    private Handler strobeHandler = new Handler(Looper.getMainLooper());

    private List<String> activeThreats = new ArrayList<>();
    private int threatIndex = 0;
    private Handler threatCycleHandler = new Handler(Looper.getMainLooper());
    private Runnable threatCycleRunnable;

    private Handler typeWriterHandler = new Handler(Looper.getMainLooper());
    private Runnable typeWriterRunnable;
    private String currentWarningText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cyber_scan);

        cameraPreview = findViewById(R.id.cameraPreview);
        glareFilter = findViewById(R.id.glareFilter);
        tvTierMode = findViewById(R.id.tvTierMode);
        tvConsoleLogs = findViewById(R.id.tvConsoleLogs);
        tvConsoleLogs.setMovementMethod(LinkMovementMethod.getInstance());

        tvSpeedWarning = findViewById(R.id.tvSpeedWarning);
        tvMasterWarning = findViewById(R.id.tvMasterWarning);

        tvEmf = findViewById(R.id.tvEmf);
        tvAudio = findViewById(R.id.tvAudio);
        tvBle = findViewById(R.id.tvBle);
        tvLan = findViewById(R.id.tvLan);
        btnMasterScan = findViewById(R.id.btnMasterScan);
        btnGlintTest = findViewById(R.id.btnGlintTest);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            emfSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        surfaceHolder = cameraPreview.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        optimizeForDeviceTier();

        btnMasterScan.setOnClickListener(v -> {
            if (!isOmniScanActive) startOmniScan(); else stopOmniScan();
        });

        btnGlintTest.setOnClickListener(v -> toggleGlintTest());

        tvMasterWarning.setOnClickListener(v -> {
            if (currentWarningText.contains("TAP TO VERIFY") || currentWarningText.contains("TAP TO VIEW")) {
                Matcher matcher = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").matcher(currentWarningText);
                if (matcher.find()) {
                    String extractedIp = matcher.group();
                    try {
                        Intent browserIntent;
                        if(currentWarningText.contains("WEBCAM")) {
                            browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + extractedIp + ":8080"));
                        } else {
                            browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + extractedIp));
                        }
                        startActivity(browserIntent);
                    } catch (Exception e) {}
                }
            }
        });
    }

    private void toggleGlintTest() {
        if (mCamera == null) return;
        isStrobeActive = !isStrobeActive;

        if (isStrobeActive) {
            btnGlintTest.setText("⏹ STOP GLINT TEST");
            btnGlintTest.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF003C")));
            glareFilter.setBackgroundColor(Color.parseColor("#66FF0000"));

            appendLogHtml("<font color='#FF003C'>> 🔦 INSTRUCTION: कमरे की लाइट बंद करो। स्क्रीन पर देखो, अगर दीवार में कोई सफ़ेद/लाल डॉट चमकता दिखे, तो वह 100% कैमरा लेंस है!</font>");
            showWarning("🔦 LOOK FOR GLOWING DOTS ON WALLS!", "#FF003C", "#FF003C");

            strobeHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!isStrobeActive) return;
                    flashState = !flashState;
                    setFlash(flashState);
                    strobeHandler.postDelayed(this, 150);
                }
            });
        } else {
            btnGlintTest.setText("👁️ ACTIVATE GLINT TEST (STROBE)");
            btnGlintTest.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#8A2BE2")));
            glareFilter.setBackgroundColor(Color.parseColor("#2600FF00"));

            strobeHandler.removeCallbacksAndMessages(null);
            setFlash(isOmniScanActive);
            showWarning("[ SYSTEM SECURE: NO THREATS ]", "#00F0FF", "#00F0FF");
        }
    }

    private void showWarning(String message, String textColorHex, String shadowColorHex) {
        if (message.equals(currentWarningText)) return;
        currentWarningText = message;

        runOnUiThread(() -> {
            tvMasterWarning.setTextColor(Color.parseColor(textColorHex));
            tvMasterWarning.setShadowLayer(15, 0, 0, Color.parseColor(shadowColorHex));
            tvMasterWarning.setText("");

            if (typeWriterRunnable != null) typeWriterHandler.removeCallbacks(typeWriterRunnable);
            typeWriterRunnable = new Runnable() {
                int index = 0;
                @Override
                public void run() {
                    if (index < message.length()) {
                        tvMasterWarning.append(String.valueOf(message.charAt(index)));
                        index++;
                        typeWriterHandler.postDelayed(this, 25);
                    }
                }
            };
            typeWriterHandler.post(typeWriterRunnable);
        });
    }

    private void updateThreatLevel(String threat, boolean isDetected) {
        runOnUiThread(() -> {
            if (isDetected) {
                if (!activeThreats.contains(threat)) activeThreats.add(threat);
            } else {
                activeThreats.remove(threat);
            }

            if (activeThreats.isEmpty() && !isStrobeActive) {
                stopThreatCycling();
                showWarning("[ SYSTEM SECURE: NO THREATS ]", "#00F0FF", "#00F0FF");
            } else if (!isStrobeActive) {
                startThreatCycling();
            }
        });
    }

    private void startThreatCycling() {
        if (threatCycleRunnable != null) return;
        threatCycleRunnable = new Runnable() {
            @Override
            public void run() {
                if (!activeThreats.isEmpty() && !isStrobeActive) {
                    String msg = activeThreats.get(threatIndex % activeThreats.size());
                    String color = msg.contains("DEVICE") ? "#00F0FF" : (msg.contains("DISTANT") ? "#FF9800" : "#FF003C");
                    showWarning(msg, color, color);
                    threatIndex++;
                    threatCycleHandler.postDelayed(this, 3000);
                } else {
                    threatCycleRunnable = null;
                }
            }
        };
        threatCycleHandler.post(threatCycleRunnable);
    }

    private void stopThreatCycling() {
        threatCycleHandler.removeCallbacks(threatCycleRunnable);
        threatCycleRunnable = null;
        threatIndex = 0;
    }

    private void optimizeForDeviceTier() {
        ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        long totalRamGb = memInfo.totalMem / (1024 * 1024 * 1024);
        if (totalRamGb >= 6) { aiThrottleMs = 300; networkThreads = 15; }
        else { aiThrottleMs = 800; networkThreads = 5; }
        networkExecutor = Executors.newFixedThreadPool(networkThreads);
    }

    private void startOmniScan() {
        activeThreats.clear();
        threatIndex = 0;
        bleBugCount = 0;
        detectedBleDevices.clear(); // 🟢 Naya Data har baar clean karenge

        tvBle.setText("0 DETECTED");
        tvLan.setText("0/255");
        tvEmf.setText("0.0 µT");
        tvAudio.setText("0 Hz");
        currentWarningText = "";

        isOmniScanActive = true;
        btnMasterScan.setText("⏹ STOP OMNI-SCAN");
        btnMasterScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF2A2A")));
        appendLogHtml("<b><font color='#00FF88'>> 🚀 INITIATING DEEP OMNI-SCAN...</font></b>");

        if(!isStrobeActive) showWarning("[ SCANNING ENVIRONMENT... ]", "#FF9800", "#FF9800");

        startAudioRadar();
        startNetworkScan();
        startMdnsSniffer();
        startBleScan();
        if(!isStrobeActive) setFlash(true);
    }

    private void stopOmniScan() {
        isOmniScanActive = false;
        btnMasterScan.setText("INITIATE FULL OMNI-SCAN");
        btnMasterScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF88")));

        stopAudioRadar();
        isNetworkScanning = false;
        stopMdnsSniffer();
        stopBleScan();
        if(!isStrobeActive) setFlash(false);

        activeThreats.clear();
        stopThreatCycling();
        if(!isStrobeActive) showWarning("[ SYSTEM SECURE: NO THREATS ]", "#00F0FF", "#00F0FF");
        appendLogHtml("<font color='#A0A0B0'>> 🛑 SCAN ABORTED. Radars offline.</font>");
    }

    private void processImageForAI(byte[] data) {
        if (!isOmniScanActive || isProcessingFrame) return;
        try {
            InputImage image = InputImage.fromByteArray(data, previewWidth, previewHeight, 90, InputImage.IMAGE_FORMAT_NV21);
            imageLabeler.process(image).addOnSuccessListener(labels -> {
                boolean found = false;
                for (ImageLabel label : labels) {
                    String t = label.getText().toLowerCase();
                    if(label.getConfidence() > 0.65f && (t.contains("camera") || t.contains("lens") || t.contains("webcam"))) {
                        found = true; break;
                    }
                }
                updateThreatLevel("⚠️ VISUAL: CAMERA LENS DETECTED!", found);
                isProcessingFrame = false;
            }).addOnFailureListener(e -> isProcessingFrame = false);
        } catch (Exception e) { isProcessingFrame = false; }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD && isOmniScanActive) {
            double emf = Math.sqrt(Math.pow(event.values[0], 2) + Math.pow(event.values[1], 2) + Math.pow(event.values[2], 2));
            tvEmf.setText(String.format(Locale.US, "%.1f µT", emf));
            updateThreatLevel("🚨 EMF: HIDDEN HARDWARE NEARBY!", emf > 80.0);
            if(emf > 80.0 && !isAlerting) { vibrator.vibrate(100); isAlerting = true; } else if(emf <= 80.0) isAlerting = false;
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && isOmniScanActive) {
            float speed = Math.abs(event.values[0] + event.values[1] + event.values[2] - lastX - lastY - lastZ);
            tvSpeedWarning.setVisibility(speed > 15 ? View.VISIBLE : View.GONE);
            lastX = event.values[0]; lastY = event.values[1]; lastZ = event.values[2];
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void startAudioRadar() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        int rate = 44100;
        int bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        isAudioRecording = true; audioRecord.startRecording();
        audioThread = new Thread(() -> {
            short[] buffer = new short[bufferSize];
            while (isAudioRecording) {
                audioRecord.read(buffer, 0, buffer.length);
                int crossings = 0;
                for (int i = 1; i < buffer.length; i++) if ((buffer[i-1] < 0 && buffer[i] >= 0)) crossings++;
                double freq = crossings * (rate / (double) buffer.length);
                runOnUiThread(() -> {
                    tvAudio.setText(String.format(Locale.US, "%.0f Hz", freq));
                    updateThreatLevel("🎙️ AUDIO: ULTRASONIC FREQUENCY DETECTED!", freq > 16000 && freq < 22000);
                });
                try { Thread.sleep(300); } catch (Exception e) {}
            }
        });
        audioThread.start();
    }
    private void stopAudioRadar() { isAudioRecording = false; if(audioThread != null) audioThread.interrupt(); }

    private void startMdnsSniffer() {
        if (nsdManager == null) return;
        isMdnsScanning = true;
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String regType) {}
            @Override public void onServiceFound(NsdServiceInfo service) {
                if (!isOmniScanActive) return;
                String name = service.getServiceName().toLowerCase();
                if (name.contains("cam") || name.contains("ip") || name.contains("tapo") || name.contains("rtsp")) {
                    runOnUiThread(() -> {
                        appendLogHtml("<font color='#FF003C'>> 🚨 SMART CAM BROADCAST: " + service.getServiceName() + "</font>");
                        updateThreatLevel("📡 SMART CAMERA BROADCAST FOUND!", true);
                        if (vibrator != null) vibrator.vibrate(500);
                    });
                }
            }
            @Override public void onServiceLost(NsdServiceInfo service) {}
            @Override public void onDiscoveryStopped(String serviceType) {}
            @Override public void onStartDiscoveryFailed(String sType, int err) { nsdManager.stopServiceDiscovery(this); }
            @Override public void onStopDiscoveryFailed(String sType, int err) { nsdManager.stopServiceDiscovery(this); }
        };
        try { nsdManager.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener); } catch (Exception e){}
    }
    private void stopMdnsSniffer() {
        if (isMdnsScanning && nsdManager != null && discoveryListener != null) {
            try { nsdManager.stopServiceDiscovery(discoveryListener); } catch (Exception e){}
            isMdnsScanning = false;
        }
    }

    private void startNetworkScan() {
        isNetworkScanning = true; tvLan.setText("0/255"); tvLan.setTextColor(Color.parseColor("#FF9800"));
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null || !wm.isWifiEnabled()) return;
        int ipAdd = wm.getConnectionInfo().getIpAddress();
        if (ipAdd == 0) return;

        int gateway = wm.getDhcpInfo().gateway;
        final String routerIp = String.format(Locale.US, "%d.%d.%d.%d", (gateway & 0xff), (gateway >> 8 & 0xff), (gateway >> 16 & 0xff), (gateway >> 24 & 0xff));

        final String subnet = String.format(Locale.US, "%d.%d.%d.", (ipAdd & 0xff), (ipAdd >> 8 & 0xff), (ipAdd >> 16 & 0xff));

        for (int i = 1; i <= 254; i++) {
            final int cur = i;
            networkExecutor.execute(() -> {
                if (!isNetworkScanning) return;
                try {
                    String targetIp = subnet + cur;
                    if (targetIp.equals(routerIp)) {
                        runOnUiThread(() -> tvLan.setText(cur + "/255"));
                        return;
                    }

                    long startTime = System.currentTimeMillis();
                    if (java.net.InetAddress.getByName(targetIp).isReachable(300)) {
                        long latency = System.currentTimeMillis() - startTime;
                        String distTag = (latency < 50) ? "[< 5m]" : (latency < 100) ? "[~10m]" : "[~20m+]";

                        if (checkPort(targetIp, 554)) {
                            runOnUiThread(() -> {
                                tvLan.setTextColor(Color.parseColor("#FF2A2A"));
                                appendLogHtml("<font color='#FF2A2A'>> 🚨 CCTV 554: " + targetIp + " " + distTag + "</font>");
                                updateThreatLevel("🌐 CCTV: " + targetIp + " (TAP TO VIEW)", true);
                                if(latency < 100 && vibrator != null) vibrator.vibrate(200);
                            });
                        }
                        else {
                            int openPort = -1;
                            if (checkPort(targetIp, 8080)) openPort = 8080;
                            else if (checkPort(targetIp, 80)) openPort = 80;

                            if (openPort != -1) {
                                boolean isCamera = inspectHttpDevice(targetIp, openPort);
                                final int finalOpenPort = openPort;
                                runOnUiThread(() -> {
                                    if (isCamera) {
                                        tvLan.setTextColor(Color.parseColor("#FF2A2A"));
                                        appendLogHtml("<font color='#FF2A2A'>> 🚨 IP WEBCAM: " + targetIp + ":" + finalOpenPort + "</font>");
                                        updateThreatLevel("🌐 IP WEBCAM: " + targetIp + " (TAP TO VIEW)", true);
                                        if (vibrator != null) vibrator.vibrate(200);
                                    } else {
                                        appendLogHtml("<font color='#00F0FF'>> ℹ️ Device HTTP: " + targetIp + ":" + finalOpenPort + "</font>");
                                        updateThreatLevel("🌐 DEVICE: " + targetIp + " (TAP TO VERIFY)", true);
                                    }
                                });
                            }
                        }
                    }
                    runOnUiThread(() -> tvLan.setText(cur + "/255"));
                } catch (Exception e) {}
            });
        }
    }

    private boolean inspectHttpDevice(String ip, int port) {
        try {
            URL url = new URL("http://" + ip + ":" + port);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(400);
            conn.setReadTimeout(400);
            conn.setRequestMethod("GET");

            String serverHeader = conn.getHeaderField("Server");
            if (serverHeader != null) {
                serverHeader = serverHeader.toLowerCase();
                if (serverHeader.contains("camera") || serverHeader.contains("webcam") || serverHeader.contains("yawcam") || serverHeader.contains("hikvision") || serverHeader.contains("dahua")) {
                    return true;
                }
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            int linesRead = 0;
            while ((inputLine = in.readLine()) != null && linesRead < 10) {
                content.append(inputLine).append(" ");
                linesRead++;
            }
            in.close();

            String html = content.toString().toLowerCase();
            if (html.contains("ip webcam") || html.contains("video stream") || html.contains("mjpeg") || html.contains("camera login")) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkPort(String ip, int port) {
        try { java.net.Socket s = new java.net.Socket(); s.connect(new java.net.InetSocketAddress(ip, port), 100); s.close(); return true; }
        catch (Exception e) { return false; }
    }

    // 🟢 FIXED: MAC Address Unique Filtering
    // 🟢 FIXED: MAC Address Unique Filtering + SMART DEVICE FILTERING (HACKER LEVEL)
    private void startBleScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (bleScanner != null) {
            bleBugCount = 0;
            isBleScanning = true;
            detectedBleDevices.clear(); // Naya Scan, Nayi Shuruat

            android.bluetooth.le.ScanSettings settings = new android.bluetooth.le.ScanSettings.Builder()
                    .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            try {
                bleScanner.startScan(null, settings, new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        if (!isOmniScanActive) return;
                        int rssi = result.getRssi();
                        if (rssi > -90) {
                            // Unique MAC Address Check
                            String macAddress = result.getDevice().getAddress();

                            if (!detectedBleDevices.contains(macAddress)) {
                                detectedBleDevices.add(macAddress);

                                // 🟢 SMART FILTER LOGIC (Whitelist for harmless devices)
                                String deviceName = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : null;
                                boolean isHarmless = false;

                                if (deviceName != null) {
                                    String lowerName = deviceName.toLowerCase();
                                    // Agar naam mein ye words hain, toh wo camera/tracker nahi hai
                                    String[] safeWords = {"bud", "airpod", "watch", "band", "tv", "speaker", "audio", "headset", "ear", "bose", "sony", "samsung", "oneplus", "macbook", "iphone"};
                                    for (String word : safeWords) {
                                        if (lowerName.contains(word)) {
                                            isHarmless = true;
                                            break;
                                        }
                                    }
                                }

                                if (isHarmless) {
                                    // 🟢 Safe Device: Ignore in Bug Count, just print to console
                                    runOnUiThread(() -> {
                                        appendLogHtml("<font color='#A0A0B0'>> ℹ️ IGNORING SAFE BLUETOOTH: " + deviceName + "</font>");
                                    });
                                } else {
                                    // 🔴 THIS IS A SUSPICIOUS DEVICE (Hidden Tracker / Cam / Unknown)
                                    bleBugCount++;
                                    String distTag = (rssi > -60) ? "[< 2m]" : "[~5m+]";
                                    String threatName = (deviceName != null) ? deviceName : "HIDDEN TRACKER"; // Trackers often hide their names

                                    runOnUiThread(() -> {
                                        tvBle.setText(bleBugCount + " BUGS");
                                        tvBle.setTextColor(Color.parseColor("#FF2A2A"));
                                        updateThreatLevel("📡 BLE BUG: " + threatName + " " + distTag, true);

                                        // Hacker Print Statement
                                        appendLogHtml("<font color='#FF2A2A'>> 🚨 SUSPICIOUS BLE: " + macAddress + " (" + threatName + ") " + distTag + "</font>");
                                    });
                                }
                            }
                        }
                    }
                });
            } catch (SecurityException e) {
                // Ignore silent missing permissions
            }
        }
    }
    private void stopBleScan() {
        if (bleScanner != null && isBleScanning) {
            try {
                bleScanner.stopScan(new ScanCallback() {});
            } catch (SecurityException e) {}
            isBleScanning = false;
        }
        tvBle.setText("0 DETECTED"); tvBle.setTextColor(Color.parseColor("#00BFFF"));
    }

    private void setFlash(boolean enable) {
        if(mCamera != null) { try { Camera.Parameters p = mCamera.getParameters(); p.setFlashMode(enable ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF); mCamera.setParameters(p); } catch (Exception e){} }
    }

    private void startCameraAndFlash() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        if (mCamera == null) {
            try {
                mCamera = Camera.open();
                Camera.Parameters params = mCamera.getParameters();
                params.setPreviewFormat(ImageFormat.NV21);
                if (params.getSupportedFocusModes() != null && params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                }
                mCamera.setParameters(params);
                mCamera.setPreviewDisplay(surfaceHolder);
                previewWidth = params.getPreviewSize().width; previewHeight = params.getPreviewSize().height;

                mCamera.setPreviewCallback((data, camera) -> {
                    long currentTime = System.currentTimeMillis();
                    if (isOmniScanActive && !isMovingTooFast && !isProcessingFrame && (currentTime - lastFrameProcessTime > aiThrottleMs)) {
                        isProcessingFrame = true; lastFrameProcessTime = currentTime; processImageForAI(data);
                    }
                });
                mCamera.startPreview();
            } catch (Exception e) {}
        }
    }

    private void appendLogHtml(String message) {
        runOnUiThread(() -> {
            logs.append(message).append("<br>");
            if(logs.length() > 1500) logs.delete(0, 500);
            tvConsoleLogs.setText(Html.fromHtml(logs.toString(), Html.FROM_HTML_MODE_COMPACT));
            int scroll = tvConsoleLogs.getLayout() != null ? tvConsoleLogs.getLayout().getLineTop(tvConsoleLogs.getLineCount()) - tvConsoleLogs.getHeight() : 0;
            if (scroll > 0) tvConsoleLogs.scrollTo(0, scroll);
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            if (emfSensor != null) sensorManager.registerListener(this, emfSensor, SensorManager.SENSOR_DELAY_NORMAL);
            if (accelSensor != null) sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (surfaceHolder.getSurface().isValid()) startCameraAndFlash();
    }

    @Override protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        stopOmniScan();
        strobeHandler.removeCallbacksAndMessages(null); // Stop strobe if active
        if (mCamera != null) { mCamera.setPreviewCallback(null); mCamera.stopPreview(); mCamera.release(); mCamera = null; }
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { startCameraAndFlash(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {}
}