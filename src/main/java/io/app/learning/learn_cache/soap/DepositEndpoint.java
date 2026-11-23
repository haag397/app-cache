package io.app.learning.learn_cache.soap;

import java.util.UUID;

import io.app.learning.learn_cache.deposit.DepositService;
import io.app.learning.learn_cache.soap.contract.CloseDepositRequest;
import io.app.learning.learn_cache.soap.contract.CloseDepositResponse;
import io.app.learning.learn_cache.soap.contract.Deposit;
import io.app.learning.learn_cache.soap.contract.GetDepositRequest;
import io.app.learning.learn_cache.soap.contract.GetDepositResponse;
import io.app.learning.learn_cache.soap.contract.OpenDepositRequest;
import io.app.learning.learn_cache.soap.contract.OpenDepositResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class DepositEndpoint {

    static final String NAMESPACE = "urn:learn-cache:deposits:v1";

    private final DepositService service;

    @PayloadRoot(namespace = NAMESPACE, localPart = "openDepositRequest")
    @ResponsePayload
    public OpenDepositResponse open(@RequestPayload OpenDepositRequest request) {
        var response = new OpenDepositResponse();
        response.setDeposit(toContract(service.openAndCache(request.getCustomerName(), request.getAmount())));
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "getDepositRequest")
    @ResponsePayload
    public GetDepositResponse get(@RequestPayload GetDepositRequest request) {
        var response = new GetDepositResponse();
        response.setDeposit(toContract(service.find(UUID.fromString(request.getId()))));
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "closeDepositRequest")
    @ResponsePayload
    public CloseDepositResponse close(@RequestPayload CloseDepositRequest request) {
        UUID id = UUID.fromString(request.getId());
        service.close(id);
        var response = new CloseDepositResponse();
        response.setId(id.toString());
        return response;
    }

    private static Deposit toContract(io.app.learning.learn_cache.deposit.Deposit deposit) {
        var result = new Deposit();
        result.setId(deposit.id().toString());
        result.setCustomerName(deposit.customerName());
        result.setAmount(deposit.amount());
        return result;
    }
}
