require("dotenv").config();

const express = require("express");
const path = require("path");
const cors = require("cors");
const sessionsRouter = require("./routes/sessions");
const ordersRouter = require("./routes/orders");
const tradesRouter = require("./routes/trades");
const alertsRouter = require("./routes/alerts");
const notificationsRouter = require("./routes/notifications");
const centralRouter = require("./routes/central");
const { startKafka } = require("./kafka");

const app = express();
const port = Number(process.env.PORT || 8090);

app.use(cors());
app.use(express.json());

app.get("/api/client/health", (req, res) => {
  res.json({ ok: true, service: "trading-client-api" });
});

app.use("/api/client/sessions", sessionsRouter);
app.use("/api/client/orders", ordersRouter);
app.use("/api/client/trades", tradesRouter);
app.use("/api/client/alerts", alertsRouter);
app.use("/api/client/notifications", notificationsRouter);
app.use("/api/client/central", centralRouter);

// 代理：将 /api/external/* 转发到账户管理系统后端（解决 CORS 问题）
const ACCOUNT_BACKEND = process.env.ACCOUNT_BACKEND || "http://localhost:8080";
app.all("/api/external/*", async (req, res, next) => {
  try {
    const target = `${ACCOUNT_BACKEND}${req.originalUrl}`;
    const headers = { "Content-Type": "application/json" };
    const body = req.method !== "GET" && req.method !== "HEAD"
      ? JSON.stringify(req.body) : undefined;
    const resp = await fetch(target, { method: req.method, headers, body });
    const data = await resp.json().catch(() => ({}));
    res.status(resp.status).json(data);
  } catch (err) {
    console.error(`Proxy error for ${req.originalUrl}: ${err.message}`);
    res.status(502).json({ code: 5000, message: "账户管理系统不可达" });
  }
});

// 静态文件服务：将项目根目录的 index.html + js/ 作为前端页面
app.use(express.static(path.join(__dirname, "..")));

app.use((err, req, res, next) => {
  console.error(err);
  const statusCode = err.statusCode || 500;
  res.status(statusCode).json({
    ok: false,
    message: statusCode === 500 ? "Trading client server error" : err.message,
  });
});

app.listen(port, () => {
  console.log(`Trading client API listening on http://localhost:${port}`);
});

startKafka().then((result) => {
  if (result.ok) {
    console.log("Kafka pipeline connected");
  } else {
    console.log(`Kafka pipeline not started: ${result.message}`);
  }
});
