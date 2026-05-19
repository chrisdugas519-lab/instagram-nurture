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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NurtureService extends AccessibilityService {

    // volatile 确保多线程可见性，停止按钮立即生效
    public static volatile boolean isRunning = false;

    private String mode;
    private String[] keywords;
    private int likeProb;    // 0-100
    private int viewMin, viewMax;
    private int duration;    // 养号总时长（分钟）
    private int screenW, screenH;
    private Random random = new Random();
    private Handler handler = new Handler(Looper.getMainLooper());
    private Handler timeoutHandler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        mode = intent.getStringExtra("mode");
        if (mode == null) mode = "mixed";
        String kwStr = intent.getStringExtra("keywords");
        keywords = kwStr != null ? kwStr.split(",") : new String[]{"爱马仕包包"};
        likeProb = intent.getIntExtra("likeProb", 60);
        viewMin = intent.getIntExtra("viewMin", 10);
        viewMax = intent.getIntExtra("viewMax", 25);
        duration = intent.getIntExtra("duration", 10);

        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;

        // 在新线程执行养号
        new Thread(this::runNurtureSession).start();
        return START_NOT_STICKY;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要处理事件
    }

    @Override
    public void onInterrupt() {
        isRunning = false;
    }

    // ============ 核心养号流程 ============

    private void runNurtureSession() {
        log("=");
        log("🚀 开始养号 - 模式: " + mode);
        log("屏幕: " + screenW + "x" + screenH);
        log("关键词: " + String.join(", ", keywords));
        log("=");

        long startTime = System.currentTimeMillis();
        long totalMs = duration * 60 * 1000L;
        long endTime = startTime + totalMs;

        try {
            log("⏱️ 设定养号时长: " + duration + " 分钟");

            if (!openInstagram()) {
                log("❌ Instagram 启动失败");
                return;
            }

            // 先确保在首页
            navigateToHome();

            if ("feed".equals(mode)) {
                log("--- FEED 模式 ---");
                while (System.currentTimeMillis() < endTime && isRunning) {
                    if (skipIfAd()) continue;
                    scrollFeed(1);
                    if (random.nextInt(100) < likeProb) likePost();
                    interruptibleSleep(viewMin, viewMax);
                    if (!isInInstagram()) { log("  ⚠️ 离开 IG，返回"); pressBack(); sleep(1000); }
                }
            } else if ("reels".equals(mode)) {
                log("--- REELS 模式 ---");
                navigateToReels();
                while (System.currentTimeMillis() < endTime && isRunning) {
                    int reelDur = randInt(viewMin, viewMax);
                    log("  📹 观看 Reel " + reelDur + "秒...");
                    interruptibleSleep(reelDur, reelDur);
                    if (!isRunning) break;
                    if (random.nextInt(100) < likeProb) {
                        likeReels();
                    }
                    // Reels 切换：300-600ms 上划，比之前 150ms 更自然
                    swipeXY(screenW/2, (int)(screenH*0.75), screenW/2, (int)(screenH*0.25), randInt(400, 700));
                    interruptibleSleep(2, 3);
                }
                pressBack();
            } else {
                // mixed: 一半时间 feed，一半时间 reels
                long halfMs = totalMs / 2;

                // 阶段 1: Feed
                log("--- FEED 阶段（约 " + (duration/2) + " 分钟）---");
                while (System.currentTimeMillis() - startTime < halfMs && isRunning) {
                    if (skipIfAd()) continue;
                    scrollFeed(1);
                    if (random.nextInt(100) < likeProb) likePost();
                    interruptibleSleep(viewMin, viewMax);
                    if (!isInInstagram()) { log("  ⚠️ 离开 IG，返回"); pressBack(); sleep(1000); }
                }

                // 阶段 2: Reels
                if (isRunning) {
                    log("--- REELS 阶段（约 " + (duration - duration/2) + " 分钟）---");
                    navigateToReels();
                    while (System.currentTimeMillis() < endTime && isRunning) {
                        int reelDur = randInt(viewMin, viewMax);
                        log("  📹 观看 Reel " + reelDur + "秒...");
                        interruptibleSleep(reelDur, reelDur);
                        if (!isRunning) break;
                        if (random.nextInt(100) < likeProb) {
                            likeReels();
                        }
                        // Reels 切换：快速上划（150ms），慢了会暂停
                        swipeXY(screenW/2, (int)(screenH*0.75), screenW/2, (int)(screenH*0.25), 150);
                        interruptibleSleep(2, 3);
                    }
                    pressBack();
                }
            }

            closeInstagram();
            log("✅ 养号完成！");

        } catch (Exception e) {
            log("❌ 错误: " + e.getMessage());
            closeInstagram();
        } finally {
            isRunning = false;
            handler.post(() -> {
                if (MainActivity.instance != null) {
                    MainActivity.instance.runOnUiThread(() -> {
                        // UI 更新交给 MainActivity
                    });
                }
            });
        }
    }

    // ============ Instagram 基础操作 ============

    private boolean openInstagram() {
        log("📱 打开 Instagram...");
        // 强制关闭后打开
        performGlobalAction(GLOBAL_ACTION_HOME);
        sleep(500);

        // 首选：尝试标准包名
        String preferredPackage = "com.instagram.android";
        Intent launch = getPackageManager().getLaunchIntentForPackage(preferredPackage);

        if (launch != null) {
            log("  ✅ 找到标准包名: " + preferredPackage);
        } else {
            log("  ⚠️ 标准包名未找到，尝试遍历已安装应用...");

            // 遍历所有已安装应用，查找包含 "instagram" 的包名
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

            if (launch == null) {
                log("  ⚠️ 未找到可启动的 Instagram 包");
            }
        }

        // Fallback：如果 PackageManager 方式全部失败，尝试用浏览器打开
        if (launch == null) {
            log("  🌐 尝试用浏览器打开 Instagram...");
            launch = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/"));
            // 不检查 launch 是否为 null，因为 ACTION_VIEW 总是可以创建
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

    // 导航到首页（确保不在探索/推荐页）
    private void navigateToHome() {
        log("🏠 导航到首页...");
        // 尝试找 Home 图标（底部导航第一个）
        AccessibilityNodeInfo home = findByDesc("Home");
        if (home == null) home = findByDesc("首页");
        if (home == null) {
            // 兜底：点击底部导航最左侧位置
            smartTap((int)(screenW * 0.10), (int)(screenH * 0.95), "首页兜底");
        } else {
            smartClickNode(home, "首页");
        }
        sleep(randInt(2000, 3000));
    }

    // ============ UI 查找（AccessibilityNodeInfo） ============

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
        // Instagram 的点赞按钮
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

    // 检测是否在首页信息流（排除 Reels / 个人主页 / 搜索页）
    private boolean isInFeed() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;
        String pkg = root.getPackageName() != null ? root.getPackageName().toString().toLowerCase() : "";
        if (!pkg.contains("instagram")) return false;
        // 通过 UI 元素判断：Feed 页面有底部导航栏和 "Home" 按钮
        AccessibilityNodeInfo home = findByDesc("Home");
        if (home == null) home = findByDesc("首页");
        return home != null;
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

    private void swipeUp(int dur) {
        int x = screenW / 2 + randInt(-40, 40);
        // 从 80% 划到 20%，距离适中；速度 800-1400ms 模拟人类自然滑动
        int sy = (int)(screenH * 0.80) + randInt(-20, 20);
        int ey = (int)(screenH * 0.20) + randInt(-20, 20);
        log("  📜 上滑信息流 (" + dur + "ms)");
        swipeXY(x, sy, x, ey, dur);
    }

    private void clickXY(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        // 修复：单次点击只做 30ms 轻触，不要当成长按
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

    // ============ 养号动作 ============

    private void scrollFeed(int times) {
        if (times == 0) times = randInt(3, 6);
        log("📜 滚动信息流 " + times + " 次...");

        for (int i = 0; i < times; i++) {
            if (!isRunning) return;

            // 检测到广告：快速划过，不要在广告区域起始划动（会触发点击）
            if (isAdPresent()) {
                log("  ⚠️ 检测到广告，快速跳过...");
                swipeUp(500);
                interruptibleSleep(2, 3);
                if (!isInFeed()) {
                    log("  ⚠️ 误触广告，返回信息流");
                    pressBack();
                    sleep(1500);
                }
                continue;
            }

            // 普通帖子：人类自然滑动速度 800-1400ms
            swipeUp(randInt(800, 1400));
            log("  ⏳ 等待内容加载...");
            sleep(800);  // 给页面加载时间
            if (!isRunning) return;

            // 误触检测：检查是否离开了信息流（进了帖子详情/Reels/广告页）
            if (!isInFeed()) {
                log("  ⚠️ 离开信息流，自动返回");
                pressBack();
                sleep(1500);
            }

            interruptibleSleep(viewMin > 2 ? 2 : 1, 3);
        }
    }

    private boolean skipIfAd() {
        if (isAdPresent()) {
            log("  ⚠️ 检测到广告，快速跳过...");
            swipeUp(200);
            interruptibleSleep(2, 3);
            if (!isInFeed()) {
                log("  ⚠️ 误触广告，返回信息流");
                pressBack();
                sleep(1500);
            }
            return true;
        }
        return false;
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

    // 可中断的睡眠：每 1 秒检查一次 isRunning
    private void interruptibleSleep(int minSec, int maxSec) {
        int totalMs = minSec * 1000;
        if (maxSec > minSec) {
            totalMs += random.nextInt((maxSec - minSec) * 1000 + 1);
        }
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < totalMs) {
            if (!isRunning) return;
            sleep(1000); // 1秒检查一次
        }
    }

    private void likePost() {
        // 先确认还在信息流，不在就跳过（避免在 Reels/广告页乱点）
        if (!isInFeed()) {
            log("  ⚠️ 不在信息流，跳过点赞");
            return;
        }
        log("❤️ 尝试点赞...");
        AccessibilityNodeInfo btn = findLikeButton();
        if (btn != null) {
            log("  ✅ 找到点赞按钮");
            smartClickNode(btn, "点赞");
            sleep(500);
        } else {
            // 兜底：点击帖子图片中央区域双击点赞
            log("  ⚠️ 未找到点赞按钮，坐标兜底");
            int likeX = screenW / 2 + randInt(-50, 50);
            int likeY = (int)(screenH * 0.42) + randInt(-40, 40);
            // 双击同一坐标：两次 tap 间隔 25ms，总耗时 ~110ms（IG 可识别为双击）
            smartTap(likeX, likeY, "双击第1下");
            sleep(25);
            smartTap(likeX, likeY, "双击第2下");
            sleep(500);
        }
        // 点赞后检测是否误触离开信息流
        if (!isInFeed()) {
            log("  ⚠️ 点赞后离开信息流，自动返回");
            pressBack();
            sleep(1000);
        }
    }

    // Reels 专用点赞：点右侧心形按钮（约 x=90%, y=65%），绝对不能点中央
    private void likeReels() {
        log("❤️ Reels 点赞...");
        // 先尝试找 Like 按钮
        AccessibilityNodeInfo btn = findLikeButton();
        if (btn != null) {
            smartClickNode(btn, "Reels点赞");
        } else {
            // 兜底：Reels 右侧按钮组 - 心形在右边约 x=88%, y=60%
            int likeX = (int)(screenW * 0.88) + randInt(-10, 10);
            int likeY = (int)(screenH * 0.60) + randInt(-20, 20);
            smartTap(likeX, likeY, "Reels点赞兜底");
        }
        interruptibleSleep(1, 1);
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
            if (!isRunning) return;
            int dur = randInt(viewMin, viewMax);
            log("  📹 观看第 " + (i+1) + " 个 Reel " + dur + "秒...");
            interruptibleSleep(dur, dur);
            if (!isRunning) return;

            if (random.nextInt(100) < likeProb) {
                smartTap(screenW/2 + randInt(-50, 50), screenH/2 + randInt(-50, 50), "点赞");
                interruptibleSleep(1, 1);
            }

            if (i < count - 1) {
                swipeXY(screenW/2, (int)(screenH*0.7), screenW/2, (int)(screenH*0.25), randInt(400, 700));
                interruptibleSleep(2, 3); // 加载下一个 Reel 的缓冲
            }
        }
        pressBack();
    }

    // ============ 工具方法 ============

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
        // 发送广播，让 MainActivity 显示日志
        try {
            android.content.Intent intent = new android.content.Intent("com.yuxi.nurture.LOG");
            intent.putExtra("msg", msg);
            sendBroadcast(intent);
        } catch (Exception e) {
            // ignore
        }
    }
}
