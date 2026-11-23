package io.app.learning.learn_cache.deposit;

import java.math.BigDecimal;
import java.util.UUID;

public record Deposit(UUID id, String customerName, BigDecimal amount) {
}
