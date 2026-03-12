package com.cardcounter;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无障碍服务 - 用于识别游戏中的牌面
 * 不依赖截图，直接读取界面节点获取文字信息
 */
public class CardAccessibilityService extends AccessibilityService {

    private static final String TAG = "CardAccessibilityService";
    private static CardAccessibilityService instance;

    // 斗地主相关应用的包名（需要根据实际游戏调整）
    private static final String[] TARGET_PACKAGES = {
            "com.tencent.mm",  // 微信小程序
            "com.jdd.zmld",   // 可能的斗地主包名
    };

    private CardDataCallback callback;
    private Handler checkHandler;
    private Runnable checkRunnable;
    private static final int CHECK_INTERVAL = 1500; // 每1.5秒检查一次

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

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "无障碍服务已创建");

        checkHandler = new Handler(Looper.getMainLooper());
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                checkForCards();
                checkHandler.postDelayed(this, CHECK_INTERVAL);
            }
        };
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 只处理我们关心的包名事件
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // 检查是否是微信小程序（微乐斗地主）
        if (!packageName.contains("tencent.mm")) {
            return;
        }

        // 当窗口内容变化时，检查牌面
        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // 延迟检查，确保界面完全加载
            checkHandler.removeCallbacks(checkRunnable);
            checkHandler.postDelayed(checkRunnable, 500);
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
     * 检查当前界面中的牌面信息
     */
    private void checkForCards() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                return;
            }

            // 查找包含牌面文字的节点
            Map<String, Integer> foundCards = parseCardsFromNode(root);

            if (!foundCards.isEmpty() && callback != null) {
                callback.onCardsFound(foundCards);
                Log.d(TAG, "识别到牌: " + foundCards);
            }

            root.recycle();
        } catch (Exception e) {
            Log.e(TAG, "检查牌面失败: " + e.getMessage());
        }
    }

    /**
     * 从无障碍节点中解析牌面信息
     */
    private Map<String, Integer> parseCardsFromNode(AccessibilityNodeInfo node) {
        Map<String, Integer> cards = new HashMap<>();

        if (node == null) {
            return cards;
        }

        // 获取节点的文字内容
        CharSequence text = node.getText();
        CharSequence contentDescription = node.getContentDescription();

        String textContent = "";
        if (text != null) {
            textContent += text.toString();
        }
        if (contentDescription != null) {
            textContent += contentDescription.toString();
        }

        // 解析文字中的牌面
        parseCardsFromText(textContent, cards);

        // 递归遍历子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                Map<String, Integer> childCards = parseCardsFromNode(child);
                // 合并结果（取最大值）
                for (Map.Entry<String, Integer> entry : childCards.entrySet()) {
                    String card = entry.getKey();
                    int count = entry.getValue();
                    cards.put(card, Math.max(cards.getOrDefault(card, 0), count));
                }
                child.recycle();
            }
        }

        return cards;
    }

    /**
     * 从文字中解析牌面信息
     */
    private void parseCardsFromText(String text, Map<String, Integer> cards) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // 匹配各种牌型

        // 1. 匹配小王、大王
        if (text.contains("小王")) {
            incrementCard(cards, "小王");
        }
        if (text.contains("大王")) {
            incrementCard(cards, "大王");
        }

        // 2. 匹配数字牌 3-10
        Pattern digitPattern = Pattern.compile("\\b([3-9]|10)\\b");
        Matcher digitMatcher = digitPattern.matcher(text);
        while (digitMatcher.find()) {
            String card = digitMatcher.group(1);
            incrementCard(cards, card);
        }

        // 3. 匹配 J, Q, K, A, 2
        Pattern letterPattern = Pattern.compile("\\b([JQKA2])\\b");
        Matcher letterMatcher = letterPattern.matcher(text);
        while (letterMatcher.find()) {
            String card = letterMatcher.group(1);
            incrementCard(cards, card);
        }

        // 4. 检测连续相同字符（如 "333" 表示三张3）
        for (String card : new String[]{"3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2"}) {
            Pattern repeatPattern = Pattern.compile("(" + card + "){2,4}");
            Matcher repeatMatcher = repeatPattern.matcher(text);
            if (repeatMatcher.find()) {
                String matched = repeatMatcher.group(0);
                int count = matched.length() / card.length();
                if (count >= 2 && count <= 4) {
                    cards.put(card, Math.max(cards.getOrDefault(card, 0), count));
                }
            }
        }
    }

    private void incrementCard(Map<String, Integer> cards, String card) {
        cards.put(card, cards.getOrDefault(card, 0) + 1);
    }

    /**
     * 手动触发一次检查
     */
    public void triggerCheck() {
        checkHandler.removeCallbacks(checkRunnable);
        checkHandler.post(checkRunnable);
    }

    /**
     * 获取当前活动的窗口信息（用于调试）
     */
    public void dumpCurrentWindow() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                dumpNode(root, 0);
                root.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "dump window error: " + e.getMessage());
        }
    }

    private void dumpNode(AccessibilityNodeInfo node, int depth) {
        if (node == null) {
            return;
        }

        String indent = "  ".repeat(depth);
        String text = node.getText() != null ? node.getText().toString() : "";
        String contentDesc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        String viewId = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "";

        Log.d(TAG, String.format("%sNode: text='%s' desc='%s' id='%s'",
                indent, text, contentDesc, viewId));

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                dumpNode(child, depth + 1);
                child.recycle();
            }
        }
    }
}
