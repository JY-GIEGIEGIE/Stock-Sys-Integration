package com.stock.publish.service;

import com.stock.publish.dto.QuoteDTO;

import java.math.BigDecimal;

public interface MarketService {
    QuoteDTO getQuote(String stockCode);
    void refreshQuotes();

    /** 消费 Kafka 成交回报：累加主力动向并推送 K线 tick。 */
    void ingestTrade(String stockCode, String buyerName, String sellerName, BigDecimal price, long quantity);
}
