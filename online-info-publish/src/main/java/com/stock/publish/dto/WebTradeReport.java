package com.stock.publish.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 中央交易系统经 Kafka topic {@code webinfo.trade.report} 推送的成交回报。
 * 字段与中央交易系统 TradeReportMsg 对齐（按字段名反序列化，忽略类型头）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebTradeReport {
    private String tradeNo;
    private String buyerOrderId;
    private String sellerOrderId;
    private String stockCode;
    private BigDecimal tradePrice;
    private Long tradeQuantity;
    private String tradeTime;
    private String buyerName;
    private String sellerName;
}
