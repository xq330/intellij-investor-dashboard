package com.vermouthx.stocker.listeners;

import com.intellij.util.messages.Topic;

public interface StockerQuoteDeleteNotifier {
    Topic<StockerQuoteDeleteNotifier> STOCK_ALL_QUOTE_DELETE_TOPIC = Topic.create("StockAllQuoteDeleteTopic", StockerQuoteDeleteNotifier.class);
    Topic<StockerQuoteDeleteNotifier> STOCK_CN_QUOTE_DELETE_TOPIC = Topic.create("StockCNQuoteDeleteTopic", StockerQuoteDeleteNotifier.class);
    Topic<StockerQuoteDeleteNotifier> QH_QUOTE_DELETE_TOPIC = Topic.create("QhQuoteDeleteTopic", StockerQuoteDeleteNotifier.class);

    void after(String code);
}
