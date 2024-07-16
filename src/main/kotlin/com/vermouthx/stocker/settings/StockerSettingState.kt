package com.vermouthx.stocker.settings

import com.vermouthx.stocker.enums.StockerQuoteColorPattern
import com.vermouthx.stocker.enums.StockerQuoteProvider

class StockerSettingState {
    var version: String = ""
    var refreshInterval: Long = 1
    var quoteProvider: StockerQuoteProvider = StockerQuoteProvider.SINA
    var quoteColorPattern: StockerQuoteColorPattern = StockerQuoteColorPattern.RED_UP_GREEN_DOWN
    var aShareList: MutableList<String> = mutableListOf()
    var qHList: MutableList<String> = mutableListOf()
}