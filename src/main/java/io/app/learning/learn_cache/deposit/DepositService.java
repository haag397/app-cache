package io.app.learning.learn_cache.deposit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static io.app.learning.learn_cache.deposit.DepositCacheConfiguration.DEPOSITS;

@Slf4j
@Service
@CacheConfig(cacheNames = DEPOSITS)
public class DepositService {

    private final DepositRepository repository;
    private final Cache cache;

    DepositService(DepositRepository repository, CacheManager cacheManager) {
        this.repository = repository;
        this.cache = Objects.requireNonNull(cacheManager.getCache(DEPOSITS));
    }

    // A cold opening makes the first read demonstrate a cache miss.
    public Deposit open(String customerName, BigDecimal amount) {
        if (customerName == null || customerName.isBlank() || customerName.strip().length() > 120) {
            throw badRequest("Customer name must contain 1 to 120 characters");
        }
        if (amount == null || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 2
                || amount.compareTo(new BigDecimal("10000000000000000")) >= 0) {
            throw badRequest("Amount must be positive with up to 16 integer digits and 2 decimal places");
        }
        return repository.insert(customerName.strip(), amount.setScale(2, RoundingMode.UNNECESSARY));
    }

    @Cacheable
    public Deposit find(UUID id) {
        return repository.find(id);
    }

    public Deposit findManually(UUID id) {
        Deposit cached = cache.get(id, Deposit.class);
        if (cached != null) {
            log.info("Manual cache HIT for {}", id);
            return cached;
        }
        log.info("Manual cache MISS for {}", id);
        Deposit deposit = repository.find(id);
        cache.put(id, deposit);
        return deposit;
    }

    @CachePut(key = "#result.id()")
    public Deposit openAndCache(String customerName, BigDecimal amount) {
        return open(customerName, amount);
    }

    @CachePut
    public Deposit refresh(UUID id) {
        return repository.find(id);
    }

    @CacheEvict
    public void close(UUID id) {
        repository.delete(id);
    }

    @CacheEvict
    public void evict(UUID id) {
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
