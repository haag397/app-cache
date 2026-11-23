package io.app.learning.learn_cache.deposit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "spring.cache.type=caffeine")
class CaffeineDepositCacheIT extends DepositCacheContract {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void storesWarmDepositInTheJvmCache() throws Exception {
        var id = openDeposit(true);

        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
        assertThat(cacheManager.getCache(DepositCacheConfiguration.DEPOSITS).get(id, Deposit.class))
                .extracting(Deposit::id).isEqualTo(id);
    }
}
