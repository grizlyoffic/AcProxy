package com.acproxy;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.UserServiceArgs;

public class DashboardActivity extends AppCompatActivity {

    private TextView shizukuStatus;
    private TextView deviceInfo;
    private TextView tokenPreview;
    private TextView proxyStatusText;
    private View statusDot;
    private Button startStopBtn;
    private Button logoutBtn;
    
    private IFileService fileService;
    private String jwtToken = "";
    private boolean isActive = false;
    private boolean shizukuReady = false;
    
    private ExecutorService executor;
    private Handler handler;
    private SharedPreferences prefs;
    
    private static final String CONFIG_PATH = "/storage/emulated/0/Android/data/com.dts.freefireth/files/localconfig.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("acproxy", MODE_PRIVATE);
        
        jwtToken = getIntent().getStringExtra("jwt_token");
        if (jwtToken == null) jwtToken = prefs.getString("jwt_token", "");
        
        initViews();
        bindShizukuService();
    }

    private void initViews() {
        shizukuStatus = findViewById(R.id.shizukuStatus);
        deviceInfo = findViewById(R.id.deviceInfo);
        tokenPreview = findViewById(R.id.tokenPreview);
        proxyStatusText = findViewById(R.id.proxyStatusText);
        statusDot = findViewById(R.id.statusDot);
        startStopBtn = findViewById(R.id.startStopBtn);
        logoutBtn = findViewById(R.id.logoutBtn);
        
        deviceInfo.setText("Model: " + Build.MODEL + "\n" +
                          "Manufacturer: " + Build.MANUFACTURER + "\n" +
                          "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                          "Brand: " + Build.BRAND);
        
        String preview = jwtToken.length() > 40 ? jwtToken.substring(0, 40) + "..." : jwtToken;
        tokenPreview.setText(preview);
        
        startStopBtn.setOnClickListener(v -> {
            if (!shizukuReady) {
                Toast.makeText(this, "Shizuku not connected!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isActive) stopProxy();
            else startProxy();
        });
        
        logoutBtn.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            if (isActive) stopProxy();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void bindShizukuService() {
        try {
            // FIXED: Use UserServiceArgs instead of Intent
            UserServiceArgs args = new UserServiceArgs(
                new ComponentName("com.acproxy", "com.acproxy.FileService")
            );
            
            Shizuku.bindUserService(args, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    fileService = IFileService.Stub.asInterface(new ShizukuBinderWrapper(service));
                    shizukuReady = true;
                    shizukuStatus.setText("Shizuku: ✅ Connected");
                    shizukuStatus.setTextColor(Color.parseColor("#00E676"));
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    fileService = null;
                    shizukuReady = false;
                    shizukuStatus.setText("Shizuku: ❌ Disconnected");
                    shizukuStatus.setTextColor(Color.parseColor("#FF5252"));
                }
            });
        } catch (Exception e) {
            shizukuStatus.setText("Shizuku: ❌ Error");
            shizukuStatus.setTextColor(Color.parseColor("#FF5252"));
        }
    }

    private void startProxy() {
        if (fileService == null) {
            Toast.makeText(this, "Shizuku not ready!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        startStopBtn.setEnabled(false);
        startStopBtn.setText("Creating...");
        
        executor.execute(() -> {
            try {
                String serverUrl = "http://203.175.125.151:10136/" + jwtToken + "/";
                
                JSONObject config = new JSONObject();
                config.put("verAddr", "https://version-ggbluellama.vercel.app/live/");
                config.put("serverLoginUrl", serverUrl);
                
                boolean ok = fileService.createFile(CONFIG_PATH, config.toString(2));
                
                handler.post(() -> {
                    startStopBtn.setEnabled(true);
                    if (ok) {
                        isActive = true;
                        startStopBtn.setText("⏹ STOP PROXY");
                        startStopBtn.setBackgroundResource(R.drawable.button_red);
                        proxyStatusText.setText("Active - Config Injected");
                        proxyStatusText.setTextColor(Color.parseColor("#00E676"));
                        statusDot.setBackgroundResource(R.drawable.dot_green);
                        Toast.makeText(this, "✅ Config Created!", Toast.LENGTH_SHORT).show();
                    } else {
                        startStopBtn.setText("▶ START PROXY");
                        Toast.makeText(this, "❌ Failed! Check permissions.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    startStopBtn.setEnabled(true);
                    startStopBtn.setText("▶ START PROXY");
                });
            }
        });
    }

    private void stopProxy() {
        if (fileService == null) return;
        
        startStopBtn.setEnabled(false);
        
        executor.execute(() -> {
            try {
                boolean ok = fileService.deleteFile(CONFIG_PATH);
                handler.post(() -> {
                    startStopBtn.setEnabled(true);
                    if (ok) {
                        isActive = false;
                        startStopBtn.setText("▶ START PROXY");
                        startStopBtn.setBackgroundResource(R.drawable.button_green);
                        proxyStatusText.setText("Not Active");
                        proxyStatusText.setTextColor(Color.parseColor("#8892B0"));
                        statusDot.setBackgroundResource(R.drawable.dot_red);
                        Toast.makeText(this, "Config Deleted!", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> startStopBtn.setEnabled(true));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
