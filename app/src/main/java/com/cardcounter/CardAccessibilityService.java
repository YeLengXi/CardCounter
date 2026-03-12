package com.cardcounter;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无障碍服务 - 支持图片节点识别
 */
public class CardAccessibilityService extends AccessibilityService {

    private static final String TAG = "CardAccessibility";
    private static CardAccessibilityService instance;

    private static final String[] TARGET_PACKAGES = {
            "com.tencent.mm",
            "com.tencent.mobileqq",
    };

    private CardDataCallback callback;
    private Handler checkHandler;
    private Runnable checkRunnable;
    private static final int CHECK_INTERVAL = 500;

    // 当前显示的所有牌（用于对比变化）
    private Map<String, Integer> currentDisplayedCards = new HashMap<>();
    // 上次的牌面状态
    private Map<String, Integer> lastCardsState = new HashMap<>();

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

    public void resetGame() {
        lastCardsState.clear();
        currentDisplayedCards.clear();
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
                    Log.e(TAG, "检查异常: " + e.getMessage());
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

            for (String target : TARGET_PACKAGES) {
                if (packageName.contains(target)) {
                    int eventType = event.getEventType();
                    if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                        eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                        checkHandler.removeCallbacks(checkRunnable);
                        checkHandler.post(checkRunnable);
                    }
                    break;
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

    private void performCardCheck() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                return;
            }

            CharSequence packageName = root.getPackageName();
            if (packageName == null || !packageName.toString().contains("tencent")) {
                root.recycle();
                return;
            }

            // 提取当前界面上的牌
            currentDisplayedCards.clear();
            extractCardsFromNodes(root, 0);

            if (DEBUG_MODE && !currentDisplayedCards.isEmpty()) {
                Log.d(TAG, "当前识别: " + currentDisplayedCards);
            }

            root.recycle();

            if (currentDisplayedCards.isEmpty()) {
                return;
            }

            // 计算差异 - 找出新打出的牌
            Map<String, Integer> newCards = calculateDifference(currentDisplayedCards, lastCardsState);

            if (!newCards.isEmpty()) {
                Log.d(TAG, "★ 新打出的牌: " + newCards);

                if (callback != null) {
                    try {
                        callback.onCardsFound(newCards);
                    } catch (Exception e) {
                        Log.e(TAG, "回调异常: " + e.getMessage());
                    }
                }
            }

            // 更新状态
            lastCardsState = new HashMap<>(currentDisplayedCards);

        } catch (Exception e) {
            Log.e(TAG, "检查牌面异常: " + e.getMessage());
        }
    }

    /**
     * 计算差异 - 当前比上次多的就是新打出的牌
     */
    private Map<String, Integer> calculateDifference(Map<String, Integer> current, Map<String, Integer> last) {
        Map<String, Integer> diff = new HashMap<>();

        for (Map.Entry<String, Integer> entry : current.entrySet()) {
            String card = entry.getKey();
            int currCount = entry.getValue();
            int lastCount = last.getOrDefault(card, 0);

            if (currCount > lastCount) {
                diff.put(card, currCount - lastCount);
            }
        }

        return diff;
    }

    /**
     * 从节点中提取牌面 - 支持图片节点
     */
    private void extractCardsFromNodes(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 30) {
            return;
        }

        try {
            // 获取所有可能的文字来源
            String card = extractCardFromNode(node);
            if (card != null) {
                currentDisplayedCards.put(card, currentDisplayedCards.getOrDefault(card, 0) + 1);
            }

            // 递归处理子节点
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    extractCardsFromNodes(child, depth + 1);
                    child.recycle();
                }
            }

        } catch (Exception e) {
            // 忽略
        }
    }

    /**
     * 从单个节点提取牌面信息
     */
    private String extractCardFromNode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        try {
            // 1. 检查 text 属性
            CharSequence text = node.getText();
            if (text != null) {
                String card = parseSingleCard(text.toString());
                if (card != null) {
                    if (DEBUG_MODE) Log.d(TAG, "从text识别: " + text + " -> " + card);
                    return card;
                }
            }

            // 2. 检查 contentDescription 属性（图片节点常用）
            CharSequence contentDesc = node.getContentDescription();
            if (contentDesc != null) {
                String desc = contentDesc.toString();
                // 过滤掉太长的描述
                if (desc.length() < 30) {
                    String card = parseSingleCard(desc);
                    if (card != null) {
                        if (DEBUG_MODE) Log.d(TAG, "从contentDescription识别: " + desc + " -> " + card);
                        return card;
                    }
                }
            }

            // 3. 检查 hint 属性
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CharSequence hint = node.getHintText();
                if (hint != null) {
                    String card = parseSingleCard(hint.toString());
                    if (card != null) {
                        if (DEBUG_MODE) Log.d(TAG, "从hint识别: " + hint + " -> " + card);
                        return card;
                    }
                }
            }

            // 4. 检查 resource-id（可能包含牌面信息）
            String resourceId = node.getViewIdResourceName();
            if (resourceId != null) {
                // 某些游戏的resource-id包含牌面信息
                // 例如: card_3, card_heart_10 等
                String card = parseCardFromResourceId(resourceId);
                if (card != null) {
                    if (DEBUG_MODE) Log.d(TAG, "从resourceId识别: " + resourceId + " -> " + card);
                    return card;
                }
            }

            // 5. 检查节点的 className，如果是ImageView，尝试特殊处理
            CharSequence className = node.getClassName();
            if (className != null && className.toString().contains("ImageView")) {
                // 对于ImageView，尝试从辅助功能属性中获取信息
                String card = extractFromImageNode(node);
                if (card != null) {
                    if (DEBUG_MODE) Log.d(TAG, "从ImageView识别: -> " + card);
                    return card;
                }
            }

        } catch (Exception e) {
            // 忽略
        }

        return null;
    }

    /**
     * 从文字中解析单个牌面
     */
    private String parseSingleCard(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        text = text.trim();

        // 过滤掉明显不是牌面的文字
        if (text.length() > 30 || text.contains("http") || text.contains("微信") ||
            text.contains("游戏") || text.contains("开始") || text.contains("房间")) {
            return null;
        }

        // 匹配大小王
        if (text.contains("小王")) return "小王";
        if (text.contains("大王")) return "大王";

        // 匹配单张牌 3-10
        Pattern digitPattern = Pattern.compile("\\b([3-9]|10)\\b");
        Matcher digitMatcher = digitPattern.matcher(text);
        if (digitMatcher.find()) {
            return digitMatcher.group(1);
        }

        // 匹配 J, Q, K, A, 2
        Pattern letterPattern = Pattern.compile("\\b([JQKA2])\\b");
        Matcher letterMatcher = letterPattern.matcher(text);
        if (letterMatcher.find()) {
            return letterMatcher.group(1).toUpperCase();
        }

        // 匹配中文数字
        if (text.contains("三")) return "3";
        if (text.contains("四")) return "4";
        if (text.contains("五")) return "5";
        if (text.contains("六")) return "6";
        if (text.contains("七")) return "7";
        if (text.contains("八")) return "8";
        if (text.contains("九")) return "9";
        if (text.contains("十")) return "10";

        return null;
    }

    /**
     * 从resource-id中解析牌面
     */
    private String parseCardFromResourceId(String resourceId) {
        if (resourceId == null) return null;

        // 小写处理
        String id = resourceId.toLowerCase();

        // 匹配常见格式: card_3, card_10, card_k 等
        Pattern pattern = Pattern.compile("(?:card|pai|poker|poker_card)[_ -]?(\\d+|[jqka2]|king|queen|jack|ace)");
        Matcher matcher = pattern.matcher(id);

        if (matcher.find()) {
            String value = matcher.group(1);
            switch (value.toLowerCase()) {
                case "1": case "ace": return "A";
                case "11": case "jack": return "J";
                case "12": case "queen": return "Q";
                case "13": case "king": return "K";
                case "2": case "3": case "4": case "5": case "6": case "7": case "8": case "9": case "10":
                    if (value.length() <= 2) {
                        return value;
                    }
                    break;
                case "j": return "J";
                case "q": return "Q";
                case "k": return "K";
                case "a": return "A";
            }
        }

        return null;
    }

    /**
     * 从图片节点提取信息
     */
    private String extractFromImageNode(AccessibilityNodeInfo node) {
        try {
            // 尝试获取所有辅助功能属性
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 可以获取更多属性
                CharSequence stateDescription = node.getStateDescription();
                if (stateDescription != null) {
                    String text = stateDescription.toString();
                    String card = parseSingleCard(text);
                    if (card != null) return card;
                }
            }

            // 检查是否有可访问的操作名称
            CharSequence className = node.getClassName();
            if (className != null) {
                String cn = className.toString();
                // 如果是ImageView且没有contentDescription，可能需要其他方式
            }

        } catch (Exception e) {
            // 忽略
        }

        return null;
    }

    /**
     * 调试：输出当前窗口结构
     */
    public void dumpCurrentText() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                Log.d(TAG, "===== 窗口结构开始 =====");
                dumpNodeRecursive(root, 0);
                Log.d(TAG, "===== 窗口结构结束 =====");
                root.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "dump error: " + e.getMessage());
        }
    }

    private void dumpNodeRecursive(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 20) return;

        try {
            String text = node.getText() != null ? node.getText().toString() : "";
            String contentDesc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
            String className = node.getClassName() != null ? node.getClassName().toString() : "";
            String resourceId = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "";

            if (!text.isEmpty() || !contentDesc.isEmpty() || !resourceId.isEmpty()) {
                String indent = "  ".repeat(Math.min(depth, 10));
                Log.d(TAG, String.format("%s[%s] id='%s' text='%s' desc='%s'",
                        indent, className, resourceId, text, contentDesc));
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

    public void triggerCheck() {
        if (checkHandler != null) {
            checkHandler.removeCallbacks(checkRunnable);
            checkHandler.post(checkRunnable);
        }
    }

    public static void setDebugMode(boolean debug) {
        DEBUG_MODE = debug;
    }
}
