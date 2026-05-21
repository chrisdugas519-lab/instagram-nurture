package com.yuxi.nurture;

// v4.6: 最简模式 - 只刷Reels，不点详情，先验证基础流程

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
    // v4.6 最简模式：打开IG → 点Reels → 无脑swipeUp刷，不点详情不检测关键词
    // 先验证基础流程正常，再逐步加回精准养号功能

    private void runNurtureSession() {
        log("=====");
        log("🚀 开始养号 - 最简Reels模式 v4.6");
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
        performGlobalAction(GLOBAL_ACTION_HOME);
        sleep(500);

        String preferredPackage = "com.instagram.android";
        Intent launch = getPackageManager().getLaunchIntentForPackage(preferredPackage);

        if (launch == null) {
            log("  ⚠️ 标准包名未找到，遍历已安装应用...");
            for (PackageInfo pkgInfo : getPackageManager().getInstalledPackages(0)) {
                String pkg = pkgInfo.packageName;
                if (pkg != null && pkg.toLowerCase().contains("instagram")) {
                    launch = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launch != null) { log("  ✅ 找到: " + pkg); break; }
                }
            }
        }
        if (launch == null) {
            launch = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/"));
        }
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        sleep(randInt(4000, 6000));

        String pkg = getRootInActiveWindow() != null ? getRootInActiveWindow().getPackageName().toString().toLowerCase() : "";
        if (pkg.contains("instagram")) { log("  ✅ Instagram 已打开"); return true; }
        log("  ⚠️ 可能未正确打开，当前包名: " + pkg);
        return false;
    }

    private void closeInstagram() {
        performGlobalAction(GLOBAL_ACTION_HOME);
        sleep(randInt(1000, 2000));
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

    private AccessibilityNodeInfo getRoot() { return getRootInActiveWindow(); }

    private AccessibilityNodeInfo findByDesc(String desc) {
        return findByDescRecursive(getRoot(), desc);
    }

    private AccessibilityNodeInfo findByDescRecursive(AccessibilityNodeInfo node, String desc) {
        if (node == null) return null;
        if (desc.equals(node.getContentDescription())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findByDescRecursive(node.getChild(i), desc);
            if (r != null) return r;
        }
        return null;
    }

    // 查找 text 包含指定字符串的节点（不区分大小写）
    private AccessibilityNodeInfo findNodeByTextContains(String keyword) {
        return findNodeByTextContainsRecursive(getRoot(), keyword.toLowerCase());
    }

    private AccessibilityNodeInfo findNodeByTextContainsRecursive(AccessibilityNodeInfo node, String keyword) {
        if (node == null) return null;
        CharSequence txt = node.getText();
        if (txt != null && txt.toString().toLowerCase().contains(keyword)) return node;
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().toLowerCase().contains(keyword)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findNodeByTextContainsRecursive(node.getChild(i), keyword);
            if (r != null) return r;
        }
        return null;
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        CharSequence txt = node.getText();
        if (txt != null && txt.length() > 0) sb.append(txt.toString()).append(" ");
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.length() > 0) sb.append(desc.toString()).append(" ");
        CharSequence hint = node.getHintText();
        if (hint != null && hint.length() > 0) sb.append(hint.toString()).append(" ");
        for (int i = 0; i < node.getChildCount(); i++) collectText(node.getChild(i), sb);
    }

    // 文字标准化：小写 + 去变音符号（Hermès → hermes）+ 繁转简
    private String normalizeText(String s) {
        if (s == null) return "";
        // Step 1: 繁体字转简体（常用高频词，重点覆盖奢侈品/皮具相关词汇）
        s = toSimplified(s);
        // Step 2: 小写
        s = s.toLowerCase();
        // Step 3: 去除变音符号（Hermès → hermes）
        s = s.replace("è","e").replace("é","e").replace("ê","e").replace("ë","e");
        s = s.replace("à","a").replace("á","a").replace("â","a").replace("ä","a");
        s = s.replace("ò","o").replace("ó","o").replace("ô","o").replace("ö","o");
        s = s.replace("ù","u").replace("ú","u").replace("û","u").replace("ü","u");
        s = s.replace("ì","i").replace("í","i").replace("î","i").replace("ï","i");
        return s;
    }

    // 繁简对照表 — 覆盖奢侈品/皮具/穿搭/台湾常用繁体字
    // 每对：[繁体, 简体]
    private static final String[][] TRAD_SIMP = {
        // 奢侈品品牌相关
        {"愛馬仕","爱马仕"},{"愛馬士","爱马仕"},{"馬仕","马仕"},
        {"賓士","宾士"},{"勞力士","劳力士"},{"香奈兒","香奈儿"},{"路易威登","路易威登"},
        {"迪奧","迪奥"},{"普拉達","普拉达"},{"古馳","古驰"},{"芙拉","芙拉"},
        // 皮具/包包相关
        {"皮革","皮革"},{"手工","手工"},{"皮包","皮包"},{"皮帶","皮带"},
        {"手提包","手提包"},{"肩背包","肩背包"},{"後背包","后背包"},
        {"錢包","钱包"},{"零錢包","零钱包"},{"卡夾","卡夹"},{"名片夾","名片夹"},
        {"縫製","缝制"},{"縫紉","缝纫"},{"裁剪","裁剪"},{"車縫","车缝"},
        {"皮料","皮料"},{"頭層皮","头层皮"},{"植鞣","植鞣"},{"壓花","压花"},
        // 穿搭/時尚相关
        {"時尚","时尚"},{"奢侈品","奢侈品"},{"精品","精品"},{"名牌","名牌"},
        {"限量版","限量版"},{"聯名","联名"},{"新款","新款"},{"開箱","开箱"},
        {"穿搭","穿搭"},{"搭配","搭配"},{"質感","质感"},{"頂級","顶级"},
        // 台湾常用高频字
        {"購買","购买"},{"買","买"},{"賣","卖"},{"賺","赚"},{"費","费"},
        {"價格","价格"},{"優惠","优惠"},{"折扣","折扣"},{"預購","预购"},
        {"訂製","订制"},{"客製化","客制化"},{"訂單","订单"},{"發貨","发货"},
        {"評論","评论"},{"留言","留言"},{"轉發","转发"},{"追蹤","追踪"},
        {"喜歡","喜欢"},{"粉絲","粉丝"},{"網紅","网红"},{"直播","直播"},
        // 常用繁体字
        {"個","个"},{"們","们"},{"來","来"},{"這","这"},{"說","说"},
        {"還","还"},{"對","对"},{"長","长"},{"發","发"},{"會","会"},
        {"從","从"},{"國","国"},{"學","学"},{"關","关"},{"問","问"},
        {"裡","里"},{"後","后"},{"傳","传"},{"圖","图"},{"歡","欢"},
    };

    private String toSimplified(String s) {
        for (String[] pair : TRAD_SIMP) {
            s = s.replace(pair[0], pair[1]);
        }
        return s;
    }

    // ============ 手势操作 ============

    private void swipeUp() {
        int dur = randInt(350, 600);
        int x = screenW / 2 + randInt(-25, 25);
        // ⚠️ sy 不能太低（y=75% 以下可能碰到底部导航栏），ey 不能太高
        int sy = (int)(screenH * 0.68) + randInt(-15, 15);
        int ey = (int)(screenH * 0.30) + randInt(-15, 15);
        log("  ⬆️ 上划 (" + dur + "ms)");
        swipeXY(x, sy, x, ey, dur);
    }

    private void smartTap(int x, int y, String label) {
        int ax = Math.max(0, x + randInt(-5, 5));
        int ay = Math.max(0, y + randInt(-5, 5));
        log("  🖱️ [" + label + "] (" + ax + ", " + ay + ")");
        clickXY(ax, ay);
    }

    private void clickXY(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        dispatchGesture(builder.build(), null, null);
        sleep(80);
    }

    private void swipeXY(int x1, int y1, int x2, int y2, int dur) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, dur));
        dispatchGesture(builder.build(), null, null);
        sleep(dur);
    }

    private void pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
        sleep(randInt(600, 1000));
    }

    // ============ 工具方法 ============

    private void interruptibleSleep(int minSec, int maxSec) {
        int totalMs = minSec * 1000;
        if (maxSec > minSec) totalMs += random.nextInt((maxSec - minSec) * 1000 + 1);
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < totalMs) {
            if (!isRunning) return;
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private int randInt(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private void log(String msg) {
        Log.d("NurtureService", msg);
        try {
            Intent intent = new Intent("com.yuxi.nurture.LOG");
            intent.putExtra("msg", msg);
            sendBroadcast(intent);
        } catch (Exception e) {}
    }
}
