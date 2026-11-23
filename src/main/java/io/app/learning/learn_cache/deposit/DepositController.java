package io.app.learning.learn_cache.deposit;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/deposits")
class DepositController {

    private final DepositService service;

    @PostMapping
    ResponseEntity<Deposit> open(@Valid @RequestBody OpenDeposit request,
                 @RequestParam(defaultValue = "false") boolean warm) {
        Deposit deposit = warm ? service.openAndCache(request.customerName(), request.amount())
                : service.open(request.customerName(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(deposit);
    }

    @GetMapping("/{id}")
    ResponseEntity<Deposit> find(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean manual) {
        Deposit deposit = manual ? service.findManually(id) : service.find(id);
        return ResponseEntity.ok(deposit);
    }

    @PutMapping("/{id}/cache")
    ResponseEntity<Deposit> refresh(@PathVariable UUID id) {
        return ResponseEntity.ok(service.refresh(id));
    }

    @DeleteMapping("/{id}/cache")
    ResponseEntity<Void> evict(@PathVariable UUID id) {
        service.evict(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> close(@PathVariable UUID id) {
        service.close(id);
        return ResponseEntity.noContent().build();
    }

    record OpenDeposit(@NotBlank String customerName, @NotNull @Positive BigDecimal amount) {
    }
}
