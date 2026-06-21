package edu.zju.se.management.service;

import edu.zju.se.management.model.ReviewRequest;
import edu.zju.se.management.model.ReviewResult;
import edu.zju.se.management.model.Stock;
import edu.zju.se.management.repository.BlacklistRepository;
import edu.zju.se.management.repository.ReviewRepository;
import edu.zju.se.management.repository.StockRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public class ReviewService {
    private static final BigDecimal LARGE_AMOUNT_THRESHOLD = new BigDecimal("100000.00");
    private static final int DAILY_AUTO_APPROVE_LIMIT = 5;

    private final StockRepository stockRepository;
    private final BlacklistRepository blacklistRepository;
    private final ReviewRepository reviewRepository;

    public ReviewService(StockRepository stockRepository, BlacklistRepository blacklistRepository, ReviewRepository reviewRepository) {
        this.stockRepository = stockRepository;
        this.blacklistRepository = blacklistRepository;
        this.reviewRepository = reviewRepository;
    }

    public ReviewResult review(ReviewRequest request) throws SQLException {
        normalize(request);
        validateRequired(request);
        ReviewResult result = evaluate(request);
        reviewRepository.save(request, result);
        return result;
    }

    public Object findResult(String reviewId) throws SQLException {
        return reviewRepository.findByReviewId(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("审查记录不存在"));
    }

    private void normalize(ReviewRequest request) {
        if ((request.side == null || request.side.isBlank()) && request.direction != null) {
            request.side = request.direction;
        }
        if (request.reviewId == null || request.reviewId.isBlank()) {
            request.reviewId = "R-" + UUID.randomUUID().toString().replace("-", "");
        }
        if (request.orderId == null || request.orderId.isBlank()) {
            request.orderId = "O-" + UUID.randomUUID().toString().replace("-", "");
        }
        if (request.accountId == null || request.accountId.isBlank()) {
            request.accountId = request.fundAccountNo;
        }
        if (request.securityAccountNo == null || request.securityAccountNo.isBlank()) {
            request.securityAccountNo = request.fundAccountNo;
        }
        if (request.amount == null && request.price != null && request.quantity > 0) {
            request.amount = request.price.multiply(BigDecimal.valueOf(request.quantity)).setScale(2, RoundingMode.HALF_UP);
        }
        if (request.clientTime == null || request.clientTime.isBlank()) {
            request.clientTime = OffsetDateTime.now().toString();
        }
    }

    private ReviewResult evaluate(ReviewRequest request) throws SQLException {
        if (stockRepository.orderExists(request.orderId)) {
            return ReviewResult.rejected(request.reviewId, request.orderId, "DUPLICATE_ORDER", "重复委托");
        }
        boolean blacklisted = request.idCardNo != null && !request.idCardNo.isBlank()
                ? blacklistRepository.isBlacklistedByIdCard(request.idCardNo)
                : blacklistRepository.isBlacklistedByUserName(request.userName);
        if (blacklisted) {
            return ReviewResult.rejected(request.reviewId, request.orderId, "ACCOUNT_RESTRICTED", "投资者在交易黑名单中");
        }
        Optional<Stock> stock = stockRepository.findByCode(request.stockCode);
        if (stock.isEmpty()) {
            return ReviewResult.rejected(request.reviewId, request.orderId, "STOCK_RESTRICTED", "股票代码不存在或未授权交易");
        }
        if ("PAUSED".equals(stock.get().status())) {
            return ReviewResult.rejected(request.reviewId, request.orderId, "STOCK_RESTRICTED", "股票处于暂停交易状态");
        }
        if (request.price.compareTo(BigDecimal.ZERO) <= 0 || request.price.scale() > 2) {
            return ReviewResult.rejected(request.reviewId, request.orderId, "PRICE_ABNORMAL", "委托价格必须大于 0 且最多两位小数");
        }
        if (request.quantity <= 0) {
            return ReviewResult.rejected(request.reviewId, request.orderId, "QUANTITY_ABNORMAL", "委托数量必须大于 0");
        }
        BigDecimal expectedAmount = request.price.multiply(BigDecimal.valueOf(request.quantity)).setScale(2, RoundingMode.HALF_UP);
        if (request.amount.setScale(2, RoundingMode.HALF_UP).compareTo(expectedAmount) != 0) {
            return ReviewResult.rejected(request.reviewId, request.orderId, "OTHER", "委托金额与价格乘数量不一致");
        }
        if (request.amount.compareTo(LARGE_AMOUNT_THRESHOLD) > 0) {
            return ReviewResult.pendingManual(request.reviewId, request.orderId, "RISK_LIMIT_EXCEEDED", "单笔委托金额超过 100000 元，需要人工核验");
        }
        int todayCount = reviewRepository.countTodayByInvestor(request.userName, request.fundAccountNo);
        if (todayCount >= DAILY_AUTO_APPROVE_LIMIT) {
            return ReviewResult.pendingManual(request.reviewId, request.orderId, "FREQUENT_TRADING", "同一投资者当日委托已达到 5 笔，需要人工核验");
        }
        return ReviewResult.approved(request.reviewId, request.orderId);
    }

    private void validateRequired(ReviewRequest request) {
        require(request.reviewId, "reviewId");
        require(request.orderId, "orderId");
        require(request.accountId, "accountId");
        require(request.fundAccountNo, "fundAccountNo");
        require(request.stockCode, "stockCode");
        require(request.side, "side");
        if (!"BUY".equals(request.side) && !"SELL".equals(request.side)) {
            throw new IllegalArgumentException("side 只能是 BUY 或 SELL");
        }
        if (request.price == null) {
            throw new IllegalArgumentException("缺少必填字段 price");
        }
        if (request.amount == null) {
            throw new IllegalArgumentException("缺少必填字段 amount");
        }
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填字段 " + field);
        }
    }
}
