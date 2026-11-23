package io.app.learning.learn_cache.soap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;

@Slf4j
class DepositSoapFaultResolver extends SoapFaultMappingExceptionResolver {

    @Override
    protected SoapFaultDefinition getFaultDefinition(Object endpoint, Exception exception) {
        var fault = new SoapFaultDefinition();
        fault.setFaultCode(SoapFaultDefinition.SERVER);
        fault.setFaultStringOrReason("Unable to process deposit request");
        if (exception instanceof ResponseStatusException status
                && status.getStatusCode().is4xxClientError()) {
            fault.setFaultCode(SoapFaultDefinition.CLIENT);
            fault.setFaultStringOrReason(status.getStatusCode().equals(HttpStatus.NOT_FOUND)
                    ? "Deposit not found" : "Invalid deposit request");
        } else {
            log.error("SOAP deposit request failed", exception);
        }
        return fault;
    }
}
