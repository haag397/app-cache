package io.app.learning.learn_cache.deposit;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Import(RedisDepositCacheIT.RedisContainers.class)
@TestPropertySource(properties = {"spring.cache.type=redis", "app.cache.ttl=60s"})
class RedisDepositCacheIT extends DepositCacheContract {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void storesJsonWithATtlAndReloadsFromPostgresAfterExpiry() throws Exception {
        var id = openDeposit(true);
        String key = "learn-cache:deposits::" + id;

        String cachedJson = redis.opsForValue().get(key);
        assertThat(cachedJson).isNotNull();
        assertThat(JsonPath.<String>read(cachedJson, "$.id")).isEqualTo(id.toString());
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);

        updateAmountOutsideTheApp(id, "30.00");
        assertThat(redis.expire(key, Duration.ofMillis(250))).isTrue();
        await().atMost(Duration.ofSeconds(5))
                .until(() -> Boolean.FALSE.equals(redis.hasKey(key)));

        assertAmount(id, 30.0);
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisContainers {

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redisContainer() {
            return new GenericContainer<>("redis:7").withExposedPorts(6379);
        }
    }
}
