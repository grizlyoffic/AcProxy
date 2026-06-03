package com.acproxy;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;

public class MainActivity extends AppCompatActivity {

    private LinearLayout lockScreen;
    private View loginScreen;
    private TextView lockStatus;
    private TextView lockMsg;
    private Button unlockBtn;
    private EditText passwordInput;
    private Button verifyBtn;
    private TextView errorText;
    private TextView createAccountBtn;
    
    private IFileService fileService;
    private boolean shizukuReady = false;
    private String jwtToken = "";
    private ExecutorService executor;
    private Handler handler;
    private SharedPreferences prefs;
    private ServiceConnection serviceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("acproxy", MODE_PRIVATE);
        
        initViews();
        handler.postDelayed(() -> checkShizuku(), 500);
    }

    private void initViews() {
        lockScreen = findViewById(R.id.lockScreen);
        loginScreen = findViewById(R.id.loginScreen);
        lockStatus = findViewById(R.id.lockStatus);
        lockMsg = findViewById(R.id.lockMsg);
        unlockBtn = findViewById(R.id.unlockBtn);
        passwordInput = findViewById(R.id.passwordInput);
        verifyBtn = findViewById(R.id.verifyBtn);
        errorText = findViewById(R.id.errorText);
        createAccountBtn = findViewById(R.id.createAccountBtn);
        
        unlockBtn.setOnClickListener(v -> checkShizuku());
        verifyBtn.setOnClickListener(v -> verifyPassword());
        createAccountBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/YourBot"));
            startActivity(i);
        });
        
        String saved = prefs.getString("saved_token", "");
        if (!saved.isEmpty()) passwordInput.setText(saved);
    }

    private void checkShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                updateLockUI("Shizuku Not Running", "Open Shizuku app and start service");
                return;
            }
            
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0);
                updateLockUI("Grant Permission", "Allow Shizuku permission popup");
                handler.postDelayed(() -> checkShizuku(), 1500);
                return;
            }
            
            // Unbind previous if exists
            if (serviceConnection != null) {
                try { Shizuku.unbindUserService(serviceConnection, false); } catch (Exception e) {}
            }
            
            serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    fileService = IFileService.Stub.asInterface(new ShizukuBinderWrapper(service));
                    shizukuReady = true;
                    handler.post(() -> unlockApp());
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    fileService = null;
                    shizukuReady = false;
                }
            };
            
            // Direct bind with ComponentName
            Shizuku.bindUserService(
                new ComponentName("com.acproxy", "com.acproxy.FileService"),
                serviceConnection
            );
            
        } catch (Exception e) {
            updateLockUI("Error", e.getMessage());
        }
    }

    private void updateLockUI(String status, String msg) {
        lockStatus.setText(status);
        lockStatus.setTextColor(status.equals("Error") ? 
            Color.parseColor("#FF5252") : Color.parseColor("#FFD740"));
        lockMsg.setText(msg);
    }

    private void unlockApp() {
        lockScreen.setVisibility(View.GONE);
        loginScreen.setVisibility(View.VISIBLE);
        loginScreen.setAlpha(0f);
        loginScreen.animate().alpha(1f).setDuration(500).start();
    }

    private void verifyPassword() {
        String pass = passwordInput.getText().toString().trim();
        if (pass.isEmpty()) {
            errorText.setText("Enter password!");
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        
        errorText.setVisibility(View.GONE);
        verifyBtn.setEnabled(false);
        verifyBtn.setText("Verifying...");
        
        executor.execute(() -> {
            try {
                String resp = ApiClient.get("https://host-ggclient.vercel.app/check?token=" + pass);
                
                handler.post(() -> {
                    verifyBtn.setEnabled(true);
                    verifyBtn.setText("VERIFY & LOGIN");
                    
                    if (resp != null) {
                        try {
                            JSONObject json = new JSONObject(resp);
                            String jt = json.optString("JwtToken", "");
                            
                            if (!jt.isEmpty()) {
                                jwtToken = jt;
                                prefs.edit().putString("saved_token", pass).apply();
                                prefs.edit().putString("jwt_token", jt).apply();
                                
                                Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                                intent.putExtra("jwt_token", jt);
                                startActivity(intent);
                                finish();
                            } else {
                                errorText.setText("Invalid password!");
                                errorText.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception e) {
                            errorText.setText("Error parsing response");
                            errorText.setVisibility(View.VISIBLE);
                        }
                    } else {
                        errorText.setText("Network error! Check internet.");
                        errorText.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    verifyBtn.setEnabled(true);
                    verifyBtn.setText("VERIFY & LOGIN");
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        if (serviceConnection != null) {
            try { Shizuku.unbindUserService(serviceConnection, false); } catch (Exception e) {}
        }
    }
}
