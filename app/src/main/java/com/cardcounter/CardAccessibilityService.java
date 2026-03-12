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
    private static final int CHECK_INTERVAL = 800; // 每0.8秒检查一次

    // 上次识别到的牌（用于检测新出的牌）
    private Map<String, Integer> lastRecognizedCards = new HashMap<>();

    // 调试模式开关
    private static boolean DEBUG_MODE = true;

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
     * 重置游戏状态
     */
    public void resetGame() {
        lastRecognizedCards.clear();
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

            // 检查当前包名
            CharSequence packageName = root.getPackageName();
            if (packageName == null || !packageName.toString().contains("tencent")) {
                root.recycle();
                return;
            }

            // 提取当前界面上的牌
            Map<String, Integer> currentCards = extractCardsFromNodes(root);

            if (DEBUG_MODE && !currentCards.isEmpty()) {
                Log.d(TAG, "当前识别到的牌: " + currentCards);
            }

            root.recycle();

            if (currentCards.isEmpty()) {
                return;
            }

            // 检测新出的牌
            Map<String, Integer> newCards = detectNewCards(currentCards);

            if (!newCards.isEmpty()) {
                Log.d(TAG, "★ 检测到新出的牌: " + newCards);

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
     */
    private Map<String, Integer> detectNewCards(Map<String, Integer> currentCards) {
        Map<String, Integer> newCards = new HashMap<>();

        for (Map.Entry<String, Integer> entry : currentCards.entrySet()) {
            String card = entry.getKey();
            int currentCount = entry.getValue();
            int lastCount = lastRecognizedCards.getOrDefault(card, 0);

            if (currentCount > lastCount) {
                newCards.put(card, currentCount - lastCount);
            }
        }

        return newCards;
    }

    /**
     * 从节点中提取牌面信息
     */
    private Map<String, Integer> extractCardsFromNodes(AccessibilityNodeInfo root) {
        Map<String, Integer> cards = new HashMap<>();

        try {
            // 先尝试检测是否在游戏中
            if (!isInGame(root)) {
                return cards;
            }

            // 深度遍历所有节点
            traverseAllNodes(root, cards, 0);

        } catch (Exception e) {
            Log.e(TAG, "提取牌面异常: " + e.getMessage());
        }

        return cards;
    }

    /**
     * 检查是否在游戏中
     */
    private boolean isInGame(AccessibilityNodeInfo root) {
        // 检查是否包含游戏相关的关键字
        String[] gameKeywords = {"斗地主", "出牌", "不出", "提示", "抢地主", "不抢", "明牌", "叫地主"};
        return containsAnyText(root, gameKeywords);
    }

    /**
     * 检查节点树中是否包含任意指定文字
     */
    private boolean containsAnyText(AccessibilityNodeInfo node, String[] keywords) {
        if (node == null) return false;

        try {
            CharSequence text = node.getText();
            CharSequence contentDesc = node.getContentDescription();

            for (String keyword : keywords) {
                if ((text != null && text.toString().contains(keyword)) ||
                    (contentDesc != null && contentDesc.toString().contains(keyword))) {
                    return true;
                }
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    boolean found = containsAnyText(child, keywords);
                    child.recycle();
                    if (found) return true;
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        return false;
    }

    /**
     * 递归遍历所有节点并提取牌面
     */
    private void traverseAllNodes(AccessibilityNodeInfo node, Map<String, Integer> cards, int depth) {
        if (node == null || depth > 30) {
            return;
        }

        try {
            // 获取所有可能的文字来源
            String text = getNodeText(node);

            if (text != null && !text.isEmpty()) {
                // 过滤掉太长的文字（可能是整段话）
                if (text.length() < 50) {
                    Map<String, Integer> nodeCards = parseCardsFromText(text);
                    if (!nodeCards.isEmpty()) {
                        for (Map.Entry<String, Integer> entry : nodeCards.entrySet()) {
                            String card = entry.getKey();
                            int count = entry.getValue();
                            cards.put(card, cards.getOrDefault(card, 0) + count);
                        }
                        if (DEBUG_MODE) {
                            Log.d(TAG, "[深度" + depth + "] 文字: '" + text + "' -> 牌: " + nodeCards);
                        }
                    }
                }
            }

            // 递归处理子节点
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    traverseAllNodes(child, cards, depth + 1);
                    child.recycle();
                }
            }

        } catch (Exception e) {
            // 忽略错误，继续处理
        }
    }

    /**
     * 获取节点的文字内容（多种来源）
     */
    private String getNodeText(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();

        try {
            // 1. text属性
            CharSequence text = node.getText();
            if (text != null) {
                sb.append(text);
            }

            // 2. contentDescription属性
            CharSequence contentDescription = node.getContentDescription();
            if (contentDescription != null) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(contentDescription);
            }

            // 3. className - 某些情况下className包含有用信息
            CharSequence className = node.getClassName();
            if (className != null) {
                String cn = className.toString();
                // 只处理包含Button、Text等UI元素的className
                if (cn.contains("Button") || cn.contains("Text") || cn.contains("Image")) {
                    // sb.append(" [").append(cn).append("]");
                }
            }

        } catch (Exception e) {
            // 忽略
        }

        return sb.toString().trim();
    }

    /**
     * 从文字中解析牌面信息
     * 支持多种格式
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
        if (text.length() > 50 || text.contains("http") || text.contains("www") ||
            text.contains("微信") || text.contains("游戏") || text.contains("开始")) {
            return cards;
        }

        // 1. 匹配大小王
        if (text.contains("小王") || text.toLowerCase().contains("xiaowang")) {
            cards.put("小王", cards.getOrDefault("小王", 0) + 1);
        }
        if (text.contains("大王") || text.toLowerCase().contains("dawang")) {
            cards.put("大王", cards.getOrDefault("大王", 0) + 1);
        }

        // 2. 匹配单张牌 3-10
        Pattern digitPattern = Pattern.compile("\\b([3-9]|10)\\b");
        Matcher digitMatcher = digitPattern.matcher(text);
        while (digitMatcher.find()) {
            String card = digitMatcher.group(1);
            cards.put(card, cards.getOrDefault(card, 0) + 1);
        }

        // 3. 匹配 J, Q, K, A, 2（大小写都支持）
        Pattern letterPattern = Pattern.compile("\\b([JQKA2jqka2])\\b");
        Matcher letterMatcher = letterPattern.matcher(text);
        while (letterMatcher.find()) {
            String card = letterMatcher.group(1).toUpperCase();
            cards.put(card, cards.getOrDefault(card, 0) + 1);
        }

        // 4. 匹配连续的相同字符（如 "333" 表示三张3）
        for (String card : new String[]{"3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2"}) {
            Pattern repeatPattern = Pattern.compile("(" + card + "){2,}");
            Matcher repeatMatcher = repeatPattern.matcher(text);
            while (repeatMatcher.find()) {
                String matched = repeatMatcher.group(0);
                int repeatCount = countRepeats(matched, card);
                if (repeatCount > 1) {
                    cards.put(card, Math.max(cards.getOrDefault(card, 0), repeatCount));
                }
            }
        }

        // 5. 匹配中文数字牌（三、四、五等）
        Pattern chinesePattern = Pattern.compile("[三四五六七八九十]");
        Matcher chineseMatcher = chinesePattern.matcher(text);
        while (chineseMatcher.find()) {
            String chinese = chineseMatcher.group();
            String card = chineseToCard(chinese);
            if (card != null) {
                cards.put(card, cards.getOrDefault(card, 0) + 1);
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
            if (text.substring(index, index + card.length()).equalsIgnoreCase(card)) {
                count++;
                index += card.length();
            } else {
                index++;
            }
        }
        return count;
    }

    /**
     * 中文数字转牌面
     */
    private String chineseToCard(String chinese) {
        switch (chinese) {
            case "三": return "3";
            case "四": return "4";
            case "五": return "5";
            case "六": return "6";
            case "七": return "7";
            case "八": return "8";
            case "九": return "9";
            case "十": return "10";
            default: return null;
        }
    }

    /**
     * 调试：输出当前窗口的所有文字
     */
    public void dumpCurrentText() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                Log.d(TAG, "===== 当前窗口文字开始 =====");
                collectAndDumpText(root, 0);
                Log.d(TAG, "===== 当前窗口文字结束 =====");
                root.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "dump error: " + e.getMessage());
        }
    }

    private void collectAndDumpText(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 25) return;

        try {
            String text = node.getText() != null ? node.getText().toString() : "";
            String contentDesc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
            String className = node.getClassName() != null ? node.getClassName().toString() : "";

            if (!text.isEmpty() || !contentDesc.isEmpty()) {
                String indent = "  ".repeat(Math.min(depth, 10));
                Log.d(TAG, String.format("%s[%s] text='%s' desc='%s'",
                        indent, className, text, contentDesc));
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    collectAndDumpText(child, depth + 1);
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

    /**
     * 设置调试模式
     */
    public static void setDebugMode(boolean debug) {
        DEBUG_MODE = debug;
        Log.d(TAG, "调试模式: " + debug);
    }
}
