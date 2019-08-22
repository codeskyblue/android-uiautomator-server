package com.github.uiautomator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.permission.FloatWindowManager;
import com.github.uiautomator.util.MemoryManager;
import com.github.uiautomator.util.Permissons4App;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.android.permission.FloatWindowManager;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private final String TAG = "ATXMainActivity";
    private final String ATX_AGENT_URL = "http://127.0.0.1:7912";

    private static final int UPDATE_STATUS_AGENT_RUNNING = 2;
    private static final int UPDATE_STATUS_AGENT_STOPPED = 3;
    private static final int UPDATE_STATUS_UIAUTOMATOR_RUNNING = 4;
    private static final int UPDATE_STATUS_UIAUTOMATOR_STOPPED = 5;

    private TextView tvInStorage;
    private TextView textViewIP;
    private TextView tvAgentStatus;

    private WindowManager windowManager = null;
    private boolean isWindowShown = false;
    private FloatView floatView;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.i(TAG, "service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "service disconnected");

            // restart service
            Intent intent = new Intent(MainActivity.this, Service.class);
            startService(intent);
        }
    };

    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            Log.d(TAG, "message what " + msg.what);
            switch (msg.what) {
                case UPDATE_STATUS_AGENT_STOPPED:
                    tvAgentStatus.setText("ATXAgent Stopped");
                    break;
                case UPDATE_STATUS_UIAUTOMATOR_RUNNING:
                    tvAgentStatus.setText("UiAutomator Running");
                    break;
                case UPDATE_STATUS_UIAUTOMATOR_STOPPED:
                    tvAgentStatus.setText("UiAutomator Stopped");
                    break;
                default:
                    break;
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        ScreenBroadcastReceiver mScreenReceiver = new ScreenBroadcastReceiver();
        registerReceiver(mScreenReceiver, filter);

        Intent serviceIntent = new Intent(this, Service.class);
        bindService(serviceIntent, connection, BIND_IMPORTANT | BIND_AUTO_CREATE);

        tvAgentStatus = findViewById(R.id.atx_agent_status);

        Button btnFinish = findViewById(R.id.btn_finish);
        btnFinish.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                stopService(new Intent(MainActivity.this, Service.class));
                finish();
            }
        });

        Button btnIdentify = findViewById(R.id.btn_identify);
        btnIdentify.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, IdentifyActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString("theme", "RED");
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });

        findViewById(R.id.accessibility).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        findViewById(R.id.development_settings).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            }
        });

        Intent intent = getIntent();
        boolean isHide = intent.getBooleanExtra("hide", false);
        if (isHide) {
            Log.i(TAG, "launch args hide:true, move to background");
            moveTaskToBack(true);
        }
        textViewIP = findViewById(R.id.ip_address);
        tvInStorage = findViewById(R.id.in_storage);

        String[] permisssions = new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS};
        Permissons4App.initPermissions(this, permisssions);
    }

    @Override
    protected void onStart() {
        super.onStart();

        checkUiautomatorStatus(null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Permissons4App.handleRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void showFloatWindow(View view) {
        boolean floatEnabled = FloatWindowManager.getInstance().checkFloatPermission(getApplicationContext());
        if (!floatEnabled) {
            Log.i(TAG, "float permission not checked");
            return;
        }
        if (floatView == null) {
            floatView = new FloatView(getApplicationContext());
        }
        floatView.show();
    }

    public void stopUiautomator(View view) {
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/uiautomator")
                .delete()
                .build();
        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Looper.prepare();
                Toast.makeText(MainActivity.this, "Uiautomator already stopped ", Toast.LENGTH_SHORT).show();
                Looper.loop();
                checkUiautomatorStatus(null);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                checkUiautomatorStatus(null);
            }
        });
    }

    public void startUiautomator(View view) {
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/uiautomator")
                .post(RequestBody.create(null, new byte[0]))
                .build();
        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Looper.prepare();
                Toast.makeText(MainActivity.this, "Uiautomator not starting", Toast.LENGTH_SHORT).show();
                Looper.loop();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Looper.prepare();
                Toast.makeText(MainActivity.this, "Uiautomator started", Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
        });
    }


    public void dismissFloatWindow(View view) {
        if (floatView != null) {
            Log.d(TAG, "remove floatView immediate");
            floatView.hide();
        }
    }

    public void atxAgentStopConfirm(View view) {
        AlertDialog.Builder localBuilder = new AlertDialog.Builder(this);
        localBuilder.setTitle("Confirm");
        localBuilder.setMessage("Sure to stop ATX_AGENT?");
        localBuilder.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                stopAtxAgent();
            }
        });
        localBuilder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        localBuilder.show();
    }

    private void stopAtxAgent() {
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/stop")
                .get()
                .build();
        new OkHttpClient().newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Looper.prepare();
                Toast.makeText(MainActivity.this, "atx-agent already stopped", Toast.LENGTH_SHORT).show();
                Looper.loop();
            }

            @Override
            public void onResponse(Call call, Response response) {
                Looper.prepare();
                Toast.makeText(MainActivity.this, "atx-agent stopped", Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
        });
    }

    public void checkAtxAgentStatus(View view) {
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/ping")
                .get()
                .build();
        new OkHttpClient().newCall(request).enqueue(new Callback() {
            Message msg = new Message();

            @Override
            public void onFailure(Call call, IOException e) {
                msg.what = UPDATE_STATUS_AGENT_STOPPED;
                handler.sendMessage(msg);
            }

            @Override
            public void onResponse(Call call, Response response) {
                msg.what = UPDATE_STATUS_AGENT_RUNNING;
                handler.sendMessage(msg);
            }
        });
    }

    public void checkUiautomatorStatus(View view) {
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/uiautomator")
                .get()
                .build();
        new OkHttpClient().newCall(request).enqueue(new Callback() {
            Message msg = new Message();

            @Override
            public void onFailure(Call call, IOException e) {
                msg.what = UPDATE_STATUS_AGENT_STOPPED;
                handler.sendMessage(msg);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String responseData = response.body().string();
                    JSONObject obj = new JSONObject(responseData);
                    boolean running = obj.getBoolean("running");
                    if (running) {
                        msg.what = UPDATE_STATUS_UIAUTOMATOR_RUNNING;
                    } else {
                        msg.what = UPDATE_STATUS_UIAUTOMATOR_STOPPED;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    msg.what = UPDATE_STATUS_UIAUTOMATOR_STOPPED;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                handler.sendMessage(msg);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();

        tvInStorage.setText(Formatter.formatFileSize(this, MemoryManager.getAvailableInternalMemorySize()) + "/" + Formatter.formatFileSize(this, MemoryManager.getTotalExternalMemorySize()));
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ip = wifiManager.getConnectionInfo().getIpAddress();
        String ipStr = (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
        textViewIP.setText(ipStr);
        textViewIP.setTextColor(Color.BLUE);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // must unbind service, otherwise it will leak memory
        // connection no need to set it to null
        unbindService(connection);
        Log.i(TAG, "unbind service");
    }
}
