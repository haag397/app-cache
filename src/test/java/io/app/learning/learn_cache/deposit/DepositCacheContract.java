package io.app.learning.learn_cache.deposit;

import java.math.BigDecimal;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import io.app.learning.learn_cache.LearnCacheApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {LearnCacheApplication.class, DepositCacheContract.DatabaseContainers.class})
@AutoConfigureMockMvc
abstract class DepositCacheContract {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void cacheAsideLoadsOnceThenEvictionMakesTheNextReadLoadFreshData() throws Exception {
        UUID id = openDeposit(false);
        updateAmountOutsideTheApp(id, "30.00");

        assertAmount(id, 30.0);

        updateAmountOutsideTheApp(id, "40.00");
        assertAmount(id, 30.0);

        mvc.perform(delete("/deposits/{id}/cache", id)).andExpect(status().isNoContent());
        assertAmount(id, 40.0);
    }

    @Test
    void warmOpeningPopulatesTheCacheAndRefreshReplacesIt() throws Exception {
        UUID id = openDeposit(true);
        updateAmountOutsideTheApp(id, "30.00");

        assertAmount(id, 25.0);
        mvc.perform(put("/deposits/{id}/cache", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(30.0));
        assertAmount(id, 30.0);
    }

    @Test
    void closingAWarmDepositDeletesTheRowAndEvictsTheCachedValue() throws Exception {
        UUID id = openDeposit(true);

        mvc.perform(delete("/deposits/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get("/deposits/{id}", id)).andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM deposits WHERE id = ?")
                .param(id).query(Long.class).single()).isZero();
    }

    @Test
    void missingDepositIsNotCached() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(get("/deposits/{id}", id)).andExpect(status().isNotFound());

        jdbc.sql("INSERT INTO deposits (id, customer_name, amount) VALUES (?, ?, ?)")
                .params(id, "Sara", new BigDecimal("25.00")).update();
        assertAmount(id, 25.0);
    }

    @Test
    void invalidOpeningReturnsBadRequest() throws Exception {
        mvc.perform(post("/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Sara","amount":-1}
                                """))
                .andExpect(status().isBadRequest());
    }

    protected UUID openDeposit(boolean warm) throws Exception {
        String response = mvc.perform(post("/deposits")
                        .param("warm", Boolean.toString(warm))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Sara","amount":25.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Sara"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    protected void assertAmount(UUID id, double amount) throws Exception {
        mvc.perform(get("/deposits/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(amount));
    }

    protected void updateAmountOutsideTheApp(UUID id, String amount) {
        assertThat(jdbc.sql("UPDATE deposits SET amount = ? WHERE id = ?")
                .params(new BigDecimal(amount), id).update()).isEqualTo(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseContainers {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:16");
        }
    }
}
