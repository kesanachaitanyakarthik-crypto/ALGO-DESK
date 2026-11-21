# Development Progress Report

**Project:** ALGO DESK (academic simulation)
**Stack:** Java 21 · JavaFX 21 · FXML · CSS · MySQL 9.7 (JDBC) · Maven 3.9.16 · JUnit 5

---

## Final verification and stabilization audit

**Status:** Complete. **311 tests, 0 failures, BUILD SUCCESS** (offline).
**319 with `-Ddb.enabled=true`. 7 network tests pass with `-Dlive.enabled=true`.**

### 1. Live market data — why the GUI fell back to SIMULATED

Three independent causes, all fixed and verified on screen:

| # | Cause | Fix |
|---|---|---|
| 1 | `TradingDeskApplication.start()` built the context but **never called `context.start()`**, so `MarketDataSourceSupervisor.start()` was never invoked and the supervisor sat in its initial `SIMULATED` state forever. The headless test passed only because it calls `context.start()` itself. | Market data now starts from the app on a daemon thread. It cannot run on the FX thread because the WebSocket handshake blocks up to 10 s. |
| 2 | `marketDataStatus()` returned a `String` cached once at attach time, so later reconnects and degradation were invisible to the UI. | Reads through to `supervisor.display()` on every snapshot. |
| 3 | The watchlist was equity tickers (`AAPL`, `MSFT`, …) while the live feed streams `BTCUSDT`, so a **LIVE badge would have sat above simulated equity prices**. | The instrument universe is now exactly the streamed set. |

Found only by looking at the running window, then fixed:

4. **`STARTING_PRICES` was a positional array paired against `Map.of()` iteration order**, which is
   unspecified. XRP was handed BTC's price level and the UI showed **`+77,301%`**. Prices are now
   resolved per symbol from an ordered `LinkedHashMap`.
5. **The simulator kept publishing while live data streamed**, mixing generated prices with real ones
   in the same cache. Synthetic publishing is now muted whenever a real source is active, and is
   re-enabled automatically if live degrades — so a reconnect re-seeds the display.
6. **The status-bar badge was a hardcoded `SIMULATION MODE` label.** It now shows actual provenance
   (`LIVE WebSocket · Streaming 5 symbols`) and is green only when real data is in use.

**Verified on screen** (screenshot, 1920×1080):

- Badge: `LIVE WebSocket · Streaming 5 symbols` (green)
- Real Binance prices: BTC 59,859.23 · ETH 1,681.49 · BNB 546.11 · SOL 70.42 · XRP 0.9237
- 22,108 events processed at 5,845/s, p50 9.5 µs, queue drained 13,318 → 0
- Disclaimer strip present; MySQL badge honest (`In-memory only (MySQL not configured)`)

**Headless proof** (`LiveMarketDataPipelineIntegrationTest`): `LIVE WebSocket`, 11–32 real ticks,
duplicates suppressed, 53 events processed end-to-end — live ticks traverse the bounded queue and
worker pool rather than bypassing them.

**Offline proof** (`OfflineFallbackTest`): with network disabled the app still starts, still processes
events, and reports `SIMULATED` — never live.

### 2. Educational-only scope — verified

- `com/tradingdesk/exchange/` — **removed** (directory absent).
- Grep for `com.tradingdesk.exchange`, `EXCHANGE_API_KEY`, `EXCHANGE_SECRET`, `HmacSHA256`,
  `apiKey`, `apiSecret` across all main and test sources: **zero matches**.
- Only outbound endpoints: `https://api.binance.com` (`/api/v3/time`, `/api/v3/ticker/bookTicker`,
  `/api/v3/klines`) and `wss://stream.binance.com:9443/stream?streams=`. **No `/order` endpoint, no
  signing, no credentials, no deposit/withdraw path.**
- The word "post" appears only as `postMargin` / `postCollateral` in the paper account (collateral
  accounting, not HTTP POST).
- Paper trading, virtual portfolio and simulated execution untouched.

### 3. Benchmark validation — resolved

The 99 µs and 0.1 µs figures measured **different things**:

| Run | processing p50 | queue-wait p50 | end-to-end p50 |
|---|---|---|---|
| No-op workload (reproduces the 0.1 µs figure) | **0.10 µs** | 133.30 µs | **133.40 µs** |
| Real per-event work, 4 prod / 4 workers, q=512 | 0.90 µs | 528.70 µs | 529.70 µs |
| Real work, 8 prod / 2 workers, q=64 (saturated) | 0.80 µs | 74.20 µs | 75.00 µs |
| Real work, 2 prod / 8 workers, q=4096 (no drops) | 0.50 µs | 993.10 µs | 993.60 µs |

The 0.1–0.6 µs numbers were **processing-only on a no-op**, i.e. dequeue overhead. The live figure
(~9.5 µs GUI, 99–124 µs in the headless harness) is end-to-end through indicators and prediction.
They were never comparable, and `StressReport` now carries its `latencyScope` string so a report is
always self-describing. All timing uses `System.nanoTime()`; boundaries are publish / dequeue /
work-complete.

### 4. ML validation — corrected downwards

| Metric | Value |
|---|---|
| Train / test samples | 686 / 294 |
| Training accuracy | 43.15% |
| **Test accuracy** | **22.45%** |
| Majority-class baseline | 23.13% |
| BUY / SELL / HOLD recall | 0.094 / 0.014 / 0.824 |

Chronological split, training-only standardisation and no look-ahead are all intact and unit-tested.
**The model does not beat the majority-class baseline.** An earlier claim of 30.3% was stale; the same
test reproduces 22.45% on three consecutive runs. It is labelled experimental and is **not** a
profitable strategy; hyper-parameters were deliberately not tuned against the test set, because that
is the leakage the brief forbids.

### 5. Final regression

| Command | Executed | Failures |
|---|---|---|
| `mvn clean test` | 311 | 0 |
| `mvn clean test -Ddb.enabled=true` (+14 MySQL) | 319 | 0 |
| `-Dlive.enabled=true` (network tests) | 7 | 0 |
| `ScreenFxmlLoadTest` (all nine screens load) | 5 | 0 |

Manually verified in the running app: START, STOP, RESET (with confirmation), all nine nav routes,
price chart, signals table, order placement, risk rejects, offline start and simulated fallback.

### 6. Remaining limitations

- **ML has no demonstrated edge** (above). It is a complete leak-free pipeline, not a trading system.
- A `Map.of()` ordering bug was caught; other positional pairings should be assumed fragile.
- CPU load is JVM-process load, not system-wide.
- Charts use `LineChart` overlays, not bespoke candlestick controls (OHLCV data is available).
- The GUI reaches LIVE when Binance is reachable from the desktop JVM; behind a restrictive
  corporate proxy it degrades to SIMULATED and says so, which is the correct behaviour.

---

## PPT alignment and final integration

**Status:** Complete. **304 tests, 0 failures, BUILD SUCCESS.**

### PPT-to-code coverage matrix

| PPT concept | Status | Where |
|---|---|---|
| Real market data (WebSocket) | **Complete, verified live** | `marketdata.ws.BinanceWebSocketClient` |
| Real historical data (REST) | **Complete, verified live** | `marketdata.BinancePublicProvider` |
| Real-time ticker + trade events | **Complete** | `StreamMessageParser` (trade + bookTicker) |
| OHLCV candles / history retrieval | **Complete** | `Candle`, `history()` |
| Bid/ask + depth imbalance | **Complete** | `Quote`, `MarketEvent.simulatedOrderBookImbalance` |
| Reconnection + heartbeat | **Complete** | `ReconnectPolicy`, watchdog in the client |
| REST fallback / connection status | **Complete** | `MarketDataSourceSupervisor` |
| Duplicate prevention / timestamps | **Complete** | `DuplicateFilter`, exchange event time |
| Distinguish LIVE / HISTORICAL / SIMULATED | **Complete** | `ProviderKind.displayLabel()` |
| Producer → BlockingQueue → ExecutorService → workers | **Complete** | `ApplicationContext.onLiveTick` → `MarketEventQueue` → `QueuedMarketDataProcessor` |
| Bounded queue + backpressure | **Complete** | drops counted, producers never blocked |
| ConcurrentHashMap / LongAdder / Atomic* | **Complete** | price cache, metrics, worker registry |
| ScheduledExecutorService | **Complete** | simulator ticks, persistence timer, WS watchdog |
| ReentrantLock | **Complete** | `RollingIndicatorEngine` window |
| Safe shutdown, exception isolation | **Complete** | processor try/finally, supervisor stop |
| JavaFX thread never blocked | **Complete** | 600 ms snapshot tick + `Platform.runLater` |
| SMA / EMA / RSI / MACD / Bollinger / ROC / ATR / Stochastic / VWAP / volatility | **Complete, reference-tested** | `TechnicalIndicators`, `AdvancedIndicators` |
| AI/ML trained classifier | **Complete, genuinely trained** | `ml.LogisticRegressionClassifier`, `ModelTrainer` |
| BUY / SELL / HOLD + confidence | **Complete** | `TrainedPredictionEngine` |
| No look-ahead / no leakage | **Complete** | truncated window; chronological split |
| Simulated orders (5 kinds), modify, cancel | **Complete** | `paper.*` |
| Partial fills, slippage, fees, spread | **Complete** | `SimulatedExecutionEngine` |
| Risk: qty, position, daily loss, deviation, duplicate | **Complete** | `DefaultRiskManager` |
| Metrics incl. P50/P95/P99 + CPU | **Complete** | `LatencySampler`, `MetricsRegistry` |
| Stress testing module | **Complete** | `StressTestRunner`, System screen button |
| Nine JavaFX screens, live data | **Complete** | `ScreenFxmlLoadTest` loads all nine |
| Educational-only disclaimer | **Complete** | header, About screen |

### Final database state

Migration **V6** adds the advanced-indicator columns to `trading_signals`
(`rsi`, `macd`, `macd_signal`, `macd_histogram`, `bollinger_*`, `atr`, `stochastic_*`, `vwap`), all
`NOT NULL DEFAULT 0`, so existing rows stay valid. Verified live: migrations idempotent, 14/14 DB
tests pass.

### Measured benchmarks (this machine, `BenchmarkReportTest`)

Latency is reported in **three separate scopes**, because they are not interchangeable:

- **proc** — dequeue → work complete. Pure consumer cost.
- **wait** — publish → dequeue. Queue saturation cost.
- **e2e** — publish → work complete. What a producer actually observes.

Workload is real per-event work (64 sqrt operations), not a no-op.

| Scenario | events/s | proc p50 | proc p99 | wait p50 | e2e p50 | dropped | peak queue |
|---|---|---|---|---|---|---|---|
| 4 prod / 4 workers, q=512 | 71,567 | 0.90 µs | 1.90 µs | 528.70 µs | 529.70 µs | 3,727 | 512/512 |
| 8 prod / 4 workers, q=256 | 150,739 | 0.90 µs | 2.00 µs | 418.70 µs | 419.60 µs | 14,459 | 256/256 |
| 8 prod / 2 workers, q=64 (saturated) | 161,816 | 0.80 µs | 1.30 µs | 74.20 µs | 75.00 µs | 17,950 | 64/64 |
| 2 prod / 8 workers, q=4096 | 85,753 | 0.50 µs | 1.20 µs | 993.10 µs | 993.60 µs | 0 | 2351/4096 |

Drops are the point: with a small queue the system degrades by **dropping rather than blocking**.

**Why the earlier 0.1–0.6 µs figures were not comparable.** Those were `processing`-only on a *no-op*
workload, so they measured dequeue overhead, not data handling. The same no-op run now reports all
scopes: **processing p50 0.10 µs but end-to-end p50 133.40 µs.** That is why a 0.1 µs number sat
next to a ~99 µs live pipeline figure without contradiction — they measured different things. The
live GUI figure (~9.5 µs) is `MetricsRegistry` processing latency inside the real pipeline.

### AI/ML evaluation (real BTCUSDT 1h, 1000 candles, reproducible)

| Metric | Value |
|---|---|
| Train / test samples | 686 / 294 |
| Training accuracy | 43.15% |
| **Test accuracy** | **22.45%** |
| Majority-class baseline | 23.13% |
| BUY / SELL / HOLD recall | 0.094 / 0.014 / 0.824 |
| Horizon | 5 bars |

**The model does not beat the majority-class baseline.** An earlier report in this file claimed
30.3%; re-running the same test three times reproduces 22.45% every time, so that figure was stale
and has been corrected. The model is an experimental component with no demonstrated edge — not tuned
to look better, because adjusting hyper-parameters against the test set is leakage. What is genuinely
verified: chronological split, no look-ahead, training-only standardisation, and a real baseline
comparison.

### Verified live this session

- **Binance WebSocket**: 18 real messages, 5 duplicates suppressed.
- **Live pipeline**: LIVE WebSocket → bounded queue → workers; 53 events processed end to end.
- **Binance REST**: real BTCUSDT candles and quotes.
- **MySQL**: 14/14 integration tests, migrations idempotent.
- **JavaFX**: application launched and ran with live counters.

### Defects found and fixed

1. **Latency read as exactly 0** — the processor used `Instant.now()` (µs resolution). Switched to
   `System.nanoTime()`; measured p50 is now ~99 µs.
2. **Orders and About screens threw NPEs** on open — `ScreenFxmlLoadTest` caught both before a user
   could. Both now guard a missing service.
3. **ML collapse / overfitting** — described above.
4. Instrument-delete test was fragile on a shared symbol; now uses a dedicated `ZZTEST` row.

### Remaining limitations

- **Live mode never reached** in my UI captures because the app degrades to SIMULATED when the
  WebSocket connection cannot be established from the GUI process; the headless integration test
  proves LIVE_WS works and processes ticks. If the screen shows SIMULATED, that is the truthful
  supervisor state, not a mock.
- ML accuracy is modest by nature of the problem (see above).
- CPU load is JVM-process load (`getProcessCpuLoad`), not system-wide.
- Chart rendering uses `LineChart`, not a bespoke candlestick control; OHLCV data is fetched and
  available, but the visual is a line overlay.

---

## Four-phase platform enhancement

**Status:** All four phases implemented; 290 tests passing, BUILD SUCCESS.

Committed in four checkpoints on `master` (baseline → paper → strategy/backtest → marketdata/exchange),
so any phase can be reviewed or reverted independently.

### Phase 1 — Paper trading

New package `com.tradingdesk.paper`: `PaperAccount`, `WorkingOrder`, `OrderLifecycle`, `OrderFill`,
`OrderRequest`/`OrderKind`, `ExecutionCosts`, `ExecutionEngine`/`SimulatedExecutionEngine`,
`PaperBroker`.

- Five order kinds: MARKET, LIMIT, STOP, STOP_LIMIT, TAKE_PROFIT; modify and cancel supported.
- Lifecycle `NEW → OPEN → PARTIALLY_FILLED → FILLED` (plus CANCELLED/REJECTED) with illegal
  transitions rejected by `OrderLifecycle`.
- Execution models bid/ask spread, adverse slippage, commission, per-tick fill fraction and liquidity
  caps, producing genuine partial fills.
- Balance, margin, equity, realised/unrealised P&L and transaction history on the account.
- `PaperBroker` reuses the existing `PortfolioManager` and `RiskManager` rather than duplicating them.
- `OrderStatus` extended **additively** (`NEW`, `OPEN`, `PARTIALLY_FILLED`, `FILLED`); existing
  constants and semantics preserved so the original 95 tests were unaffected.

### Phase 2 — Indicators, strategies, backtesting

- `AdvancedIndicators`: RSI (Wilder), MACD, Bollinger Bands, ATR (Wilder), Stochastic, VWAP — each
  verified against hand-computed reference values.
- `com.tradingdesk.strategy`: `TradingStrategy`, `StrategyParameters` (validated, with defaults),
  `StrategyRegistry` and five strategies — MA crossover, RSI reversal, MACD crossover, Bollinger
  reversal, momentum — each with entry, exit, stop, target and sizing.
- `com.tradingdesk.backtest`: `HistoricalDataset` (truncated context prevents look-ahead),
  `BacktestEngine`, `BacktestMetrics`, `BacktestComparison`.
- Look-ahead safety is structural: at bar *i* the strategy physically receives only bars `0..i`, and
  its decision fills at bar *i+1*'s open. A dedicated test asserts this for every decision.
- Metrics: total return, win rate, max drawdown, profit factor, Sharpe, average win/loss, trade count.

### Phase 3 — Market data

- `com.tradingdesk.marketdata`: `MarketDataProvider` SPI, `ProviderKind`, `Candle`, `Quote`,
  `MarketDataStatus`, `BinanceResponseParser`, `BinancePublicProvider`, `SimulatedMarketDataProvider`.
- `ProviderKind` makes mislabelling impossible: simulated data always renders as
  "Simulated (not live)".
- Binance provider uses **public** endpoints only (`/api/v3/klines`, `/api/v3/ticker/bookTicker`) —
  no API key — with request throttling, an OHLCV cache and graceful error handling.
- **Verified against the live Binance API**: 4/4 integration tests passed, fetching real BTCUSDT
  candles and a real quote.

### Phase 4 — Exchange integration (SUPERSEDED — removed)

> **Historical entry.** This entire package has since been **deleted**, because the mandate moved to
> an educational-only simulation with no real-order pathway of any kind. `com.tradingdesk.exchange`,
> `TradingModeGuard`, `LiveSafetyLimits`, `RequestSigner`, `ExchangeCredentials` and `MockExchange` no
> longer exist, and no source file references `EXCHANGE_API_KEY`/`EXCHANGE_API_SECRET`. Guarding live
> trading with a flag was replaced by not having live trading at all. The notes below describe what
> was built at the time.

- `com.tradingdesk.exchange`: `ExchangeClient`, `MockExchange`, `ExchangeBalance/Position/Order`,
  `LiveSafetyLimits`, `ExchangeCredentials`, `RequestSigner`, `TradingModeGuard`, `LiveTradingDisabledException`.
- Live trading demanded all three of `-Dtd.live=true`, environment credentials, and the exact phrase
  `ENABLE LIVE TRADING`; each was re-checked before every order. `killSwitch()` permanently reverted
  to paper for the process.
- Credentials came from the environment only, were masked in `toString()`, and the secret had no
  public accessor.
- `RequestSigner` implemented HMAC-SHA256 verified against a published vector.
- **No adapter could send a real order.** `MockExchange` was the sole implementation; it was a sandbox
  with failure injection and rate-limit simulation, and it is what the tests exercised.

### Database

`MigrationRunner` + five additive, versioned migrations (V1–V5) applied in transactions and recorded
in `schema_version`. Verified live: all five applied, the run is idempotent, and all ten expected
tables exist. **No existing table or row was altered or dropped.**

### Regression found and fixed

The pre-existing `instrumentInsertSelectUpdateDelete` test deleted the shared symbol `MSFT`, which
foreign keys from `trading_signals` can block once real data exists. It now uses a dedicated
`ZZTEST` symbol, so it cannot be affected by other rows. This was a latent fragility, not caused by
the new phases, and it is fixed.

---

## Visual redesign and branding

**Status:** Complete and verified by running the application.

### Branding
- Application identity: **ALGO DESK — High-Frequency Algorithmic Trading System**.
- `branding/algodesk-logo.svg`: hexagonal venue ring enclosing three ascending bars and a rising
  arrow, filled with a navy→electric-blue gradient. Drawn with `evenodd` so the ring is a true hole.
- Used in the sidebar, header, About screen and as the OS window icon (rendered from the vector at
  64 px and 32 px, so it stays sharp at 125%/150% DPI).

### Icon system
- Sixteen local SVG icons: grid, chart-line, activity, wallet, clipboard, shield, bar-chart, cpu,
  database, gear, play, stop, refresh, search, download, info.
- JavaFX has no SVG loader, so `IconLoader` parses each file's path data into an `SVGPath`,
  preserving `fill-rule="evenodd"` and concatenating every `<path>` a file declares.
- No emojis, no ad-hoc Unicode symbols; one consistent 24×24 grid.

### Company marks
- `TickerBadge` renders a deterministic monogram badge per symbol (initial + stable accent colour).
- These are badges, not corporate logos — reproducing trademarked artwork would require licensed
  assets this project does not ship.

### Shell and navigation
- `shell.fxml` fixes the chrome (sidebar, header, status bar); only the centre swaps.
- Nine routed screens, each its own FXML + controller: Dashboard, Market Watch, Trading Signals,
  Portfolio, Orders, Risk Management, Analytics, System Monitoring, Settings.
- One 600 ms `Timeline` in `ShellController` builds an immutable `ViewSnapshot` and pushes it to the
  **active view only** — no per-view timers, inactive views cost nothing.

### Theme
- Navy/charcoal surfaces, electric-blue accent, semantic green/red/amber.
- Hover, pressed, selected, focused and disabled states for nav items, buttons, tables and inputs.
- Status chips per state (EXECUTED green, REJECTED/CRITICAL red, APPROVED blue, PENDING/WARNING amber).

### Interaction
- Tooltips on nav items, controls and chart points.
- RESET STATE asks for confirmation; start/stop failures surface an error dialog.
- START disables while running and shows a transient "STARTING…" state.
- Empty tables show meaningful placeholders rather than blank grids.
- Search box filters the active view (symbol, rationale, status or side).

### Charts, all fed by live data
| Chart | Series |
|---|---|
| Price trend (Dashboard) | % change per symbol vs session open |
| Equity curve (Portfolio) | total equity per refresh |
| Signal distribution (Signals) | BUY / SELL / HOLD counts |
| Price + SMA + EMA (Analytics) | live price with both moving averages |

### Defects found and fixed during this work
1. **FXML `%` prefix.** `text="% change …"` made FXML treat the string as a resource-bundle key and
   the whole Dashboard view failed with "No resources specified". Reworded the two labels.
2. **Icons lost their holes.** `IconLoader` dropped `fill-rule`, so the gear hub, search ring and
   shield tick filled solid. The rule is now carried into `SVGPath.setFillRule`.
3. **Icons lost sub-paths.** Only the first `d` attribute was read, so the search handle, CPU pins
   and clipboard lines vanished. All `<path>` elements are now concatenated.
4. **Two icons were unreadable** (`chart-line` filled as a blob, `database` not a cylinder);
   both redesigned.
5. **CRITICAL alerts rendered green** — the severity chips shared a CSS rule with `chip-buy`.
   Severity colours are now declared independently.
6. **Bar chart was all one colour**; per-category style classes are now applied to the bar nodes.
7. **Analytics chart was empty** because switching symbol cleared all history; history is now kept
   per symbol.
8. **Header text truncated** at smaller widths; title/subtitle and disclaimer now have minimum widths.

### Verification
- `mvn clean test` → **BUILD SUCCESS, 92 tests, 0 failures** (10 MySQL integration tests skipped by
  design).
- Launched the application and exercised every screen: navigation, START/STOP, instrument filtering,
  live prices, signals, orders, risk alerts, equity curve, SMA/EMA overlays, log stream.
- Resized the window down to 1366×768-equivalent: no clipped buttons, overlapping panels or hidden
  columns; charts rescale.
- Measured live (actual readings, not estimates): 18 236 events processed, 7 workers, 17.9 µs average
  latency, queue depth 0, 0 dropped events.

### Not verified

_Resolved — the MySQL integration tests now run and pass; see "MySQL integration test repair" below._

---

## MySQL integration test repair

**Status:** Fixed and verified against the live local database.

### Symptom
`mvn test -Ddb.enabled=true` reported the class as `Tests run: 0, Failures: 0, Errors: 0, Skipped: 0`
while the application itself connected fine and logged `MySQL connected and schema verified`.

### Root causes (each confirmed experimentally)

1. **The flag never reached the test JVM.** `-Ddb.enabled=true` sets a *Maven* property, not a system
   property of Surefire's forked JVM. `@EnabledIfSystemProperty` therefore saw nothing, the class
   condition disabled every test, and Surefire reported `0` rather than a skip.
   *Evidence:* a probe printed `db.enabled = true` only when passed via `argLine`; an identical
   `@EnabledIfSystemProperty` probe ran 1 test with the flag and skipped 1 without it.

2. **Credentials never reached the test JVM.** `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` are defined
   in IntelliJ's run configuration (`.idea/workspace.xml`), which the application inherits but a
   Maven fork does not. A probe showed all three unset and `MySqlPersistence.connect()` failing with
   "No MySQL password configured".

3. **`assumeTrue` in `@BeforeAll` masked everything.** When the connection could not be established
   the assumption aborted the whole container, which Surefire renders as the uninformative
   `Tests run: 0, Skipped: 0` — hiding both the missing flag and the missing credentials.

4. **A latent test-design flaw.** `ConfigLoader.of(properties)` consulted environment variables even
   when handed an explicit `Properties`, so `DatabaseConfigTest` failed whenever a developer had real
   `DB_*` variables set. That behaviour is not testable.

### Fixes

- `pom.xml`: forwards `db.enabled` into the fork via `systemPropertyVariables`, and `DB_URL` /
  `DB_USERNAME` / `DB_PASSWORD` via `environmentVariables` (read from the Maven environment only —
  nothing hardcoded). Default `db.enabled=false`, so a plain `mvn test` never needs a database.
- Removed `assumeTrue` from `@BeforeAll`; added `connectOrFail()` which reports the real reason as a
  test failure instead of a silent zero.
- `ConfigLoader.explicit(properties)` added — a loader that ignores ambient sources so credential
  handling is deterministic. `DatabaseConfigTest` uses it and gained a test proving `ConfigLoader.of`
  still lets system properties take precedence.
- `OrderDao.delete(UUID)` added so DELETE is covered per row, not only via `deleteAll()`.
- `MySqlPersistenceIntegrationTest` rewritten: 12 tests covering INSERT, SELECT, UPDATE and DELETE on
  instruments, market events, signals, orders, executions, portfolio snapshots and metrics; rejects
  root and asserts the `trading_app` account; cleans up only its own rows.

### Results (actual, not estimated)

| Command | Result |
|---|---|
| `mvn clean test` | **95 tests, 0 failures, 0 errors, 12 skipped** — BUILD SUCCESS |
| `mvn clean test -Ddb.enabled=true` | **95 tests, 0 failures, 0 errors, 0 skipped** — BUILD SUCCESS |

MySQL integration tests executed: **12, all passing** (2.676 s of real round-trips):

```
connectionIsValid · schemaContainsEveryRequiredTable · connectedAsApplicationAccountNotRoot
instrumentInsertSelectUpdateDelete · marketEventInsertAndSelectBack
marketEventBatchInsertIsTransactional · tradingSignalInsertAndSelectBack
orderInsertSelectUpdateDelete · orderAndExecutionArePersistedInOneTransaction
rejectedOrderSelectsItsReason · portfolioSnapshotInsertAndSelectLatest
systemMetricsBatchInsertAndSelectBack
```

Independently confirmed with the `mysql` client over a separate connection: an INSERT → SELECT →
UPDATE → DELETE cycle on `instruments` behaved correctly, and all seven tables (`instruments`,
`market_events`, `trading_signals`, `orders`, `executions`, `portfolio_snapshots`, `system_metrics`)
are present.

**CRUD is now genuinely verified against the local MySQL database.**

### Security note

During diagnosis a command meant to redact credentials printed the `trading_app` password to the
terminal transcript. That password should be rotated. Nothing was written into any committed file,
and no credential appears in `pom.xml`, source, or `config/db.properties`.

---

## Phase 1 — Project structure

**Status:** Complete

Professional Maven layout with one package per architectural concern:
`application, model, simulator, processor, concurrency, indicators, prediction, risk,
orders, portfolio, database, controller, service, utils`.

**Created**
- `pom.xml` — Java 21, JavaFX 21.0.2, MySQL Connector/J 9.7.0, JUnit 5.13.4, javafx-maven-plugin, surefire
- All package directories with their core classes and interfaces
- `utils/`: `ConfigLoader`, `Formatters`, `NamedThreadFactory`, `Logging`, `SimulationDisclaimer`

**Modified**
- `pom.xml` — compiler switched to `maven.compiler.release=21`; dependencies and plugin versions pinned
- `Main.java` — now delegates to the JavaFX application shell (behaviour and window title preserved)

**Verification:** `mvn compile` → BUILD SUCCESS

---

## Phase 2 — Database integration (MySQL + JDBC)

**Status:** Code complete and covered by tests. Live verification pending local credentials.

**Created**
- `database/DatabaseConnection` — reusable connection class (`open()`, `inTransaction()`, `testConnection()`)
- `database/DatabaseConfig` — credential resolution (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) with no hardcoded secrets
- `database/SchemaMigrator` — executes `sql/schema.sql`, verifies required tables, `missingTables()`
- `database/Persistence` / `MySqlPersistence` — persistence facade
- `database/SqlWork` — functional interface for transactional work
- `database/dao/InstrumentDao` + `JdbcInstrumentDao`
- `database/dao/MarketEventDao` + `JdbcMarketEventDao` (single + batch insert, count, delete)
- `database/dao/TradeSignalDao` + `JdbcTradeSignalDao`
- `database/dao/OrderDao` + `JdbcOrderDao` (insert, update, findById, open, history, counts)
- `database/dao/ExecutionDao` + `JdbcExecutionDao`
- `database/dao/PortfolioSnapshotDao` + `JdbcPortfolioSnapshotDao`
- `database/dao/SystemMetricDao` + `JdbcSystemMetricDao`
- `database/dao/OrderPersistence` — order + execution written in **one transaction**
- `database/dao/InstrumentResolver`, `JdbcSupport`
- `src/main/resources/sql/schema.sql` — 7 tables
- `src/main/resources/sql/provision_user.sql` — creates DB + non-root user
- `config/db.properties.example`
- Tests: `DatabaseConfigTest`, `MySqlPersistenceIntegrationTest` (opt-in)

**Removed (superseded in Phase 1):** `ConnectionProvider`, `DirectConnectionProvider`, `Transactions`,
`DatabaseConfigFactory`, `src/main/resources/db.properties` — replaced by the classes above.

**Verification**
- Every DAO uses `PreparedStatement`; no string-concatenated SQL.
- Transactions used for batch inserts and order+execution persistence.
- `DatabaseConfig.toString()` masks the password (asserted by test).
- Missing password fails fast with an actionable message; app then runs in-memory instead of crashing.
- **Not yet verified against a live server** — see Remaining Issues.

---

## Phase 3 — Market data simulator

**Status:** Complete

**Created**
- `simulator/SimulatedMarketDataSimulator` — Gaussian random walk per symbol, configurable volatility/drift,
  bid/ask derived from a configurable spread range, monotonic sequence numbers
- `simulator/SimulatorConfig` — tick interval, volatility, drift, spread and volume bounds, `defaults()`
- `simulator/SimulationRunner` — scheduled producer, publishes into the bounded queue, counts drops
- `simulator/MarketEventPublisher`
- Tests: `SimulatedMarketDataSimulatorTest` (validity, spread invariants, sequence order, backpressure)

**Symbols:** AAPL, MSFT, GOOG, AMZN, NVDA.

**Note:** an early defect where the runner called a no-op publisher was found by `SystemIntegrationTest`
and fixed — events now flow producer → queue → workers.

---

## Phase 4 — Concurrent processing engine

**Status:** Complete

**Created**
- `concurrency/MarketEventQueue` — bounded `LinkedBlockingQueue` wrapper with depth/capacity
- `concurrency/ExecutorManager` — fixed worker pool + scheduled pool, bounded shutdown
- `concurrency/MetricsRegistry` — processed/failed/dropped counts, average/peak/last latency, throughput
- `concurrency/WorkerRegistry` — active/total worker counters
- `concurrency/ConcurrentPriceCache` — thread-safe last-price map
- `processor/QueuedMarketDataProcessor` — worker loops, per-event exception isolation, drain-on-shutdown
- Tests: `ConcurrencyComponentsTest` (7 tests incl. 2,000-event no-loss/no-duplicate drain under 4 workers)

**Metrics exposed:** active workers, queue depth/capacity, events processed, dropped, average & peak latency, throughput.

**JavaFX safety:** workers never touch UI; the controller reads snapshots on the FX thread.

---

## Phase 5 — Trading prediction engine

**Status:** Complete

**Created**
- `indicators/TechnicalIndicators` — SMA, EMA, ROC, rolling volatility, order-book imbalance
- `indicators/RollingIndicatorEngine` — per-symbol rolling window guarded by `ReentrantLock`
- `indicators/IndicatorSnapshot` — includes `sampleCount` and `hasSufficientData()`
- `prediction/PredictionEngine` — extensible interface (name/description for transparency)
- `prediction/PredictionConfig`, `prediction/RuleBasedPredictionEngine`
- Tests: `TechnicalIndicatorsTest`, `RollingIndicatorEngineTest`, `RuleBasedPredictionEngineTest`

**Rule-based (explicitly not ML):** MA crossover + momentum, with an insufficient-history path
(`HOLD`, confidence 0) and a volatility ceiling filter. The classifier slot is left open behind the interface.

---

## Phase 6 — Risk and order management

**Status:** Complete

**Created**
- `risk/RiskManager`, `risk/DefaultRiskManager` — all five rules
- `risk/RiskConfiguration`, `risk/RiskContext`, `risk/RiskRule`
- `orders/OrderManagementSystem`, `orders/DefaultOrderManagementSystem`
- `orders/OrderResult`, `orders/OrderStatistics`
- Tests: `DefaultRiskManagerTest` (8), `OrderLifecycleTest` (8)

**Statuses implemented exactly as specified:** `PENDING`, `APPROVED`, `REJECTED`, `EXECUTED`, `CANCELLED`.

Every `REJECTED` order carries a human-readable reason (enforced by `Order`'s compact constructor).

**Lifecycle:** signal → order → risk validation → simulated match → portfolio update → history.
All executions are simulated; no brokerage integration exists.

---

## Phase 7 — JavaFX dashboard

**Status:** Complete

**Created**
- `resources/fxml/dashboard.fxml` — ten sections: navigation sidebar, market overview, live price monitor,
  trading signals, price trend chart, portfolio, open orders, order history, system performance, activity log
- `resources/css/theme.css` — dark navy terminal theme
- `controller/DashboardController` — full FXML binding, 600 ms refresh `Timeline`
- `application/TradingDeskApplication` — loads FXML + CSS, 1440×830 window

**Functional:** START / STOP / RESET STATE, per-symbol checkboxes, symbol focus, live line chart,
BUY/SELL/HOLD table, order tables with rejection reasons, risk alerts, live logs.

**Verified interactively:** with the simulation running the dashboard showed 564 events processed,
7 active workers, ~446 µs average latency, live prices with day change and volume, generated signals,
and executed orders — all sourced from the running engine, no mock data.

---

## Phase 8 — System integration

**Status:** Complete

**Created**
- `application/ApplicationContext` — composition root wiring simulator → queue → workers → indicators →
  prediction → risk → orders → portfolio → service, with optional MySQL and a persistence timer
- `service/TradingPipeline` — the consumer chaining indicators → prediction → risk → orders
- `service/TradingService` / `DefaultTradingService` — facade used by the UI
- Tests: `SystemIntegrationTest` (4 end-to-end tests)

**Integration state**
- Wired end-to-end and proven by tests plus a live UI run.
- MySQL persistence is wired for orders, executions, signals, portfolio snapshots and metrics,
  but **live DB verification is pending credentials** (see below).

---

## Test results

`mvn clean test` → **74 tests, 0 failures, 0 errors** (10 DB integration tests skipped by design)

| Class | Tests |
|---|---|
| SystemIntegrationTest | 4 |
| ConcurrencyComponentsTest | 7 |
| DatabaseConfigTest | 4 |
| RollingIndicatorEngineTest | 3 |
| TechnicalIndicatorsTest | 6 |
| OrderLifecycleTest | 8 |
| DefaultPortfolioManagerTest | 12 |
| RuleBasedPredictionEngineTest | 6 |
| DefaultRiskManagerTest | 8 |
| SimulatedMarketDataSimulatorTest | 6 |

Plus `MySqlPersistenceIntegrationTest` (10 tests) which is opt-in via `-Ddb.enabled=true`.

`mvn compile` / `mvn clean test` → **BUILD SUCCESS**

---

## Measured results (from the running application)

Captured from the live dashboard during real runs — actual readings, not estimates:

| Metric | Observed |
|--------|----------|
| Events processed | 339 – 1,330 across runs |
| Active worker threads | 7 |
| Average latency | 217 – 715 µs |
| Peak latency | ~1.1 – 1.5 s (JIT warm-up spike) |
| Queue depth | 0 (consumers keep up) |
| Events dropped | 0 |
| Throughput | ~33 events/s at default 30 ms tick |
| Orders executed / rejected | e.g. 75 (8) |
| Portfolio value | 999,661.49 (starting 1,000,000) |

No formal benchmark suite has been run; CPU utilisation and peak sustained throughput were not
measured and are deliberately not claimed.

---

## Bugs found and fixed during integration

1. **Simulator events never reached the queue.** `SimulationRunner.tick()` called
   `simulator.emit()`, but `ApplicationContext` injected a no-op publisher, so the pipeline sat idle
   with zero events. `SystemIntegrationTest` caught this; the runner now publishes directly.
2. **Phantom equity from uncovered sells.** Selling without a position credited cash but left no
   position, so portfolio value exceeded starting cash (1,345,403 vs 1,000,000). `Position` now has a
   signed quantity, `DefaultPortfolioManager` realises P&L on the closed portion and opens a short for
   the remainder, and closed positions no longer contribute market value.
3. **Price chart could not render.** Series were only built on checkbox change, never at startup, and
   plotting mixed price levels (120 vs 420) on one axis flattened every line. Now built on init and
   plotted as % change from session start.
4. **Window overflowed the display.** 1440×900 exceeded a 1536×864 screen at 125% DPI; reduced to
   1440×830 with a 1180×680 minimum.
5. **Symbol checkboxes were clipped.** An `HBox` squeezed five labels into ~176 px; replaced with a
   wrapping `FlowPane`.
6. **Misleading metrics.** "SPREAD" showed a cross-symbol price difference rather than a bid/ask
   spread, queue capacity was computed from a hardcoded constant, and throughput was diluted by
   idle time before START. All three now report real values.

---

## Remaining issues

1. **Live MySQL verification is pending.** `trading_desk` and `trading_app` must be provisioned and
   `DB_PASSWORD` set. Until then the app runs in-memory only and `MySqlPersistenceIntegrationTest`
   is disabled. All SQL/DAO code exists and is unit-tested around, but is not proven against a server.
2. **Market events are not persisted to `market_events`.** The table and DAO exist, but streaming
   every tick to MySQL would dominate runtime; persistence currently covers orders, executions,
   signals, portfolio snapshots and metrics.
3. **Peak latency includes JIT warm-up** (1.1–1.5 s) and is not a steady-state figure.
4. **Sidebar navigation items are presentational** — they do not switch views yet.
5. **Short positions are now modelled but not risk-capped**; there is no explicit short-exposure limit
   beyond the position-value rule.
6. `org/example/Main.java` (the original IntelliJ template) is still present but unused.

