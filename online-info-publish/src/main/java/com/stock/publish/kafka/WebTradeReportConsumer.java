package com.stock.publish.kafka;

import com.stock.publish.dto.WebTradeReport;
import com.stock.publish.service.MarketService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 订阅中央交易系统推送的成交回报（Kafka topic: {@code webinfo.trade.report}）。
 *
 * 每条成交驱动：主力动向累加 + K线 tick 入库。
 * 这是本系统的实时数据来源——取代了原先对中央交易系统的 REST 轮询取成交。
 */
@Component
public class WebTradeReportConsumer {

    private final MarketService marketService;

    public WebTradeReportConsumer(MarketService marketService) {
        this.marketService = marketService;
    }

    @KafkaListener(
            topics = "webinfo.trade.report",
            groupId = "${spring.kafka.consumer.group-id:webinfo-publish-group}")
    public void onTradeReport(WebTradeReport report) {
        if (report == null || report.getStockCode() == null) {
            return;
        }
        BigDecimal price = report.getTradePrice() != null ? report.getTradePrice() : BigDecimal.ZERO;
        long qty = report.getTradeQuantity() != null ? report.getTradeQuantity() : 0L;
        marketService.ingestTrade(
                report.getStockCode(),
                report.getBuyerName(),
                report.getSellerName(),
                price,
                qty);
    }
}
