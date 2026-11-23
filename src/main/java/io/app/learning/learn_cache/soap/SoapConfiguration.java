package io.app.learning.learn_cache.soap;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;

@Configuration
public class SoapConfiguration implements WsConfigurer {

    private static final ClassPathResource SCHEMA = new ClassPathResource("soap/deposits-v1.xsd");

    @Bean
    SimpleXsdSchema depositSchema() {
        return new SimpleXsdSchema(SCHEMA);
    }

    @Bean
    DefaultWsdl11Definition deposits(SimpleXsdSchema depositSchema) {
        var definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("DepositsPort");
        definition.setLocationUri("/soap");
        definition.setTargetNamespace(DepositEndpoint.NAMESPACE);
        definition.setSchema(depositSchema);
        return definition;
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        interceptors.add(payloadValidation());
    }

    @Bean
    PayloadValidatingInterceptor payloadValidation() {
        var validation = new PayloadValidatingInterceptor();
        validation.setSchema(SCHEMA);
        validation.setValidateRequest(true);
        validation.setValidateResponse(true);
        return validation;
    }

    @Bean
    DepositSoapFaultResolver depositSoapFaultResolver() {
        var resolver = new DepositSoapFaultResolver();
        resolver.setOrder(0);
        return resolver;
    }
}
