# ALGO DESK — Demonstration Guide

> **EDUCATIONAL SIMULATION ONLY – REAL MARKET DATA, VIRTUAL TRADING. NO REAL-MONEY TRANSACTIONS.**

A step-by-step guide for demonstrating the system. Every command and every number in this guide has
been run on this machine; nothing here is illustrative.

---

## 1. Application startup

### Requirements

| Requirement | Version |
|---|---|
| JDK | 21 |
| Maven | 3.9+ (bundled with IntelliJ works) |
| JavaFX | 21.0.2 (resolved by Maven, nothing to install) |
| MySQL | 9.x — **optional** |
| Internet | **optional** — the app runs fully offline |

### Run

```bash
mvn javafx:run
```

Or run `com.tradingdesk.Main` from IntelliJ.

The window opens at 1366×768 (minimum 1180×700) and is verified at 1366×768, 1600×900 and
1920×1080, including Windows 125% display scaling.

### What to expect on first launch

- Sidebar shows all **nine** screens.
- Header shows `ALGO DESK`, run state, live clock, database status and the permanent disclaimer.
- Status bar bottom-left names the **actual data source** — `LIVE WebSocket`, `LIVE REST`,
  `SIMULATED`, `HISTORICAL` or `DELAYED`.
- Press **START** to begin the processing pipeline.

---

## 2. Database configuration (optional)

The application runs **in-memory only** when MySQL is not configured. Nothing fails; the sidebar
simply reads `In-memory only (MySQL not configured)`.

Credentials are read **only** from environment variables. They are never read from a file, never
logged, and never displayed.

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL, e.g. `jdbc:mysql://localhost:3306/trading_desk` |
| `DB_USERNAME` | database user (use the non-root `trading_app` account) |
| `DB_PASSWORD` | that user's password |

The one-time account provisioning script is `src/main/resources/sql/provision_user.sql`. It creates
the `trading_desk` database and the `trading_app` user. **Do not use the MySQL root account.** Set the
three variables in your own environment or IntelliJ run configuration — do not commit them.

> Do not paste real passwords into this file, into a chat, or into a screenshot during the demo.

Migrations are additive and versioned (V1–V6) and recorded in `schema_version`. Re-running is a
no-op. No migration drops or truncates data.

Verify persistence:

```bash
mvn test -Ddb.enabled=true -Dtest=MySqlPersistenceIntegrationTest,MigrationRunnerIntegrationTest
```

Expected: **14 tests, 0 failures** (2 migration + 12 real CRUD over `instruments`, `market_events`,
`trading_signals`, `orders`, `executions`, `portfolio_snapshots`, `system_metrics`).

---

## 3. Demonstrating live market data

Real data comes from **public, read-only** Binance endpoints. No API key is used or needed.

### In the application

1. Launch the app. Market data connects automatically on a background thread — the window never
   freezes during the WebSocket handshake.
2. Look at the **bottom-left status badge**. With a working connection it reads:

   ```
   LIVE WebSocket · Streaming 5 symbols
   ```

   and is **green**. If Binance is unreachable it reads `SIMULATED · …` and is **amber**.
3. **Dashboard → Market Watch** table shows the five streamed instruments with real prices, real
   session change and real volume.
4. **System Monitoring** shows the connection detail, live tick count, duplicates suppressed and
   reconnects.

### From the command line

```bash
mvn test -Dlive.enabled=true -Dtest=LiveMarketDataPipelineIntegrationTest
```

This proves the decisive claim: real ticks travel **through the bounded queue and worker pool**, not
around them. A typical run reported:

```
market data mode : LIVE WebSocket
live ticks       : 8
events processed : 47
```

### Source labels — how to read them

| Badge | Meaning |
|---|---|
| `LIVE WebSocket · Streaming 5 symbols` | Real-time exchange stream |
| `LIVE REST · WebSocket unavailable; using REST` | Real data, polled over HTTPS |
| `SIMULATED · No network; using the simulator` | **No external source.** Prices are generated locally |
| `HISTORICAL · …` | Real candles fetched for backtesting |

A simulated price is never labelled live. The label is derived from the mode the supervisor actually
achieved, not from a constant.

---

## 4. Demonstrating the fallback chain

Order of degradation: **LIVE_WS → LIVE_REST → SIMULATED**.

```bash
mvn test -Dlive.enabled=true -Dtest=MarketDataFallbackChainTest
```

Expected: **6 tests, 0 failures**, and a log containing

```
INFO: Market data degraded to LIVE REST
INFO: Market data degraded to SIMULATED
```

This exercises each branch for real, including the middle one (dead WebSocket, working HTTPS) that
matters behind a corporate proxy.

---

## 5. Demonstrating virtual order execution

Everything is filled against **virtual cash**. There is no exchange connection.

1. Press **START** so market events are flowing.
2. Go to **Orders**. Choose symbol, side (BUY/SELL), type
   (MARKET / LIMIT / STOP / STOP_LIMIT / TAKE_PROFIT), quantity and price.
3. Submit. The order enters the table and moves through
   `NEW → OPEN → PARTIALLY_FILLED → FILLED` (or `CANCELLED` / `REJECTED`).
4. Watch **Portfolio**: quantity, average entry price and market value update after the fill.
5. **Dashboard** shows portfolio value, realized and unrealized P&L.

Two details worth pointing out:

- **A market order does not fill instantly** at the displayed price. It waits for the next tick and
  fills at the *ask* (buy) or *bid* (sell), including spread, slippage and commission.
- **Partial fills** accumulate over successive ticks until the order is complete.

Automated proof:

```bash
mvn test -Dtest=PaperBrokerTest,SimulatedExecutionEngineTest,WorkingOrderLifecycleTest
```

Expected: **39 tests, 0 failures**, asserting fill price, position size and cash movement.

---

## 6. Demonstrating risk management

Go to **Risk Management**. Each rule is shown with its current state and the rejection history.

| Rule | Behaviour |
|---|---|
| Maximum order quantity | Oversized orders rejected |
| Maximum total position size | Exposure cap |
| Maximum daily simulated loss | Trading stops for the day |
| Price deviation | Far-from-market limits rejected |
| Duplicate order prevention | Same order resubmitted within the window rejected |

Every rejection carries a **human-readable reason**, shown in the table and written to the log — no
silent failures.

```bash
mvn test -Dtest=DefaultRiskManagerTest
```

Expected: **8 tests, 0 failures**.

---

## 7. Demonstrating performance monitoring

Go to **System Monitoring** and **Dashboard → System Performance**.

| Metric | Meaning |
|---|---|
| Events processed | Total drained from the bounded queue |
| Throughput | Events/s from actual counts and elapsed time |
| Average / peak latency | Processing cost, `System.nanoTime()` |
| P50 / P95 / P99 | Latency percentiles |
| Queue depth / capacity | 25,000-slot bounded queue |
| Active workers | Worker threads currently draining |
| Dropped / rejected | Backpressure drops and invalid events |
| CPU utilization | Process load (`getProcessCpuLoad`) |

Press **RUN STRESS TEST** on the System screen. It runs concurrent producers against a worker pool on
a background thread and prints **measured** throughput, latency percentiles, drops and peak queue
depth.

```bash
mvn test -Dtest=BenchmarkReportTest
```

Measured on this machine (`processing` = dequeue→work done, `wait` = publish→dequeue,
`e2e` = publish→work done):

| Scenario | events/s | proc p50 | wait p50 | e2e p50 | dropped |
|---|---|---|---|---|---|
| 4 prod / 4 workers, q=512 | 71,567 | 0.90 µs | 528.70 µs | 529.70 µs | 3,727 |
| 8 prod / 4 workers, q=256 | 150,739 | 0.90 µs | 418.70 µs | 419.60 µs | 14,459 |
| 8 prod / 2 workers, q=64 (saturated) | 161,816 | 0.80 µs | 74.20 µs | 75.00 µs | 17,950 |
| 2 prod / 8 workers, q=4096 | 85,753 | 0.50 µs | 993.10 µs | 993.60 µs | 0 |

The drops are the point: under saturation the system **drops rather than blocks**, which is the
backpressure behaviour being demonstrated.

> Latency figures are only comparable within one scope column. `processing` excludes queue wait;
> `end-to-end` includes it.

---

## 8. Demonstrating offline fallback

Two ways, depending on whether you want to disturb the machine's networking.

### Option A — offline flag (recommended for a live demo)

This exercises the **same** `allowNetwork=false` path the tests use; it is not a separate code path.

```bat
set "TD_OFFLINE=true"
mvn javafx:run
```

Or run `com.tradingdesk.Main` from IntelliJ with environment variable `TD_OFFLINE=true`.

> Note: use `set "TD_OFFLINE=true"` with quotes. Plain `set TD_OFFLINE=true && …` in cmd.exe stores
> a trailing space, which is why the application trims the value.

### Option B — actually disconnect

Unplug the network (or run on a machine with no internet) and launch normally.

### Expected behaviour in both cases

- The application **starts and remains fully functional**. It never hangs or crashes.
- The status badge reads `SIMULATED · No network; using the simulator` in **amber**.
- Prices continue updating — generated locally and **labelled** as simulated.
- START / STOP / RESET, all nine screens, charts, orders and risk checks keep working.
- Because the synthetic source is active, the full workflow runs: signals fire, orders execute and
  the portfolio P&L moves. A verified offline run showed 8 orders executed, 1 rejected and realized
  P&L of −2.42, with prices at simulator levels (BTC ≈ 96,300) rather than live levels (≈ 60,000) —
  which is exactly how you can tell the two apart on screen.

Automated proof:

```bash
mvn test -Dtest=OfflineFallbackTest,MarketDataFallbackChainTest
```

Expected: **8 tests, 0 failures**, asserting the app starts, processes events and reports `SIMULATED`
— and that simulated data is never labelled live.

---

## 9. Expected behaviour when Binance is unavailable

| Situation | Result |
|---|---|
| Binance WebSocket blocked, HTTPS OK | `LIVE REST` — real data, polled |
| Both blocked / no internet | `SIMULATED`, amber, app fully functional |
| Connection drops mid-session | Supervisor degrades and updates the badge within one refresh (600 ms) |
| Stale stream (no message) | Watchdog reconnects automatically with capped backoff |
| Duplicate messages | Suppressed and counted, never displayed twice |

The JavaFX Application Thread is never blocked: market data connects on a daemon thread and the UI
refreshes from a snapshot on a timer.

---

## 10. Analytics, indicators and backtesting

- **Analytics** shows SMA/EMA chart, RSI, MACD, Bollinger Bands, ATR, Stochastic and VWAP, plus a
  **strategy backtest comparison** over real Binance 1-hour history.
- Press **RUN BACKTEST**. Results (total return, win rate, max drawdown, profit factor, Sharpe, trade
  count) are measured, and the data source is named under the table.
- Backtesting is **look-ahead safe by construction**: the strategy receives only bars `0..i` and fills
  at bar `i+1` open. This is structural, not a convention.

### AI/ML component — state this honestly

A genuine logistic-regression classifier exists (standardisation, class weighting, chronological
split, no leakage). Measured on real BTCUSDT hourly data:

| Metric | Value |
|---|---|
| Training accuracy | 43.15% |
| **Test accuracy** | **22.45%** |
| Majority-class baseline | 23.13% |

**It does not beat the baseline.** It is an experimental component with no predictive edge, and it
should be presented as such — a complete, leak-free training and evaluation pipeline, not a trading
strategy. Rule-based and ML signals remain distinguishable in the UI.

---

## 11. Confirming there is no live trading

```bash
dir src\main\java\com\tradingdesk\exchange      # must not exist
findstr /s /i /m "EXCHANGE_API_KEY apiSecret HmacSHA256" src\main\java\*.java   # no matches
```

- The `com.tradingdesk.exchange` package was **deleted** (live-trading guard, mock exchange,
  HMAC signing, credentials).
- Only read-only public endpoints are contacted: `/api/v3/time`, `/api/v3/ticker/bookTicker`,
  `/api/v3/klines`, plus the public market-data WebSocket. **No `/order` endpoint.**
- Every fill is simulated against virtual funds.

---

## 12. Full regression

```bash
mvn clean test                                  # 317 tests, 0 failures
mvn clean test -Ddb.enabled=true                # + 14 real MySQL CRUD tests
mvn clean test -Dlive.enabled=true              # + network-dependent tests
```

Expected: **BUILD SUCCESS**, zero failures. Network and database tests are skipped automatically when
those resources are absent, so the offline suite always passes.

---

## 13. Demo script (about 4 minutes)

1. Launch. Point at the badge — **LIVE WebSocket**, green. Real prices in Market Watch.
2. **START**. Point at events/s, queue depth, P50/P95/P99 climbing.
3. **Orders** — submit a market BUY. Show it go `OPEN → FILLED`, then show **Portfolio** updating.
4. **Risk Management** — submit an oversized order, show the rejection reason.
5. **Analytics** — RUN BACKTEST, show measured metrics and the named data source.
6. **System Monitoring** — RUN STRESS TEST, show measured throughput and drops.
7. Disable the network, relaunch → **SIMULATED**, amber, everything still works.
8. Close on the disclaimer and the ML result stated honestly.

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Badge says `SIMULATED` on a networked machine | Outbound WebSocket blocked. REST fallback may still succeed; otherwise this is correct behaviour. |
| `In-memory only (MySQL not configured)` | `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` not set for this process. |
| Window does not open | `mvn -B javafx:run` and read the stack trace; usually a JDK version mismatch (needs 21+). |
| Tables empty until START is pressed | Expected — the pipeline only runs when started. |
