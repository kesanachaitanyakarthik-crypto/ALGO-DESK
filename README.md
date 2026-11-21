# ALGO DESK

**High-Frequency Algorithmic Trading Desk — Real-Time Market Data Processing and AI-Driven Trading Signals**

> **EDUCATIONAL SIMULATION ONLY — REAL MARKET DATA, VIRTUAL TRADING. NO REAL-MONEY TRANSACTIONS.**

An academic project demonstrating Java multithreading and concurrency utilities through a
high-frequency trading simulation. It ingests **real public Binance market data** over WebSocket and
REST, pushes every tick through a bounded queue and a worker pool, computes technical indicators,
generates rule-based **and** machine-learning signals, applies risk controls, and executes **virtual**
orders against a simulated portfolio.

There is no brokerage connection, no order routing, no deposit or withdrawal, and no live execution
path anywhere in the codebase.

---

## Platform capabilities

### Real market data (`com.tradingdesk.marketdata`)

- `BinanceWebSocketClient` — public combined streams (`@trade`, `@bookTicker`) over the JDK
  `WebSocket`, with automatic reconnection (exponential backoff, capped), a heartbeat/staleness
  watchdog, duplicate suppression and a disconnect callback.
- `BinancePublicProvider` — public REST for OHLCV candles and quotes; request throttling and caching.
- `MarketDataSourceSupervisor` — degrades `LIVE_WS → LIVE_REST → SIMULATED` and reports only the mode
  it actually achieved. The UI shows that label, so simulated data is never presented as live.
- `LiveTickAdapter` — converts stream messages into `MarketEvent`s and **publishes them into the same
  bounded queue** the simulator uses, so live data flows through the identical concurrency pipeline.

### High-frequency event processing (`concurrency`, `processor`)

```
real ticks / simulator → MarketEventQueue (bounded) → ExecutorService workers
   → TradingPipeline → indicators → prediction → risk → order matching → portfolio → UI
```

- Bounded `LinkedBlockingQueue` with explicit backpressure (drop + count, never block the producer).
- Fixed worker pool with worker monitoring, queue-depth monitoring and safe shutdown.
- `MetricsRegistry` — events processed, throughput, average/peak latency, **P50/P95/P99** via
  `LatencySampler`, rejected and dropped counts, process CPU load.
- `StressTestRunner` — concurrent producers vs a worker pool, reporting measured throughput, latency
  percentiles, drops and peak queue depth.

### Technical indicators (`indicators`)

SMA, EMA, ROC, rolling volatility, order-book imbalance, plus RSI (Wilder), MACD, Bollinger Bands,
ATR, Stochastic Oscillator and VWAP — all computed from the same rolling window and unit-tested
against hand-computed reference values.

### AI/ML prediction (`com.tradingdesk.ml`)

A genuine trainable classifier, not a label on the rule engine:

- `FeatureExtractor` — SMA/EMA ratios, RSI, ROC, volatility, MACD histogram, depth imbalance.
- `LogisticRegressionClassifier` — multinomial softmax, mini-batch gradient descent, per-feature
  standardisation learned from the **training set only**, √inverse-frequency class weighting, L2.
- `ModelTrainer` — forward-return labels, strictly chronological train/test split (no shuffling, so no
  leakage), precision/recall, confusion matrix and a majority-class baseline.
- `TrainedPredictionEngine` — implements the existing `PredictionEngine` contract, so rule-based and
  ML signals are interchangeable and clearly labelled.

**Measured on real BTCUSDT hourly data (1000 candles, 5-bar horizon, reproducible):**

| Metric | Value |
|---|---|
| Train / test samples | 686 / 294 |
| Training accuracy | 43.15% |
| **Test accuracy** | **22.45%** |
| Majority-class baseline | 23.13% |

**The classifier does not beat the majority-class baseline. It is an experimental component and it
has no demonstrated predictive edge.** The 43% → 22% gap is overfitting plus regime shift between the
chronological training and test windows; BUY/SELL recall is close to zero, so the model effectively
predicts HOLD.

This is stated plainly rather than tuned away: adjusting hyper-parameters until the test score looks
good would be fitting the evaluation set, which is exactly the leakage the brief forbids. The value
of the component is that a complete, leak-free training and evaluation pipeline exists and reports
its real numbers.

---

## Interface

A fixed shell (sidebar, header, status bar) hosts nine routed screens, each loaded from its own
FXML file and refreshed from a single 600 ms snapshot loop:

| Screen | Contents |
|--------|----------|
| Dashboard | KPI cards, market watch, latest signals, price-trend chart, system performance, activity log |
| Market Watch | Full instrument list with session change, volume and trend |
| Trading Signals | All generated signals plus a BUY/SELL/HOLD distribution chart |
| Portfolio | Equity curve, cash and P&L cards, executed-order ledger |
| Orders | Open orders and full history with rejection reasons |
| Risk Management | Live risk alerts and the active threshold reference |
| Analytics | Price with SMA/EMA overlays, ROC, volatility, order-book imbalance |
| System Monitoring | Queue, workers, latency, throughput, dropped events, live log |
| Settings | Branding, runtime, database status and signal-model disclosure |

Branding assets are vectors under `src/main/resources` — `branding/algodesk-logo.svg` and sixteen
icons in `icons/`. Company marks are generated monogram badges, **not** licensed corporate logos.

---

## Platform capabilities

Beyond the original simulation, the project now includes four additional layers.

### Paper trading (`com.tradingdesk.paper`)

- `PaperAccount` — virtual balance, cash, posted margin, available balance, equity, realised P&L and
  a bounded transaction history.
- Order kinds: `MARKET`, `LIMIT`, `STOP`, `STOP_LIMIT`, `TAKE_PROFIT`, with cancellation and
  modification before any fill.
- `WorkingOrder` + `OrderLifecycle` enforce `NEW → OPEN → PARTIALLY_FILLED → FILLED` (or
  `CANCELLED`/`REJECTED`) and reject illegal transitions.
- `SimulatedExecutionEngine` charges the spread (buys at the ask, sells at the bid), applies adverse
  slippage, commission, a per-tick fill fraction and a liquidity cap — which together produce partial
  fills rather than instant full execution.
- `PaperBroker` reuses the existing `PortfolioManager` and `RiskManager`; it does not duplicate
  position or risk logic.

### Strategies and backtesting (`com.tradingdesk.strategy`, `com.tradingdesk.backtest`)

- Indicators: SMA, EMA, ROC, rolling volatility (existing) plus RSI, MACD, Bollinger Bands, ATR,
  Stochastic Oscillator and VWAP.
- Five strategies — MA crossover, RSI reversal, MACD crossover, Bollinger reversal, momentum — each
  with configurable entry, exit, stop-loss, take-profit, sizing and parameters.
- `BacktestEngine` is look-ahead-safe by construction: at bar *i* the strategy can only see bars
  `0..i`, and its decision is filled at bar *i+1*'s open. Fees and slippage apply on both sides.
- Metrics: total return, win rate, maximum drawdown, profit factor, Sharpe ratio, average win/loss
  and trade count. `BacktestComparison` ranks strategies over one identical dataset.

### Market data (`com.tradingdesk.marketdata`)

- `MarketDataProvider` SPI with declared capabilities, plus `Candle` and `Quote`.
- `ProviderKind` distinguishes SIMULATED, LIVE, DELAYED and HISTORICAL, so simulated data is always
  labelled "not live" in the UI.
- `BinancePublicProvider` uses only **public** REST endpoints (`/api/v3/klines`,
  `/api/v3/ticker/bookTicker`) — no API key — with request throttling and an OHLCV cache.
  `SimulatedMarketDataProvider` exposes the existing simulator through the same contract.

### Exchange integration — removed

The `com.tradingdesk.exchange` package no longer exists. It previously contained `ExchangeClient`,
`MockExchange`, `ExchangeCredentials`, `RequestSigner` (HMAC-SHA256), `TradingModeGuard`,
`LiveSafetyLimits` and `LiveTradingDisabledException`. All of it was deleted so that **no
order-placement code path exists at all** (see [Educational-only scope](#educational-only-scope-no-live-trading-exists)).

---

## Contents

- [Architecture](#architecture)
- [Technologies](#technologies)
- [Setup](#setup)
- [Database configuration](#database-configuration)
- [Run instructions](#run-instructions)
- [Module descriptions](#module-descriptions)
- [Concurrency model](#concurrency-model)
- [Prediction model](#prediction-model)
- [Testing](#testing)
- [Project structure](#project-structure)

---

## Architecture

```
Market Data Simulator  (ScheduledExecutorService, 5 symbols)
        │  publishes MarketEvent
        ▼
Bounded BlockingQueue  (LinkedBlockingQueue, capacity 25,000)
        │
        ▼
ExecutorService Worker Pool  (fixed pool, N = cores − 1)
        │  each worker runs the pipeline stage
        ▼
Market Data Processor ──► Technical Indicators (SMA, EMA, ROC, volatility, imbalance)
        │                        per-symbol rolling windows, ReentrantLock
        ▼
Prediction Engine  (rule-based MA crossover + momentum  →  BUY / SELL / HOLD)
        │
        ▼
Risk Manager  (5 rules, every rejection carries a reason)
        │
        ▼
Order Manager  (PENDING → APPROVED → EXECUTED | REJECTED | CANCELLED)
        │
        ├──► Portfolio Manager  (positions, cash, realized / unrealized P&L)
        ├──► JavaFX Dashboard   (UI updates only via Platform.runLater)
        └──► MySQL Persistence  (JDBC, PreparedStatement, transactions)
```

---

## Technologies

| Area | Technology |
|------|------------|
| Language | Java 21 |
| UI | JavaFX 21 (FXML + CSS) |
| Build | Maven 3.9.16 |
| Database | MySQL 9.7 |
| Persistence | JDBC + `PreparedStatement` (no ORM) |
| Testing | JUnit 5.13 |
| IDE | IntelliJ IDEA |

No Spring, no Hibernate/JPA, no web frontend — deliberately plain Java.

---

## Setup

### Prerequisites

- JDK 21 (`java -version` should report 21)
- Maven 3.9+ (or use IntelliJ's bundled Maven)
- MySQL 9.x running locally on port 3306 (optional — the app runs in-memory without it)

### 1. Clone and build

```bash
mvn clean compile
```

### 2. Create the database and application user

Run **once** as a MySQL administrator (MySQL Workbench or the `mysql` CLI).
Edit the password placeholder in `src/main/resources/sql/provision_user.sql` first, then:

```bash
mysql -u root -p < src/main/resources/sql/provision_user.sql
```

This creates database `trading_desk` and user `trading_app`. **The application never uses root.**

### 3. Create the schema

```bash
mysql -u trading_app -p trading_desk < src/main/resources/sql/schema.sql
```

(The application also runs these `CREATE TABLE IF NOT EXISTS` statements at startup.)

### 4. Configure credentials

Credentials are **never** hardcoded. Set environment variables:

```powershell
# PowerShell
$env:DB_URL="jdbc:mysql://localhost:3306/trading_desk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME="trading_app"
$env:DB_PASSWORD="your-password"
```

```bash
# bash
export DB_URL="jdbc:mysql://localhost:3306/trading_desk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DB_USERNAME="trading_app"
export DB_PASSWORD="your-password"
```

Alternatively copy `config/db.properties.example` to `config/db.properties` (gitignored) and fill it in.

---

## Database configuration

Resolution order for each setting: **system property → environment variable → `config/db.properties` → default**.

| Setting | Environment variable | Property file key |
|---------|---------------------|-------------------|
| JDBC URL | `DB_URL` | `db.url` |
| Username | `DB_USERNAME` | `db.username` |
| Password | `DB_PASSWORD` | `db.password` |
| Driver | — | `db.driver` |

If no password is configured the application logs a warning and runs **in-memory only**; the
dashboard shows `In-memory only (MySQL not configured)` in the sidebar. Nothing crashes.

### Tables

| Table | Purpose |
|-------|---------|
| `instruments` | Symbol master data (symbol, display name, type) |
| `market_events` | Every simulated tick (last/bid/ask price, volume, timestamp) |
| `trading_signals` | Generated signals with the indicator values that produced them |
| `orders` | Full order lifecycle including `rejection_reason` |
| `executions` | Simulated fills, linked to orders by foreign key |
| `portfolio_snapshots` | Periodic cash / market value / P&L snapshots |
| `system_metrics` | Events processed, latency, queue depth, throughput |

---

## Run instructions

```bash
mvn javafx:run
```

Or run `com.tradingdesk.Main` directly from IntelliJ.

Inside the dashboard:

- **START** — begins the simulator and processing pipeline (button disables while running)
- **STOP** — halts the simulator (workers drain and exit cleanly)
- **RESET STATE** — clears portfolio, orders and metrics, after a confirmation dialog
- **Sidebar** — switches between the nine screens
- **Search box** — filters rows by symbol or rationale on the active view
- **Instrument checkboxes** — choose which symbols feed the pipeline

The window opens at 1366×768 and is usable down to 1180×700; layouts are verified at
1366×768, 1600×900 and 1920×1080, including Windows display scaling at 125%.

---

## Educational-only scope (no live trading exists)

**There is no live-trading mode, no exchange adapter, and no order-placement pathway in this
codebase.** The `com.tradingdesk.exchange` package — which previously held a live-trading guard,
`MockExchange`, HMAC request signing and credential handling — has been **removed**, together with
`TradingModeGuard`, `LiveSafetyLimits`, `EXCHANGE_API_KEY`/`EXCHANGE_API_SECRET` and the
`-Dtd.live=true` flag.

`EXCHANGE_API_KEY` and `EXCHANGE_API_SECRET` are no longer read anywhere. Grepping the sources for
`exchange`, `apiKey`, `apiSecret` or `HmacSHA256` returns zero matches.

### What does exist

- **Read-only public market data.** Only three Binance endpoints are contacted:
  `/api/v3/time`, `/api/v3/ticker/bookTicker` and `/api/v3/klines`, plus the public market-data
  WebSocket. No API key is sent, and none is required.
- **Virtual execution.** `PaperBroker` + `SimulatedExecutionEngine` fill orders against virtual cash
  with spread, slippage and fees. Every fill is simulated.

### Why this is safe by construction, not by a guard

The previous design defended live trading with a three-factor guard. That is weaker than simply not
having the capability: a guard can be bypassed by a bug, whereas a missing adapter cannot place an
order at all. Removing the code was the stronger fix.

---

## Database migrations

`MigrationRunner` applies versioned SQL from `src/main/resources/db/migration` and records each
version in `schema_version`.

| Version | Contents |
|---------|----------|
| V1 | Baseline marker (the original seven tables are created by `SchemaMigrator`) |
| V2 | `paper_accounts`, `paper_orders`, `paper_fills` |
| V3 | `strategies`, `backtest_runs`, `backtest_trades` |
| V4 | `candles`, `market_data_status` |
| V5 | `exchange_connections` (metadata only — **no credentials are ever stored**) |
| V6 | Advanced indicator columns on `trading_signals` (RSI, MACD, Bollinger, ATR, Stochastic, VWAP), all `NOT NULL DEFAULT 0` so existing rows stay valid |

Migrations are **additive by policy**: they create and alter, never drop or truncate, so existing
records survive. Each runs in its own transaction alongside its version row, and re-running is a
no-op. A unit test asserts no migration contains destructive DDL.

---

## Module descriptions

| Package | Responsibility |
|---------|----------------|
| `application` | Composition root (`ApplicationContext`), JavaFX entry point, dependency wiring |
| `controller` | `ShellController` (router + refresh loop) and one controller per routed view |
| `ui` | Branding, icon loader, ticker badges, snapshot layer, reusable table cells, notifications |
| `model` | Immutable records & enums: `Instrument`, `MarketEvent`, `Order`, `TradeSignal`, `Position`, `Execution`, `PortfolioSnapshot`, `RiskAlert`, `SystemMetric` |
| `simulator` | `SimulatedMarketDataSimulator` (random-walk prices with Gaussian shocks), `SimulationRunner` (scheduled ticking + backpressure), `SimulatorConfig` |
| `processor` | `QueuedMarketDataProcessor` — worker loops that drain the queue and invoke consumers |
| `concurrency` | `MarketEventQueue`, `ExecutorManager`, `MetricsRegistry`, `WorkerRegistry`, `ConcurrentPriceCache` |
| `indicators` | `TechnicalIndicators` (pure functions) and `RollingIndicatorEngine` (per-symbol windows) |
| `prediction` | `PredictionEngine` interface + `RuleBasedPredictionEngine` baseline |
| `risk` | `RiskManager` interface, `DefaultRiskManager`, `RiskRule`, `RiskConfiguration` |
| `orders` | `OrderManagementSystem` + lifecycle implementation, `OrderResult`, `OrderStatistics` |
| `portfolio` | `PortfolioManager` + `DefaultPortfolioManager` (positions, cash, P&L) |
| `database` | `DatabaseConnection`, `DatabaseConfig`, `SchemaMigrator`, `Persistence`, `MySqlPersistence` |
| `database.dao` | `Jdbc*Dao` classes — one per table, all `PreparedStatement`-based |
| `service` | `TradingService` facade and `TradingPipeline` (the consumer that chains the stages) |
| `utils` | `ConfigLoader`, `Formatters`, `NamedThreadFactory`, `Logging`, `SimulationDisclaimer` |

---

## Concurrency model

- **`ExecutorService`** — fixed pool of `max(2, cores − 1)` workers drains the event queue.
- **`BlockingQueue`** — `LinkedBlockingQueue` with capacity 25,000. On overflow the event is
  *dropped and counted* rather than blocking the producer (explicit backpressure policy).
- **`ConcurrentHashMap`** — per-symbol indicators, prices, volumes and quotes.
- **`AtomicLong` / `LongAdder`** — event counters and latency accumulators.
- **`ScheduledExecutorService`** — drives simulator ticks and periodic metric persistence.
- **`ReentrantLock`** — guards each symbol's rolling price window.
- **`CompletableFuture` / `CountDownLatch`** — shutdown coordination and worker join.
- **JavaFX safety** — worker threads never touch UI controls. The controller pulls a consistent
  snapshot on the JavaFX Application Thread via a `Timeline`; all cross-thread state is concurrent
  or volatile.

Shutdown: `STOP` flips the running flag, workers observe it within their 200 ms poll timeout,
the monitor latch releases, then pools are shut down with a bounded `awaitTermination`.

---

## Prediction model

**This is a transparent rule-based baseline, not a trained AI/ML model.**

Indicators computed per symbol over a rolling window (20 samples, ROC period 5):

| Indicator | Method |
|-----------|--------|
| Simple Moving Average | mean of the last *n* prices |
| Exponential Moving Average | seeded from SMA, then smoothed |
| Rate of Change | `(current − n-back) / n-back` |
| Rolling Volatility | population standard deviation of the window |
| Order Book Imbalance | mean of `(last − mid) / spread` per tick |

Decision logic:

```
if samples < 8                 → HOLD  ("Insufficient history"), confidence 0
if volatility > ceiling        → HOLD  (risk-off filter),  confidence 0.2
if EMA > SMA and ROC > +0.15%  → BUY
if EMA < SMA and ROC < −0.15%  → SELL
otherwise                      → HOLD
```

The `PredictionEngine` interface is deliberately small so a trained classifier can be substituted
without touching the rest of the pipeline.

---

## Testing

```bash
mvn test
```

| Test class | Covers |
|------------|--------|
| `TechnicalIndicatorsTest` | SMA, EMA, ROC, volatility, imbalance maths |
| `RollingIndicatorEngineTest` | Window bounding, empty state, reset |
| `RuleBasedPredictionEngineTest` | BUY/SELL/HOLD, insufficient history, volatility filter |
| `DefaultRiskManagerTest` | All five risk rules, duplicate expiry, rejection reasons |
| `OrderLifecycleTest` | PENDING → APPROVED → EXECUTED, rejection reasons, statistics |
| `DefaultPortfolioManagerTest` | Positions, average price, realized P&L, exposure |
| `ConcurrencyComponentsTest` | Bounded queue, atomic metrics, contention, no-loss draining |
| `SimulatedMarketDataSimulatorTest` | Event validity, sequence monotonicity, backpressure drops |
| `DatabaseConfigTest` | Credential loading, missing-password refusal, no leak in `toString` |
| `SystemIntegrationTest` | Full pipeline end-to-end, sustained load, order flow, clean restart/stop |
| `MySqlPersistenceIntegrationTest` | Live CRUD for every table (opt-in, see below) |

The MySQL integration test is **opt-in** so the normal build needs no database:

```bash
mvn test -Ddb.enabled=true        # requires DB_URL / DB_USERNAME / DB_PASSWORD
```

> **Note:** `-D` on the Maven command line sets a *Maven* property. Surefire runs tests in a forked
> JVM that does not inherit it, so `pom.xml` forwards `db.enabled` and the `DB_*` variables into the
> fork explicitly. Without that forwarding the integration class is silently excluded and Surefire
> reports the confusing `Tests run: 0, Skipped: 0`.

If the flag is set but the credentials are missing, the tests **fail with an explicit
"MySQL is not reachable: …" message** rather than passing quietly.

---

## Project structure

```
src/main/java/com/tradingdesk/
├── Main.java                    # entry point
├── application/                 # app shell, composition root
├── controller/                  # FXML controller
├── concurrency/                 # queue, executors, metrics, caches
├── database/                    # connection, schema, persistence facade
│   └── dao/                     # JDBC DAO implementations
├── indicators/                  # technical analysis
├── model/                       # domain records and enums
├── orders/                      # order lifecycle
├── portfolio/                   # portfolio and P&L
├── prediction/                  # signal generation
├── processor/                   # concurrent event processing
├── risk/                        # risk rules
├── service/                     # pipeline + facade
├── simulator/                   # market data simulation
└── utils/                       # config, formatting, logging

src/main/resources/
├── branding/algodesk-logo.svg
├── css/theme.css
├── fxml/
│   ├── shell.fxml
│   ├── dashboard-view.fxml
│   ├── market-watch.fxml
│   ├── signals-view.fxml
│   ├── portfolio-view.fxml
│   ├── orders-view.fxml
│   ├── risk-view.fxml
│   ├── analytics-view.fxml
│   ├── system-view.fxml
│   └── about-view.fxml
├── icons/  (16 SVG icons)
└── sql/
    ├── provision_user.sql
    └── schema.sql
```
