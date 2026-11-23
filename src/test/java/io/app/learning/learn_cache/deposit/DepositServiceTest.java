package io.app.learning.learn_cache.deposit;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.web.server.ResponseStatusException;

import static io.app.learning.learn_cache.deposit.DepositCacheConfiguration.DEPOSITS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

    @Mock
    private DepositRepository repository;

    private DepositService service;

    @BeforeEach
    void setUp() {
        service = new DepositService(repository, new ConcurrentMapCacheManager(DEPOSITS));
    }

    @Test
    void openingNormalizesNameAndAmountBeforeSaving() {
        var deposit = sampleDeposit();
        when(repository.insert("Sara", new BigDecimal("25.00"))).thenReturn(deposit);

        assertThat(service.open("  Sara  ", new BigDecimal("25"))).isEqualTo(deposit);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidOpenings")
    void invalidOpeningNeverWritesToTheRepository(String scenario, String name, BigDecimal amount) {
        assertThatThrownBy(() -> service.open(name, amount))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(BAD_REQUEST));

        verifyNoInteractions(repository);
    }

    static Stream<Arguments> invalidOpenings() {
        return Stream.of(
                Arguments.of("missing name", null, BigDecimal.ONE),
                Arguments.of("blank name", "  ", BigDecimal.ONE),
                Arguments.of("name exceeds database limit", "a".repeat(121), BigDecimal.ONE),
                Arguments.of("missing amount", "Sara", null),
                Arguments.of("zero amount", "Sara", BigDecimal.ZERO),
                Arguments.of("negative amount", "Sara", BigDecimal.ONE.negate()),
                Arguments.of("fraction exceeds two digits", "Sara", new BigDecimal("1.001")),
                Arguments.of("amount exceeds database precision", "Sara", new BigDecimal("10000000000000000")));
    }

    @Test
    void manualCacheReadsRepositoryOnceAcrossMissAndHit() {
        var deposit = sampleDeposit();
        when(repository.find(deposit.id())).thenReturn(deposit);

        assertThat(service.findManually(deposit.id())).isEqualTo(deposit);
        assertThat(service.findManually(deposit.id())).isEqualTo(deposit);

        verify(repository).find(deposit.id());
    }

    @Test
    void manualCacheDoesNotStoreFailedLookup() {
        var deposit = sampleDeposit();
        when(repository.find(deposit.id()))
                .thenThrow(new ResponseStatusException(NOT_FOUND))
                .thenReturn(deposit);

        assertThatThrownBy(() -> service.findManually(deposit.id()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(NOT_FOUND));
        assertThat(service.findManually(deposit.id())).isEqualTo(deposit);
    }

    private static Deposit sampleDeposit() {
        return new Deposit(UUID.randomUUID(), "Sara", new BigDecimal("25.00"));
    }
}
