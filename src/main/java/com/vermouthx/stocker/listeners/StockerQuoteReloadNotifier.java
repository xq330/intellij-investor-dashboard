package com.vermouthx.stocker.listeners;

import com.intellij.util.messages.Topic;

public interface StockerQuoteReloadNotifier {
    Topic<StockerQuoteReloadNotifier> STOCK_ALL_QUOTE_RELOAD_TOPIC = Topic.create("StockerAllQuoteReloadTopic", StockerQuoteReloadNotifier.class);
    Topic<StockerQuoteReloadNotifier> STOCK_CN_QUOTE_RELOAD_TOPIC = Topic.create("StockerCNQuoteReloadTopic", StockerQuoteReloadNotifier.class);
    Topic<StockerQuoteReloadNotifier> QH_QUOTE_RELOAD_TOPIC = Topic.create("QhQuoteReloadTopic", StockerQuoteReloadNotifier.class);

    void clear();
}
