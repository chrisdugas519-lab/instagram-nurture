package com.yuxi.nurture;

// v4.3: 排除作者节点+主页检测+Reels恢复

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
                    sleep(1000); // 等详情内容完全渲染
                    String matchedKw = detectKeywordInDetail();
                    if (matchedKw != null) {
                        // 5. 命中关键词：关详情 → 点赞（按用户设定概率）
                        log("  🎯 命中关键词 [" + matchedKw + "]");
                        dismissDetail();
                        if (random.nextInt(100) < likeProb) {
                            likeReels();
                            likedCount++;
                        } else {
                            log("  🎲 概率跳过点赞");
                        }
                        // 命中了多看一会再划走
                        watchedCount++;
                        int extraWatch = randInt(viewMin, viewMax);
                        log("  📺 命中，继续看 " + extraWatch + " 秒");
                        interruptibleSleep(extraWatch, extraWatch);
                    } else {
                        // 没命中：关掉详情，直接划走
                        log("  ⏭️ 无关键词，关闭详情划走");
                        dismissDetail();
                    }
                } else {
                    // 没找到描述栏 / 误入话题页，直接划走
                    log("  ⏭️ 未打开详情，划走");
                }

                // 6. 上划切换下一个
                swipeUp();
                sleep(randInt(1500, 2500));

                // 每次 swipe 后检查是否还在 Reels，不在则恢复
                if (isOnHomeFeed()) {
                    log("  ⚠️ 检测到在首页，重新进入 Reels");
                    navigateToReels();
                    sleep(randInt(1500, 2500));
                } else if (reelIndex % 5 == 0 && !isInInstagram()) {
                    log("  ⚠️ 离开 IG，尝试返回并重进 Reels");
                    pressBack();
                    sleep(800);
                    navigateToReels();
                }
                // 偶尔检查 Reels tab 是否还 active（防止被导航到其他 tab）
                if (reelIndex % 12 == 0) {
                    revalidateReelsTab();
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
    // ⚠️ 严禁找 "更多"/"more" 文本 — Reels 右上 overflow 菜单也有 "更多" 文字
    // ⚠️ 严禁点作者头像/名字区域 — 那是跳个人主页的，不是开详情
    //
    // Reels 底部布局（从左到右）：
    //   [作者头像 0~12%] [作者名 12%~20%] [描述文字 20%~55%] ... [❤️ 赞 80%+] ... [底部导航栏 0~100%, y>93%]
    //
    // 正确策略：坐标点描述文字区（x=28%~48%, y=88%~92%），
    //           这个区间避开了作者头像/名字和右侧互动按钮，也避开了底部导航栏。

    private boolean openReelDescription() {
        // 方案1：精确定位底部描述区的 clickable 节点（需排除作者节点）
        AccessibilityNodeInfo root = getRoot();
        if (root != null) {
            AccessibilityNodeInfo captionNode = findBottomCaptionNode(root);
            if (captionNode != null) {
                log("  📖 点击底部描述节点");
                captionNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                sleep(800);

                if (isOnProfilePage()) {
                    // 点到作者头像/名字了！立刻 back 回 Reels
                    log("  ⚠️ 误入个人主页，pressBack 返回");
                    pressBack();
                    sleep(500);
                } else if (isDetailPageOpen()) {
                    log("  ✅ 详情已打开（节点点击）");
                    return true;
                } else {
                    log("  ⚠️ 节点点击未打开详情，dismiss");
                    dismissOverlayMenu();
                }
            }
        }

        // 方案2：坐标点击描述文字区（安全区间：x=28%~48%, y=88%~92%）
        // 这个位置在作者名字右侧、赞按钮左侧，精准命中描述文字
        int tx = (int)(screenW * 0.35) + randInt(-15, 15);
        int ty = (int)(screenH * 0.895) + randInt(-8, 8);
        log("  📖 坐标点击描述区 (" + tx + ", " + ty + ")");
        clickXY(tx, ty);
        sleep(800);

        // 检查结果
        if (isOnProfilePage()) {
            log("  ⚠️ 坐标点到了作者区，pressBack 返回");
            pressBack();
            sleep(500);
            return false;
        }
        if (isOnHashTagPage()) {
            log("  ⚠️ 误入话题页，pressBack 返回");
            pressBack();
            sleep(500);
            return false;
        }
        if (isDetailPageOpen()) {
            log("  ✅ 详情已打开（坐标点击）");
            return true;
        }
        // 如果点到主页后又 back，可能在首页；检测并恢复到 Reels
        if (isOnHomeFeed()) {
            log("  ⚠️ 似乎回到了首页，重新进入 Reels");
            navigateToReels();
            sleep(randInt(1500, 2500));
            return false;
        }

        log("  ⚠️ 详情未打开");
        return false;
    }

    // 在可见树中寻找底部区域的 caption 节点（排除作者头像/名字）
    // ⚠️ 底部左侧有两个紧挨的 clickable 区域：作者信息（跳主页）、描述文字（开详情）
    //    必须排除作者节点，否则会误入个人主页导致整个流程跑偏
    private AccessibilityNodeInfo findBottomCaptionNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        int bottomMinY = (int)(screenH * 0.80);

        java.util.List<AccessibilityNodeInfo> candidates = new java.util.ArrayList<>();
        collectCaptionCandidates(node, bottomMinY, candidates);

        if (candidates.isEmpty()) {
            log("  ⚠️ 底部区域未找到候选节点");
            return null;
        }

        AccessibilityNodeInfo best = null;
        int bestScore = -1;
        for (AccessibilityNodeInfo c : candidates) {
            Rect r = new Rect();
            c.getBoundsInScreen(r);
            int area = (r.right - r.left) * (r.bottom - r.top);

            // === 排除规则 ===
            CharSequence cd = c.getContentDescription();
            String cdStr = cd != null ? cd.toString().toLowerCase() : "";

            // 1. 排除音频节点
            if (cdStr.contains("音频") || cdStr.contains("audio") ||
                cdStr.contains("原声") || cdStr.contains("original") ||
                cdStr.contains("音乐") || cdStr.contains("music")) continue;

            // 2. 排除作者头像/个人主页相关
            if (cdStr.contains("查看用户") || cdStr.contains("view user") ||
                cdStr.contains("个人主页") || cdStr.contains("profile") ||
                cdStr.contains("查看个人") || cdStr.contains("用户")) continue;

            // 3. 排除太靠左的小节点（作者头像约在 x=0~12% 且面积很小，如圆形头像）
            if (r.left < screenW * 0.15 && area < 12000) continue;

            // 4. 排除 ImageView 类节点（作者头像是 ImageView）
            String clsName = c.getClassName() != null ? c.getClassName().toString().toLowerCase() : "";
            if (clsName.contains("imageview") || clsName.contains("image")) continue;

            // === 评分规则 ===
            int score = 0;
            // 左侧但不太靠左加分（中间偏左 = 描述文字区）
            if (r.left >= screenW * 0.18 && r.left < screenW * 0.55) score += 10;
            // 太靠左（x < 15%）减分（作者区）
            if (r.left < screenW * 0.15) score -= 25;
            // text 非空且有一定长度加分（短文字通常是用户名）
            CharSequence txt = c.getText();
            int txtLen = txt != null ? txt.length() : 0;
            if (txtLen >= 15) score += 12;      // 长文字=描述
            else if (txtLen >= 5) score += 4;    // 中等=可能是描述片段
            else score -= 8;                      // 很短或无=可能是用户名
            // 面积适中加分
            int maxArea = (int)(screenW * screenH * 0.20);
            if (area > 500 && area < maxArea) score += 2;
            // 在底部区域中间 y 范围加分
            if (r.top > screenH * 0.85 && r.top < screenH * 0.94) score += 3;

            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best != null) {
            Rect r = new Rect();
            best.getBoundsInScreen(r);
            log("  📍 选中候选: bounds=(" + r.left + "," + r.top + "-" + r.right + "," + r.bottom + "), class="
                + (best.getClassName() != null ? best.getClassName().toString() : "?")
                + ", text=" + (best.getText() != null ? best.getText().toString().substring(0, Math.min(30, best.getText().length())) : "N/A")
                + ", score=" + bestScore);
        }
        return best;
    }

    private void collectCaptionCandidates(AccessibilityNodeInfo node, int minY,
                                          java.util.List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (rect.bottom > minY && node.isClickable()) {
            out.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectCaptionCandidates(node.getChild(i), minY, out);
        }
    }

    // 检测是否已打开详情页（出现了评论/点赞数/回复等 UI 元素）
    private boolean isDetailPageOpen() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        String text = sb.toString();
        // Instagram 详情页特征文字
        return text.contains("评论") || text.contains("Comment")
            || text.contains("回复") || text.contains("Reply")
            || text.contains("次赞") || text.contains("likes")
            || text.contains("查看") || text.contains("view");
    }

    // 检测是否误入了个人主页
    private boolean isOnProfilePage() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        String text = sb.toString();
        // 主页特征：有「帖子」「粉丝」「正在关注」、有「编辑主页」或「分享主页」
        boolean hasPosts = text.contains("帖子") || text.contains("posts") || text.contains("貼文");
        boolean hasFollowers = text.contains("粉丝") || text.contains("followers") || text.contains("粉絲");
        boolean hasFollowing = text.contains("正在关注") || text.contains("following") || text.contains("追蹤中");
        boolean hasEditOrShare = text.contains("编辑主页") || text.contains("edit profile")
                              || text.contains("分享主页") || text.contains("share profile")
                              || text.contains("編輯個人資料");
        return (hasPosts && hasFollowers && hasFollowing) || hasEditOrShare;
    }

    // 检测是否在首页 Feed（非 Reels）
    private boolean isOnHomeFeed() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;
        // 首页特征：底部有"首页"按钮高亮 + 上面是贴文列表（不是全屏视频）
        // 简单判断：没有 fullscreen Reels 特征 + 有"首页"相关节点
        // 更可靠：找是否有 Reels 标识；没有 Reels 标识但有"首页"=在首页
        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
        if (!pkg.contains("instagram")) return false;

        // 检测是否有 Reels 全屏特征文字
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        String text = sb.toString();
        boolean isReelsMode = text.contains("Reels") || text.contains("reels");
        boolean hasHomeActive = false;
        // 找底部导航栏"首页"是否处于 active 状态
        AccessibilityNodeInfo homeTab = findNodeByTextContains("首页");
        if (homeTab == null) homeTab = findNodeByTextContains("主頁");
        if (homeTab != null) {
            hasHomeActive = homeTab.isSelected() || homeTab.isFocused();
        }
        return !isReelsMode && hasHomeActive;
    }
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return false;
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        String text = sb.toString();
        // 话题页特征：大量 ## 开头文字、关注/取消关注话题按钮
        int hashCount = 0;
        for (String word : text.split("\\s+")) {
            if (word.startsWith("#")) hashCount++;
        }
        boolean hasFollowTopic = text.contains("关注话题") || text.contains("追蹤話題")
                              || text.contains("Follow") || text.contains("follow");
        return (hashCount >= 3 && hasFollowTopic);
    }

    // 安全关闭详情页（含话题页误入的情况）
    // 策略：先 pressBack → 如果还在详情/话题页 → 点击视频区域返回
    private void dismissDetail() {
        pressBack();
        sleep(500);

        // 检查是否成功回到 Reels 界面
        if (isDetailPageOpen() || isOnHashTagPage()) {
            log("  ↩️ back 未关闭详情，点击视频区域返回");
            int vx = screenW / 2 + randInt(-30, 30);
            int vy = (int)(screenH * 0.32) + randInt(-20, 20);
            clickXY(vx, vy);
            sleep(600);
        }
    }

    // 关闭可能弹出来的 overlay 菜单（不感兴趣/举报 等）
    private void dismissOverlayMenu() {
        // 尝试找 "取消" "关闭" "Cancel" "Close" 按钮
        AccessibilityNodeInfo cancelBtn = findNodeByTextContains("取消");
        if (cancelBtn == null) cancelBtn = findNodeByTextContains("Cancel");
        if (cancelBtn == null) cancelBtn = findNodeByTextContains("Close");
        if (cancelBtn != null) {
            cancelBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            sleep(400);
            return;
        }
        // 兜底：按 back 关闭 overlay
        pressBack();
        sleep(400);
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

    // 确认当前仍在 Reels tab，不在则重新进入
    private void revalidateReelsTab() {
        AccessibilityNodeInfo root = getRoot();
        if (root == null) return;
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        String text = sb.toString();
        // 如果收集的文字中找不到任何 Reels 特征且不在详情页，可能跑偏了
        boolean inReels = text.contains("Reels") || text.contains("reels") || isDetailPageOpen();
        if (!inReels && isInInstagram()) {
            log("  🔄 Reels tab 丢失，重新进入");
            navigateToReels();
        }
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
