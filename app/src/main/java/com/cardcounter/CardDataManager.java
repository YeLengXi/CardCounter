package com.cardcounter;

import java.util.HashMap;
import java.util.Map;

/**
 * 牌数据管理器 - 单例模式
 */
public class CardDataManager {

    private static CardDataManager instance;

    // 牌的初始数量
    private static final Map<String, Integer> INITIAL_CARDS = new HashMap<>();
    static {
        INITIAL_CARDS.put("大王", 1);
        INITIAL_CARDS.put("小王", 1);
        INITIAL_CARDS.put("2", 4);
        INITIAL_CARDS.put("A", 4);
        INITIAL_CARDS.put("K", 4);
        INITIAL_CARDS.put("Q", 4);
        INITIAL_CARDS.put("J", 4);
        INITIAL_CARDS.put("10", 4);
        INITIAL_CARDS.put("9", 4);
        INITIAL_CARDS.put("8", 4);
        INITIAL_CARDS.put("7", 4);
        INITIAL_CARDS.put("6", 4);
        INITIAL_CARDS.put("5", 4);
        INITIAL_CARDS.put("4", 4);
        INITIAL_CARDS.put("3", 4);
    }

    // 当前剩余数量
    private Map<String, Integer> currentCards;

    // 监听器
    private OnDataChangeListener listener;

    public interface OnDataChangeListener {
        void onCardChanged(String cardName, int count);
        void onTotalChanged(int total);
    }

    private CardDataManager() {
        reset();
    }

    public static CardDataManager getInstance() {
        if (instance == null) {
            instance = new CardDataManager();
        }
        return instance;
    }

    /**
     * 重置所有牌
     */
    public void reset() {
        currentCards = new HashMap<>();
        for (Map.Entry<String, Integer> entry : INITIAL_CARDS.entrySet()) {
            currentCards.put(entry.getKey(), entry.getValue());
        }
        notifyTotalChanged();
    }

    /**
     * 获取牌的当前数量
     */
    public int getCardCount(String cardName) {
        Integer count = currentCards.get(cardName);
        return count != null ? count : 0;
    }

    /**
     * 获取牌的初始数量
     */
    public int getInitialCount(String cardName) {
        Integer count = INITIAL_CARDS.get(cardName);
        return count != null ? count : 0;
    }

    /**
     * 设置牌的数量
     */
    public void setCardCount(String cardName, int count) {
        int max = getInitialCount(cardName);
        if (count < 0) count = 0;
        if (count > max) count = max;

        currentCards.put(cardName, count);
        notifyCardChanged(cardName, count);
        notifyTotalChanged();
    }

    /**
     * 增加牌的数量
     */
    public void addCard(String cardName) {
        int current = getCardCount(cardName);
        setCardCount(cardName, current + 1);
    }

    /**
     * 减少牌的数量
     */
    public void removeCard(String cardName) {
        int current = getCardCount(cardName);
        setCardCount(cardName, current - 1);
    }

    /**
     * 获取剩余总数
     */
    public int getTotalCount() {
        int total = 0;
        for (int count : currentCards.values()) {
            total += count;
        }
        return total;
    }

    /**
     * 获取所有牌
     */
    public Map<String, Integer> getAllCards() {
        return new HashMap<>(currentCards);
    }

    /**
     * 设置监听器
     */
    public void setOnDataChangeListener(OnDataChangeListener listener) {
        this.listener = listener;
    }

    private void notifyCardChanged(String cardName, int count) {
        if (listener != null) {
            listener.onCardChanged(cardName, count);
        }
    }

    private void notifyTotalChanged() {
        if (listener != null) {
            listener.onTotalChanged(getTotalCount());
        }
    }
}
