package com.vermouthx.stocker.views.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import com.vermouthx.stocker.StockerAppManager
import com.vermouthx.stocker.entities.StockerQuote
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.utils.StockerQuoteHttpUtil
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class StockerManagementDialog(val project: Project?) : DialogWrapper(project) {

    private val setting = StockerSetting.instance

    private val tabMap: MutableMap<Int, JPanel> = mutableMapOf()

    private val currentSymbols: MutableMap<StockerMarketType, CollectionListModel<StockerQuote>> = mutableMapOf()

    private var currentMarketSelection: StockerMarketType = StockerMarketType.AShare

    init {
        title = "Manage Favorite Stocks"
        init()
    }

    override fun createCenterPanel(): DialogPanel {
        val tabbedPane = JBTabbedPane()
        tabbedPane.add("CN", createTabContent(0))
        tabbedPane.add("HK", createTabContent(1))
        tabbedPane.add("US", createTabContent(2))
//        tabbedPane.add("Crypto", createTabContent(3))
        tabbedPane.addChangeListener {
            currentMarketSelection = when (tabbedPane.selectedIndex) {
                0 -> {
                    StockerMarketType.AShare
                }
                1 -> {
                    StockerMarketType.HKStocks
                }
                2 -> {
                    StockerMarketType.USStocks
                }
//                3 -> {
//                    StockerMarketType.Crypto
//                }
                else -> return@addChangeListener
            }
        }

        val aShareListModel = CollectionListModel(
            StockerQuoteHttpUtil.get(
                StockerMarketType.AShare,
                setting.quoteProvider,
                setting.aShareList
            )
        )
        currentSymbols[StockerMarketType.AShare] = aShareListModel
        tabMap[0]?.let { pane ->
            renderTabPane(pane, aShareListModel)
        }

        val hkStocksListModel = CollectionListModel(
            StockerQuoteHttpUtil.get(
                StockerMarketType.HKStocks,
                setting.quoteProvider,
                setting.hkStocksList
            )
        )
        currentSymbols[StockerMarketType.HKStocks] = hkStocksListModel
        tabMap[1]?.let { pane ->
            renderTabPane(pane, hkStocksListModel)
        }

        val usStocksListModel = CollectionListModel(
            StockerQuoteHttpUtil.get(
                StockerMarketType.USStocks,
                setting.quoteProvider,
                setting.usStocksList
            )
        )
        currentSymbols[StockerMarketType.USStocks] = usStocksListModel
        tabMap[2]?.let { pane ->
            renderTabPane(pane, usStocksListModel)
        }

        tabbedPane.selectedIndex = 0
        return panel {
            row {
                tabbedPane(CCFlags.grow)
            }
        }.withPreferredWidth(300).withPreferredHeight(500)
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : OkAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    val myApplication = StockerAppManager.myApplication(project)
                    if (myApplication != null) {
                        myApplication.shutdownThenClear()
                        currentSymbols[StockerMarketType.AShare]?.let { symbols ->
                            setting.aShareList = symbols.items.map { it.code }.toMutableList()
                        }
                        currentSymbols[StockerMarketType.HKStocks]?.let { symbols ->
                            setting.hkStocksList = symbols.items.map { it.code }.toMutableList()
                        }
                        currentSymbols[StockerMarketType.USStocks]?.let { symbols ->
                            setting.usStocksList = symbols.items.map { it.code }.toMutableList()
                        }
                        myApplication.schedule()
                    }
                    super.actionPerformed(e)
                }
            },
            cancelAction
        )
    }

    private fun createTabContent(index: Int): JComponent {
        val pane = JPanel(BorderLayout())
        tabMap[index] = pane
        return panel {
            row {
                pane(CCFlags.grow)
            }
        }.withPreferredHeight(500)
    }

    private fun renderTabPane(pane: JPanel, listModel: CollectionListModel<StockerQuote>) {
        val list = JBList(listModel)
        val toolbar = ToolbarDecorator.createDecorator(list)
        val toolbarPane = toolbar.createPanel()
        list.installCellRenderer { symbol ->
            panel {
                row {
                    label(symbol.code)
                    label(
                        if (symbol.name.length <= 20) {
                            symbol.name
                        } else {
                            "${symbol.name.substring(0, 20)}..."
                        }
                    )
                }
            }.withBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16))
        }
        val scrollPane = JBScrollPane()
        scrollPane.setViewportView(list)
        pane.add(toolbarPane, BorderLayout.NORTH)
        pane.add(scrollPane, BorderLayout.CENTER)
    }

}
