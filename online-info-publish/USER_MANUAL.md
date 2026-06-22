# 网上信息发布系统 · 用户手册

> 面向投资者的行情信息门户：实时行情、盘口、主力动向、K 线与技术指标。
> 本手册说明三档用户的可见范围、如何登录与升级 VIP、各功能用途，以及数据来源与计算/呈现方式。
> （部署/启动请见仓库 `README.md` 与 `STARTUP.md`；本手册不是 README。）

---

## 一、系统简介

网上信息发布系统是股票交易系统中的**行情信息发布端**：它不接受下单，只负责把市场行情**展示**给投资者。
- 前端：`http://localhost:3000`（Vue3）
- 后端：`http://localhost:8083`（Spring Boot，接口前缀 `/api/publish`）
- 数据：每 5 秒从中央交易系统拉取最新成交与盘口，计算成行情/K线/主力动向后呈现。

系统按用户身份分**三档权限**，可见信息逐级增多。

---

## 二、用户角色与可见信息范围

身份由后端 `AuthInterceptor` 依据请求头 `X-Fund-Acc-No`（登录后携带的资金账号）判定：
- 无该头 → **游客 GUEST**；
- 有该头、但订阅表无记录 → 自动建档（`is_premium=false`）→ **标准 STANDARD**；
- 订阅表 `is_premium=true` → **VIP PREMIUM_VIP**。

订阅状态存于 `stock_publish.local_user_subscription`（字段 `global_user_id` / `is_premium` / `upgrade_time`）。

### 可见范围对照表

| 信息 / 功能 | 游客 GUEST | 标准 STANDARD | VIP PREMIUM_VIP |
|---|:---:|:---:|:---:|
| 股票搜索（代码/名称/拼音） | ✓ | ✓ | ✓ |
| 行情列表 / 个股报价（最新价、昨收、涨跌幅、停牌状态） | ✓ | ✓ | ✓ |
| 盘口（买一/卖一价与挂单量） | ✗ | ✓ | ✓ |
| 主力动向（当日最大买方/卖方账户及数量） | ✗ | ✓ | ✓ |
| K 线：5M / 15M / 30M / 1H / 1D（含 MA5/MA10） | ✗ | ✓ | ✓ |
| K 线：1W / 1M / 1Y（长周期） | ✗ | ✗ | ✓ |
| MACD 指标（DIF / DEA / MACD 柱） | ✗ | ✗ | ✓ |

> 权限在**后端强制**：游客请求盘口/主力字段会被置空（`maskByRole`），请求 K 线接口直接 403；标准用户请求长周期 403、且返回的 K 线中 MACD 字段被置 null。前端只是同步呈现这一规则（越权按钮会弹出升级框并把图表模糊）。

**逐档说明：**
- **游客**：可浏览行情列表、搜索、看个股的基础报价；进入个股详情只见顶部报价卡 + 一句“登录后可查看盘口/主力/K线”的引导。
- **标准用户**（登录即获得）：在个股详情解锁**盘口**、**主力动向**、**日内及日 K 线（含均线）**；长周期与 MACD 仍锁定。
- **VIP**：解锁**全周期 K 线（周/月/年）**与 **MACD** 技术指标。

---

## 三、如何登录

> **重要：开户/注册不在本系统办理。** 投资者的资金账户与证券账户由**管理员在“账户管理系统”**（`http://localhost:5173`）创建。本系统只做登录识别，不提供注册入口。

登录步骤：
1. 在本系统页面点顶栏 **登录**（或导航门户进入“账户管理系统”），跳转到账户系统登录页 `http://localhost:5173/login`。
2. 在账户系统用**资金账号 + 密码**登录（账号由管理员开好；演示账号见 README 测试账号矩阵，如 `2026000000000001 / 123`）。
3. 登录后拿到自己的**资金账号**，回到本系统，在顶栏输入框**粘贴资金账号并回车**。
4. 系统即把你识别为**标准用户 STANDARD**（角色与账号持久化在浏览器 localStorage）。此后所有请求自动带上 `X-Fund-Acc-No` 头,后端据此放行对应数据。
5. 点 **退出** 即清除身份、回到游客。

---

## 四、如何升级 VIP

1. 以标准用户登录后，进入任一个股详情页。
2. 点击 K 线上的**长周期按钮（1W / 1M / 1Y）**或 MACD 相关入口 —— 因越权会弹出**升级会员**弹窗（图表同时被模糊）。
3. 弹窗内点“立即升级”，进入**模拟支付**（手机号 + 验证码，价格 ¥99.00/月）。
4. 确认后前端调用 `POST /api/publish/user/upgrade`，后端把 `local_user_subscription.is_premium` 置为 `true`。
5. 升级成功，角色变为 **PREMIUM_VIP**，图表模糊消失，全周期 K 线与 MACD 立即解锁。

> 说明：支付为演示用的模拟流程；升级本质是把订阅表的 VIP 标志打开。游客调用升级接口会被拒（请先登录），已是 VIP 重复升级也会被拒。

---

## 五、页面与功能

- **导航门户（`/`）**：全系统统一入口，点击进入各子系统（本系统进“行情首页”，其余进各自登录页）。
- **行情首页（`/home`）**：大盘指数、股票搜索、实时行情表（每 5 秒刷新，点行可进个股）。对所有角色一致可见。
- **个股详情（`/stock/:code`）**：顶部报价卡（全员）+ 盘口/主力/K 线（按角色解锁，见第二节）。每 5 秒刷新报价；K 线在进入时加载、可切换周期。
- 顶栏在非门户页提供 **返回导航** 按钮。

---

## 六、数据来源与计算 / 呈现方式

### 6.1 数据来源（Kafka 订阅 + 快照补全）

- **成交流（主数据，Kafka）**：本系统后端**订阅 Kafka topic `webinfo.trade.report`**（中央交易系统每撮合出一笔成交即发布），由 `WebTradeReportConsumer` 消费 → 经 `MarketService.ingestTrade(...)` 驱动**主力动向累加**与 **K 线 tick 入库**。这是行情/K线/主力的实时数据来源。
- **价格与盘口快照（补全，REST）**：`MarketServiceImpl.refreshQuotes`（cron `*/5 * * * * *`）每 5 秒调 `GET /api/central-trading/market/snapshot/{code}` 取最新价与买一/卖一盘口写入行情缓存（盘口不在成交流里，故由快照补全）。
- **启动时**：`GET /api/central-trading/stocks` 同步全量股票字典 → 写入 `stock_publish.sync_stock_info`。

### 6.2 落地与缓存

- **Kafka 成交回报到达时**（`ingestTrade`）：每笔成交追加到 Redis 列表 `tick:{code}`（供 K 线聚合）；并对买/卖账户在 Redis Hash `top_buyer:{code}:{日期}` / `top_seller:{code}:{日期}` 用 `HINCRBY` 原子累加（主力动向）。
- **REST 快照刷新时**：最新价 + 盘口写 Redis `quote:{code}`（TTL 5s，缓存击穿用 `lock:quote:{code}` 分布式锁保护）。

### 6.3 计算

- **行情**：直接取 Redis `quote:`（缺失则回退查 `sync_stock_info` + `kline_5m_data`）；按角色 `maskByRole` 决定是否带盘口/主力。
- **5 分钟 K 线**：定时任务（cron `0 */5 * * * *`，每 5 分钟整）从 `tick:{code}` 聚合 OHLCV（开=首笔价、收=末笔价、高/低=极值、量=求和）写入 `kline_5m_data`，再清空该 tick 列表。
- **多周期 K 线**：从 `kline_5m_data` 按“块合并”聚合——15M=3 根、30M=6、1H=12、1D=48、1W=240、1M=1056、1Y=12000（5M 直接返回）。
- **均线/指标**：MA5/MA10 为滑动窗口均值；**MACD** 用 EMA 递推：`EMA12`(α=2/13)、`EMA26`(α=2/27)、`DIF=EMA12-EMA26`、`DEA`(α=2/10)、`MACD柱=2×(DIF-DEA)`，首日初始化为收盘价。MACD 字段仅对 VIP 返回。
- **主力动向**：查当日 `top_buyer/top_seller` Hash，取累计量最大的账户作为“最大买方/卖方”。

### 6.4 刷新节奏一览

| 环节 | 周期 |
|---|---|
| 订阅 Kafka 成交回报（webinfo.trade.report） | 实时（每笔成交） |
| 后端轮询中央交易取行情 | 每 5 秒 |
| Redis 行情缓存 TTL | 5 秒 |
| 5 分钟 K 线落盘 | 每 5 分钟整 |
| 前端轮询后端（首页/详情报价） | 每 5 秒 |
| 主力累计 | 每笔成交即时 |

---

## 附录 A：自动挂单 → 信息发布 全链路内部流程

演示数据由自动交易脚本驱动，端到端经过 5 个子系统：

```
auto-trade.sh
  │  每1~3秒随机生成买/卖单，curl POST
  ▼
交易客户端 :8090 (Node)   /api/client/orders
  │  写本地 trading_client.order_record，并发布到 Kafka
  ▼  topic: central.order.command  (含 fundAccountNo/securityAccountNo/...)
中央交易 CT :8082        @KafkaListener 消费 → 撮合引擎
  │  写 central_trading.order_book / trade_record / 价格历史
  │  成交后发 Kafka：client.trade.report / client.order.report / client.stock.quote
  │  webinfo.trade.report —— **本发布系统订阅此 topic 获取成交流**
  ▼  CT 同时提供 REST：/api/central-trading/market/snapshot/{code}（价格+盘口）
网上信息发布后端 :8083    @KafkaListener 订阅 webinfo.trade.report（成交流）
  │  每笔成交 → Redis tick:/top_buyer:/top_seller:（K线与主力）
  │  另每5秒 REST 拉 CT 快照 → Redis quote:（最新价+盘口）
  │  每5分钟聚合 tick → MySQL stock_publish.kline_5m_data
  ▼  对外 REST：/api/publish/market/quote、/market/kline、/stock/search
发布前端 :3000 (Vue)      每5秒轮询后端 → 行情表 / 个股报价 / 盘口 / 主力 / K线
```

要点：全链路以 **Kafka** 为主：下单→撮合走 `central.order.command`；撮合结果→信息发布走 **`webinfo.trade.report`**（本系统 `@KafkaListener` 订阅消费，驱动主力与 K 线）；价格/盘口快照另由 REST 每 5 秒补全。

---

## 附录 B：自动交易测试说明

**目的**：在没有真人操作时，持续产生成交，让行情/K线/主力有数据可展示。

**启动 / 停止**：
- 随全栈启动：`bash /e/stock-system/start-stack.sh`（其最后一步即 `nohup bash auto-trade.sh`）；`--no-auto` 可跳过。
- 单独启动：`cd /e/stock-system && nohup bash auto-trade.sh > /tmp/at.log 2>&1 &`
- 停止（MSYS 下按命令行匹配）：
  `powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { \$_.CommandLine -like '*auto-trade.sh*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"`

**脚本行为**：10 个资金账户 × 10 只股票，随机买/卖各半，价格在昨收附近浮动（买 -2%~+1%、卖 -1%~+2%），数量 100~5000 股整百，每 1~3 秒一笔；启动后**内置等 60 秒**待 Kafka 就绪再开始下单。

**如何验证链路通**：
1. **看成交增长**：`"C:/Users/JY/mysql/bin/mysql.exe" -u root -proot -N -e "SELECT COUNT(*) FROM central_trading.trade_record"` —— 多次执行计数应持续增加（约每分钟 +20~60 笔）。
2. **看前端行情刷新**：开 `http://localhost:3000/home`，行情表的最新价/涨跌幅每 5 秒更新；进个股详情可见盘口、主力动向实时变化。
3. **看 K 线充实**：5 分钟 K 线每 5 分钟落盘一次——**第一根 K 线约需运行 5 分钟以上**才出现；长周期（周/月/年）需积累更久。

**数据充实预期**：
- 行情/盘口/主力：约 10~15 秒即有（撮合出成交后下一轮 5 秒轮询即拉到；主力为即时累计）。
- K 线：≥ 5 分钟出现首根；多跑几分钟后日内 K 线与 MACD（VIP）才有意义。

---

## 速查

| 项 | 值 |
|---|---|
| 前端 | http://localhost:3000 （门户 `/`、行情首页 `/home`、个股 `/stock/:code`） |
| 后端接口前缀 | http://localhost:8083/api/publish |
| 身份标识头 | `X-Fund-Acc-No`（=资金账号） |
| 角色 | GUEST / STANDARD / PREMIUM_VIP |
| 订阅表 | `stock_publish.local_user_subscription`（`is_premium`） |
| 升级接口 | `POST /api/publish/user/upgrade` |
| 行情/ K线/搜索 | `GET /market/quote/{code}`、`GET /market/kline?stockCode=&period=`、`GET /stock/search?keyword=` |
| 开户/注册 | 由管理员在账户管理系统（:5173）办理 |
