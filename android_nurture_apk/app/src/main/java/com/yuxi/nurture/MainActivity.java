package com.yuxi.nurture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    private RadioGroup rgMode;
    private EditText etKeywords, etViewMin, etViewMax;
    private SeekBar sbLikeProb;
    private TextView tvLikeProb, tvLog;
    private Button btnStart, btnStop;
    private ScrollView scrollLog;

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    public static MainActivity instance;
    private BroadcastReceiver logReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);

        initViews();
        loadConfig();
        registerLogReceiver();

        btnStart.setOnClickListener(v -> {
            if (!isAccessibilityServiceEnabled()) {
                showAccessibilityDialog();
                return;
            }
            saveConfig();
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            NurtureService.isRunning = true;
            appendLog("🚀 启动养号任务...");

            Intent intent = new Intent(this, NurtureService.class);
            intent.putExtra("mode", getSelectedMode());
            intent.putExtra("keywords", etKeywords.getText().toString());
            intent.putExtra("likeProb", sbLikeProb.getProgress());
            intent.putExtra("viewMin", Integer.parseInt(etViewMin.getText().toString()));
            intent.putExtra("viewMax", Integer.parseInt(etViewMax.getText().toString()));
            startService(intent);
        });

        btnStop.setOnClickListener(v -> {
            NurtureService.isRunning = false;
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            appendLog("⏹ 停止请求已发送");
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logReceiver != null) {
            unregisterReceiver(logReceiver);
            logReceiver = null;
        }
        instance = null;
    }

    private void registerLogReceiver() {
        logReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (NurtureService.ACTION_LOG.equals(intent.getAction())) {
                    String msg = intent.getStringExtra(NurtureService.EXTRA_LOG_MSG);
                    if (msg != null) {
                        appendLog(msg);
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(NurtureService.ACTION_LOG);
        registerReceiver(logReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    private void initViews() {
        rgMode = findViewById(R.id.rg_mode);
        etKeywords = findViewById(R.id.et_keywords);
        etViewMin = findViewById(R.id.et_view_min);
        etViewMax = findViewById(R.id.et_view_max);
        sbLikeProb = findViewById(R.id.sb_like_prob);
        tvLikeProb = findViewById(R.id.tv_like_prob);
        tvLog = findViewById(R.id.tv_log);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        scrollLog = findViewById(R.id.scroll_log);

        sbLikeProb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLikeProb.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private String getSelectedMode() {
        int id = rgMode.getCheckedRadioButtonId();
        if (id == R.id.rb_search) return "search";
        if (id == R.id.rb_reels) return "reels";
        if (id == R.id.rb_mixed) return "mixed";
        return "feed";
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + NurtureService.class.getName();
        int enabled = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        if (enabled != 1) return false;
        String list = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return list != null && list.contains(service);
    }

    private void showAccessibilityDialog() {
        new AlertDialog.Builder(this)
            .setTitle("需要无障碍权限")
            .setMessage("请在设置中开启「御玺工坊 - IG 养号」的无障碍服务权限，才能自动操作 Instagram。")
            .setPositiveButton("去开启", (d, w) -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void saveConfig() {
        getSharedPreferences("config", MODE_PRIVATE).edit()
            .putString("mode", getSelectedMode())
            .putString("keywords", etKeywords.getText().toString())
            .putInt("likeProb", sbLikeProb.getProgress())
            .putInt("viewMin", Integer.parseInt(etViewMin.getText().toString()))
            .putInt("viewMax", Integer.parseInt(etViewMax.getText().toString()))
            .apply();
    }

    private void loadConfig() {
        SharedPreferences sp = getSharedPreferences("config", MODE_PRIVATE);
        String mode = sp.getString("mode", "mixed");
        rgMode.check(R.id.rb_mixed);
        if ("feed".equals(mode)) rgMode.check(R.id.rb_feed);
        else if ("search".equals(mode)) rgMode.check(R.id.rb_search);
        else if ("reels".equals(mode)) rgMode.check(R.id.rb_reels);

        etKeywords.setText(sp.getString("keywords", "爱马仕包包,Hermès bag,luxury leather bag"));
        sbLikeProb.setProgress(sp.getInt("likeProb", 60));
        tvLikeProb.setText(sbLikeProb.getProgress() + "%");
        etViewMin.setText(String.valueOf(sp.getInt("viewMin", 5)));
        etViewMax.setText(String.valueOf(sp.getInt("viewMax", 15)));
    }

    public void appendLog(final String msg) {
        uiHandler.post(() -> {
            String text = tvLog.getText().toString();
            if (text.length() > 5000) text = text.substring(text.length() - 3000);
            tvLog.setText(text + "\n" + msg);
            scrollLog.fullScroll(ScrollView.FOCUS_DOWN);
        });
    }
}
