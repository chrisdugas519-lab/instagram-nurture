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
    // 策略：广告直接划走 → 正常视频先看几秒 → 点开底部描述栏
    //       → 详情里检测关键词 → 命中就点赞 → 关掉返回 → 继续下一个

    private void runNurtureSession() {
        log("=====");
        log("🚀 开始养号 - 精准 Reels 模式");
        log("屏幕: " + screenW + "x" + screenH);
        log("关键词: " + String.join(", ", keywords));
        log("策略: 广告划走 / 正常视频看几秒→点开详情→关键词点赞");
        log("=====");

        long endTime = System.currentTimeMillis() + duration * 60 * 1000L;

        try {
            if (!openInstagram()) { log("❌ 打开 IG 失败"); return; }
            sleep(randInt(2000, 3000));
            navigateToReels();

            int reelIndex = 0, adCount = 0, likedCount = 0, watchedCount = 0;

            while (System.currentTimeMillis() < endTime && isRunning) {
                reelIndex++;
                log("--- Reel #" + reelIndex + " ---");

                // 1. 先检测广告，是广告直接划走
                if (isReelsAd()) {
                    adCount++;
                    log("  🚫 广告，直接划走");
                    swipeUp();
                    sleep(randInt(1500, 2500));
                    continue;
                }

                // 2. 正常视频：先看几秒（3~5秒），让 IG 算法记录观看行为
                int quickWatch = randInt(3, 5);
                log("  👀 先观看 " + quickWatch + " 秒...");
                interruptibleSleep(quickWatch, quickWatch);
                if (!isRunning) break;

                // 3. 点开底部描述栏（标题那一行）
                boolean opened = openReelDescription();
                if (opened) {
                    // 4. 在详情页检测关键词
                    sleep(800); // 等详情内容渲染
                    String matchedKw = detectKeywordInDetail();
                    if (matchedKw != null) {
                        // 5. 命中关键词：点赞（按用户设定概率）
                        log("  🎯 命中关键词 [" + matchedKw + "]");
                        if (random.nextInt(100) < likeProb) {
                            pressBack(); // 先关掉详情
                            sleep(500);
                            likeReels();
                            likedCount++;
                        } else {
                            pressBack();
                            sleep(500);
                        }
                        // 命中了多看一会再划走
                        watchedCount++;
                        int extraWatch = randInt(viewMin, viewMax);
                        log("  📺 命中，继续看 " + extraWatch + " 秒");
                        interruptibleSleep(extraWatch, extraWatch);
                    } else {
                        // 没命中：关掉详情，直接划走
                        log("  ⏭️ 无关键词，关闭详情划走");
                        pressBack();
                        sleep(300);
                    }
                } else {
                    // 没找到描述栏（部分视频没有描述），直接划走
                    log("  ⏭️ 未找到描述栏，划走");
                }

                // 6. 上划切换下一个
                swipeUp();
                sleep(randInt(1500, 2500));

                // 偶尔检查是否离开了 IG
                if (reelIndex % 8 == 0 && !isInInstagram()) {
                    log("  ⚠️ 离开 IG，尝试返回");
                    pressBack();
                    sleep(800);
                    navigateToReels();
                }
            }

            pressBack();
            closeInstagram();
            log("=====");
            log("✅ 养号完成！共处理 " + reelIndex + " 个 Reels");
            log("  ❤️ 点赞: " + likedCount + " 次");
            log("  📺 完整观看（命中关键词）: " + watchedCount + " 个");
            log("  🚫 广告跳过: " + adCount + " 个");
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

    // ============ 点开底部描述栏 ============
    // Reels 底部描述区域：标题文字 + "..." 展开按钮
    // 策略：找 "更多"/"more" 按钮，或者直接点底部文字区域

    private boolean openReelDescription() {
        // 方案1：找 "更多" / "more" / "..." 展开按钮
        AccessibilityNodeInfo moreBtn = findNodeByTextContains("更多");
        if (moreBtn == null) moreBtn = findNodeByTextContains("more");
        if (moreBtn == null) moreBtn = findNodeByTextContains("...");
        if (moreBtn != null) {
            log("  📖 点开 [更多] 展开描述");
            moreBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            sleep(600);
            return true;
        }

        // 方案2：直接点底部文字区域（描述行大约在屏幕 y=88%~93%，左侧）
        // 这个位置是视频底部作者名+描述那行
        int tx = (int)(screenW * 0.40) + randInt(-20, 20);
        int ty = (int)(screenH * 0.90) + randInt(-20, 20);
        log("  📖 找不到 [更多]，尝试点底部描述区域 (" + tx + ", " + ty + ")");
        clickXY(tx, ty);
        sleep(800);

        // 检测是否打开了详情（页面结构变化：出现评论/更多文字）
        AccessibilityNodeInfo check = findNodeByTextContains("1次赞");
        if (check == null) check = findNodeByTextContains("likes");
        if (check == null) check = findNodeByTextContains("评论");
        if (check != null) {
            log("  ✅ 详情已打开");
            return true;
        }
        log("  ⚠️ 详情未确认打开");
        return true; // 还是返回 true，后面会做关键词检测
    }

    // ============ 在详情页检测关键词 ============
    // 打开详情后，整页文字更全（完整描述 + 标签 + 评论）

    private String detectKeywordInDetail() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return null;

        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        String pageText = normalizeText(sb.toString());

        // 调试日志：显示前 300 字符
        String preview = pageText.length() > 300 ? pageText.substring(0, 300) + "..." : pageText;
        log("  🔍 详情文字: " + preview.replace('\n', ' '));

        for (String kw : keywords) {
            String k = normalizeText(kw.trim());
            if (k.isEmpty()) continue;
            if (pageText.contains(k)) {
                return kw.trim();
            }
        }
        return null;
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
        AccessibilityNodeInfo reelsIcon = findByDesc("Reels");
        if (reelsIcon != null) {
            reelsIcon.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            smartTap((int)(screenW * 0.30), (int)(screenH * 0.95), "Reels兜底");
        }
        sleep(randInt(2500, 3500));
    }

    private boolean isInInstagram() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;
        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
        return pkg.contains("instagram");
    }

    // ============ 广告检测 ============

    private boolean isReelsAd() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        String text = sb.toString().toLowerCase();
        // 广告特征词（注意：要求出现专属广告标识，不能用太泛的词）
        String[] adMarkers = {"sponsored", "推广", "赞助商", "learn more", "了解更多",
                              "shop now", "立即购买", "get offer", "立即申请", "install now"};
        for (String m : adMarkers) {
            if (text.contains(m)) {
                log("  🚫 广告标识: " + m);
                return true;
            }
        }
        return false;
    }

    // ============ 点赞 ============

    private static final String[] LIKE_DESCS = {"Like", "喜欢", "赞"};
    private static final String[] UNLIKE_DESCS = {"Unlike", "已赞", "取消赞"};

    private void likeReels() {
        log("  ❤️ 点赞...");
        sleep(400);

        // 优先用 Accessibility 节点点击（最准确，不依赖坐标）
        AccessibilityNodeInfo btn = findLikeButton();
        if (btn != null) {
            boolean ok = btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            sleep(500);
            if (ok) {
                // 验证
                if (findUnlikeButton() != null) {
                    log("  ✅ 点赞成功");
                } else {
                    log("  ⚠️ 点赞未确认");
                }
            } else {
                // performAction 失败时用坐标兜底
                Rect rect = new Rect();
                btn.getBoundsInScreen(rect);
                clickXY(rect.centerX(), rect.centerY());
                sleep(500);
                log("  ↩️ 坐标兜底点击");
            }
            return;
        }
        log("  ⚠️ 找不到 Like 按钮，跳过点赞");
    }

    private AccessibilityNodeInfo findLikeButton() {
        return findLikeButtonRecursive(getRoot());
    }

    private AccessibilityNodeInfo findLikeButtonRecursive(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString().trim();
            for (String s : LIKE_DESCS) { if (d.equals(s)) return node; }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findLikeButtonRecursive(node.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    private AccessibilityNodeInfo findUnlikeButton() {
        return findUnlikeButtonRecursive(getRoot());
    }

    private AccessibilityNodeInfo findUnlikeButtonRecursive(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString().trim();
            for (String s : UNLIKE_DESCS) { if (d.equals(s)) return node; }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findUnlikeButtonRecursive(node.getChild(i));
            if (r != null) return r;
        }
        return null;
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

    // 文字标准化：小写 + 去变音符号（Hermès → hermes）
    private String normalizeText(String s) {
        if (s == null) return "";
        s = s.toLowerCase();
        s = s.replace("è","e").replace("é","e").replace("ê","e").replace("ë","e");
        s = s.replace("à","a").replace("á","a").replace("â","a").replace("ä","a");
        s = s.replace("ò","o").replace("ó","o").replace("ô","o").replace("ö","o");
        s = s.replace("ù","u").replace("ú","u").replace("û","u").replace("ü","u");
        s = s.replace("ì","i").replace("í","i").replace("î","i").replace("ï","i");
        return s;
    }

    // ============ 手势操作 ============

    private void swipeUp() {
        int dur = randInt(400, 700);
        int x = screenW / 2 + randInt(-30, 30);
        int sy = (int)(screenH * 0.75);
        int ey = (int)(screenH * 0.25);
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
