package io.app.learning.learn_cache.deposit;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Repository
@RequiredArgsConstructor
class DepositRepository {

    private final JdbcClient jdbc;

    Deposit insert(String customerName, BigDecimal amount) {
        var deposit = new Deposit(UUID.randomUUID(), customerName, amount);
        jdbc.sql("INSERT INTO deposits (id, customer_name, amount) VALUES (?, ?, ?)")
                .params(deposit.id(), deposit.customerName(), deposit.amount()).update();
        return deposit;
    }

    Deposit find(UUID id) {
        log.info("Loading deposit {} from the database", id);
        return jdbc.sql("SELECT id, customer_name, amount FROM deposits WHERE id = ?")
                .param(id)
                .query((rs, row) -> new Deposit(rs.getObject("id", UUID.class),
                        rs.getString("customer_name"), rs.getBigDecimal("amount")))
                .optional()
                .orElseThrow(DepositRepository::notFound);
    }

    void delete(UUID id) {
        if (jdbc.sql("DELETE FROM deposits WHERE id = ?").param(id).update() == 0) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Deposit not found");
    }
}
