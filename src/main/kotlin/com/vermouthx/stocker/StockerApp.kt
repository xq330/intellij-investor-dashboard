package com.vermouthx.stocker

import com.intellij.openapi.application.ApplicationManager
import com.vermouthx.stocker.enums.StockerMarketIndex
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.listeners.StockerQuoteReloadNotifier.*
import com.vermouthx.stocker.listeners.StockerQuoteUpdateNotifier.*
import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.utils.StockerQuoteHttpUtil
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class StockerApp {

    private val setting = StockerSetting.instance
    private val messageBus = ApplicationManager.getApplication().messageBus

    private var scheduledExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(4)

    private var scheduleInitialDelay: Long = 3
    private val schedulePeriod: Long = StockerSetting.instance.refreshInterval

    fun schedule() {
        if (scheduledExecutorService.isShutdown) {
            scheduledExecutorService = Executors.newScheduledThreadPool(4)
            scheduleInitialDelay = 0
        }
        scheduledExecutorService.scheduleAtFixedRate(
            createQuoteUpdateThread(StockerMarketType.AShare, setting.aShareList),
            scheduleInitialDelay,
            schedulePeriod,
            TimeUnit.SECONDS
        )
        scheduledExecutorService.scheduleAtFixedRate(
            createQuoteUpdateThread(StockerMarketType.QH, setting.qhList),
            scheduleInitialDelay,
            schedulePeriod,
            TimeUnit.SECONDS
        )
    }

    fun shutdown() {
        scheduledExecutorService.shutdown()
    }

    fun isShutdown(): Boolean {
        return scheduledExecutorService.isShutdown
    }

    private fun clear() {
        messageBus.syncPublisher(STOCK_ALL_QUOTE_RELOAD_TOPIC).clear()
        messageBus.syncPublisher(STOCK_CN_QUOTE_RELOAD_TOPIC).clear()
        messageBus.syncPublisher(QH_QUOTE_RELOAD_TOPIC).clear()
    }

    fun shutdownThenClear() {
        shutdown()
        clear()
    }

    private fun createAllQuoteUpdateThread(): Runnable {
        return Runnable {
            val quoteProvider = setting.quoteProvider
            val allStockQuotes = listOf(
                StockerQuoteHttpUtil.get(StockerMarketType.AShare, quoteProvider, setting.aShareList),
                StockerQuoteHttpUtil.get(StockerMarketType.QH, quoteProvider, setting.qhList)
            ).flatten()
            val allStockIndices = listOf(
                StockerQuoteHttpUtil.get(StockerMarketType.AShare, quoteProvider, StockerMarketIndex.CN.codes),
                StockerQuoteHttpUtil.get(StockerMarketType.QH, quoteProvider, StockerMarketIndex.QH.codes)
            ).flatten()
            val publisher = messageBus.syncPublisher(STOCK_ALL_QUOTE_UPDATE_TOPIC)
            publisher.syncQuotes(allStockQuotes, setting.allStockListSize)
            publisher.syncIndices(allStockIndices)
        }
    }

    private fun createQuoteUpdateThread(marketType: StockerMarketType, stockCodeList: List<String>): Runnable {
        return Runnable {
            refresh(marketType, stockCodeList)
        }
    }

    private fun refresh(
        marketType: StockerMarketType, stockCodeList: List<String>
    ) {
        val quoteProvider = setting.quoteProvider
        val size = stockCodeList.size
        when (marketType) {
            StockerMarketType.AShare -> {
                val quotes = StockerQuoteHttpUtil.get(marketType, quoteProvider, stockCodeList)
                val indices = StockerQuoteHttpUtil.get(marketType, quoteProvider, StockerMarketIndex.CN.codes)
                val publisher = messageBus.syncPublisher(STOCK_CN_QUOTE_UPDATE_TOPIC)
                publisher.syncQuotes(quotes, size)
                publisher.syncIndices(indices)
            }
            StockerMarketType.QH -> {
                val quotes = StockerQuoteHttpUtil.get(marketType, quoteProvider, stockCodeList)
                val indices = StockerQuoteHttpUtil.get(marketType, quoteProvider, StockerMarketIndex.QH.codes)
                val publisher = messageBus.syncPublisher(QH_QUOTE_UPDATE_TOPIC)
                publisher.syncQuotes(quotes, size)
                publisher.syncIndices(indices)
            }
        }
    }
}
