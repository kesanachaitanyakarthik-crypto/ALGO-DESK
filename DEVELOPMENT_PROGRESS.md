# Development Progress Report

**Project:** ALGO DESK (academic simulation)
**Stack:** Java 21 · JavaFX 21 · FXML · CSS · MySQL 9.7 (JDBC) · Maven 3.9.16 · JUnit 5

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

### Phase 4 — Exchange integration (safely gated)

- `com.tradingdesk.exchange`: `ExchangeClient`, `MockExchange`, `ExchangeBalance/Position/Order`,
  `LiveSafetyLimits`, `ExchangeCredentials`, `RequestSigner`, `TradingModeGuard`, `LiveTradingDisabledException`.
- Live trading demands all three of `-Dtd.live=true`, environment credentials, and the exact phrase
  `ENABLE LIVE TRADING`; each is re-checked before every order. `killSwitch()` permanently reverts to
  paper for the process.
- Credentials come from the environment only, are masked in `toString()`, and the secret has no
  public accessor.
- `RequestSigner` implements HMAC-SHA256 verified against a published vector.
- **No adapter can send a real order.** `MockExchange` is the sole implementation; it is a sandbox
  with failure injection and rate-limit simulation, and it is what the tests exercise.

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

