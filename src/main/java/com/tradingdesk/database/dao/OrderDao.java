package com.tradingdesk.database.dao;

import com.tradingdesk.model.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderDao {

    void insert(Order order);

    boolean update(Order order);

    Optional<Order> findById(UUID orderId);

    List<Order> findOpenOrders(int limit);

    List<Order> findHistory(int limit);

    long count();

    long countRejected();

    boolean delete(UUID orderId);

    int deleteAll();
}
