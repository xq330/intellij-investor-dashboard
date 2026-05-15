package com.vermouthx.stocker.utils

import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.entities.StockerSuggestion
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.enums.StockerQuoteProvider
import org.apache.commons.text.StringEscapeUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.google.gson.Gson
import com.google.gson.JsonObject

object StockerSuggestHttpUtil {
    private val log = Logger.getInstance(javaClass)
    private val httpClientPool = StockerHttpClientPool(log)
    
    // 从配置文件加载期货品种数据
    private val futures: Map<String, String> by lazy {
        loadFuturesFromConfig()
    }
    
    /**
     * 从 resources/futures.json 加载期货品种配置
     */
    private fun loadFuturesFromConfig(): Map<String, String> {
        return try {
            val inputStream = javaClass.getResourceAsStream("/futures.json")
                ?: throw IllegalStateException("Cannot find futures.json in resources")
            
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)
            
            val allFutures = mutableMapOf<String, String>()
            
            // 遍历所有交易所配置
            for ((exchangeKey, exchangeValue) in jsonObject.entrySet()) {
                val exchangeObj = exchangeValue.asJsonObject
                val futuresObj = exchangeObj.getAsJsonObject("futures")
                
                // 提取该交易所的所有期货品种
                for ((name, code) in futuresObj.entrySet()) {
                    allFutures[name] = code.asString
                }
            }
            
            log.info("Loaded ${allFutures.size} futures from configuration")
            allFutures
        } catch (e: Exception) {
            log.error("Failed to load futures configuration, using empty map", e)
            emptyMap()
        }
    }

    fun closeConnections() {
        httpClientPool.close()
    }

    /**
     * Search for stock/crypto suggestions
     * @param key Search term
     * @param provider Quote provider (Sina or Tencent)
     * @param marketTypeFilter Optional filter to limit results to specific market types.
     *                         If null, returns all results. If specified, only returns matching market types.
     * @return List of suggestions matching the filter
     */
    fun suggest(
        key: String,
        provider: StockerQuoteProvider,
        marketTypeFilter: Set<StockerMarketType>? = null
    ): List<StockerSuggestion> {
        var list = parseQhSuggestion(key)
        if(list.size > 0){
            return list
        }
        val url = "${provider.suggestHost}$key"
        val httpGet = HttpGet(url)
        if (provider == StockerQuoteProvider.SINA) {
            httpGet.setHeader("Referer", "https://finance.sina.com.cn") // Sina API requires this header
        }
        return try {
            httpClientPool.client().execute(httpGet).use { response ->
                val allSuggestions = when (provider) {
                    StockerQuoteProvider.SINA -> {
                        val responseText = EntityUtils.toString(response.entity, "UTF-8")
                        parseSinaSuggestion(responseText)
                    }

                    StockerQuoteProvider.TENCENT -> {
                        val responseText = EntityUtils.toString(response.entity, "UTF-8")
                        parseTencentSuggestion(responseText)
                    }
                }

                // Apply market type filter if specified
                if (marketTypeFilter != null) {
                    allSuggestions.filter { it.market in marketTypeFilter }
                } else {
                    allSuggestions
                }
            }
        } catch (e: Exception) {
            log.warn(e)
            emptyList()
        }
    }

    private fun parseQhSuggestion(key: String) :List<StockerSuggestion> {
        val result = mutableListOf<StockerSuggestion>()
        for (entry in futures) {
            if(key == entry.key){
                var monthList = generateYearMonthList()
                result.add(StockerSuggestion(futures[key].toString() + "0",  key + "连续",StockerMarketType.QH))
                for (month in monthList) {
                    result.add(StockerSuggestion(futures[key].toString() + "" + month,  key + "" + month,StockerMarketType.QH))
                }
                break
            }
        }
        return result;
    }

    fun generateYearMonthList(): List<String> {
        // 获取当前的日期
        val currentDate = LocalDate.now()
        // 准备一个列表来存储12个月的年月字符串
        val yearMonthList = mutableListOf<String>()
        // 日期格式化器，用于获取年份后两位和月份
        val formatter = DateTimeFormatter.ofPattern("yyMM")
        // 生成从当前月开始的12个月
        for (i in 0 until 12) {
            // 计算当前日期之后的月份
            val futureDate = currentDate.plusMonths(i.toLong())
            // 将日期格式化为特定字符串
            val formattedDate = futureDate.format(formatter)
            // 将生成的字符串加入列表
            yearMonthList.add(formattedDate)
        }
        return yearMonthList
    }

    private fun parseSinaSuggestion(responseText: String): List<StockerSuggestion> {
        val result = mutableListOf<StockerSuggestion>()
        val regex = Regex("var suggestvalue=\"(.*?)\";")
        val matchResult = regex.find(responseText)
        val (_, snippetsText) = matchResult!!.groupValues
        if (snippetsText.isEmpty()) {
            return emptyList()
        }
        val snippets = snippetsText.split(";")
        for (snippet in snippets) {
            val columns = snippet.split(",")
            if (columns.size < 5) {
                continue
            }
            when (columns[1]) {
                "11" -> {
                    if (columns[4].startsWith("S*ST")) {
                        continue
                    }
                    result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.AShare))
                }

                "22" -> {
                    val code = columns[3].replace("of", "")
                    when {
                        code.startsWith("15") || code.startsWith("16") || code.startsWith("18") -> result.add(
                            StockerSuggestion("SZ$code", columns[4], StockerMarketType.AShare)
                        )

                        code.startsWith("50") || code.startsWith("51") -> result.add(
                            StockerSuggestion(
                                "SH$code", columns[4], StockerMarketType.AShare
                            )
                        )
                    }
                }

                "31" -> result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.HKStocks))
                "41" -> result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.USStocks))
                "71" -> {
                    // Only include crypto codes that follow the supported pattern: BTC{COIN}{FIAT}
                    // Examples: BTCBTCUSD, BTCETHUSD, BTCBTCCNY
                    val cryptoCode = columns[3].uppercase()
                    if (isSupportedCryptoCode(cryptoCode)) {
                        result.add(StockerSuggestion(cryptoCode, columns[4], StockerMarketType.Crypto))
                    }
                }
                "81" -> result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.AShare))
                "87" -> result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.QH))
            }
        }
        return result
    }

    /**
     * Check if a crypto code follows Sina's supported format.
     * Based on testing, Sina only supports USD/USDT-based crypto pairs with BTC prefix.
     *
     * Supported pattern: BTC{COIN}USD or BTC{COIN}USDT where {COIN} is the cryptocurrency name
     *
     * Examples of supported codes:
     * - BTCBTCUSD (Bitcoin/USD) ✅
     * - BTCBTCUSDT (Bitcoin/USDT) ✅
     * - BTCETHUSD (Ethereum/USD) ✅
     * - BTCETHUSDT (Ethereum/USDT) ✅
     * - BTCLTCUSD (Litecoin/USD) ✅
     *
     * Unsupported examples:
     * - BTCUSD (too short, missing coin name) ❌
     * - ETHUSD (missing BTC prefix) ❌
     * - BCHUSD (missing BTC prefix) ❌
     * - BTCBTCCNY (CNY not supported) ❌
     * - BTCBTCEUR (EUR not supported) ❌
     */
    private fun isSupportedCryptoCode(code: String): Boolean {
        // Must start with "BTC" prefix
        if (!code.startsWith("BTC")) {
            return false
        }

        // Must end with "USD" or "USDT"
        val endsWithUSD = code.endsWith("USD")
        val endsWithUSDT = code.endsWith("USDT")
        if (!endsWithUSD && !endsWithUSDT) {
            return false
        }

        // Length check: minimum is BTCBTCUSD (9), with USDT it's 10+
        // Maximum reasonable length is 15 chars
        if (code.length < 9 || code.length > 15) {
            return false
        }

        // Pattern: BTC + {COIN} + (USD|USDT)
        // The coin name part must exist (at least 3 chars after BTC and before USD/USDT)
        val fiatSuffix = if (endsWithUSDT) "USDT" else "USD"
        val coinPart = code.substring(3, code.length - fiatSuffix.length)

        // Coin name must have at least 3 characters
        if (coinPart.length < 3) {
            return false
        }

        return true
    }

    private fun parseTencentSuggestion(responseText: String): List<StockerSuggestion> {
        if (responseText.isEmpty()) {
            return emptyList()
        }
        val result = mutableListOf<StockerSuggestion>()
        val snippets = responseText.replace("v_hint=\"", "").replace("\"", "").split("^")
        for (snippet in snippets) {
            val columns = snippet.split("~")
            if (columns.size < 3) {
                continue
            }
            val type = columns[0]
            val code = columns[1]
            val rawName = columns[2]
            val name = StringEscapeUtils.unescapeJava(rawName)
            when (type) {
                "sz", "sh" -> result.add(StockerSuggestion(type.uppercase() + code, name, StockerMarketType.AShare))

                "hk" -> result.add(StockerSuggestion(code, name, StockerMarketType.HKStocks))

                "us" -> result.add(StockerSuggestion(code.split(".")[0].uppercase(), name, StockerMarketType.USStocks))
            }
        }
        return result
    }
}
