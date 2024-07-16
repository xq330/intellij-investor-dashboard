package com.vermouthx.stocker.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.messages.MessageBus;
import com.vermouthx.stocker.entities.StockerSuggestion;
import com.vermouthx.stocker.enums.StockerMarketType;
import com.vermouthx.stocker.listeners.StockerQuoteDeleteNotifier;
import com.vermouthx.stocker.settings.StockerSetting;

public class StockerActionUtil {
    public static boolean addStock(StockerMarketType market, StockerSuggestion suggest, Project project) {
        StockerSetting setting = StockerSetting.Companion.getInstance();
        String code = suggest.getCode();
        String fullName = suggest.getName();
        if (!setting.containsCode(code)) {
            if (StockerQuoteHttpUtil.INSTANCE.validateCode(market, setting.getQuoteProvider(), code)) {
                switch (market) {
                    case AShare:
                        return setting.getAShareList().add(code);
                    case QH:
                        return setting.getQhList().add(code);
                }
            } else {
                String errMessage = fullName + " is not supported.";
                String errTitle = "Not Supported Stock";
                Messages.showErrorDialog(project, errMessage, errTitle);
                removeStock(market, suggest);
                return false;
            }
        }
        return false;
    }

    public static boolean removeStock(StockerMarketType market, StockerSuggestion suggest) {
        StockerSetting setting = StockerSetting.Companion.getInstance();
        MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
        setting.removeCode(market, suggest.getCode());
        StockerQuoteDeleteNotifier publisher = null;
        switch (market) {
            case AShare:
                publisher = messageBus.syncPublisher(StockerQuoteDeleteNotifier.STOCK_CN_QUOTE_DELETE_TOPIC);
                break;
            case QH:
                publisher = messageBus.syncPublisher(StockerQuoteDeleteNotifier.QH_QUOTE_DELETE_TOPIC);
                break;

        }
        StockerQuoteDeleteNotifier publisherToAll = messageBus.syncPublisher(StockerQuoteDeleteNotifier.STOCK_ALL_QUOTE_DELETE_TOPIC);
        if (publisher != null) {
            publisherToAll.after(suggest.getCode());
            publisher.after(suggest.getCode());
            return true;
        }
        return false;
    }
}
