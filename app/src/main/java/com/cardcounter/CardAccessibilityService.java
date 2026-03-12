package com.cardcounter;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无障碍服务 - 用于识别游戏中的牌面
 * 通过读取UI节点文字识别出牌
 */
public class CardAccessibilityService extends AccessibilityService {

    private static final String TAG = "CardAccessibility";
    private static CardAccessibilityService instance;

    // 斗地主相关应用的包名
    private static final String[] TARGET_PACKAGES = {
            "com.tencent.mm",  // 微信小程序
            "com.tencent.mobileqq",  // 手机QQ
    };

    private CardDataCallback callback;
    private Handler checkHandler;
    private Runnable checkRunnable;
    private static final int CHECK_INTERVAL = 1000; // 每1秒检查一次

    // 记录已处理的牌（用于去重）
    private Set<String> processedCardHashes = new HashSet<>();

    // 当前是否在游戏中
    private boolean isInGame = false;

    // 上次识别到的牌（用于检测新出的牌）
    private Map<String, Integer> lastRecognizedCards = new HashMap<>();

    public interface CardDataCallback {
        void onCardsFound(Map<String, Integer> cards);
    }

    public static CardAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isEnabled() {
        return instance != null;
    }

    public void setCallback(CardDataCallback callback) {
        this.callback = callback;
    }

    /**
     * 重置游戏状态（新一局游戏开始时调用）
     */
    public void resetGame() {
        synchronized (processedCardHashes) {
            processedCardHashes.clear();
        }
        lastRecognizedCards.clear();
        isInGame = false;
        Log.d(TAG, "游戏状态已重置");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "无障碍服务已创建");

        checkHandler = new Handler(Looper.getMainLooper());
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    performCardCheck();
                } catch (Exception e) {
                    Log.e(TAG, "定时检查异常: " + e.getMessage());
                }
                checkHandler.postDelayed(this, CHECK_INTERVAL);
            }
        };

        checkHandler.post(checkRunnable);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

            // 检查是否是目标应用
            boolean isTargetApp = false;
            for (String target : TARGET_PACKAGES) {
                if (packageName.contains(target)) {
                    isTargetApp = true;
                    break;
                }
            }

            if (isTargetApp) {
                int eventType = event.getEventType();

                // 窗口状态变化可能是新游戏开始
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    // 检测是否离开游戏界面，重置状态
                    if (!isInGameWindow(packageName)) {
                        // 可能离开了游戏，但不一定要重置
                    }
                }

                // 界面内容变化时立即检查
                if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    checkHandler.removeCallbacks(checkRunnable);
                    checkHandler.post(checkRunnable);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "处理事件失败: " + e.getMessage());
        }
    }

    /**
     * 检查当前是否在游戏窗口
     */
    private boolean isInGameWindow(String packageName) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;

            // 检查是否包含游戏相关的文字
            boolean inGame = false;
            String gameKeywords[] = {"斗地主", "出牌", "不出", "提示", "抢地主", "不抢"};
            for (String keyword : gameKeywords) {
                if (containsText(root, keyword)) {
                    inGame = true;
                    break;
                }
            }

            root.recycle();
            return inGame;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查节点树中是否包含指定文字
     */
    private boolean containsText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;

        try {
            CharSequence nodeText = node.getText();
            if (nodeText != null && nodeText.toString().contains(text)) {
                return true;
            }

            CharSequence contentDesc = node.getContentDescription();
            if (contentDesc != null && contentDesc.toString().contains(text)) {
                return true;
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    boolean found = containsText(child, text);
                    child.recycle();
                    if (found) return true;
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "无障碍服务被中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        if (checkHandler != null) {
            checkHandler.removeCallbacks(checkRunnable);
        }
        Log.d(TAG, "无障碍服务已销毁");
    }

    /**
     * 执行牌面检查 - 核心逻辑
     */
    private void performCardCheck() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                return;
            }

            // 提取当前界面上的牌
            Map<String, Integer> currentCards = extractCardsFromNodes(root);
            root.recycle();

            if (currentCards.isEmpty()) {
                // 没有检测到牌，可能不在游戏中
                return;
            }

            // 检测新出的牌（对比上次识别的结果）
            Map<String, Integer> newCards = detectNewCards(currentCards);

            if (!newCards.isEmpty()) {
                Log.d(TAG, "检测到新出的牌: " + newCards);

                if (callback != null) {
                    try {
                        callback.onCardsFound(newCards);
                    } catch (Exception e) {
                        Log.e(TAG, "回调异常: " + e.getMessage());
                    }
                }
            }

            // 更新上次识别结果
            lastRecognizedCards = new HashMap<>(currentCards);

        } catch (Exception e) {
            Log.e(TAG, "检查牌面异常: " + e.getMessage());
        }
    }

    /**
     * 检测新出的牌
     * 对比当前识别的牌和上次识别的牌，找出新增的
     */
    private Map<String, Integer> detectNewCards(Map<String, Integer> currentCards) {
        Map<String, Integer> newCards = new HashMap<>();

        for (Map.Entry<String, Integer> entry : currentCards.entrySet()) {
            String card = entry.getKey();
            int currentCount = entry.getValue();
            int lastCount = lastRecognizedCards.getOrDefault(card, 0);

            if (currentCount > lastCount) {
                // 数量增加了，说明有新牌被打出
                newCards.put(card, currentCount - lastCount);
            }
        }

        // 检查上次存在但现在不存在的牌（可能是连续出牌后消失了）
        for (Map.Entry<String, Integer> entry : lastRecognizedCards.entrySet()) {
            String card = entry.getKey();
            int lastCount = entry.getValue();
            int currentCount = currentCards.getOrDefault(card, 0);

            if (currentCount < lastCount) {
                // 牌减少了，可能需要记录（但这种情况通常不会发生）
            }
        }

        return newCards;
    }

    /**
     * 从节点中提取牌面信息
     * 使用节点哈希去重，避免重复计数
     */
    private Map<String, Integer> extractCardsFromNodes(AccessibilityNodeInfo root) {
        Map<String, Integer> cards = new HashMap<>();
        Set<String> nodeHashes = new HashSet<>();

        try {
            traverseAndCollectCards(root, cards, nodeHashes, 0);
        } catch (Exception e) {
            Log.e(TAG, "提取牌面异常: " + e.getMessage());
        }

        return cards;
    }

    /**
     * 递归遍历节点并收集牌面信息
     */
    private void traverseAndCollectCards(AccessibilityNodeInfo node, Map<String, Integer> cards,
                                         Set<String> nodeHashes, int depth) {
        if (node == null || depth > 15) {
            return;
        }

        try {
            // 获取节点文字
            String textContent = getNodeText(node);

            if (textContent != null && !textContent.isEmpty()) {
                // 解析这个节点的牌面
                Map<String, Integer> nodeCards = parseCardsFromText(textContent);

                // 使用节点哈希防止重复计数
                String nodeHash = getNodeHash(node, textContent);
                if (!nodeHashes.contains(nodeHash)) {
                    nodeHashes.add(nodeHash);

                    // 累加牌数
                    for (Map.Entry<String, Integer> entry : nodeCards.entrySet()) {
                        String card = entry.getKey();
                        int count = entry.getValue();
                        cards.put(card, cards.getOrDefault(card, 0) + count);
                    }

                    if (!nodeCards.isEmpty()) {
                        Log.d(TAG, "节点 [" + depth + "] 文字: '" + textContent + "' -> 牌: " + nodeCards);
                    }
                }
            }

            // 递归处理子节点
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    traverseAndCollectCards(child, cards, nodeHashes, depth + 1);
                    child.recycle();
                }
            }

        } catch (Exception e) {
            // 忽略错误，继续处理
        }
    }

    /**
     * 获取节点的文字内容
     */
    private String getNodeText(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();

        try {
            CharSequence text = node.getText();
            if (text != null) {
                sb.append(text);
            }

            CharSequence contentDescription = node.getContentDescription();
            if (contentDescription != null) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(contentDescription);
            }
        } catch (Exception e) {
            // 忽略
        }

        return sb.toString().trim();
    }

    /**
     * 生成节点的唯一哈希标识
     */
    private String getNodeHash(AccessibilityNodeInfo node, String text) {
        try {
            int hashCode = 0;
            if (text != null) {
                hashCode = text.hashCode();
            }

            // 获取节点在屏幕上的位置作为额外标识
            android.graphics.Rect bounds = new android.graphics.Rect();
            node.getBoundsInScreen(bounds);

            return text + "_" + bounds.left + "_" + bounds.top + "_" + hashCode;
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    /**
     * 从文字中解析牌面信息
     * 支持多种格式：单独牌名、连续牌名、组合等
     */
    private Map<String, Integer> parseCardsFromText(String text) {
        Map<String, Integer> cards = new HashMap<>();

        if (text == null || text.isEmpty()) {
            return cards;
        }

        // 清理文字
        text = text.trim()
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .replaceAll("\\s+", " ");

        // 过滤掉明显不是牌面的文字
        if (text.length() > 50 || text.contains("http") || text.contains("www")) {
            return cards;
        }

        // 1. 匹配大小王
        if (text.contains("小王")) {
            cards.put("小王", cards.getOrDefault("小王", 0) + 1);
        }
        if (text.contains("大王")) {
            cards.put("大王", cards.getOrDefault("大王", 0) + 1);
        }

        // 2. 匹配单张牌 3-10
        Pattern digitPattern = Pattern.compile("\\b([3-9]|10)\\b");
        Matcher digitMatcher = digitPattern.matcher(text);
        while (digitMatcher.find()) {
            String card = digitMatcher.group(1);
            cards.put(card, cards.getOrDefault(card, 0) + 1);
        }

        // 3. 匹配 J, Q, K, A, 2, j, q, k, a
        Pattern letterPattern = Pattern.compile("\\b([JQKA2jqka2])\\b");
        Matcher letterMatcher = letterPattern.matcher(text);
        while (letterMatcher.find()) {
            String card = letterMatcher.group(1).toUpperCase();
            cards.put(card, cards.getOrDefault(card, 0) + 1);
        }

        // 4. 处理连续相同字符（如 "333" 表示三张3）
        for (String card : new String[]{"3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2"}) {
            // 先移除已经单独匹配的
            int baseCount = cards.getOrDefault(card, 0);

            Pattern repeatPattern = Pattern.compile("(" + card + "){2,}");
            Matcher repeatMatcher = repeatPattern.matcher(text);
            while (repeatMatcher.find()) {
                String matched = repeatMatcher.group(0);
                int repeatCount = countRepeats(matched, card);
                if (repeatCount > 1) {
                    // 累加重复数量
                    cards.put(card, baseCount + repeatCount);
                }
            }
        }

        // 5. 特殊处理：中文数字牌
        Pattern chineseNumPattern = Pattern.compile("[三四五六七八九十]+");
        Matcher chineseMatcher = chineseNumPattern.matcher(text);
        if (chineseMatcher.find()) {
            String chinese = chineseMatcher.group();
            Map<String, Integer> converted = convertChineseCards(chinese);
            for (Map.Entry<String, Integer> entry : converted.entrySet()) {
                cards.put(entry.getKey(), cards.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }
        }

        return cards;
    }

    /**
     * 计算重复字符的数量
     */
    private int countRepeats(String text, String card) {
        int count = 0;
        int index = 0;
        while (index <= text.length() - card.length()) {
            if (text.substring(index, index + card.length()).equals(card)) {
                count++;
                index += card.length();
            } else {
                index++;
            }
        }
        return count;
    }

    /**
     * 转换中文牌名
     */
    private Map<String, Integer> convertChineseCards(String chinese) {
        Map<String, Integer> result = new HashMap<>();
        String[] mapping = {"三", "四", "五", "六", "七", "八", "九", "十"};
        String[] cards = {"3", "4", "5", "6", "7", "8", "9", "10"};

        for (int i = 0; i < mapping.length; i++) {
            if (chinese.contains(mapping[i])) {
                result.put(cards[i], result.getOrDefault(cards[i], 0) + 1);
            }
        }

        return result;
    }

    /**
     * 调试：输出窗口结构
     */
    public void dumpWindowInfo() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                dumpNodeRecursive(root, 0);
                root.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "dump error: " + e.getMessage());
        }
    }

    private void dumpNodeRecursive(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 20) return;

        try {
            String indent = "  ".repeat(Math.min(depth, 10));
            String text = node.getText() != null ? node.getText().toString() : "";
            String contentDesc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
            String className = node.getClassName() != null ? node.getClassName().toString() : "";

            if (!text.isEmpty() || !contentDesc.isEmpty()) {
                Log.d(TAG, String.format("%s[%s] t='%s' d='%s'",
                        indent, className, text, contentDesc));
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    dumpNodeRecursive(child, depth + 1);
                    child.recycle();
                }
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    /**
     * 手动触发检查
     */
    public void triggerCheck() {
        if (checkHandler != null) {
            checkHandler.removeCallbacks(checkRunnable);
            checkHandler.post(checkRunnable);
        }
    }
}
