package edu.zju.se.management.http;

import com.sun.net.httpserver.HttpExchange;
import edu.zju.se.management.model.Admin;
import edu.zju.se.management.model.Stock;
import edu.zju.se.management.repository.AdminRepository;
import edu.zju.se.management.repository.AuditRepository;
import edu.zju.se.management.repository.BlacklistRepository;
import edu.zju.se.management.repository.ReviewRepository;
import edu.zju.se.management.repository.StockRepository;
import edu.zju.se.management.service.AuthService;
import edu.zju.se.management.service.AccountManagementClient;
import edu.zju.se.management.service.CentralTradingClient;
import edu.zju.se.management.util.JsonUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminHandler extends BaseHandler {
    private final AuthService authService;
    private final AdminRepository adminRepository;
    private final StockRepository stockRepository;
    private final BlacklistRepository blacklistRepository;
    private final ReviewRepository reviewRepository;
    private final AuditRepository auditRepository;
    private final CentralTradingClient centralTradingClient;
    private final AccountManagementClient accountManagementClient;

    public AdminHandler(AuthService authService, AdminRepository adminRepository, StockRepository stockRepository, BlacklistRepository blacklistRepository, ReviewRepository reviewRepository, AuditRepository auditRepository, CentralTradingClient centralTradingClient, AccountManagementClient accountManagementClient) {
        this.authService = authService;
        this.adminRepository = adminRepository;
        this.stockRepository = stockRepository;
        this.blacklistRepository = blacklistRepository;
        this.reviewRepository = reviewRepository;
        this.auditRepository = auditRepository;
        this.centralTradingClient = centralTradingClient;
        this.accountManagementClient = accountManagementClient;
    }

    @Override
    protected void route(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("POST".equals(method) && "/api/admin/login".equals(path)) {
            LoginRequest request = JsonUtil.read(exchange.getRequestBody(), LoginRequest.class);
            sendJson(exchange, 200, ApiResponse.ok(authService.login(request.username, request.password)));
            return;
        }

        if ("POST".equals(method) && "/api/admin/register".equals(path)) {
            RegisterRequest request = JsonUtil.read(exchange.getRequestBody(), RegisterRequest.class);
            if (request.confirmPassword == null || !request.confirmPassword.equals(request.password)) {
                throw new IllegalArgumentException("两次输入的密码不一致");
            }
            sendJson(exchange, 200, ApiResponse.ok(authService.register(request.username, request.password)));
            return;
        }

        Admin admin = authService.requireAdmin(bearerToken(exchange));
        String[] parts = pathParts(exchange);

        if ("GET".equals(method) && "/api/admin/stocks".equals(path)) {
            Object stocks;
            if (centralTradingClient.enabled()) {
                List<Map<String, Object>> centralStocks = centralTradingClient.getStocks();
                if (!"SUPER_ADMIN".equals(admin.role())) {
                    centralStocks.removeIf(stock -> {
                        try { return !stockRepository.canManage(admin.id(), admin.role(), stock.get("stockCode").toString()); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    });
                }
                stocks = centralStocks;
            } else stocks = stockRepository.findStocksForAdmin(admin.id(), admin.role());
            sendJson(exchange, 200, ApiResponse.ok(stocks));
            return;
        }

        if ("GET".equals(method) && parts.length == 6 && "stocks".equals(parts[3]) && "orders".equals(parts[5])) {
            String stockCode = parts[4];
            requireStockAccess(admin, stockCode);
            if (centralTradingClient.enabled()) {
                sendJson(exchange, 200, ApiResponse.ok(centralTradingClient.getOrders(stockCode)));
                return;
            }
            Stock stock = stockRepository.findByCode(stockCode)
                    .orElseThrow(() -> new IllegalArgumentException("股票不存在"));
            Map<String, Object> data = new HashMap<>();
            data.put("stockCode", stock.stockCode());
            data.put("stockName", stock.stockName());
            data.put("lastPrice", stock.lastPrice());
            data.put("lastQuantity", stock.lastQuantity());
            data.put("status", stock.status());
            data.put("buyOrders", stockRepository.findOrders(stockCode, "BUY"));
            data.put("sellOrders", stockRepository.findOrders(stockCode, "SELL"));
            sendJson(exchange, 200, ApiResponse.ok(data));
            return;
        }

        if ("POST".equals(method) && parts.length == 6 && "stocks".equals(parts[3]) && "limit-rate".equals(parts[5])) {
            requireStockAccess(admin, parts[4]);
            LimitRateRequest request = JsonUtil.read(exchange.getRequestBody(), LimitRateRequest.class);
            if (centralTradingClient.enabled()) {
                centralTradingClient.setLimitRate(parts[4], request.stockType == null ? "NORMAL" : request.stockType, request.nextLimitRate);
            } else {
                stockRepository.updateLimitRate(parts[4], request.nextLimitRate);
            }
            auditRepository.log(admin, "SET_LIMIT_RATE", "STOCK", parts[4], request.nextLimitRate);
            sendJson(exchange, 200, ApiResponse.ok(Map.of("stockCode", parts[4], "nextLimitRate", request.nextLimitRate)));
            return;
        }

        if ("POST".equals(method) && parts.length == 6 && "stocks".equals(parts[3]) && "pause".equals(parts[5])) {
            requireStockAccess(admin, parts[4]);
            if (centralTradingClient.enabled()) centralTradingClient.pause(parts[4]);
            else stockRepository.updateStatus(parts[4], "PAUSED");
            auditRepository.log(admin, "PAUSE_TRADING", "STOCK", parts[4], "暂停交易");
            sendJson(exchange, 200, ApiResponse.ok(Map.of("stockCode", parts[4], "status", "PAUSED")));
            return;
        }

        if ("POST".equals(method) && parts.length == 6 && "stocks".equals(parts[3]) && "resume".equals(parts[5])) {
            requireStockAccess(admin, parts[4]);
            if (centralTradingClient.enabled()) centralTradingClient.resume(parts[4]);
            else stockRepository.updateStatus(parts[4], "TRADING");
            auditRepository.log(admin, "RESUME_TRADING", "STOCK", parts[4], "重启交易");
            sendJson(exchange, 200, ApiResponse.ok(Map.of("stockCode", parts[4], "status", "TRADING")));
            return;
        }

        if ("GET".equals(method) && "/api/admin/blacklist".equals(path)) {
            sendJson(exchange, 200, ApiResponse.ok(blacklistRepository.findAllActive()));
            return;
        }

        if ("GET".equals(method) && "/api/admin/reviews/pending".equals(path)) {
            sendJson(exchange, 200, ApiResponse.ok(reviewRepository.findPendingManual()));
            return;
        }

        if ("POST".equals(method) && parts.length == 6 && "reviews".equals(parts[3])) {
            ManualReviewRequest request = JsonUtil.read(exchange.getRequestBody(), ManualReviewRequest.class);
            if ("approve".equals(parts[5])) {
                reviewRepository.decideManualReview(parts[4], true, request.reason, admin.username());
                auditRepository.log(admin, "APPROVE_REVIEW", "REVIEW", parts[4], request.reason);
                sendJson(exchange, 200, ApiResponse.ok(Map.of("reviewId", parts[4], "approved", true)));
                return;
            }
            if ("reject".equals(parts[5])) {
                reviewRepository.decideManualReview(parts[4], false, request.reason, admin.username());
                auditRepository.log(admin, "REJECT_REVIEW", "REVIEW", parts[4], request.reason);
                sendJson(exchange, 200, ApiResponse.ok(Map.of("reviewId", parts[4], "approved", false)));
                return;
            }
        }

        if ("POST".equals(method) && "/api/admin/blacklist".equals(path)) {
            BlacklistRequest request = JsonUtil.read(exchange.getRequestBody(), BlacklistRequest.class);
            if (request.idCardNo == null || !request.idCardNo.matches("\\d{17}[0-9Xx]")) {
                throw new IllegalArgumentException("身份证号必须是合法的 18 位格式");
            }
            if (request.userName == null || request.userName.isBlank()) {
                throw new IllegalArgumentException("缺少必填字段 userName");
            }
            Object entry = blacklistRepository.add(
                    request.idCardNo.toUpperCase(),
                    request.userName.trim(),
                    request.fundAccountNo,
                    request.securityAccountNo,
                    request.reason
            );
            auditRepository.log(admin, "ADD_BLACKLIST", "INVESTOR", request.idCardNo, request.reason);
            sendJson(exchange, 200, ApiResponse.ok(entry));
            return;
        }

        if ("DELETE".equals(method) && parts.length == 5 && "blacklist".equals(parts[3])) {
            blacklistRepository.remove(parts[4]);
            auditRepository.log(admin, "REMOVE_BLACKLIST", "BLACKLIST", parts[4], "移出黑名单");
            sendJson(exchange, 200, ApiResponse.ok(Map.of("removed", true)));
            return;
        }

        if ("POST".equals(method) && "/api/admin/password".equals(path)) {
            PasswordRequest request = JsonUtil.read(exchange.getRequestBody(), PasswordRequest.class);
            if (!edu.zju.se.management.util.PasswordUtil.verify(request.oldPassword, admin.passwordHash())) {
                throw new SecurityException("原密码错误");
            }
            adminRepository.changePassword(admin.id(), request.newPassword);
            auditRepository.log(admin, "CHANGE_PASSWORD", "ADMIN", Long.toString(admin.id()), "修改密码");
            sendJson(exchange, 200, ApiResponse.ok(Map.of("changed", true)));
            return;
        }

        if ("GET".equals(method) && "/api/admin/users".equals(path)) {
            requireSuperAdmin(admin);
            sendJson(exchange, 200, ApiResponse.ok(adminRepository.findAllWithPermissions()));
            return;
        }

        if ("POST".equals(method) && parts.length == 6 && "users".equals(parts[3]) && "permissions".equals(parts[5])) {
            requireSuperAdmin(admin);
            PermissionRequest request = JsonUtil.read(exchange.getRequestBody(), PermissionRequest.class);
            long targetAdminId = Long.parseLong(parts[4]);
            String role = "SUPER_ADMIN".equals(request.role) ? "SUPER_ADMIN" : "ADMIN";
            adminRepository.replacePermissions(targetAdminId, role, request.stockCodes == null ? List.of() : request.stockCodes);
            auditRepository.log(admin, "UPDATE_PERMISSIONS", "ADMIN", parts[4], role + " " + request.stockCodes);
            sendJson(exchange, 200, ApiResponse.ok(Map.of("updated", true)));
            return;
        }

        if ("GET".equals(method) && "/api/admin/audit-logs".equals(path)) {
            requireSuperAdmin(admin);
            sendJson(exchange, 200, ApiResponse.ok(auditRepository.findRecent(200)));
            return;
        }

        if ("POST".equals(method) && "/api/admin/accounts/freeze".equals(path)) {
            AccountControlRequest request = JsonUtil.read(exchange.getRequestBody(), AccountControlRequest.class);
            validateAccountControl(request);
            accountManagementClient.freeze(request.accountType, request.accountNo, request.freezeType, request.reason);
            auditRepository.log(admin, "FREEZE_ACCOUNT", request.accountType, request.accountNo, request.freezeType + " " + request.reason);
            sendJson(exchange, 200, ApiResponse.ok(Map.of("accountNo", request.accountNo, "frozen", true)));
            return;
        }

        if ("POST".equals(method) && "/api/admin/accounts/unfreeze".equals(path)) {
            AccountControlRequest request = JsonUtil.read(exchange.getRequestBody(), AccountControlRequest.class);
            validateAccountControl(request);
            accountManagementClient.unfreeze(request.accountType, request.accountNo, request.freezeType, request.reason);
            auditRepository.log(admin, "UNFREEZE_ACCOUNT", request.accountType, request.accountNo, request.freezeType + " " + request.reason);
            sendJson(exchange, 200, ApiResponse.ok(Map.of("accountNo", request.accountNo, "frozen", false)));
            return;
        }

        sendJson(exchange, 404, ApiResponse.fail("接口不存在"));
    }

    public static class LoginRequest {
        public String username;
        public String password;
    }

    public static class RegisterRequest {
        public String username;
        public String password;
        public String confirmPassword;
    }

    public static class LimitRateRequest {
        public String nextLimitRate;
        public String stockType;
    }

    public static class BlacklistRequest {
        public String idCardNo;
        public String userName;
        public String fundAccountNo;
        public String securityAccountNo;
        public String reason;
    }

    public static class ManualReviewRequest {
        public String reason;
    }

    public static class PasswordRequest {
        public String oldPassword;
        public String newPassword;
    }

    public static class PermissionRequest {
        public String role;
        public List<String> stockCodes;
    }

    public static class AccountControlRequest {
        public String accountType;
        public String accountNo;
        public String freezeType;
        public String reason;
    }

    private void requireStockAccess(Admin admin, String stockCode) throws Exception {
        if (!stockRepository.canManage(admin.id(), admin.role(), stockCode)) {
            throw new SecurityException("无权管理股票 " + stockCode);
        }
    }

    private void requireSuperAdmin(Admin admin) {
        if (!"SUPER_ADMIN".equals(admin.role())) throw new SecurityException("需要超级管理员权限");
    }

    private void validateAccountControl(AccountControlRequest request) {
        if (!"FUND".equals(request.accountType) && !"SECURITY".equals(request.accountType))
            throw new IllegalArgumentException("accountType 只能是 FUND 或 SECURITY");
        if (request.accountNo == null || request.accountNo.isBlank()) throw new IllegalArgumentException("账户号不能为空");
        if (!"LOSS".equals(request.freezeType) && !"VIOLATION".equals(request.freezeType))
            throw new IllegalArgumentException("freezeType 只能是 LOSS 或 VIOLATION");
    }
}
