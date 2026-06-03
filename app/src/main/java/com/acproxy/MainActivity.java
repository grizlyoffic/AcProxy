package com.acproxy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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
    
    private String jwtToken = "";
    private ExecutorService executor;
    private Handler handler;
    private SharedPreferences prefs;
    private boolean isDestroyed = false;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = new Shizuku.OnBinderReceivedListener() {
        @Override
        public void onBinderReceived() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    checkAndUnlock();
                }
            });
        }
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = new Shizuku.OnBinderDeadListener() {
        @Override
        public void onBinderDead() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showLockScreen("Shizuku Disconnected", "Service stopped");
                }
            });
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = 
        new Shizuku.OnRequestPermissionResultListener() {
            @Override
            public void onRequestPermissionResult(int requestCode, int grantResult) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            checkAndUnlock();
                        } else {
                            showLockScreen("Permission Denied", "Grant Shizuku permission to continue");
                        }
                    }
                });
            }
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("acproxy", MODE_PRIVATE);
        
        initViews();
        setupShizukuListeners();
        handler.post(new Runnable() {
            @Override
            public void run() {
                checkAndUnlock();
            }
        });
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
        
        unlockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Shizuku.requestPermission(0);
                    Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (intent != null) startActivity(intent);
                    else Toast.makeText(MainActivity.this, "Shizuku app not installed!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Open Shizuku app manually", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        verifyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                verifyPassword();
            }
        });
        
        createAccountBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/YourBot"));
                startActivity(i);
            }
        });
        
        String savedJwt = prefs.getString("jwt_token", "");
        String savedPass = prefs.getString("saved_token", "");
        if (!savedPass.isEmpty()) passwordInput.setText(savedPass);
        
        if (!savedJwt.isEmpty() && Shizuku.pingBinder() && 
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            jwtToken = savedJwt;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    goToDashboard();
                }
            }, 500);
        }
    }

    private void setupShizukuListeners() {
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
    }

    private void checkAndUnlock() {
        if (isDestroyed) return;
        
        try {
            boolean binderAlive = Shizuku.pingBinder();
            
            if (!binderAlive) {
                showLockScreen("Shizuku Not Running", "Open Shizuku app and start the service");
                return;
            }
            
            int permission = Shizuku.checkSelfPermission();
            
            if (permission == PackageManager.PERMISSION_GRANTED) {
                lockScreen.setVisibility(View.GONE);
                loginScreen.setVisibility(View.VISIBLE);
                loginScreen.setAlpha(0f);
                loginScreen.animate().alpha(1f).setDuration(400).start();
                
                String savedJwt = prefs.getString("jwt_token", "");
                if (!savedJwt.isEmpty()) {
                    jwtToken = savedJwt;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToDashboard();
                        }
                    }, 300);
                }
            } else {
                Shizuku.requestPermission(0);
                showLockScreen("Permission Required", "Tap button below to grant Shizuku permission");
            }
        } catch (Exception e) {
            showLockScreen("Error", "Shizuku check failed");
        }
    }

    private void showLockScreen(String status, String msg) {
        lockScreen.setVisibility(View.VISIBLE);
        loginScreen.setVisibility(View.GONE);
        lockStatus.setText(status);
        lockMsg.setText(msg);
        
        if (status.contains("Permission") || status.contains("Not Running")) {
            lockStatus.setTextColor(Color.parseColor("#FFD740"));
            unlockBtn.setVisibility(View.VISIBLE);
        } else {
            lockStatus.setTextColor(Color.parseColor("#FF5252"));
        }
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
        
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String resp = ApiClient.get("https://host-ggclient.vercel.app/check?token=" + pass);
                    
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
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
                                        goToDashboard();
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
                        }
                    });
                } catch (Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            verifyBtn.setEnabled(true);
                            verifyBtn.setText("VERIFY & LOGIN");
                        }
                    });
                }
            }
        });
    }

    private void goToDashboard() {
        Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
        intent.putExtra("jwt_token", jwtToken);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isDestroyed = false;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAndUnlock();
            }
        }, 300);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isDestroyed = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
    }
}
