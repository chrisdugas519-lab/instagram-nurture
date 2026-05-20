package com.yuxi.nurture;

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

    // volatile 确保多线程可见性，停止按钮立即生效
    public static volatile boolean isRunning = false;

    private String[] keywords;
    private int likeProb;    // 0-100
    private int viewMin, viewMax;
    private int duration;    // 养号总时长（分钟）
    private int screenW, screenH;
    private Random random = new Random();
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String kwStr = intent.getStringExtra("keywords");
        keywords = kwStr != null ? kwStr.split(",") : new String[]{"爱马仕包包"};
        likeProb = intent.getIntExtra("likeProb", 60);
        viewMin = intent.getIntExtra("viewMin", 10);
        viewMax = intent.getIntExtra("viewMax", 25);
        duration = intent.getIntExtra("duration", 10);

        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;

        new Thread(this::runNurtureSession).start();
        return START_NOT_STICKY;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        isRunning = false;
    }

    // ============ 核心养号流程（纯 Reels） ============

    private void runNurtureSession() {
        log("=");
        log("🚀 开始养号 - 纯 Reels 模式");
        log("屏幕: " + screenW + "x" + screenH);
        log("关键词: " + String.join(", ", keywords));
        log("=");

        long startTime = System.currentTimeMillis();
        long endTime = startTime + duration * 60 * 1000L;

        try {
            log("⏱️ 养号时长: " + duration + " 分钟");

            if (!openInstagram()) {
                log("❌ Instagram 启动失败");
                return;
            }

            sleep(randInt(2000, 3000));
            navigateToReels();

            int reelIndex = 0;
            int adCount = 0;
            int relevantCount = 0;
            int skipCount = 0;
            while (System.currentTimeMillis() < endTime && isRunning) {
                reelIndex++;

                // 检测广告：遇到广告直接跳过，不观看、不点赞
                if (isReelsAd()) {
                    adCount++;
                    log("  🚫 广告 #" + adCount + "，跳过");
                    int swipeDur = randInt(400, 700);
                    int x = screenW / 2 + randInt(-30, 30);
                    int sy = (int)(screenH * 0.75);
                    int ey = (int)(screenH * 0.25);
                    swipeXY(x, sy, x, ey, swipeDur);
                    interruptibleSleep(2, 3);
                    continue;
                }

                // 检测内容相关性
                boolean relevant = isReelsRelevant();

                if (relevant) {
                    // 命中关键词 → 完整观看 + 高概率点赞
                    relevantCount++;
                    int watchDur = randInt(viewMin, viewMax);
                    log("  🎯 第 " + reelIndex + " 个 Reel - 命中 [" + String.join(",", matchedKeywords) + "] 观看 " + watchDur + "秒");
                    interruptibleSleep(watchDur, watchDur);
                    if (!isRunning) break;

                    // 高概率点赞（80%）
                    if (random.nextInt(100) < 80) {
                        likeReels();
                    }
                } else {
                    // 未命中关键词 → 只看 3 秒就跳过
                    skipCount++;
                    log("  ⏭️ 第 " + reelIndex + " 个 Reel - 不相关，3秒跳过");
                    interruptibleSleep(3, 3);
                }

                // 上划切换下一个 Reel（400-700ms）
                int swipeDur = randInt(400, 700);
                int x = screenW / 2 + randInt(-30, 30);
                int sy = (int)(screenH * 0.75);
                int ey = (int)(screenH * 0.25);
                log("  ⬆️ 上划切换 (" + swipeDur + "ms)");
                swipeXY(x, sy, x, ey, swipeDur);

                // 切换后等加载
                interruptibleSleep(2, 3);

                // 偶尔检测是否还在 Instagram
                if (reelIndex % 10 == 0 && !isInInstagram()) {
                    log("  ⚠️ 离开 IG，返回");
                    pressBack();
                    sleep(1000);
                    navigateToReels();
                }
            }

            pressBack();
            closeInstagram();
            log("✅ 养号完成！共 " + reelIndex + " 个 Reels");
            log("  🎯 命中关键词: " + relevantCount + " 个（完整观看+点赞）");
            log("  ⏭️ 不相关跳过: " + skipCount + " 个（3秒跳过）");
            log("  🚫 广告跳过: " + adCount + " 个");

        } catch (Exception e) {
            log("❌ 错误: " + e.getMessage());
            closeInstagram();
        } finally {
            isRunning = false;
            handler.post(() -> {
                if (MainActivity.instance != null) {
                    MainActivity.instance.runOnUiThread(() -> {});
                }
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

        if (launch != null) {
            log("  ✅ 找到标准包名: " + preferredPackage);
        } else {
            log("  ⚠️ 标准包名未找到，尝试遍历已安装应用...");
            List<PackageInfo> installedPackages = getPackageManager().getInstalledPackages(0);
            String foundPackage = null;

            for (PackageInfo pkgInfo : installedPackages) {
                String pkgName = pkgInfo.packageName;
                if (pkgName != null && pkgName.toLowerCase().contains("instagram")) {
                    foundPackage = pkgName;
                    log("  📦 找到 Instagram 相关包: " + foundPackage);
                    launch = getPackageManager().getLaunchIntentForPackage(foundPackage);
                    if (launch != null) {
                        log("  ✅ 使用找到的包名启动: " + foundPackage);
                        break;
                    }
                }
            }
        }

        if (launch == null) {
            log("  🌐 尝试用浏览器打开 Instagram...");
            launch = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/"));
        }

        if (launch == null) {
            log("  ❌ Instagram 启动失败");
            return false;
        }

        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        sleep(randInt(4000, 6000));

        String pkg = getRootInActiveWindow() != null ? getRootInActiveWindow().getPackageName().toString().toLowerCase() : "";
        if (pkg.contains("instagram")) {
            log("  ✅ Instagram 已打开");
            return true;
        }

        log("  ⚠️ Instagram 可能未正确打开，当前包名: " + pkg);
        return false;
    }

    private void closeInstagram() {
        log("🔴 关闭 Instagram...");
        performGlobalAction(GLOBAL_ACTION_HOME);
        sleep(randInt(1000, 2000));
    }

    private void navigateToReels() {
        log("🎬 导航到 Reels...");
        AccessibilityNodeInfo reelsIcon = findByDesc("Reels");
        if (reelsIcon != null) {
            smartClickNode(reelsIcon, "Reels 图标");
        } else {
            smartTap((int)(screenW * 0.30), (int)(screenH * 0.95), "Reels(兜底)");
        }
        sleep(randInt(2500, 3500));
    }

    private boolean isInInstagram() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;
        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
        return pkg.contains("instagram");
    }

    // ============ UI 查找 ============

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

    private AccessibilityNodeInfo findLikeButton() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return null;
        return findLikeButtonRecursive(root);
    }

    private AccessibilityNodeInfo findLikeButtonRecursive(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString();
            // 精确匹配 "Like" 或 "喜欢"（不是 "Unlike" / "已赞"）
            if (d.equals("Like") || d.equals("喜欢")) {
                return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findLikeButtonRecursive(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    // ============ Reels 内容相关性检测 ============
    // 读取当前 Reel 页面的所有文字（标题、描述、标签等），
    // 匹配用户设定的关键词。匹配到 = 目标内容，没匹配 = 不相关。

    private String[] matchedKeywords; // 记录匹配到的关键词，供日志显示

    private boolean isReelsRelevant() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;

        // 收集当前页面所有文字
        StringBuilder allText = new StringBuilder();
        collectText(root, allText);
        String pageText = allText.toString().toLowerCase();

        matchedKeywords = null;
        for (String kw : keywords) {
            String k = kw.trim().toLowerCase();
            if (k.isEmpty()) continue;
            if (pageText.contains(k)) {
                matchedKeywords = new String[]{kw.trim()};
                return true;
            }
        }
        return false;
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        // 读取 text 属性
        CharSequence txt = node.getText();
        if (txt != null && txt.length() > 0) {
            sb.append(txt.toString()).append(" ");
        }
        // 读取 contentDescription 属性（很多 IG 元素用这个）
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.length() > 0) {
            sb.append(desc.toString()).append(" ");
        }
        // 读取 hint 属性
        CharSequence hint = node.getHintText();
        if (hint != null && hint.length() > 0) {
            sb.append(hint.toString()).append(" ");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectText(node.getChild(i), sb);
        }
    }

    // ============ Reels 广告检测 ============

    private boolean isReelsAd() {
        // 检测 Reels 页面中的广告标识
        String[] adTexts = {"Sponsored", "sponsored", "推广", "赞助", "广告", "查看详情", "Learn more", "了解更多", "立即免费体验"};
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;
        return isReelsAdRecursive(root, adTexts);
    }

    private boolean isReelsAdRecursive(AccessibilityNodeInfo node, String[] adTexts) {
        if (node == null) return false;
        CharSequence txt = node.getText();
        CharSequence desc = node.getContentDescription();
        if (txt != null) {
            String s = txt.toString().toLowerCase();
            for (String kw : adTexts) {
                if (s.contains(kw.toLowerCase())) return true;
            }
        }
        if (desc != null) {
            String s = desc.toString().toLowerCase();
            for (String kw : adTexts) {
                if (s.contains(kw.toLowerCase())) return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (isReelsAdRecursive(node.getChild(i), adTexts)) return true;
        }
        return false;
    }

    // ============ 手势操作 ============

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

    private void clickXY(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 30));
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

    // ============ Reels 专用点赞 ============
    // 核心原理：Reels 点赞必须点击右侧的 Like 按钮（❤️ 图标），
    // 绝对不能双击屏幕（会触发暂停），也不能点击屏幕中间（也是暂停）。
    // 兜底方案：如果找不到 Like 按钮，尝试点击屏幕右侧 85%~92% 的区域
    // 但该区域不是点赞按钮就跳过，避免误触暂停。

    private void likeReels() {
        log("  ❤️ Reels 点赞...");
        sleep(300); // 短暂延迟，确保 Reel 已完全加载

        // 方案1：通过 Accessibility 找到 Like 按钮（最可靠）
        AccessibilityNodeInfo btn = findLikeButton();
        if (btn != null) {
            Rect rect = new Rect();
            btn.getBoundsInScreen(rect);
            // 确保按钮在屏幕右侧区域（Reels 爱心按钮在右边）
            int cx = rect.centerX();
            int cy = rect.centerY();
            log("  ❤️ 找到 Like 按钮 (" + cx + ", " + cy + ")，点击");
            clickXY(cx, cy);
            sleep(500);
            // 验证：再次查找，如果变成 "Unlike" 说明点赞成功
            AccessibilityNodeInfo check = findUnlikeButton();
            if (check != null) {
                log("  ✅ 点赞成功（已变为 Unlike）");
            } else {
                log("  ⚠️ 点击后未确认点赞成功");
            }
            return;
        }

        // 方案2：如果找不到 Like 按钮，尝试按坐标点击右侧爱心位置
        // Reels 爱心按钮通常在屏幕右侧 x=85%~90%, y=55%~65%
        int likeX = (int)(screenW * 0.88) + randInt(-15, 15);
        int likeY = (int)(screenH * 0.60) + randInt(-20, 20);
        log("  ❤️ 未找到 Like 按钮，尝试坐标点击 (" + likeX + ", " + likeY + ")");
        clickXY(likeX, likeY);
        sleep(500);
    }

    private AccessibilityNodeInfo findUnlikeButton() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return null;
        return findUnlikeButtonRecursive(root);
    }

    private AccessibilityNodeInfo findUnlikeButtonRecursive(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString();
            if (d.equals("Unlike") || d.equals("已赞") || d.equals("取消赞")) {
                return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findUnlikeButtonRecursive(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    // ============ 工具方法 ============

    private void interruptibleSleep(int minSec, int maxSec) {
        int totalMs = minSec * 1000;
        if (maxSec > minSec) {
            totalMs += random.nextInt((maxSec - minSec) * 1000 + 1);
        }
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < totalMs) {
            if (!isRunning) return;
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
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
