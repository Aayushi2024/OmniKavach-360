package com.example.rakshak360;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import ai.picovoice.porcupine.PorcupineManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
//yha m telegram code lga skta hu baad m
private final String TELEGRAM_TOKEN = "YOUR_TOKEN_HERE";
private final String CHAT_ID = "YOUR_CHAT_ID";
    private static final OkHttpClient httpClient = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build();
    private static WebSocket webSocket;
    private static boolean isCallReceiverRegistered = false;
    private static final String SERVER_URL = "ws://unfretfully-multirole-halle.ngrok-free.dev/mobile-alert";

    private FusedLocationProviderClient fusedLocationClient;

    private androidx.appcompat.widget.SwitchCompat switchAegis, switchAI, switchNetwork, switchSelfie, btnToggleShake, switchChronos;
    private TextView tvActiveModuleCount;

    private boolean isDarkMode = false;
    private TextView btnThemeToggle;
    private RelativeLayout mainRoot;
    private LinearLayout bottomNav;

    private LinearLayout lockScreenOverlay, btnFingerprint;
    private RelativeLayout fakePowerScreen, scamAlertScreen;
    private EditText pinInput;
    private Button btnSubmitPin, btnStopAlert;
    private TextView tvAlertTitle, tvAlertDesc;

    private TextView tvFaceIcon, tvFaceText;

    private ScrollView viewDashboard, viewModules;
    private RelativeLayout viewSos;
    private LinearLayout navSystem, navModules, navSos;
    private TextView txtSystem, txtModules, txtSos, icSystem, icModules, icSos, tvShakeStatus;

    // 🟢 CHRONOS PROTOCOL VARIABLES
    private TextView tvSubChronos, tvReady, tvSubStat2;
    private CardView cardStat2;
    private CountDownTimer chronosTimer;
    private boolean isChronosActive = false;
    private long chronosTimeMillis = 0;

    private boolean isShakeEnabled = false;
    private Handler sosHoldHandler = new Handler(Looper.getMainLooper());
    private boolean isSosTriggered = false;
    private SharedPreferences prefs;
    private boolean isUnlocked = false;
    private int wrongPinCount = 0;

    private boolean isCameraInUse = false;
    private boolean hasPromptedFaceScan = false;
    private boolean isBiometricVerified = false;
    private ObjectAnimator faceBlinkAnimator;

    private LocationManager locationManager;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String audioFilePath;

    private Vibrator scamVibrator;
    private Ringtone scamRingtone;
    private SensorManager sensorManager;
    private float accelVal, accelLast, shake;
    private Camera mCamera;
    private SurfaceHolder surfaceHolder;
    private SurfaceView hiddenCameraView;

    private ActivityResultLauncher<Intent> contactPickerLauncher;
    private PorcupineManager porcupineManager;
    private int powerBtnCount = 0;
    private long lastPowerBtnTime = 0;
    private long lastSosTime = 0;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private static final BroadcastReceiver callReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                if (webSocket == null) {
                    Request request = new Request.Builder().url(SERVER_URL).build();
                    webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
                        @Override public void onMessage(WebSocket webSocket, String text) {
                            if (text.contains("SCAM_ALERT")) {
                                Intent bringIntent = new Intent(context, MainActivity.class);
                                bringIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                bringIntent.putExtra("TRIGGER_SCAM_ALERT", true);
                                context.startActivity(bringIntent);
                            }
                        }
                    });
                }
            } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                if (webSocket != null) { webSocket.close(1000, "Call Ended"); webSocket = null; }
            }
        }
    };

    private final BroadcastReceiver hardwareReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                isUnlocked = false;
                hasPromptedFaceScan = false;
                isBiometricVerified = false;
                runOnUiThread(() -> {
                    if (lockScreenOverlay != null) { lockScreenOverlay.setVisibility(View.VISIBLE); lockScreenOverlay.setAlpha(1f); pinApp(); }
                });
            }
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (!isUnlocked) {
                    Intent bringToFront = new Intent(context, MainActivity.class);
                    bringToFront.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    context.startActivity(bringToFront);
                }
            }
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                if (switchAegis != null && switchAegis.isChecked()) {
                    Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    if (batteryIntent != null) {
                        int chargePlug = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1);
                        if (chargePlug == android.os.BatteryManager.BATTERY_PLUGGED_USB) triggerAegisPortAlert();
                    }
                }
            }
            if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_SCREEN_OFF.equals(action)) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastPowerBtnTime < 1000) powerBtnCount++; else powerBtnCount = 1;
                lastPowerBtnTime = currentTime;
                if (powerBtnCount >= 3) { triggerStealthSOS("Power Button Stealth Trigger"); powerBtnCount = 0; }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestInitialPermissions();
        checkAdvancedPermissions();
        cleanupOldIntruderPhotos();

        try {
            Intent serviceIntent = new Intent(this, LockService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
            else startService(serviceIntent);
        } catch (Exception e) {}

        if (!isCallReceiverRegistered) {
            IntentFilter callFilter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getApplicationContext().registerReceiver(callReceiver, callFilter, Context.RECEIVER_NOT_EXPORTED);
            else getApplicationContext().registerReceiver(callReceiver, callFilter);
            isCallReceiverRegistered = true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else { getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON); }

        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("RakshakPrefs", MODE_PRIVATE);

        mainRoot = findViewById(R.id.rootLayout);
        bottomNav = findViewById(R.id.bottomNav);
        lockScreenOverlay = findViewById(R.id.lockScreenOverlay);
        fakePowerScreen = findViewById(R.id.fakePowerScreen);
        scamAlertScreen = findViewById(R.id.scamAlertScreen);
        pinInput = findViewById(R.id.pinInput);
        btnSubmitPin = findViewById(R.id.btnSubmitPin);
        btnFingerprint = findViewById(R.id.btnFingerprint);
        btnStopAlert = findViewById(R.id.btnStopAlert);

        tvAlertTitle = findViewById(R.id.tvAlertTitle);
        tvAlertDesc = findViewById(R.id.tvAlertDesc);
        tvFaceIcon = findViewById(R.id.tvFaceIcon);
        tvFaceText = findViewById(R.id.tvFaceText);

        hiddenCameraView = findViewById(R.id.hiddenCameraView);
        viewDashboard = findViewById(R.id.view_dashboard);
        viewModules = findViewById(R.id.view_modules);
        viewSos = findViewById(R.id.view_sos);

        navSystem = findViewById(R.id.nav_system); navModules = findViewById(R.id.nav_modules); navSos = findViewById(R.id.nav_sos);
        txtSystem = findViewById(R.id.txt_system); txtModules = findViewById(R.id.txt_modules); txtSos = findViewById(R.id.txt_sos);
        icSystem = findViewById(R.id.ic_system); icModules = findViewById(R.id.ic_modules); icSos = findViewById(R.id.ic_sos);

        tvReady = findViewById(R.id.tvReady);
        tvSubStat2 = findViewById(R.id.tvSubStat2);
        cardStat2 = findViewById(R.id.cardStat2);
        switchChronos = findViewById(R.id.switchChronos);
        tvSubChronos = findViewById(R.id.tvSubChronos);

        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        if(btnThemeToggle != null) {
            btnThemeToggle.setOnClickListener(v -> {
                isDarkMode = !isDarkMode;
                btnThemeToggle.setText(isDarkMode ? "☀️" : "🌙");
                applyThemeUI();
            });
        }

        switchAegis = findViewById(R.id.switchAegis);
        switchAI = findViewById(R.id.switchAI);
        switchNetwork = findViewById(R.id.switchNetwork);
        switchSelfie = findViewById(R.id.switchSelfie);
        btnToggleShake = findViewById(R.id.btnToggleShake);
        tvActiveModuleCount = findViewById(R.id.tvActiveModuleCount);
        tvShakeStatus = findViewById(R.id.tvShakeStatus);

        android.widget.CompoundButton.OnCheckedChangeListener moduleListener = (buttonView, isChecked) -> {
            androidx.appcompat.widget.SwitchCompat sw = (androidx.appcompat.widget.SwitchCompat) buttonView;
            if (isChecked) {
                sw.setTrackTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00C853")));
                sw.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            } else {
                sw.setTrackTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336")));
                sw.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            }
            if (sw.getId() == R.id.switchSelfie) prefs.edit().putBoolean("selfie_enabled", isChecked).apply();
            updateActiveModulesCounter();
        };

        if (switchAegis != null) { switchAegis.setOnCheckedChangeListener(moduleListener); moduleListener.onCheckedChanged(switchAegis, switchAegis.isChecked()); }
        if (switchAI != null) { switchAI.setOnCheckedChangeListener(moduleListener); moduleListener.onCheckedChanged(switchAI, switchAI.isChecked()); }

        if (switchSelfie != null) {
            switchSelfie.setChecked(prefs.getBoolean("selfie_enabled", true));
            switchSelfie.setOnCheckedChangeListener(moduleListener);
            moduleListener.onCheckedChanged(switchSelfie, switchSelfie.isChecked());
        }

        if (switchNetwork != null) {
            switchNetwork.setOnCheckedChangeListener((buttonView, isChecked) -> {
                moduleListener.onCheckedChanged(buttonView, isChecked);
                if (isChecked) startNetworkSentinel();
                else stopNetworkSentinel();
            });
            moduleListener.onCheckedChanged(switchNetwork, switchNetwork.isChecked());
            if (switchNetwork.isChecked()) startNetworkSentinel();
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        isShakeEnabled = prefs.getBoolean("shake_enabled", false);
        if (btnToggleShake != null) {
            btnToggleShake.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isShakeEnabled = isChecked;
                prefs.edit().putBoolean("shake_enabled", isShakeEnabled).apply();
                if (isChecked) {
                    btnToggleShake.setTrackTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00C853")));
                    if (tvShakeStatus != null) { tvShakeStatus.setText("Status: ON"); tvShakeStatus.setTextColor(Color.parseColor("#00C853")); }
                    if(sensorManager != null) sensorManager.registerListener(MainActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
                } else {
                    btnToggleShake.setTrackTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336")));
                    if (tvShakeStatus != null) { tvShakeStatus.setText("Status: OFF"); tvShakeStatus.setTextColor(Color.parseColor("#F44336")); }
                    if(sensorManager != null) sensorManager.unregisterListener(MainActivity.this);
                }
                updateActiveModulesCounter();
            });
            btnToggleShake.setChecked(isShakeEnabled);
        }

        if (switchChronos != null) {
            switchChronos.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    switchChronos.setTrackTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00C853")));
                    if (!isChronosActive) showChronosSetupDialog();
                } else {
                    switchChronos.setTrackTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336")));
                    if (isChronosActive) stopChronosProtocol();
                }
                updateActiveModulesCounter();
            });
        }

        if (cardStat2 != null) {
            cardStat2.setOnClickListener(v -> {
                if (isChronosActive) {
                    startChronosProtocol(chronosTimeMillis);
                    Toast.makeText(MainActivity.this, "✅ Safe Check-In Confirmed! Protocol Reset.", Toast.LENGTH_SHORT).show();
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
                }
            });
        }

        navSystem.setOnClickListener(v -> switchTabAnimated("system"));
        navModules.setOnClickListener(v -> switchTabAnimated("modules"));
        navSos.setOnClickListener(v -> switchTabAnimated("sos"));

        if (pinInput != null) pinInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(4) });

        LinearLayout cardCyberShield = findViewById(R.id.cardCyberShield);
        if(cardCyberShield != null) cardCyberShield.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, CyberScanActivity.class)));

        LinearLayout cardSafetyMap = findViewById(R.id.cardSafetyMap);
        if(cardSafetyMap != null) cardSafetyMap.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SafetyMapActivity.class)));

        CardView cardHealthBg = findViewById(R.id.cardHealthBg);
        if(cardHealthBg != null) cardHealthBg.setOnClickListener(v -> showIntruderGallery());

        CardView cardOfflineAiBg = findViewById(R.id.cardOfflineAiBg);
        if(cardOfflineAiBg != null) {
            cardOfflineAiBg.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, OfflineAiActivity.class)));
        }

        TextView tvEditGuardians = findViewById(R.id.tvEditGuardians);
        if (tvEditGuardians != null) {
            tvEditGuardians.setOnClickListener(v -> showModernContactsPage());
        }

        updateGuardianInitialsUI();

        FrameLayout btnMainSOS = findViewById(R.id.btnMainSOS);
        TextView sosText = findViewById(R.id.sosInstructionText);

        Runnable triggerSosAction = () -> {
            isSosTriggered = true;
            triggerStealthSOS("Manual UI SOS");
            if (sosText != null) { sosText.setText("CRITICAL ALERT SENT"); sosText.setTextColor(Color.parseColor("#ff2a2a")); }
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if(v != null) v.vibrate(500);
        };

        if (btnMainSOS != null) {
            btnMainSOS.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (!isSosTriggered) {
                            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(150).start();
                            sosHoldHandler.postDelayed(triggerSosAction, 1500);
                            if(sosText != null) sosText.setText("HOLD TO TRIGGER...");
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                        sosHoldHandler.removeCallbacks(triggerSosAction);
                        if(!isSosTriggered && sosText != null) {
                            sosText.setText("GUARDIAN PULSE\nPress and hold for 2s to broadcast Live Location & audio stealthily.");
                            sosText.setTextColor(Color.parseColor(isDarkMode ? "#8A95A5" : "#8A95A5"));
                        }
                        break;
                }
                return true;
            });
        }

        startSosPulseAnimation();
        if(btnStopAlert != null) btnStopAlert.setOnClickListener(v -> stopRedAlert());

        if (pinInput != null) {
            pinInput.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!isBiometricVerified) {
                        takeIntruderSelfie();
                        Toast.makeText(MainActivity.this, "Security Warning: Face not verified!", Toast.LENGTH_SHORT).show();
                    }
                }
                return false;
            });
        }

        if (btnFingerprint != null) {
            btnFingerprint.setOnClickListener(v -> {
                if (!isBiometricVerified) { takeIntruderSelfie(); }
                showNativeFingerprintPrompt();
            });
        }

        if(btnSubmitPin != null) {
            btnSubmitPin.setOnClickListener(v -> {
                String pin = pinInput.getText().toString();
                if (pin.equals("1234")) {
                    if (!isBiometricVerified) { takeIntruderSelfie(); }
                    unlockSystem();
                }
                else if (pin.equals("9999")) {
                    if (!isAccessibilityServiceEnabled(this, GhostClickService.class)) {
                        Toast.makeText(this, "Enable Accessibility for Guest Mode!", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        return;
                    }
                    try { stopLockTask(); } catch (Exception e) {}
                    pinInput.setText("");
                    // 🟢 FAKE DECRYPT SCREEN REMOVED FOR STEALTH MODE!
                    triggerStealthSOS("Aura Duress PIN Triggered!");
                    GhostClickService.triggerGuestMode = true;
                    try { startService(new Intent(this, GhostClickService.class)); } catch (Exception e) {}

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Intent auraIntent = new Intent("android.settings.USER_SETTINGS");
                        auraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(auraIntent);
                    }, 500);
                }
                else if (pin.equals("0000")) {
                    triggerFakePowerOff();
                } else {
                    wrongPinCount++;
                    pinInput.setText("");
                    Toast.makeText(this, "Access Denied", Toast.LENGTH_SHORT).show();
                    takeIntruderSelfie();
                }
            });
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createNotificationChannel();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_POWER_CONNECTED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(hardwareReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(hardwareReceiver, filter);

        setupWakeWordDetection();

        if(hiddenCameraView != null) {
            surfaceHolder = hiddenCameraView.getHolder();
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        contactPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) extractContactNumber(result.getData().getData());
        });

        if (getIntent() != null && getIntent().getBooleanExtra("TRIGGER_SCAM_ALERT", false)) {
            triggerDynamicRedAlert("SCAM CALL BLOCKED", "Deepfake / AI Voice Detected");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("TRIGGER_SCAM_ALERT", false)) {
            triggerDynamicRedAlert("SCAM CALL BLOCKED", "Deepfake / AI Voice Detected");
        }
    }

    private void showChronosSetupDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#05080D"));
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER);

        CardView cardView = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(60, 0, 60, 0);
        cardView.setLayoutParams(cardParams);
        cardView.setCardBackgroundColor(Color.parseColor("#151A25"));
        cardView.setRadius(30f);
        cardView.setCardElevation(20f);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(60, 80, 60, 80);
        mainLayout.setGravity(Gravity.CENTER);
        cardView.addView(mainLayout);
        rootLayout.addView(cardView);

        TextView title = new TextView(this);
        title.setText("CHRONOS DIRECTIVE");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.05f);
        title.setGravity(Gravity.CENTER);

        TextView desc = new TextView(this);
        desc.setText("Autonomous Dead-Man Switch.\nSpecify your safe window. Failure to check-in triggers an untraceable stealth SOS.");
        desc.setTextColor(Color.parseColor("#8A95A5"));
        desc.setTextSize(13);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, 20, 0, 60);
        desc.setLineSpacing(4f, 1f);
        mainLayout.addView(title);
        mainLayout.addView(desc);

        LinearLayout timeInputLayout = new LinearLayout(this);
        timeInputLayout.setOrientation(LinearLayout.HORIZONTAL);
        timeInputLayout.setGravity(Gravity.CENTER);
        timeInputLayout.setPadding(0, 0, 0, 60);

        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setColor(Color.parseColor("#0A0F1A"));
        inputBg.setCornerRadius(20f);
        inputBg.setStroke(3, Color.parseColor("#2A3546"));

        EditText etHours = new EditText(this);
        etHours.setHint("00");
        etHours.setTextColor(Color.parseColor("#FFFFFF"));
        etHours.setHintTextColor(Color.parseColor("#475569"));
        etHours.setInputType(InputType.TYPE_CLASS_NUMBER);
        etHours.setLayoutParams(new LinearLayout.LayoutParams(140, 140));
        etHours.setGravity(Gravity.CENTER);
        etHours.setBackground(inputBg);
        etHours.setTextSize(24);
        etHours.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView lblHr = new TextView(this);
        lblHr.setText("HR");
        lblHr.setTextColor(Color.parseColor("#8A95A5"));
        lblHr.setTextSize(10);
        lblHr.setGravity(Gravity.CENTER);
        lblHr.setPadding(0,10,0,0);
        LinearLayout hrContainer = new LinearLayout(this);
        hrContainer.setOrientation(LinearLayout.VERTICAL);
        hrContainer.addView(etHours);
        hrContainer.addView(lblHr);

        TextView colon1 = new TextView(this);
        colon1.setText(" : ");
        colon1.setTextColor(Color.parseColor("#475569"));
        colon1.setTextSize(24);
        colon1.setTypeface(null, android.graphics.Typeface.BOLD);
        colon1.setPadding(10,0,10,40);

        EditText etMins = new EditText(this);
        etMins.setHint("00");
        etMins.setTextColor(Color.parseColor("#FFFFFF"));
        etMins.setHintTextColor(Color.parseColor("#475569"));
        etMins.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMins.setLayoutParams(new LinearLayout.LayoutParams(140, 140));
        etMins.setGravity(Gravity.CENTER);
        etMins.setBackground(inputBg);
        etMins.setTextSize(24);
        etMins.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView lblMin = new TextView(this);
        lblMin.setText("MIN");
        lblMin.setTextColor(Color.parseColor("#8A95A5"));
        lblMin.setTextSize(10);
        lblMin.setGravity(Gravity.CENTER);
        lblMin.setPadding(0,10,0,0);
        LinearLayout minContainer = new LinearLayout(this);
        minContainer.setOrientation(LinearLayout.VERTICAL);
        minContainer.addView(etMins);
        minContainer.addView(lblMin);

        TextView colon2 = new TextView(this);
        colon2.setText(" : ");
        colon2.setTextColor(Color.parseColor("#475569"));
        colon2.setTextSize(24);
        colon2.setTypeface(null, android.graphics.Typeface.BOLD);
        colon2.setPadding(10,0,10,40);

        EditText etSecs = new EditText(this);
        etSecs.setHint("00");
        etSecs.setTextColor(Color.parseColor("#FFFFFF"));
        etSecs.setHintTextColor(Color.parseColor("#475569"));
        etSecs.setInputType(InputType.TYPE_CLASS_NUMBER);
        etSecs.setLayoutParams(new LinearLayout.LayoutParams(140, 140));
        etSecs.setGravity(Gravity.CENTER);
        etSecs.setBackground(inputBg);
        etSecs.setTextSize(24);
        etSecs.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView lblSec = new TextView(this);
        lblSec.setText("SEC");
        lblSec.setTextColor(Color.parseColor("#8A95A5"));
        lblSec.setTextSize(10);
        lblSec.setGravity(Gravity.CENTER);
        lblSec.setPadding(0,10,0,0);
        LinearLayout secContainer = new LinearLayout(this);
        secContainer.setOrientation(LinearLayout.VERTICAL);
        secContainer.addView(etSecs);
        secContainer.addView(lblSec);

        timeInputLayout.addView(hrContainer);
        timeInputLayout.addView(colon1);
        timeInputLayout.addView(minContainer);
        timeInputLayout.addView(colon2);
        timeInputLayout.addView(secContainer);

        mainLayout.addView(timeInputLayout);

        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnCancel = new Button(this);
        btnCancel.setText("ABORT");
        btnCancel.setTextColor(Color.parseColor("#FF3366"));
        android.graphics.drawable.GradientDrawable btnCancelBg = new android.graphics.drawable.GradientDrawable();
        btnCancelBg.setColor(Color.parseColor("#1C151A"));
        btnCancelBg.setCornerRadius(15f);
        btnCancelBg.setStroke(2, Color.parseColor("#FF3366"));
        btnCancel.setBackground(btnCancelBg);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, 120, 1);
        cancelParams.setMargins(0, 0, 15, 0);
        btnCancel.setLayoutParams(cancelParams);

        btnCancel.setOnClickListener(v -> {
            if (switchChronos != null) switchChronos.setChecked(false);
            dialog.dismiss();
        });

        Button btnStart = new Button(this);
        btnStart.setText("INITIALIZE");
        btnStart.setTextColor(Color.parseColor("#05080D"));
        btnStart.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable btnStartBg = new android.graphics.drawable.GradientDrawable();
        btnStartBg.setColor(Color.parseColor("#00E5FF"));
        btnStartBg.setCornerRadius(15f);
        btnStart.setBackground(btnStartBg);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, 120, 1);
        startParams.setMargins(15, 0, 0, 0);
        btnStart.setLayoutParams(startParams);

        btnStart.setOnClickListener(v -> {
            String hStr = etHours.getText().toString();
            String mStr = etMins.getText().toString();
            String sStr = etSecs.getText().toString();

            int h = hStr.isEmpty() ? 0 : Integer.parseInt(hStr);
            int m = mStr.isEmpty() ? 0 : Integer.parseInt(mStr);
            int s = sStr.isEmpty() ? 0 : Integer.parseInt(sStr);

            long totalMillis = (h * 3600L + m * 60L + s) * 1000L;

            if (totalMillis <= 0) {
                Toast.makeText(this, "⚠️ Specify a valid time window!", Toast.LENGTH_SHORT).show();
            } else {
                chronosTimeMillis = totalMillis;
                startChronosProtocol(chronosTimeMillis);
                dialog.dismiss();
            }
        });

        actionLayout.addView(btnCancel);
        actionLayout.addView(btnStart);
        mainLayout.addView(actionLayout);

        dialog.setContentView(rootLayout);
        dialog.show();
    }

    private void startChronosProtocol(long millis) {
        if (chronosTimer != null) chronosTimer.cancel();
        isChronosActive = true;
        if (switchChronos != null) switchChronos.setChecked(true);
        if (tvSubChronos != null) {
            tvSubChronos.setText("Active: Tap Dashboard to Check-in");
            tvSubChronos.setTextColor(Color.parseColor("#00C853"));
        }

        chronosTimer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long h = (millisUntilFinished / 1000) / 3600;
                long m = ((millisUntilFinished / 1000) % 3600) / 60;
                long s = (millisUntilFinished / 1000) % 60;

                if(tvReady != null) {
                    if (h > 0) {
                        tvReady.setText(String.format(Locale.getDefault(), "⏳ %02d:%02d:%02d", h, m, s));
                    } else {
                        tvReady.setText(String.format(Locale.getDefault(), "⏳ %02d:%02d", m, s));
                    }
                    tvReady.setTextColor(Color.parseColor("#FF003C"));
                }
                if(tvSubStat2 != null) {
                    tvSubStat2.setText("TAP TO CHECK-IN");
                    tvSubStat2.setTextColor(Color.parseColor("#FF003C"));
                }
            }

            @Override
            public void onFinish() {
                stopChronosProtocol();
                triggerStealthSOS("Chronos Directive: User failed to check-in on time!");
                Toast.makeText(MainActivity.this, "Stealth Protocol Executed Silently.", Toast.LENGTH_SHORT).show();
            }
        }.start();
    }

    private void stopChronosProtocol() {
        isChronosActive = false;
        if (chronosTimer != null) {
            chronosTimer.cancel();
            chronosTimer = null;
        }
        if (switchChronos != null) switchChronos.setChecked(false);
        if (tvSubChronos != null) {
            tvSubChronos.setText("Dead-Man Switch. Off");
            tvSubChronos.setTextColor(Color.parseColor("#8798AD"));
        }
        if(tvReady != null) {
            tvReady.setText("Ready");
            tvReady.setTextColor(Color.parseColor("#00C853"));
        }
        if(tvSubStat2 != null) {
            tvSubStat2.setText("TRIGGER STATUS");
            tvSubStat2.setTextColor(Color.parseColor("#8798AD"));
        }
    }

    private void updateGuardianInitialsUI() {
        LinearLayout container = findViewById(R.id.guardianInitialsContainer);
        if (container == null) return;
        container.removeAllViews();

        Set<String> savedContacts = prefs.getStringSet("sos_contacts", new HashSet<>());
        int count = 0;
        int sizePx = (int) (45 * getResources().getDisplayMetrics().density);

        for (String contact : savedContacts) {
            if (count >= 5) break;
            String[] parts = contact.split(":");
            String name = parts[0].trim();
            String initial = name.length() > 0 ? String.valueOf(name.charAt(0)).toUpperCase() : "U";

            TextView tv = new TextView(this);
            tv.setText(initial);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(18f);
            tv.setGravity(Gravity.CENTER);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);

            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(Color.parseColor("#1C2533"));
            gd.setCornerRadius(100f);
            gd.setStroke(3, Color.parseColor("#00E5FF"));
            tv.setBackground(gd);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
            params.setMargins(0, 0, 25, 0);
            tv.setLayoutParams(params);

            container.addView(tv);
            count++;
        }

        if (savedContacts.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No Guardians Added. Tap EDIT to add.");
            tv.setTextColor(Color.parseColor("#8A95A5"));
            tv.setTextSize(13f);
            container.addView(tv);
        }
    }

    private void startCustomFaceScan() {
        isBiometricVerified = false;

        runOnUiThread(() -> {
            if (tvFaceIcon != null && tvFaceText != null) {
                tvFaceIcon.setText("[ 👤 ]");
                tvFaceIcon.setTextColor(Color.parseColor("#00E5FF"));
                tvFaceText.setText("Auto-Scanning Face...");

                if (faceBlinkAnimator != null) faceBlinkAnimator.cancel();
                faceBlinkAnimator = ObjectAnimator.ofFloat(tvFaceIcon, "alpha", 1f, 0.2f, 1f);
                faceBlinkAnimator.setDuration(600);
                faceBlinkAnimator.setRepeatCount(3);
                faceBlinkAnimator.start();
            }
        });

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isUnlocked) {
                isBiometricVerified = true;
                runOnUiThread(() -> {
                    if (tvFaceIcon != null && tvFaceText != null) {
                        tvFaceIcon.setAlpha(1f);
                        tvFaceIcon.setText("[ ✅ ]");
                        tvFaceIcon.setTextColor(Color.parseColor("#00C853"));
                        tvFaceText.setText("Face Verified! Enter PIN/Fingerprint to Unlock.");
                    }
                });
            }
        }, 2200);
    }

    private void showNativeFingerprintPrompt() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("OmniKavach Secure Unlock")
                .setSubtitle("Place Finger on Sensor")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use PIN")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                if (!isBiometricVerified) { takeIntruderSelfie(); }
                unlockSystem();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                takeIntruderSelfie();
            }
        });

        try { biometricPrompt.authenticate(promptInfo); } catch (Exception e) {}
    }

    private void takeIntruderSelfie() {
        if (switchSelfie != null && !switchSelfie.isChecked()) {
            return;
        }
        if (isCameraInUse) return;
        isCameraInUse = true;

        new Thread(() -> {
            try {
                int frontCamId = -1; Camera.CameraInfo info = new Camera.CameraInfo();
                for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                    Camera.getCameraInfo(i, info);
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) { frontCamId = i; break; }
                }

                if (frontCamId != -1) {
                    mCamera = Camera.open(frontCamId);
                    android.graphics.SurfaceTexture dummySurface = new android.graphics.SurfaceTexture(0);
                    mCamera.setPreviewTexture(dummySurface);
                    mCamera.startPreview();

                    Thread.sleep(600);

                    mCamera.takePicture(null, null, (data, camera) -> {
                        try {
                            File photo = new File(getExternalFilesDir(null), "intruder_" + System.currentTimeMillis() + ".jpg");
                            FileOutputStream fos = new FileOutputStream(photo);
                            fos.write(data);
                            fos.close();

                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "📸 Security Alert: Intruder Photo Saved!", Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {} finally {
                            if (mCamera != null) { mCamera.stopPreview(); mCamera.release(); mCamera = null; }
                            isCameraInUse = false;
                        }
                    });
                } else { isCameraInUse = false; }
            } catch (Exception e) {
                if (mCamera != null) { try { mCamera.release(); } catch(Exception ex){} mCamera = null; }
                isCameraInUse = false;
            }
        }).start();
    }

    private void cleanupOldIntruderPhotos() {
        File dir = getExternalFilesDir(null);
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles((d, name) -> name.startsWith("intruder_"));
            if (files != null) {
                long cutoff = System.currentTimeMillis() - (24L * 60 * 60 * 1000);
                for (File f : files) { if (f.lastModified() < cutoff) f.delete(); }
            }
        }
    }

    private void cleanupAllIntruderPhotos() {
        File dir = getExternalFilesDir(null);
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles((d, name) -> name.startsWith("intruder_"));
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
    }

    private void showIntruderGallery() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#05080D"));
        mainLayout.setPadding(40, 60, 40, 40);

        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("📸 Intruder Logs (24h)");
        title.setTextColor(Color.parseColor("#ff2a2a"));
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView closeBtn = new TextView(this);
        closeBtn.setText("✖");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(24);
        closeBtn.setPadding(20, 20, 20, 20);
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        headerLayout.addView(title); headerLayout.addView(closeBtn); mainLayout.addView(headerLayout);

        Button btnDeleteAll = new Button(this);
        btnDeleteAll.setText("🗑️ DELETE ALL LOGS");
        btnDeleteAll.setTextColor(Color.WHITE);
        btnDeleteAll.setBackgroundColor(Color.parseColor("#D32F2F"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
        btnParams.setMargins(0, 20, 0, 30);
        btnDeleteAll.setLayoutParams(btnParams);
        btnDeleteAll.setOnClickListener(v -> {
            cleanupAllIntruderPhotos();
            dialog.dismiss();
            Toast.makeText(this, "All Intruder Logs Deleted Successfully!", Toast.LENGTH_SHORT).show();
        });
        mainLayout.addView(btnDeleteAll);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);
        mainLayout.addView(scrollView);

        File dir = getExternalFilesDir(null);
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles((d, name) -> name.startsWith("intruder_"));
            if (files != null && files.length > 0) {
                for (File f : files) {
                    LinearLayout item = new LinearLayout(this);
                    item.setOrientation(LinearLayout.HORIZONTAL);
                    item.setPadding(0, 30, 0, 30);
                    item.setGravity(Gravity.CENTER_VERTICAL);

                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
                    if (bmp != null) {
                        iv.setImageBitmap(bmp);
                        iv.setLayoutParams(new LinearLayout.LayoutParams(250, 250));
                        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    }

                    TextView tvDate = new TextView(this);
                    String date = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(new Date(f.lastModified()));
                    tvDate.setText("Suspicious Activity:\n" + date);
                    tvDate.setTextColor(Color.WHITE);
                    tvDate.setTextSize(16);
                    tvDate.setPadding(40, 0, 0, 0);

                    item.addView(iv); item.addView(tvDate);
                    listContainer.addView(item);
                }
            } else {
                TextView tvNo = new TextView(this);
                tvNo.setText("No intruders detected. Device is safe.");
                tvNo.setTextColor(Color.parseColor("#00C853"));
                tvNo.setPadding(0, 30, 0, 0);
                listContainer.addView(tvNo);
                btnDeleteAll.setVisibility(View.GONE);
            }
        }
        dialog.setContentView(mainLayout); dialog.show();
    }

    private void pinApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                    try { startLockTask(); } catch (Exception e) {}
                }
            } else {
                try { startLockTask(); } catch (Exception e) {}
            }
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void unlockSystem() {
        wrongPinCount = 0;
        isUnlocked = true;

        if (mCamera != null) {
            try { mCamera.stopPreview(); mCamera.release(); } catch (Exception e) {}
            mCamera = null;
        }
        isCameraInUse = false;

        try { stopLockTask(); } catch (Exception e) {}

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if(pinInput != null) pinInput.setText("");
        if (lockScreenOverlay != null) {
            lockScreenOverlay.animate().alpha(0f).setDuration(400).withEndAction(() -> lockScreenOverlay.setVisibility(View.GONE)).start();
        }
        isBiometricVerified = false;
        if (faceBlinkAnimator != null) faceBlinkAnimator.cancel();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isUnlocked) {
            pinApp();
        }
    }

    @Override
    public void onBackPressed() {
        if (!isUnlocked) {
            Toast.makeText(this, "Terminal Encrypted. Access Denied.", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!isUnlocked) {
            if (lockScreenOverlay != null) { lockScreenOverlay.setVisibility(View.VISIBLE); lockScreenOverlay.setAlpha(1f); }
            pinApp();

            if (!hasPromptedFaceScan) {
                hasPromptedFaceScan = true;
                startCustomFaceScan();
            }
        }
        updateGuardianInitialsUI();
    }

    private void showFakeDecryptingScreen() {
        try {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);

            LinearLayout fakeScreen = new LinearLayout(this); fakeScreen.setBackgroundColor(Color.parseColor("#05080D")); fakeScreen.setOrientation(LinearLayout.VERTICAL); fakeScreen.setGravity(Gravity.CENTER);
            TextView icon = new TextView(this); icon.setText("🔓"); icon.setTextSize(60); icon.setGravity(Gravity.CENTER); fakeScreen.addView(icon);
            TextView text = new TextView(this); text.setText("DECRYPTING SYSTEM...\nPlease Wait..."); text.setTextColor(Color.parseColor("#00E676")); text.setTextSize(22); text.setGravity(Gravity.CENTER); text.setPadding(0, 30, 0, 0); fakeScreen.addView(text);
            windowManager.addView(fakeScreen, params);
        } catch (Exception e) { Log.e("Rakshak", "Overlay permission required for stealth screen."); }
    }

    private void startSosPulseAnimation() {
        CardView ring1 = findViewById(R.id.sosPulseRing1);
        CardView ring2 = findViewById(R.id.sosPulseRing2);
        if (ring1 != null && ring2 != null) {
            animatePulse(ring1, 0);
            animatePulse(ring2, 1000);
        }
    }

    private void animatePulse(View ring, long delay) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 2.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 2.0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(ring, "alpha", 0.6f, 0f);

        scaleX.setRepeatCount(ValueAnimator.INFINITE); scaleY.setRepeatCount(ValueAnimator.INFINITE); alpha.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setDuration(2000); scaleY.setDuration(2000); alpha.setDuration(2000);
        scaleX.setStartDelay(delay); scaleY.setStartDelay(delay); alpha.setStartDelay(delay);

        scaleX.start(); scaleY.start(); alpha.start();
    }

    private void switchTabAnimated(String tab) {
        fadeView(viewDashboard, tab.equals("system"));
        fadeView(viewModules, tab.equals("modules"));
        fadeView(viewSos, tab.equals("sos"));

        int activeColor = Color.parseColor("#00f0ff"); int sosColor = Color.parseColor("#ff2a2a"); int inactiveColor = Color.parseColor("#8798AD");

        if(txtSystem != null) { txtSystem.setTextColor(tab.equals("system") ? activeColor : inactiveColor); txtSystem.setTypeface(null, tab.equals("system") ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL); icSystem.setAlpha(tab.equals("system") ? 1.0f : 0.5f); }
        if(txtModules != null) { txtModules.setTextColor(tab.equals("modules") ? activeColor : inactiveColor); txtModules.setTypeface(null, tab.equals("modules") ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL); icModules.setAlpha(tab.equals("modules") ? 1.0f : 0.5f); }
        if(txtSos != null) { txtSos.setTextColor(tab.equals("sos") ? sosColor : inactiveColor); txtSos.setTypeface(null, tab.equals("sos") ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL); icSos.setAlpha(tab.equals("sos") ? 1.0f : 0.5f); }
    }

    private void fadeView(View view, boolean show) {
        if (view == null) return;
        if (show) { view.setAlpha(0f); view.setVisibility(View.VISIBLE); view.animate().alpha(1f).setDuration(200).start(); }
        else { view.animate().alpha(0f).setDuration(200).withEndAction(() -> view.setVisibility(View.GONE)).start(); }
    }

    private void startNetworkSentinel() {
        if (connectivityManager == null) connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(@NonNull Network network) { super.onAvailable(network); checkWifiSecurityStatus(); }
        };
        try { connectivityManager.registerNetworkCallback(request, networkCallback); } catch (Exception e) {}
    }

    private void stopNetworkSentinel() {
        if (connectivityManager != null && networkCallback != null) { try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception e) {} }
    }

    private void checkWifiSecurityStatus() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {
            boolean isDangerousOpenNetwork = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                int securityType = wifiInfo.getCurrentSecurityType();
                if (securityType == WifiInfo.SECURITY_TYPE_OPEN || securityType == WifiInfo.SECURITY_TYPE_OWE) { isDangerousOpenNetwork = true; }
            } else {
                String ssid = wifiInfo.getSSID();
                if (ssid != null && ssid.length() > 2) ssid = ssid.substring(1, ssid.length() - 1);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    List<ScanResult> networkList = wifiManager.getScanResults();
                    if (networkList != null) {
                        for (ScanResult network : networkList) {
                            if (network.SSID.equals(ssid)) {
                                String capabilities = network.capabilities;
                                if (!capabilities.contains("WEP") && !capabilities.contains("WPA") && !capabilities.contains("EAP")) { isDangerousOpenNetwork = true; } break;
                            }
                        }
                    }
                }
            }
            if (isDangerousOpenNetwork) {
                triggerDynamicRedAlert("UNSECURED WI-FI BLOCKED", "Open Network Detected!\nARP Spoofing / Packet Sniffing Prevented.");
                saveToHistory("Network Sentinel blocked Open Wi-Fi");
            }
        }
    }

    private void updateActiveModulesCounter() {
        int count = 0;
        if (switchAegis != null && switchAegis.isChecked()) count++;
        if (switchAI != null && switchAI.isChecked()) count++;
        if (switchNetwork != null && switchNetwork.isChecked()) count++;
        if (switchSelfie != null && switchSelfie.isChecked()) count++;
        if (btnToggleShake != null && btnToggleShake.isChecked()) count++;
        if (switchChronos != null && switchChronos.isChecked()) count++;
        if (tvActiveModuleCount != null) tvActiveModuleCount.setText(String.valueOf(count));
    }

    private void applyThemeUI() {
        if(mainRoot != null) mainRoot.setBackgroundColor(Color.parseColor(isDarkMode ? "#0B0F19" : "#F4F6F9"));
        if(bottomNav != null) bottomNav.setBackgroundColor(Color.parseColor(isDarkMode ? "#05080D" : "#FFFFFF"));
        recursivelyUpdateTheme(mainRoot);
    }

    private void recursivelyUpdateTheme(View view) {
        Object tag = view.getTag();
        if (tag != null) {
            String tagString = tag.toString();
            if (view instanceof CardView) {
                if (tagString.equals("theme_card")) ((CardView) view).setCardBackgroundColor(Color.parseColor(isDarkMode ? "#151A25" : "#FFFFFF"));
                if (tagString.equals("theme_icon_bg")) ((CardView) view).setCardBackgroundColor(Color.parseColor(isDarkMode ? "#1C2533" : "#F4F6F9"));
            } else if (view instanceof TextView) {
                if (tagString.equals("theme_text_main")) ((TextView) view).setTextColor(Color.parseColor(isDarkMode ? "#FFFFFF" : "#1C2A3A"));
                if (tagString.equals("theme_text_sub")) ((TextView) view).setTextColor(Color.parseColor(isDarkMode ? "#8A95A5" : "#8798AD"));
            }
        }
        if (view instanceof ViewGroup) { ViewGroup vg = (ViewGroup) view; for (int i = 0; i < vg.getChildCount(); i++) recursivelyUpdateTheme(vg.getChildAt(i)); }
    }

    private void triggerDynamicRedAlert(String title, String description) {
        runOnUiThread(() -> {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "Rakshak:Alert");
                wakeLock.acquire(3000);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true); }
            else { getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD); }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            if(scamAlertScreen != null) scamAlertScreen.setVisibility(View.VISIBLE);
            if(tvAlertTitle != null) tvAlertTitle.setText(title);
            if(tvAlertDesc != null) tvAlertDesc.setText(description);

            if (scamVibrator == null) scamVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (scamVibrator != null) { scamVibrator.cancel(); scamVibrator.vibrate(new long[]{0, 1000, 500}, 0); }
            try {
                if (scamRingtone == null) {
                    Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    scamRingtone = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) scamRingtone.setLooping(true);
                }
                if (!scamRingtone.isPlaying()) scamRingtone.play();
            } catch (Exception e) {}
        });
    }

    private void stopRedAlert() {
        runOnUiThread(() -> {
            if(scamAlertScreen != null) scamAlertScreen.setVisibility(View.GONE);
            if (scamVibrator != null) scamVibrator.cancel();
            if (scamRingtone != null) { try { if (scamRingtone.isPlaying()) scamRingtone.stop(); } catch (Exception e) {} scamRingtone = null; }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    private void requestInitialPermissions() {
        ArrayList<String> permList = new ArrayList<>();
        permList.add(Manifest.permission.SEND_SMS);
        permList.add(Manifest.permission.RECORD_AUDIO);
        permList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permList.add(Manifest.permission.READ_CONTACTS);
        permList.add(Manifest.permission.CAMERA);
        permList.add(Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { permList.add(Manifest.permission.BLUETOOTH_SCAN); permList.add(Manifest.permission.BLUETOOTH_CONNECT); }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permList.add(Manifest.permission.POST_NOTIFICATIONS);
        ActivityCompat.requestPermissions(this, permList.toArray(new String[0]), 100);
    }

    private void checkAdvancedPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Enable 'Display over other apps' for Omni-Kavach.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS); intent.setData(Uri.parse("package:" + getPackageName())); startActivity(intent);
            }
        }
    }

    public boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityService) {
        android.content.ComponentName expectedComponentName = new android.content.ComponentName(context, accessibilityService);
        String enabledServicesSetting = Settings.Secure.getString(context.getContentResolver(),  Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServicesSetting == null) return false;
        android.text.TextUtils.SimpleStringSplitter colonSplitter = new android.text.TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);
        while (colonSplitter.hasNext()) {
            String componentNameString = colonSplitter.next();
            android.content.ComponentName enabledService = android.content.ComponentName.unflattenFromString(componentNameString);
            if (enabledService != null && enabledService.equals(expectedComponentName)) return true;
        }
        return false;
    }

    private void saveToHistory(String action) {
        String currentHistory = prefs.getString("sos_history", "");
        String timestamp = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
        String newEntry = "🚨 " + action + "\n🕒 " + timestamp + "SPLIT_LOG";
        prefs.edit().putString("sos_history", newEntry + currentHistory).apply();
    }

    private void showModernContactsPage() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout mainLayout = new LinearLayout(this); mainLayout.setOrientation(LinearLayout.VERTICAL); mainLayout.setBackgroundColor(Color.parseColor("#050505")); mainLayout.setPadding(40, 60, 40, 40);

        LinearLayout headerLayout = new LinearLayout(this); headerLayout.setOrientation(LinearLayout.HORIZONTAL); headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this); title.setText("Emergency Circle"); title.setTextColor(Color.parseColor("#00f0ff")); title.setTextSize(24); title.setTypeface(null, android.graphics.Typeface.BOLD); title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView closeBtn = new TextView(this); closeBtn.setText("✖"); closeBtn.setTextColor(Color.WHITE); closeBtn.setTextSize(24); closeBtn.setPadding(20, 20, 20, 20); closeBtn.setOnClickListener(v -> dialog.dismiss());
        headerLayout.addView(title); headerLayout.addView(closeBtn); mainLayout.addView(headerLayout);

        Button btnAdd = new Button(this); btnAdd.setText("+ Add New Contact"); btnAdd.setTextColor(Color.BLACK); btnAdd.setBackgroundColor(Color.parseColor("#00f0ff"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 140); btnParams.setMargins(0, 40, 0, 40); btnAdd.setLayoutParams(btnParams);
        btnAdd.setOnClickListener(v -> { dialog.dismiss(); contactPickerLauncher.launch(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)); });
        mainLayout.addView(btnAdd);

        ScrollView scrollView = new ScrollView(this); LinearLayout listContainer = new LinearLayout(this); listContainer.setOrientation(LinearLayout.VERTICAL); scrollView.addView(listContainer); mainLayout.addView(scrollView);

        Set<String> savedContacts = prefs.getStringSet("sos_contacts", new HashSet<>());
        for (String contact : savedContacts) {
            CardView card = new CardView(this); LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); cardParams.setMargins(0, 0, 0, 30); card.setLayoutParams(cardParams); card.setCardBackgroundColor(Color.parseColor("#141416"));
            LinearLayout cardInner = new LinearLayout(this); cardInner.setOrientation(LinearLayout.HORIZONTAL); cardInner.setPadding(40, 40, 40, 40); cardInner.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout textLayout = new LinearLayout(this); textLayout.setOrientation(LinearLayout.VERTICAL); textLayout.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            String[] parts = contact.split(":");
            TextView tvName = new TextView(this); tvName.setText(parts[0]); tvName.setTextColor(Color.WHITE); tvName.setTextSize(18); tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView tvNum = new TextView(this); tvNum.setText(parts.length > 1 ? parts[1] : ""); tvNum.setTextColor(Color.parseColor("#a0a0b0"));
            textLayout.addView(tvName); textLayout.addView(tvNum);
            TextView btnDelete = new TextView(this); btnDelete.setText("🗑️"); btnDelete.setTextSize(22); btnDelete.setPadding(20, 20, 0, 20);
            btnDelete.setOnClickListener(v -> { Set<String> updated = new HashSet<>(prefs.getStringSet("sos_contacts", new HashSet<>())); updated.remove(contact); prefs.edit().putStringSet("sos_contacts", updated).apply(); listContainer.removeView(card); });
            cardInner.addView(textLayout); cardInner.addView(btnDelete); card.addView(cardInner); listContainer.addView(card);
        }

        dialog.setOnDismissListener(d -> updateGuardianInitialsUI());

        dialog.setContentView(mainLayout); dialog.show();
    }

    private void triggerAegisPortAlert() {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
            LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setBackgroundColor(Color.parseColor("#050505")); layout.setGravity(Gravity.CENTER); layout.setPadding(60, 60, 60, 60);
            TextView title = new TextView(this); title.setText("🛡️ AEGIS PORT ACTIVATED"); title.setTextColor(Color.parseColor("#00f0ff")); title.setTextSize(24); title.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView msg = new TextView(this); msg.setText("\nPublic Power Source Detected!\nData pins logically blocked.\nDevice is charging in Safe Mode."); msg.setTextColor(Color.WHITE); msg.setTextSize(16); msg.setGravity(Gravity.CENTER);
            layout.addView(title); layout.addView(msg);
            AlertDialog dialog = builder.setView(layout).create(); dialog.show(); saveToHistory("Aegis Port Activated");
            new Handler().postDelayed(dialog::dismiss, 4000);
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("RakshakChannel", "Rakshak Alerts", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        float x = event.values[0], y = event.values[1], z = event.values[2];
        accelLast = accelVal; accelVal = (float) Math.sqrt((double) (x * x + y * y + z * z)); float delta = accelVal - accelLast; shake = shake * 0.9f + delta;
        if (isShakeEnabled && shake > 12) triggerStealthSOS("Shake Detected SOS");
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void triggerFakePowerOff() {
        if(fakePowerScreen != null) fakePowerScreen.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        triggerStealthSOS("Phone Stolen (Fake Power Off)");
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && fakePowerScreen != null && fakePowerScreen.getVisibility() == View.VISIBLE) { fakePowerScreen.setVisibility(View.GONE); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private void triggerStealthSOS(String reason) {
        long currentTime = System.currentTimeMillis(); if (currentTime - lastSosTime < 15000) return; lastSosTime = currentTime;
        Set<String> savedContacts = prefs.getStringSet("sos_contacts", new HashSet<>()); saveToHistory(reason);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        String locLink = "Location not found";
                        if (location != null) locLink = "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
                        if (!savedContacts.isEmpty()) sendSMSToAll(savedContacts, reason, locLink); takeStealthPhotoForSOS(reason, locLink);
                    })
                    .addOnFailureListener(e -> {
                        if (!savedContacts.isEmpty()) sendSMSToAll(savedContacts, reason, "Location Unavailable"); takeStealthPhotoForSOS(reason, "Location Unavailable");
                    });
        } else {
            if (!savedContacts.isEmpty()) sendSMSToAll(savedContacts, reason, "Location Permission Denied"); takeStealthPhotoForSOS(reason, "Location Permission Denied");
        }
        startStealthRecording();
    }

    private void takeStealthPhotoForSOS(String reason, String exactLocationLink) {
        if (isCameraInUse) return;
        isCameraInUse = true;
        new Thread(() -> {
            try {
                int frontCamId = -1; Camera.CameraInfo info = new Camera.CameraInfo();
                for (int i = 0; i < Camera.getNumberOfCameras(); i++) { Camera.getCameraInfo(i, info); if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) { frontCamId = i; break; } }
                if (frontCamId != -1) {
                    Camera cam = Camera.open(frontCamId);
                    android.graphics.SurfaceTexture dummySurface = new android.graphics.SurfaceTexture(0);
                    cam.setPreviewTexture(dummySurface);
                    cam.startPreview();
                    Thread.sleep(800);
                    cam.takePicture(null, null, (data, camera) -> {
                        try {
                            File photo = new File(getExternalFilesDir(null), "sos_intruder.jpg"); FileOutputStream fos = new FileOutputStream(photo); fos.write(data); fos.close();
                            String messageBody = "🚨 OMNI-KAVACH EMERGENCY SOS 🚨\n\nAlert: " + reason + "\n📍 Live tracking link: \n" + exactLocationLink;
                            MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("chat_id", CHAT_ID).addFormDataPart("caption", messageBody);
                            if (photo.exists()) bodyBuilder.addFormDataPart("photo", photo.getName(), RequestBody.create(photo, MediaType.parse("image/jpeg")));
                            Request request = new Request.Builder().url("https://api.telegram.org/bot" + TELEGRAM_TOKEN + "/sendPhoto").post(bodyBuilder.build()).build();
                            httpClient.newCall(request).enqueue(new Callback() { @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {} @Override public void onResponse(@NonNull Call call, @NonNull Response response) { response.close(); } });
                        } catch (Exception e) {} finally { cam.release(); isCameraInUse = false; }
                    });
                } else { isCameraInUse = false; }
            } catch (Exception e) { isCameraInUse = false; }
        }).start();
    }

    private void sendSMSToAll(Set<String> contacts, String reason, String exactLocationLink) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return;
        SmsManager smsManager = SmsManager.getDefault();
        String finalMessage = "🚨 OMNI-KAVACH SECURITY 🚨\n\nAlert: " + reason + "\n\nTrack Live Location:\n" + exactLocationLink;
        for (String contactEntry : contacts) {
            try {
                String[] parts = contactEntry.split(":");
                if(parts.length > 1) {
                    ArrayList<String> messageParts = smsManager.divideMessage(finalMessage);
                    smsManager.sendMultipartTextMessage(parts[1].trim(), null, messageParts, null, null);
                }
            } catch (Exception e) {}
        }
    }

    private void startStealthRecording() {
        if (isRecording) return;
        try {
            audioFilePath = getExternalCacheDir().getAbsolutePath() + "/sos_audio.3gp"; mediaRecorder = new MediaRecorder(); mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC); mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP); mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB); mediaRecorder.setOutputFile(audioFilePath); mediaRecorder.prepare(); mediaRecorder.start(); isRecording = true;
            new Handler().postDelayed(this::stopRecording, 15000);
        } catch (Exception e) {}
    }

    private void stopRecording() { if (mediaRecorder != null) { try { mediaRecorder.stop(); } catch (Exception e) {} mediaRecorder.release(); mediaRecorder = null; isRecording = false; } }

    private void extractContactNumber(Uri contactUri) {
        try (Cursor cursor = getContentResolver().query(contactUri, new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String number = cursor.getString(0).replaceAll("[^0-9+]", ""); if(number.length() == 10) number = "+91" + number;
                Set<String> savedContacts = new HashSet<>(prefs.getStringSet("sos_contacts", new HashSet<>())); savedContacts.add(cursor.getString(1) + ": " + number); prefs.edit().putStringSet("sos_contacts", savedContacts).apply();

                updateGuardianInitialsUI();
            }
        } catch (Exception e) {}
    }

    private void setupWakeWordDetection() {
        try {
            porcupineManager = new PorcupineManager.Builder().setAccessKey("vIbmhKnQy8cVGWRgbMvlvKLil10K6uAIc8m4wXdODCTubH3cau342A==").setKeywords(new Porcupine.BuiltInKeyword[]{Porcupine.BuiltInKeyword.PORCUPINE}).build(getApplicationContext(), (keywordIndex) -> { runOnUiThread(() -> { triggerStealthSOS("Voice Wake-Word Triggered"); Toast.makeText(this, "Voice SOS Active!", Toast.LENGTH_LONG).show(); }); });
            porcupineManager.start();
        } catch (PorcupineException e) {}
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(hardwareReceiver); if (porcupineManager != null) { porcupineManager.stop(); porcupineManager.delete(); } if (sensorManager != null) sensorManager.unregisterListener(this); stopRecording(); stopRedAlert(); stopNetworkSentinel(); } catch (Exception e) {}
    }
}
