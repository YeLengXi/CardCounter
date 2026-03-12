package com.cardcounter;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 牌面识别器
 * 使用 ML Kit OCR 识别屏幕中的牌面
 */
public class CardRecognizer {

    private static final String TAG = "CardRecognizer";
    private static CardRecognizer instance;

    private TextRecognizer recognizer;
    private CardRecognitionCallback callback;

    public interface CardRecognitionCallback {
        void onCardsRecognized(Map<String, Integer> playedCards);
        void onRecognitionError(String error);
    }

    public static CardRecognizer getInstance() {
        if (instance == null) {
            instance = new CardRecognizer();
        }
        return instance;
    }

    public void init() {
        // 使用中文文本识别器
        recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    }

    /**
     * 识别屏幕截图中打出的牌
     * @param bitmap 屏幕截图
     * @param callback 识别结果回调
     */
    public void recognizeCards(Bitmap bitmap, CardRecognitionCallback callback) {
        this.callback = callback;

        if (bitmap == null) {
            if (callback != null) {
                callback.onRecognitionError("截图为空");
            }
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    Map<String, Integer> playedCards = parsePlayedCards(text);
                    Log.d(TAG, "识别到的牌: " + playedCards);
                    if (callback != null) {
                        callback.onCardsRecognized(playedCards);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "OCR识别失败: " + e.getMessage());
                    if (callback != null) {
                        callback.onRecognitionError(e.getMessage());
                    }
                });
    }

    /**
     * 解析 OCR 结果，提取打出的牌
     */
    private Map<String, Integer> parsePlayedCards(Text text) {
        Map<String, Integer> playedCards = new HashMap<>();

        String fullText = text.getText();
        Log.d(TAG, "OCR识别结果: " + fullText);

        // 匹配各种牌型
        // 匹配单张: 3, 4, 5, 6, 7, 8, 9, 10, J, Q, K, A, 2, 小王, 大王
        // 使用正则表达式匹配

        // 匹配数字牌 3-10
        Pattern digitPattern = Pattern.compile("\\b([3-9]|10)\\b");
        Matcher digitMatcher = digitPattern.matcher(fullText);
        while (digitMatcher.find()) {
            String card = digitMatcher.group(1);
            playedCards.put(card, playedCards.getOrDefault(card, 0) + 1);
        }

        // 匹配 J, Q, K, A, 2
        Pattern letterPattern = Pattern.compile("\\b([JQKA2])\\b");
        Matcher letterMatcher = letterPattern.matcher(fullText);
        while (letterMatcher.find()) {
            String card = letterMatcher.group(1);
            playedCards.put(card, playedCards.getOrDefault(card, 0) + 1);
        }

        // 匹配小王、大王
        if (fullText.contains("小王")) {
            playedCards.put("小王", playedCards.getOrDefault("小王", 0) + 1);
        }
        if (fullText.contains("大王")) {
            playedCards.put("大王", playedCards.getOrDefault("大王", 0) + 1);
        }

        // 分析文本块中的数字（用于识别多张相同牌）
        for (Text.TextBlock block : text.getTextBlocks()) {
            String blockText = block.getText();
            // 查找如 "333" "4444" 等连续相同数字
            for (String card : new String[]{"3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2"}) {
                Pattern pattern = Pattern.compile("(" + card + ")+");
                Matcher matcher = pattern.matcher(blockText);
                if (matcher.find()) {
                    String matched = matcher.group(0);
                    int count = matched.length() / card.length();
                    if (count > 0 && count <= 4) { // 最多4张相同牌
                        playedCards.put(card, Math.max(playedCards.getOrDefault(card, 0), count));
                    }
                }
            }
        }

        return playedCards;
    }

    /**
     * 从截图中识别玩家手中的牌
     * 这个方法会分析屏幕底部的牌区
     */
    public void recognizeHandCards(Bitmap bitmap, CardRecognitionCallback callback) {
        if (bitmap == null) {
            if (callback != null) {
                callback.onRecognitionError("截图为空");
            }
            return;
        }

        // 裁剪底部手牌区域（假设手牌在屏幕底部30%区域）
        int height = bitmap.getHeight();
        int width = bitmap.getWidth();
        int handCardHeight = (int) (height * 0.3);

        Bitmap handCardArea = Bitmap.createBitmap(
                bitmap,
                0,
                height - handCardHeight,
                width,
                handCardHeight
        );

        recognizeCards(handCardArea, callback);
    }

    public void release() {
        if (recognizer != null) {
            recognizer.close();
        }
    }
}
