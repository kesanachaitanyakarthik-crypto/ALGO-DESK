# ALGO DESK

**A Java/JavaFX algorithmic-trading desk simulation**

> **EDUCATIONAL SIMULATION ONLY — REAL MARKET DATA, VIRTUAL TRADING, NO REAL-MONEY TRANSACTIONS.**

ALGO DESK demonstrates concurrent market-data processing, technical indicators, strategy and
machine-learning signal components, risk validation, paper execution, backtesting, persistence,
and JavaFX UI design. It can consume public Binance data and also run entirely with simulated data.

There is no brokerage connection, exchange order-placement code, deposit or withdrawal flow, or
live-trading execution path.

## Highlights

- Public Binance WebSocket and REST market data with `LIVE WS -> LIVE REST -> SIMULATED` fallback.
- A bounded `LinkedBlockingQueue` feeding a fixed Java executor worker pool.
- Rolling technical indicators including SMA, EMA, ROC, volatility, RSI, MACD, Bollinger Bands,
  ATR, Stochastic Oscillator, VWAP, and order-book imbalance.
- A trainable logistic-regression classifier alongside a transparent rule-based prediction engine.
- Five built-in strategies: moving-average crossover, RSI reversal, MACD crossover,
  Bollinger reversal, and momentum.
- A look-ahead-safe backtest engine with costs and slippage plus comparison metrics.
- Paper accounts and simulated fills with spread, slippage, commission, and liquidity limits.
- Five risk rules covering order quantity, position size, daily loss, price deviation, and duplicate
  orders.
- Optional JDBC persistence in MySQL with versioned migrations.
- Nine routed JavaFX screens refreshed from one 600 ms snapshot timeline.

## Disclaimer

This project is for software engineering education and demonstration. It is not investment advice,
and its signals must not be used for real trading decisions.

All order placement, matching, fills, balances, positions, P&L, and execution behavior in this
application are simulated. No real-money trading occurs.

Only public, read-only Binance endpoints are used. Public market data is not a real-money trading
integration. The exchange migration file is metadata-only and the application contains no exchange
credentials, signing, or order-routing implementation.

## Interface

The fixed application shell contains a sidebar, header, status bar, instrument selectors, and nine
routed screens:

| Screen | Main content |
|---|---|
| Dashboard | KPIs, market watch, signals, price trend, performance, and activity |
| Market Watch | Instrument prices, session change, volume, and trend |
| Trading Signals | Generated signals and BUY/SELL/HOLD distribution |
| Portfolio | Virtual equity, cash, P&L, positions, and execution ledger |
| Orders | Open orders and order history with rejection or cancellation details |
| Risk Management | Active alerts and risk-rule thresholds |
| Analytics | Price overlays, ROC, volatility, and order-book imbalance |
| System Monitoring | Queue depth, workers, latency, throughput, drops, and live status |
| Settings | Branding, runtime, database status, and model disclosure |

The window opens at 1366 x 768 and has a minimum size of 1180 x 700. The UI is also checked at
1600 x 900 and 1920 x 1080, including Windows display scaling.

## Architecture

```text
LIVE_WS (public Binance WebSocket)
        |
        +-- available --> ACTIVE SOURCE
        |
        | unavailable
        v
LIVE_REST (public Binance REST fallback)
        |
        +-- available --> ACTIVE SOURCE
        |
        | unavailable
        v
SIMULATED (local market-data simulator)
        |
        +-------------> ACTIVE SOURCE
                         |
                         v
MarketEventQueue (bounded, 25,000)
        |
        v
ExecutorService (fixed workers)
        |
        v
TradingPipeline
indicators -> prediction
        |
        v
Risk validation
        |
        v
OMS -> Paper execution
        |
  +-----+-----+
  |           |
  v           v
Portfolio  JavaFX snapshot
and P&L
  |
  v
MySQL persistence (optional)
```

The supervisor follows `LIVE_WS -> LIVE_REST -> SIMULATED`. A synthetic tick is never presented as
live. Real and simulated ticks are adapted when needed and all three sources feed the same
`MarketEventQueue`, `ExecutorService`, `TradingPipeline`, risk checks, paper execution, portfolio,
and UI snapshot path. UI controllers do not run their own timers or touch state directly from
worker threads; `ShellController` publishes a consistent snapshot to the active view on the JavaFX
application thread.

## Market Data

The application watches five Binance crypto instruments:

- `BTCUSDT`
- `ETHUSDT`
- `BNBUSDT`
- `SOLUSDT`
- `XRPUSDT`

`BinanceWebSocketClient` connects to public combined trade and book-ticker streams. It includes
reconnection with capped exponential backoff, duplicate filtering, and stale-stream detection.
`BinancePublicProvider` uses the unauthenticated public `/api/v3/time`,
`/api/v3/ticker/bookTicker`, and `/api/v3/klines` endpoints.

`MarketDataSourceSupervisor` reports the source mode it actually achieved:

| Badge/source | Meaning |
|---|---|
| `LIVE WebSocket` | Public streaming data |
| `LIVE REST` | Public polled data after WebSocket degradation |
| `SIMULATED` | Locally generated prices |
| `DELAYED` or `HISTORICAL` | Supported provider modes for historical/rest data |

A simulated price is never labelled live.

## Technical and ML Components

### Indicators

The indicator layer provides simple and exponential moving averages, rate of change, rolling
volatility, RSI, MACD, Bollinger Bands, ATR, Stochastic Oscillator, VWAP, and order-book imbalance.
Rolling values are held per symbol and the pure indicator calculations are unit-tested.

### Rule-based signals

The baseline rule engine uses a 20-sample rolling window and a 5-sample ROC period. Insufficient
history or excessive volatility produces HOLD. EMA/SMA direction and ROC thresholds determine BUY
or SELL.

### Logistic-regression classifier

`FeatureExtractor`, `LogisticRegressionClassifier`, and `ModelTrainer` implement a genuine training
pipeline:

- Chronological train/test split; the test set is not shuffled with the training set.
- Forward-return labels with a configurable horizon.
- Per-feature standardisation learned from training data only.
- Mini-batch softmax-gradient-descent training with inverse-frequency class weighting and L2 regularization.
- Precision, recall, a confusion matrix, test accuracy, and a majority-class baseline.

An opt-in real-data evaluation trained on 1000 BTCUSDT hourly candles with a 5-bar horizon reported:

| Metric | Snapshot |
|---|---:|
| Train / test samples | 686 / 294 |
| Training accuracy | 43.15% |
| Test accuracy | 22.45% |
| Majority-class baseline | 23.13% |

The trained classifier did not beat the baseline in that run. It is an experimental educational
component, not a demonstrated trading edge or a guaranteed signal source.

## Paper Trading and Backtesting

Paper trading uses a virtual account and virtual execution. A market or limit order is filled against
successive market events rather than guaranteed at the immediately displayed price. The execution
model applies spread, adverse slippage, commission, fill fraction, and liquidity limits.

Working orders support:

```text
MARKET | LIMIT | STOP | STOP_LIMIT | TAKE_PROFIT
NEW -> OPEN -> PARTIALLY_FILLED -> FILLED
       \-> CANCELLED
       \-> REJECTED
```

The backtest engine processes bars chronologically: it evaluates strategies at bar `i` and fills
decisions at bar `i+1`'s open. It applies fees and slippage and reports total return, win rate,
maximum drawdown, profit factor, Sharpe ratio, average win/loss, and trade count.

## Risk Controls

The default risk manager validates every order against:

| Rule | Default behavior |
|---|---|
| Maximum order quantity | Reject quantities above the configured limit |
| Maximum position size | Reject projected position values above the limit |
| Daily maximum loss | Reject after the realized-loss threshold is reached |
| Price deviation | Reject prices too far from the current market price |
| Duplicate order | Reject identical orders inside the duplicate window |

Every rejection carries a reason and appears in the risk alerts and order history.

## Technologies

| Area | Technology |
|---|---|
| Language | Java 21 |
| UI | JavaFX 21.0.2, FXML, CSS |
| Build | Maven |
| Database | MySQL 9.x |
| Persistence | JDBC and `PreparedStatement`; no ORM |
| Testing | JUnit 5 |
| IDE | IntelliJ IDEA |

No Spring, Hibernate/JPA, web frontend, or real brokerage SDK is used.

## Prerequisites

- JDK 21
- Maven 3.9+ or IntelliJ's bundled Maven
- MySQL 9.x only if database persistence is wanted
- Internet access only for live Binance or live-network tests

## Setup

### 1. Clone and compile

```bash
mvn clean compile
```

### 2. Configure MySQL, if required

MySQL is optional. If no valid password or reachable database is available, the application logs a
warning and continues with in-memory state.

As a MySQL administrator, edit the password placeholder in
`src/main/resources/sql/provision_user.sql`, then run:

```bash
mysql -u root -p < src/main/resources/sql/provision_user.sql
```

The script creates the `trading_desk` database and a non-root `trading_app` account. Create the
schema with:

```bash
mysql -u trading_app -p trading_desk < src/main/resources/sql/schema.sql
```

The application also verifies required tables and can run its versioned migration files at startup.

### 3. Configure credentials

Credentials are never hardcoded. `DatabaseConfig` resolves each setting with this precedence:

1. Java system property
2. Environment variable
3. `config/db.properties`
4. Built-in default

PowerShell:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/trading_desk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME="trading_app"
$env:DB_PASSWORD="your-password"
```

Bash:

```bash
export DB_URL="jdbc:mysql://localhost:3306/trading_desk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DB_USERNAME="trading_app"
export DB_PASSWORD="your-password"
```

Alternatively copy `config/db.properties.example` to `config/db.properties` and fill in:

```properties
db.url=jdbc:mysql://localhost:3306/trading_desk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.username=trading_app
db.password=your-password
db.driver=com.mysql.cj.jdbc.Driver
```

The `config/db.properties` file is gitignored and must not be committed.

| Setting | System/environment key | Property key |
|---|---|---|
| JDBC URL | `DB_URL` | `db.url` |
| Username | `DB_USERNAME` | `db.username` |
| Password | `DB_PASSWORD` | `db.password` |
| Driver | none | `db.driver` |

## Running

```bash
mvn javafx:run
```

Or run `com.tradingdesk.Main` from IntelliJ.

Inside the application:

- **START** starts the market-data source and processing pipeline.
- **STOP** halts the simulator and lets workers drain.
- **RESET STATE** clears virtual portfolio, order, and metric state after confirmation.
- The sidebar switches routes.
- The search field filters the active screen.
- The instrument checkboxes select symbols for the pipeline.

### Offline mode

Set the environment variable before launching:

PowerShell:

```powershell
$env:TD_OFFLINE="true"
mvn javafx:run
```

IntelliJ run configurations can set `TD_OFFLINE=true` in the environment. The application can also
inspect the `td.offline` Java system property. Offline mode goes directly to the simulated source
and labels it as simulated.

You can also open a screen directly for demos:

```bash
mvn javafx:run -Dtd.startView=SYSTEM
```

## Database Persistence

The schema is split across `src/main/resources/sql/schema.sql` and additive migrations V1 through V6.
The baseline and migration runner include:

| Table area | Purpose |
|---|---|
| `instruments` | Instrument master data |
| `market_events` | Tick and quote events |
| `trading_signals` | Generated signals and indicator values |
| `orders` / `executions` | Order lifecycle and fills |
| `portfolio_snapshots` | Periodic cash, value, and P&L |
| `system_metrics` | Queue, throughput, latency, and worker measurements |
| `paper_accounts` / `paper_orders` / `paper_fills` | Paper-trading state |
| `strategies` / `backtest_runs` / `backtest_trades` | Backtest definitions and results |
| `candles` / `market_data_status` | Historical data and source status |
| `exchange_connections` | Non-secret exchange metadata only |

Migrations are additive and record applied versions in `schema_version`. Re-running a migration
runner is designed to be safe and does not drop or truncate existing data.

## Concurrency Model

- Fixed executor pool of `max(2, availableProcessors - 1)` workers.
- `LinkedBlockingQueue` with capacity 25,000; overflow is dropped and counted.
- Per-symbol indicator state protected by locks and concurrent caches.
- Atomic and `LongAdder` counters track throughput and latency.
- A scheduled executor drives simulator ticks and metric persistence.
- JavaFX controls are updated only on the JavaFX application thread.
- Shutdown uses bounded executor termination and worker coordination.

The metrics include processed events, throughput, average and peak latency, P50/P95/P99 latency,
queue depth, rejected/dropped counts, and process CPU load.

## Testing

Run the normal unit and offline test suite:

```bash
mvn clean test
```

The suite covers indicators, rolling windows, rule-based predictions, risk rules, order lifecycle,
portfolio P&L, paper fills, strategies, backtesting, concurrency, database configuration,
persistence, UI FXML loading, and offline/live fallback wiring.

### Verified test summary

| Configuration | Total | Passed | Failures | Skipped |
|---|---:|---:|---:|---:|
| Standard Surefire suite | 317 | 294 | 0 | 23 |

The standard suite result represents the normal test run with optional MySQL and live-network
configurations disabled. Its `23` skips include opt-in database and network-dependent tests.
The 317-test count therefore does not mean that every optional test ran in the same configuration.

When MySQL testing was enabled separately, **14/14 MySQL integration tests passed**. Live Binance
network testing is likewise separate and opt-in; it does not need to pass for the standard offline
suite to build successfully.

### MySQL integration tests

These tests are opt-in:

```bash
mvn test -Ddb.enabled=true
```

Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for the test JVM. The Maven Surefire configuration
forwards the flag and variables explicitly.

If the flag is enabled but credentials or MySQL are unavailable, the integration tests report the
database failure instead of silently passing.

### Live Binance tests

Network-dependent tests are also opt-in:

```bash
mvn test -Dlive.enabled=true
```

This enables public WebSocket, public REST, fallback, live-pipeline, and real-data model tests.
Network-dependent tests skip or assume unavailable services according to their test conditions.

## AI-Assisted Development Acknowledgement

GitHub Copilot assisted with documentation work on this repository, including reorganizing and
editing `README.md`, preserving verified feature descriptions, correcting duplicate or stale
documentation, and formatting test and architecture summaries. Copilot did not independently design
or develop the application. The source code, domain implementation, test suite, database setup,
and runtime behavior were created and maintained as this project evolved; Copilot assistance was
limited to the review and documentation tasks described here.

## Project Structure

```text
src/main/java/com/tradingdesk/
├── Main.java
├── application/       composition root and JavaFX entry point
├── backtest/          chronological backtesting and metrics
├── concurrency/       queue, executors, metrics, caches
├── controller/        routed-screen controllers
├── database/          connection, migrations, DAOs, persistence
├── indicators/        pure and rolling technical indicators
├── marketdata/        public Binance REST/WebSocket and supervisor
├── ml/                feature extraction, training, and classifier
├── model/             instruments, events, orders, positions, and snapshots
├── orders/            order lifecycle and matching
├── paper/             virtual account and execution engine
├── portfolio/         positions, cash, and P&L
├── prediction/        rule and ML prediction contracts
├── processor/         concurrent queue consumer
├── risk/              risk configuration and rule validation
├── service/           trading pipeline and application facade
├── simulator/         synthetic market-data source
├── strategy/          strategy contracts, implementations, registry
└── ui/                branding, snapshot, navigation, and reusable cells

src/main/resources/
├── css/
├── fxml/
├── branding/
├── icons/
├── db/migration/
└── sql/
```
