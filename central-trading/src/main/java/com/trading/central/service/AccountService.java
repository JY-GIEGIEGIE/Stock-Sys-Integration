package com.trading.central.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 对接账户管理系统的真实结算接口（/api/external/trade/*）。
 *
 * 资金变更 txn_type ∈ {买入冻结, 买入扣款, 卖出回款, 撤单解冻}
 * 持仓变更 change_type ∈ {买入增加, 卖出冻结, 卖出扣减, 撤单释放}
 * amount / quantity 一律取正数，方向由类型码决定。
 *
 * app.account.mock=true 时全部短路（只记日志、不调账户系统），用于脱账户系统独立演示。
 */
@Slf4j
@Service
public class AccountService {

    private final RestTemplate restTemplate;

    @Value("${app.account.api-base}")
    private String apiBase;

    @Value("${app.account.mock:true}")
    private boolean isMock;

    public AccountService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("rawtypes")
    private void callAccountApi(String path, Map<String, Object> body) {
        if (isMock) {
            log.debug("[AccountService Mock] {} {}", path, body);
            return;
        }
        String url = apiBase + path;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("账户接口 " + path + " 返回 " + response.getStatusCode());
            }
        } catch (RuntimeException e) {
            log.error("[AccountService] {} 调用失败: {} body={}", path, e.getMessage(), body);
            throw e;
        }
    }

    // ============ 资金：POST /api/external/trade/fund-balance ============

    private void updateFund(String fundAccNo, String refOrderId, String txnType, BigDecimal amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("fund_acc_no", fundAccNo);
        body.put("ref_order_id", refOrderId);
        body.put("txn_type", txnType);
        body.put("amount", amount == null ? BigDecimal.ZERO : amount.abs());
        callAccountApi("/api/external/trade/fund-balance", body);
    }

    /** 买单受理：冻结资金（可用→冻结）。ref 用 orderId。 */
    public void freezeFunds(String fundAccNo, BigDecimal amount, String refOrderId) {
        updateFund(fundAccNo, refOrderId, "买入冻结", amount);
    }

    /** 买方成交：扣划冻结资金。ref 用 tradeNo。 */
    public void settleBuyFunds(String fundAccNo, BigDecimal amount, String refOrderId) {
        updateFund(fundAccNo, refOrderId, "买入扣款", amount);
    }

    /** 卖方成交：资金回款（→可用）。ref 用 tradeNo。 */
    public void settleSellFunds(String fundAccNo, BigDecimal amount, String refOrderId) {
        updateFund(fundAccNo, refOrderId, "卖出回款", amount);
    }

    /** 买单撤单：解冻资金。ref 用 orderId。 */
    public void releaseFunds(String fundAccNo, BigDecimal amount, String refOrderId) {
        updateFund(fundAccNo, refOrderId, "撤单解冻", amount);
    }

    // ============ 持仓：POST /api/external/trade/security-holding ============

    private void updateHolding(String secAccNo, String stockCode, String stockName,
                               String refOrderId, String changeType, int quantity, BigDecimal price) {
        Map<String, Object> body = new HashMap<>();
        body.put("sec_acc_no", secAccNo);
        body.put("stock_code", stockCode);
        body.put("stock_name", (stockName != null && !stockName.isBlank()) ? stockName : stockCode);
        body.put("ref_order_id", refOrderId);
        body.put("change_type", changeType);
        body.put("quantity", Math.abs(quantity));
        if (price != null) {
            body.put("price", price);
        }
        callAccountApi("/api/external/trade/security-holding", body);
    }

    /** 卖单受理：冻结持仓（可用→冻结，账户系统校验可卖足额）。ref 用 orderId。 */
    public void freezeHolding(String secAccNo, String stockCode, String stockName, int quantity, String refOrderId) {
        updateHolding(secAccNo, stockCode, stockName, refOrderId, "卖出冻结", quantity, null);
    }

    /** 卖方成交：扣减冻结持仓。ref 用 tradeNo。 */
    public void settleSellerHolding(String secAccNo, String stockCode, String stockName, int quantity, BigDecimal price, String refOrderId) {
        updateHolding(secAccNo, stockCode, stockName, refOrderId, "卖出扣减", quantity, price);
    }

    /** 买方成交：增加持仓。ref 用 tradeNo。 */
    public void settleBuyerHolding(String secAccNo, String stockCode, String stockName, int quantity, BigDecimal price, String refOrderId) {
        updateHolding(secAccNo, stockCode, stockName, refOrderId, "买入增加", quantity, price);
    }

    /** 卖单撤单：释放冻结持仓。ref 用 orderId。 */
    public void releaseHolding(String secAccNo, String stockCode, String stockName, int quantity, String refOrderId) {
        updateHolding(secAccNo, stockCode, stockName, refOrderId, "撤单释放", quantity, null);
    }

    // ============ 账户名（展示用，失败降级到 用户XXXX）============

    @SuppressWarnings("rawtypes")
    public String getAccountName(String accountId) {
        if (accountId == null) {
            return "用户";
        }
        String suffix = accountId.length() >= 4 ? accountId.substring(accountId.length() - 4) : accountId;
        if (isMock) {
            return "用户" + suffix;
        }
        try {
            String url = apiBase + "/api/fund-accounts/" + accountId + "/name";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object name = response.getBody().get("accountName");
                if (name != null) return name.toString();
                Object realName = response.getBody().get("realName");
                if (realName != null) return realName.toString();
            }
        } catch (Exception e) {
            log.debug("[AccountService] 获取账户名称失败: {}，使用默认值", accountId);
        }
        return "用户" + suffix;
    }
}
