package com.tradingdesk.database.dao;

import com.tradingdesk.database.DataAccessException;
import com.tradingdesk.database.DatabaseConnection;
import com.tradingdesk.model.SignalType;
import com.tradingdesk.model.TradeSignal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcTradeSignalDao implements TradeSignalDao {

    private static final String INSERT = """
            INSERT INTO trading_signals
                (symbol, signal_type, confidence, rationale, sma, ema, roc, volatility, imbalance,
                 generated_at, rsi, macd, macd_signal, macd_histogram, bollinger_upper,
                 bollinger_middle, bollinger_lower, atr, stochastic_k, stochastic_d, vwap)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_RECENT = """
            SELECT symbol, signal_type, confidence, rationale, sma, ema, roc, volatility, imbalance,
                   generated_at, rsi, macd, macd_signal, macd_histogram, bollinger_upper,
                   bollinger_middle, bollinger_lower, atr, stochastic_k, stochastic_d, vwap
            FROM trading_signals
            ORDER BY generated_at DESC, signal_id DESC
            LIMIT ?
            """;

    private static final String COUNT = "SELECT COUNT(*) FROM trading_signals";
    private static final String DELETE_ALL = "DELETE FROM trading_signals";

    private final DatabaseConnection database;
    private final InstrumentResolver instruments;

    public JdbcTradeSignalDao(DatabaseConnection database, InstrumentResolver instruments) {
        this.database = database;
        this.instruments = instruments;
    }

    @Override
    public void insert(TradeSignal signal) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            bind(statement, signal);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to insert trading signal for "
                    + signal.instrument().symbol(), e);
        }
    }

    @Override
    public void insertBatch(List<TradeSignal> signals) {
        if (signals.isEmpty()) {
            return;
        }
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                for (TradeSignal signal : signals) {
                    bind(statement, signal);
                    statement.addBatch();
                }
                return statement.executeBatch().length;
            }
        });
    }

    private static void bind(PreparedStatement statement, TradeSignal signal) throws SQLException {
        statement.setString(1, signal.instrument().symbol());
        statement.setString(2, signal.type().name());
        statement.setDouble(3, signal.confidence());
        statement.setString(4, truncate(signal.rationale(), 255));
        statement.setDouble(5, signal.indicators().simpleMovingAverage());
        statement.setDouble(6, signal.indicators().exponentialMovingAverage());
        statement.setDouble(7, signal.indicators().rateOfChange());
        statement.setDouble(8, signal.indicators().rollingVolatility());
        statement.setDouble(9, signal.indicators().orderBookImbalance());
        JdbcSupport.setInstant(statement, 10, signal.generatedAt());
        statement.setDouble(11, signal.indicators().rsi());
        statement.setDouble(12, signal.indicators().macd());
        statement.setDouble(13, signal.indicators().macdSignal());
        statement.setDouble(14, signal.indicators().macdHistogram());
        statement.setDouble(15, signal.indicators().bollingerUpper());
        statement.setDouble(16, signal.indicators().bollingerMiddle());
        statement.setDouble(17, signal.indicators().bollingerLower());
        statement.setDouble(18, signal.indicators().atr());
        statement.setDouble(19, signal.indicators().stochasticK());
        statement.setDouble(20, signal.indicators().stochasticD());
        statement.setDouble(21, signal.indicators().vwap());
    }

    @Override
    public List<TradeSignal> findRecent(int limit) {
        List<TradeSignal> signals = new ArrayList<>();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(FIND_RECENT)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    signals.add(new TradeSignal(
                            instruments.resolve(resultSet.getString("symbol")),
                            JdbcSupport.getEnum(resultSet, "signal_type", SignalType.class),
                            resultSet.getDouble("confidence"),
                            resultSet.getString("rationale"),
                            new com.tradingdesk.indicators.IndicatorSnapshot(
                                    resultSet.getDouble("sma"),
                                    resultSet.getDouble("ema"),
                                    resultSet.getDouble("roc"),
                                    resultSet.getDouble("volatility"),
                                    resultSet.getDouble("imbalance"),
                                    0,
                                    JdbcSupport.getInstant(resultSet, "generated_at"),
                                    resultSet.getDouble("rsi"),
                                    resultSet.getDouble("macd"),
                                    resultSet.getDouble("macd_signal"),
                                    resultSet.getDouble("macd_histogram"),
                                    resultSet.getDouble("bollinger_upper"),
                                    resultSet.getDouble("bollinger_middle"),
                                    resultSet.getDouble("bollinger_lower"),
                                    resultSet.getDouble("atr"),
                                    resultSet.getDouble("stochastic_k"),
                                    resultSet.getDouble("stochastic_d"),
                                    resultSet.getDouble("vwap")),
                            JdbcSupport.getInstant(resultSet, "generated_at")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read trading signals", e);
        }
        return signals;
    }

    @Override
    public long count() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(COUNT);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DataAccessException("Unable to count trading signals", e);
        }
    }

    @Override
    public int deleteAll() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to delete trading signals", e);
        }
    }

    static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
