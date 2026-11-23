package io.app.learning.learn_cache.soap;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;

import static org.assertj.core.api.Assertions.assertThat;

class DepositSoapFaultResolverTest {

    private final DepositSoapFaultResolver resolver = new DepositSoapFaultResolver();

    @Test
    void missingDepositMapsToClientFault() {
        var fault = resolver.getFaultDefinition(null,
                new ResponseStatusException(HttpStatus.NOT_FOUND, "private context"));

        assertThat(fault.getFaultCode()).isEqualTo(SoapFaultDefinition.CLIENT);
        assertThat(fault.getFaultStringOrReason()).isEqualTo("Deposit not found");
    }

    @Test
    void invalidBusinessInputMapsToClientFaultWithoutLeakingInternalContext() {
        var fault = resolver.getFaultDefinition(null,
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "private context"));

        assertThat(fault.getFaultCode()).isEqualTo(SoapFaultDefinition.CLIENT);
        assertThat(fault.getFaultStringOrReason()).isEqualTo("Invalid deposit request");
    }

    @Test
    void unexpectedErrorMapsToGenericServerFault() {
        var fault = resolver.getFaultDefinition(null,
                new IllegalStateException("database credentials and internal SQL"));

        assertThat(fault.getFaultCode()).isEqualTo(SoapFaultDefinition.SERVER);
        assertThat(fault.getFaultStringOrReason()).isEqualTo("Unable to process deposit request");
    }
}
