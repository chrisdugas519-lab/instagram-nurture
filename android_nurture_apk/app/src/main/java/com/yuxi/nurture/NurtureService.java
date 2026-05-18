package com.yuxi.nurture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NurtureService extends AccessibilityService {

    public static final String ACTION_LOG = "com.yuxi.nurture.LOG";
    public static final String EXTRA_LOG_MSG = "msg";
    public static boolean isRunning = false;

    private String mode;
    private String[] keywords;
    private int likeProb;
    private int viewMin, viewMax;
    private int screenW, screenH;
    private Random random = new Random();
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        try {
            mode = intent.getStringExtra("mode");
            if (mode == null) mode = "mixed";
            String kwStr = intent.getStringExtra("keywords");
            keywords = kwStr != null ? kwStr.split(",") : new String[]{"爱马仕包包"};
            likeProb = intent.getIntExtra("likeProb", 60);
            viewMin = intent.getIntExtra("viewMin", 5);
            viewMax = intent.getIntExtra("viewMax", 15);

            screenW = getResources().getDisplayMetrics().widthPixels;
            screenH = getResources().getDisplayMetrics().heightPixels;

            new Thread(this::runNurtureSession).start();
        } catch (Exception e) {
            log("❌ onStartCommand 异常: " + e.getMessage());
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        isRunning = false;
    }

    private void runNurtureSession() {
        log("=");
        log("🚀 开始养号 - 模式: " + mode);
        log("屏幕: " + screenW + "x" + screenH);
        log("关键词: " + String.join(", ", keywords));
        log("=");

        try {
            if (!openInstagram()) {
                log("❌ Instagram 启动失败，请确认已安装 Instagram");
                return;
            }

            if ("feed".equals(mode)) {
                scrollFeed(0);
                for (int i = 0; i < randInt(1, 3); i++) {
                    if (random.nextInt(100) < likeProb) likePost();
                    scrollFeed(1);
                }
            } else if ("search".equals(mode)) {
                searchBrowse();
            } else if ("reels".equals(mode)) {
                watchReels(0);
            } else {
                List<String> actions = new ArrayList<>();
                actions.add("feed");
                actions.add("search");
                actions.add("reels");
                java.util.Collections.shuffle(actions);
                int num = randInt(2, 3);
                for (int i = 0; i < num; i++) {
                    String action = actions.get(i);
                    log("--- " + action.toUpperCase() + " ---");
                    if ("feed".equals(action)) scrollFeed(0);
                    else if ("search".equals(action)) searchBrowse();
                    else watchReels(0);
                    randomSleep(3, 8);
                }
            }

            closeInstagram();
            log("✅ 养号完成！");

        } catch (Exception e) {
            log("❌ 错误: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            closeInstagram();
        } finally {
            isRunning = false;
            handler.post(() -> {
                if (MainActivity.instance != null) {
                    MainActivity.instance.runOnUiThread(() -> {
                    });
                }
            });
        }
    }

    private boolean openInstagram() {
        log("📱 打开 Instagram...");

        Intent launch = getPackageManager().getLaunchIntentForPackage("com.instagram.android");
        if (launch == null) {
            log("  ❌ 未找到 Instagram (包名: com.instagram.android)");
            return false;
        }

        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // 在主线程启动 Activity，避免后台线程限制
        handler.post(() -> {
            try {
                startActivity(launch);
                log("  🟢 startActivity 已调用");
            } catch (Exception e) {
                log("  ❌ startActivity 异常: " + e.getMessage());
            }
        });

        // 等待 Instagram 启动（增加等待时间）
        sleep(randInt(5000, 7000));

        // 循环检测是否已打开
        for (int i = 0; i < 20; i++) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                CharSequence pkgName = root.getPackageName();
                if (pkgName != null && pkgName.toString().contains("instagram")) {
                    log("  ✅ Instagram 已打开");
                    return true;
                }
            }
            log("  ⏳ 等待 Instagram 启动... (" + (i + 1) + "/20)");
            sleep(500);
        }

        log("  ⚠️ 未检测到 Instagram，但可能已打开，继续执行");
        return true;
    }

    private void closeInstagram() {
        log("🔴 关闭 Instagram...");
        performGlobalAction(GLOBAL_ACTION_HOME);
        sleep(randInt(1000, 2000));
    }

    private AccessibilityNodeInfo getRoot() {
        return getRootInActiveWindow();
    }

    private AccessibilityNodeInfo findByDesc(String desc) {
        return findByDesc(getRoot(), desc);
    }

    private AccessibilityNodeInfo findByDesc(AccessibilityNodeInfo root, String desc) {
        if (root == null) return null;
        if (desc.equals(root.getContentDescription())) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findByDesc(child, desc);
                if (result != null) return result;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findByText(String text) {
        return findByText(getRoot(), text);
    }

    private AccessibilityNodeInfo findByText(AccessibilityNodeInfo root, String text) {
        if (root == null) return null;
        CharSequence nodeText = root.getText();
        if (nodeText != null && nodeText.toString().contains(text)) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findByText(child, text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findLikeButton() {
        AccessibilityNodeInfo btn = findByDesc("Like");
        if (btn == null) btn = findByDesc("喜欢");
        return btn;
    }

    private boolean isAdPresent() {
        String[] adKws = {"赞助内容", "Sponsored", "广告", "推广"};
        for (String kw : adKws) {
            if (findByText(kw) != null) return true;
        }
        return false;
    }

    private boolean isInInstagram() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;
        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
        return pkg.contains("instagram");
    }

    private void smartTap(int x, int y, String label) {
        int ox = randInt(-5, 5);
        int oy = randInt(-5, 5);
        int ax = Math.max(0, x + ox);
        int ay = Math.max(0, y + oy);
        log("  🖱️ [" + label + "] (" + ax + ", " + ay + ")");
        clickXY(ax, ay);
    }

    private void smartClickNode(AccessibilityNodeInfo node, String label) {
        if (node == null) {
            log("  ⚠️ [" + label + "] 元素不存在");
            return;
        }
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        int cx = rect.left + randInt(5, Math.max(5, rect.width() - 10));
        int cy = rect.top + randInt(5, Math.max(5, rect.height() - 10));
        smartTap(cx, cy, label);
    }

    private void swipeUp(int dur) {
        int x = screenW / 2 + randInt(-50, 50);
        int sy = (int)(screenH * 0.85) + randInt(-30, 30);
        int ey = (int)(screenH * 0.15) + randInt(-30, 30);
        log("  📜 上滑信息流 (" + dur + "ms)");
        swipeXY(x, sy, x, ey, dur);
    }

    private void clickXY(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));
        dispatchGesture(builder.build(), null, null);
    }

    private void swipeXY(int x1, int y1, int x2, int y2, int dur) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, dur));
        dispatchGesture(builder.build(), null, null);
    }

    private void pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
        sleep(randInt(800, 1200));
    }

    private void scrollFeed(int times) {
        if (times == 0) times = randInt(3, 6);
        log("📜 滚动信息流 " + times + " 次...");

        for (int i = 0; i < times; i++) {
            if (isAdPresent()) {
                log("  ⚠️ 检测到广告，跳过");
                continue;
            }
            swipeUp(randInt(200, 400));
            randomSleep(viewMin, viewMax);

            if (!isInInstagram()) {
                log("  ⚠️ 离开 IG，返回");
                pressBack();
                sleep(1000);
            }
        }
    }

    private void likePost() {
        log("❤️ 尝试点赞...");
        AccessibilityNodeInfo btn = findLikeButton();
        if (btn != null) {
            log("  ✅ 找到点赞按钮");
            smartClickNode(btn, "点赞");
            sleep(100);
            smartClickNode(btn, "点赞第二次");
        } else {
            log("  ⚠️ 未找到，坐标兜底");
            smartTap(screenW/2 + randInt(-80, 80), (int)(screenH * 0.45) + randInt(-50, 50), "双击点赞");
            sleep(80);
            smartTap(screenW/2 + randInt(-80, 80), (int)(screenH * 0.45) + randInt(-50, 50), "双击点赞");
        }
    }

    private void searchBrowse() {
        log("🔍 搜索浏览...");

        AccessibilityNodeInfo searchIcon = findByDesc("Search");
        if (searchIcon != null) {
            smartClickNode(searchIcon, "搜索图标");
        } else {
            smartTap((int)(screenW * 0.70), (int)(screenH * 0.95), "搜索图标(兜底)");
        }
        sleep(randInt(1500, 2500));

        String kw = keywords[random.nextInt(keywords.length)].trim();
        log("  🔑 搜索: " + kw);

        AccessibilityNodeInfo searchBar = findNodeByClassName(getRoot(), "android.widget.EditText");
        if (searchBar != null) {
            smartClickNode(searchBar, "搜索栏");
        } else {
            smartTap(screenW/2, 120, "搜索栏(兜底)");
        }
        sleep(500);

        log("  ⚠️ 请手动输入或确保搜索栏已激活");
        sleep(2000);

        pressBack();
        sleep(500);
        AccessibilityNodeInfo searchBtn = findByText("搜索");
        if (searchBtn == null) searchBtn = findByText("Search");
        if (searchBtn != null) smartClickNode(searchBtn, "搜索确认");

        sleep(randInt(2000, 3000));
        scrollFeed(randInt(2, 4));
        pressBack();
    }

    private void watchReels(int count) {
        if (count == 0) count = randInt(2, 4);
        log("🎬 观看 " + count + " 个 Reels...");

        AccessibilityNodeInfo reelsIcon = findByDesc("Reels");
        if (reelsIcon != null) {
            smartClickNode(reelsIcon, "Reels 图标");
        } else {
            smartTap((int)(screenW * 0.30), (int)(screenH * 0.95), "Reels(兜底)");
        }
        sleep(randInt(2000, 3000));

        for (int i = 0; i < count; i++) {
            int dur = randInt(viewMin, viewMax);
            log("  📹 观看第 " + (i+1) + " 个 Reel " + dur + "秒...");
            sleep(dur * 1000);

            if (random.nextInt(100) < likeProb) {
                smartTap(screenW/2 + randInt(-50, 50), screenH/2 + randInt(-50, 50), "点赞");
                sleep(500);
            }

            if (i < count - 1) {
                swipeXY(screenW/2, (int)(screenH*0.7), screenW/2, (int)(screenH*0.25), randInt(300, 500));
                sleep(randInt(1000, 2000));
            }
        }
        pressBack();
    }

    private AccessibilityNodeInfo findNodeByClassName(AccessibilityNodeInfo root, String className) {
        if (root == null) return null;
        CharSequence cls = root.getClassName();
        if (cls != null && className.equals(cls.toString())) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findNodeByClassName(child, className);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void randomSleep(int minSec, int maxSec) {
        int dur = minSec * 1000 + random.nextInt((maxSec - minSec) * 1000 + 1);
        sleep(dur);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int randInt(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private void log(String msg) {
        Log.d("NurtureService", msg);
        Intent intent = new Intent(ACTION_LOG);
        intent.putExtra(EXTRA_LOG_MSG, msg);
        sendBroadcast(intent);
    }
}
