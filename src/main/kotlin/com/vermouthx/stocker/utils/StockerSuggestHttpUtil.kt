package com.vermouthx.stocker.utils

import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.entities.StockerSuggestion
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.enums.StockerQuoteProvider
import org.apache.commons.text.StringEscapeUtils
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object StockerSuggestHttpUtil {

    @JvmStatic
    private var futures: Map<String,String> = mapOf(
        Pair("玉米", "C"),
        Pair("大豆", "A"),
        Pair("豆粕", "M"),
        Pair("豆油", "Y"),
        Pair("棕榈油", "P"),
        Pair("白糖", "SR"),
        Pair("棉花", "CF"),
        Pair("PTA", "TA"),
        Pair("橡胶", "RU"),
        Pair("铜", "CU"),
        Pair("铝", "AL"),
        Pair("锌", "ZN"),
        Pair("铅", "PB"),
        Pair("镍", "NI"),
        Pair("锡", "SN"),
        Pair("黄金", "AU"),
        Pair("白银", "AG"),
        Pair("螺纹钢", "RB"),
        Pair("线材", "WR"),
        Pair("热卷", "HC"),
        Pair("铁矿石", "I"),
        Pair("焦煤", "JM"),
        Pair("焦炭", "J"),
        Pair("鸡蛋", "JD"),
        Pair("玻璃", "FG"),
        Pair("动力煤", "ZC"),
        Pair("甲醇", "MA"),
        Pair("聚乙烯", "L"),
        Pair("聚氯乙烯", "V"),
        Pair("聚丙烯", "PP"),
        Pair("沥青", "BU"),
        Pair("纸浆", "SP"),
        Pair("苹果", "AP"),
        Pair("红枣", "CJ"),
        Pair("尿素", "UR"),
        Pair("粳米", "RR"),
        Pair("液化气", "PG"),
        Pair("期权", "OPTIONS")
    )

    private val log = Logger.getInstance(javaClass)

    private val httpClientPool = run {
        val connectionManager = PoolingHttpClientConnectionManager()
        connectionManager.maxTotal = 20
        val requestConfig = RequestConfig.custom().build()
        HttpClients.custom().setConnectionManager(connectionManager).setDefaultRequestConfig(requestConfig)
            .useSystemProperties().build()
    }

    fun suggest(key: String, provider: StockerQuoteProvider): List<StockerSuggestion> {
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
            val response = httpClientPool.execute(httpGet)
            when (provider) {
                StockerQuoteProvider.SINA -> {
                    val responseText = EntityUtils.toString(response.entity, "UTF-8")
                    parseSinaSuggestion(responseText)
                }

                StockerQuoteProvider.TENCENT -> {
                    val responseText = EntityUtils.toString(response.entity, "UTF-8")
                    parseTencentSuggestion(responseText)
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
                "71" -> result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.Crypto))
                "81" -> result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.AShare))
                "87" -> result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.QH))
            }
        }
        return result
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
