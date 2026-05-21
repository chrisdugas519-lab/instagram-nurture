package com.yuxi.nurture;

// v4.7: 最简模式 - 只刷Reels，不点详情，先验证基础流程

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.pm.PackageInfo;

import java.util.List;
import java.util.Random;

public class NurtureService extends AccessibilityService {

    public static volatile boolean isRunning = false;

    private String[] keywords;
    private int likeProb;
    private int viewMin, viewMax;
    private int duration;
    private int screenW, screenH;
    private Random random = new Random();
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String kwStr = intent.getStringExtra("keywords");
        keywords = kwStr != null ? kwStr.split(",") : new String[]{"爱马仕", "Hermes", "luxury bag"};
        likeProb = intent.getIntExtra("likeProb", 60);
        viewMin = intent.getIntExtra("viewMin", 8);
        viewMax = intent.getIntExtra("viewMax", 20);
        duration = intent.getIntExtra("duration", 10);
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;
        new Thread(this::runNurtureSession).start();
        return START_NOT_STICKY;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { isRunning = false; }

    // ============ 核心养号流程 ============
    // v4.7 最简模式：打开IG → 点Reels → 无脑swipeUp刷，不点详情不检测关键词
    // 先验证基础流程正常，再逐步加回精准养号功能

    private void runNurtureSession() {
        log("=====");
        log("🚀 开始养号 - 最简Reels模式 v4.7");
        log("屏幕: " + screenW + "x" + screenH);
        log("策略: 只刷Reels，不点详情，不检测关键词");
        log("=====");

        long endTime = System.currentTimeMillis() + duration * 60 * 1000L;

        try {
            if (!openInstagram()) { log("❌ 打开 IG 失败"); return; }
            sleep(randInt(3000, 5000));

            // 进入Reels
            navigateToReels();
            sleep(randInt(2000, 3000));

            int reelIndex = 0;

            while (System.currentTimeMillis() < endTime && isRunning) {
                reelIndex++;
                log("--- Reel #" + reelIndex + " ---");

                // 简单看几秒然后划走
                int watchTime = randInt(3, 6);
                log("  👀 观看 " + watchTime + " 秒...");
                interruptibleSleep(watchTime, watchTime);
                if (!isRunning) break;

                // 上划下一个
                swipeUp();
                sleep(randInt(1200, 2000));

                // 每10轮检查一次是否还在IG
                if (reelIndex % 10 == 0 && !isInInstagram()) {
                    log("  ⚠️ 离开 IG，尝试返回");
                    pressBack();
                    sleep(1000);
                    navigateToReels();
                }
            }

            pressBack();
            closeInstagram();
            log("=====");
            log("✅ 养号完成！共刷 " + reelIndex + " 个 Reels");
            log("=====");

        } catch (Exception e) {
            log("❌ 错误: " + e.getMessage());
            closeInstagram();
        } finally {
            isRunning = false;
            handler.post(() -> {
                if (MainActivity.instance != null) MainActivity.instance.runOnUiThread(() -> {});
            });
        }
    }

    // ============ Instagram 基础操作 ============

    private boolean openInstagram() {
        log("📱 打开 Instagram...");
        // 先尝试从后台唤起
        AccessibilityNodeInfo root = getRoot();
        if (root != null) {
            String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
            if (pkg.contains("instagram")) {
                log("  ✅ IG 已在前台");
                return true;
            }
        }
        // 用 URI 唤起
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("instagram://app"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            intent = getPackageManager().getLaunchIntentForPackage("com.instagram.android");
            if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); }
        }
        sleep(4000);
        root = getRoot();
        if (root != null) {
            String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
            if (pkg.contains("instagram")) { log("  ✅ IG 已打开"); return true; }
        }
        return false;
    }

    private void closeInstagram() {
        log("📱 关闭 Instagram");
        pressBack(); pressBack(); pressBack();
        sleep(500);
    }

    private void navigateToReels() {
        log("🎬 导航到 Reels...");

        // 方案1：findByDesc("Reels") — 最可靠
        AccessibilityNodeInfo reelsIcon = findByDesc("Reels");
        if (reelsIcon != null) {
            log("  🎬 通过 findByDesc('Reels') 点击");
            reelsIcon.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            sleep(randInt(2500, 3500));
            log("  ✅ 已点击 Reels");
            return;
        }

        // 方案2：坐标兜底 — Reels tab 在底部导航栏第 2 个位置（约 x=20%）
        int rx = (int)(screenW * 0.20) + randInt(-10, 10);
        int ry = (int)(screenH * 0.955) + randInt(-5, 5);
        log("  🎬 坐标点击 Reels tab (" + rx + ", " + ry + ")");
        clickXY(rx, ry);
        sleep(randInt(2500, 3500));
        log("  ✅ 坐标点击完成");
    }

    private boolean isInInstagram() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;
        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
        return pkg.contains("instagram");
    }

    // ============ UI 查找工具 ============

    private AccessibilityNodeInfo getRoot() {
        return getRootInActiveWindow();
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private void interruptibleSleep(int minSec, int maxSec) {
        long totalMs = randInt(minSec, maxSec) * 1000L;
        long elapsed = 0;
        while (elapsed < totalMs && isRunning) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            elapsed += 200;
        }
    }

    private int randInt(int min, int max) {
        if (min > max) { int t = min; min = max; max = t; }
        return random.nextInt(max - min + 1) + min;
    }

    // ---------- 手势点击 ----------
    private void clickXY(int x, int y) {
        try {
            Path p = new Path();
            p.moveTo(x, y);
            GestureDescription gd = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 1))
                .build();
            dispatchGesture(gd, null, null);
        } catch (Exception ignored) {}
    }

    private void swipeUp() {
        int startX = screenW / 2 + randInt(-40, 40);
        int startY = (int)(screenH * 0.72) + randInt(-30, 30);
        int endY   = (int)(screenH * 0.25) + randInt(-20, 20);
        try {
            Path p = new Path();
            p.moveTo(startX, startY);
            p.lineTo(startX + randInt(-15, 15), endY);
            GestureDescription gd = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 380 + randInt(0, 120)))
                .build();
            dispatchGesture(gd, null, null);
        } catch (Exception ignored) {}
    }

    private void pressBack() {
        try {
            performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (Exception ignored) {}
    }

    // ---------- 节点查找 ----------
    private AccessibilityNodeInfo findByDesc(String target) {
        return findByDescRecursive(getRoot(), target.toLowerCase());
    }

    private AccessibilityNodeInfo findByDescRecursive(AccessibilityNodeInfo node, String target) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().toLowerCase().contains(target)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findByDescRecursive(node.getChild(i), target);
            if (r != null) return r;
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeByTextContains(String keyword) {
        return findNodeByTextContainsRecursive(getRoot(), keyword.toLowerCase());
    }

    private AccessibilityNodeInfo findNodeByTextContainsRecursive(AccessibilityNodeInfo node, String kw) {
        if (node == null) return null;
        CharSequence txt = node.getText();
        if (txt != null && txt.toString().toLowerCase().contains(kw)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findNodeByTextContainsRecursive(node.getChild(i), kw);
            if (r != null) return r;
        }
        return null;
    }

    // ---------- 文字收集（调试用） ----------
    private void collectText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        CharSequence t = node.getText();
        if (t != null) { sb.append(t.toString()).append("\n"); }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectText(node.getChild(i), sb);
        }
    }

    // ---------- 日志 ----------
    private void log(String msg) {
        Log.i("YuxiNurture", msg);
    }
}
