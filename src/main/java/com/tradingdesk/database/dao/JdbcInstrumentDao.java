package com.tradingdesk.database.dao;

import com.tradingdesk.database.DataAccessException;
import com.tradingdesk.database.DatabaseConnection;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class JdbcInstrumentDao implements InstrumentDao {

    private static final String UPSERT = """
            INSERT INTO instruments (symbol, display_name, instrument_type)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE display_name = VALUES(display_name),
                                    instrument_type = VALUES(instrument_type)
            """;

    private static final String FIND = """
            SELECT symbol, display_name, instrument_type FROM instruments WHERE symbol = ?
            """;

    private static final String FIND_ALL = """
            SELECT symbol, display_name, instrument_type FROM instruments ORDER BY symbol
            """;

    private static final String DELETE = "DELETE FROM instruments WHERE symbol = ?";

    private final DatabaseConnection database;

    public JdbcInstrumentDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void upsert(Instrument instrument) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, instrument.symbol());
            statement.setString(2, instrument.displayName());
            statement.setString(3, instrument.type().name());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to upsert instrument " + instrument.symbol(), e);
        }
    }

    @Override
    public Optional<Instrument> find(String symbol) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(FIND)) {
            statement.setString(1, symbol);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read instrument " + symbol, e);
        }
    }

    @Override
    public Map<String, Instrument> findAll() {
        Map<String, Instrument> instruments = new LinkedHashMap<>();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Instrument instrument = map(resultSet);
                instruments.put(instrument.symbol(), instrument);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read instruments", e);
        }
        return instruments;
    }

    @Override
    public boolean delete(String symbol) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, symbol);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Unable to delete instrument " + symbol, e);
        }
    }

    private static Instrument map(ResultSet resultSet) throws SQLException {
        return new Instrument(
                resultSet.getString("symbol"),
                resultSet.getString("display_name"),
                JdbcSupport.getEnum(resultSet, "instrument_type", InstrumentType.class));
    }
}
