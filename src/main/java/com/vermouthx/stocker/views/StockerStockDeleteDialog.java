package com.vermouthx.stocker.views;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.messages.MessageBus;
import com.vermouthx.stocker.entities.StockerQuote;
import com.vermouthx.stocker.enums.StockerMarketType;
import com.vermouthx.stocker.listeners.StockerQuoteDeleteNotifier;
import com.vermouthx.stocker.settings.StockerSetting;
import com.vermouthx.stocker.utils.StockerQuoteHttpUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockerStockDeleteDialog extends DialogWrapper {
    private final JPanel mPane = new JBPanel<>(new BorderLayout());
    private final JTabbedPane tabbedPane = new JBTabbedPane();
    private final Map<Integer, JScrollPane> tabMap = new HashMap<>();
    private StockerMarketType currentMarketSelection = StockerMarketType.AShare;

    public StockerStockDeleteDialog(Project project) {
        super(project);
        init();
        setTitle("Manage Stocks");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        tabbedPane.add("CN", createTabContent(0));
        tabbedPane.add("HK", createTabContent(1));
        tabbedPane.add("US", createTabContent(2));
        tabbedPane.addChangeListener(e -> {
            String selectedMarket = null;
            switch (tabbedPane.getSelectedIndex()) {
                case 0:
                    selectedMarket = StockerMarketType.AShare.getTitle();
                    break;
                case 1:
                    selectedMarket = StockerMarketType.HKStocks.getTitle();
                    break;
                case 2:
                    selectedMarket = StockerMarketType.USStocks.getTitle();
            }
            StockerSetting setting = StockerSetting.Companion.getInstance();
            if (StockerMarketType.AShare.getTitle().equals(selectedMarket)) {
                currentMarketSelection = StockerMarketType.AShare;
                List<StockerQuote> quotes = StockerQuoteHttpUtil.INSTANCE.get(StockerMarketType.AShare, setting.getQuoteProvider(), setting.getAShareList());
                setupStockSymbols(quotes);
                return;
            }
            if (StockerMarketType.HKStocks.getTitle().equals(selectedMarket)) {
                currentMarketSelection = StockerMarketType.HKStocks;
                List<StockerQuote> quotes = StockerQuoteHttpUtil.INSTANCE.get(StockerMarketType.HKStocks, setting.getQuoteProvider(), setting.getHkStocksList());
                setupStockSymbols(quotes);
                return;
            }
            if (StockerMarketType.USStocks.getTitle().equals(selectedMarket)) {
                currentMarketSelection = StockerMarketType.USStocks;
                List<StockerQuote> quotes = StockerQuoteHttpUtil.INSTANCE.get(StockerMarketType.USStocks, setting.getQuoteProvider(), setting.getUsStocksList());
                setupStockSymbols(quotes);
            }
        });
        mPane.add(tabbedPane, BorderLayout.CENTER);
        mPane.setMaximumSize(new Dimension(300, 400));
        mPane.setPreferredSize(new Dimension(300, 400));
        return mPane;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{};
    }

    private JScrollPane createTabContent(int index) {
        JScrollPane container = new JBScrollPane();
        tabMap.put(index, container);
        return container;
    }

    public void setupStockSymbols(List<StockerQuote> symbols) {
        JScrollPane container = tabMap.get(tabbedPane.getSelectedIndex());
        JPanel inner = new JBPanel<>();
        inner.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        for (StockerQuote symbol : symbols) {
            JPanel row = new JBPanel<>(new GridLayout(1, 3));
            row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()));
            row.setMaximumSize(new Dimension(400, 30));
            JLabel lbCode = new JBLabel(symbol.getCode());
            String name = symbol.getName();
            if (name.length() > 6) {
                name = name.substring(0, 6) + "...";
            }
            JLabel lbName = new JBLabel(name);
            JButton operation = new JButton("Delete");
            operation.addActionListener(e -> {
                StockerSetting setting = StockerSetting.Companion.getInstance();
                setting.removeCode(currentMarketSelection, symbol.getCode());
                MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
                StockerQuoteDeleteNotifier publisher = null;
                switch (currentMarketSelection) {
                    case AShare:
                        publisher = messageBus.syncPublisher(StockerQuoteDeleteNotifier.STOCK_CN_QUOTE_DELETE_TOPIC);
                        break;
                    case HKStocks:
                        publisher = messageBus.syncPublisher(StockerQuoteDeleteNotifier.STOCK_HK_QUOTE_DELETE_TOPIC);
                        break;
                    case USStocks:
                        publisher = messageBus.syncPublisher(StockerQuoteDeleteNotifier.STOCK_US_QUOTE_DELETE_TOPIC);
                }
                StockerQuoteDeleteNotifier publisherToAll = messageBus.syncPublisher(StockerQuoteDeleteNotifier.STOCK_ALL_QUOTE_DELETE_TOPIC);
                if (publisher != null) {
                    operation.setEnabled(false);
                    publisherToAll.after(symbol.getCode().toUpperCase());
                    publisher.after(symbol.getCode().toUpperCase());
                }
            });
            row.add(lbCode);
            row.add(lbName);
            row.add(operation);
            inner.add(row);
        }
        container.setViewportView(inner);
    }
}
