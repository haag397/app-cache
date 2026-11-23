package io.app.learning.learn_cache.soap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DepositSoapIT.Containers.class)
@TestPropertySource(properties = "spring.cache.type=caffeine")
class DepositSoapIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void exposesVersionedContractAsWsdl() throws Exception {
        var response = http.send(HttpRequest.newBuilder(uri("/soap/deposits.wsdl")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("urn:learn-cache:deposits:v1",
                "openDeposit", "getDeposit", "closeDeposit", "DepositsPort", uri("/soap").toString());
    }

    @Test
    void opensReadsFromCacheAndClosesTheSameDepositAcrossSoapAndRest() throws Exception {
        var opened = soap("""
                <d:openDepositRequest>
                  <d:customerName>Sara</d:customerName>
                  <d:amount>2500.25</d:amount>
                </d:openDepositRequest>
                """);
        assertThat(opened.statusCode()).isEqualTo(200);
        String id = elementText(opened.body(), "id");
        UUID depositId = UUID.fromString(id);

        // A database change lets the next read prove the opening populated the cache.
        jdbc.sql("UPDATE deposits SET amount = 3000.00 WHERE id = ?").param(depositId).update();

        var found = soap("<d:getDepositRequest><d:id>" + id + "</d:id></d:getDepositRequest>");
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(elementText(found.body(), "amount")).isEqualTo("2500.25");
        var rest = http.send(HttpRequest.newBuilder(uri("/deposits/" + id)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(rest.statusCode()).isEqualTo(200);
        assertThat(rest.body()).contains("2500.25");

        var closed = soap("<d:closeDepositRequest><d:id>" + id + "</d:id></d:closeDepositRequest>");
        assertThat(closed.statusCode()).isEqualTo(200);
        assertThat(elementText(closed.body(), "id")).isEqualTo(id);

        var missing = soap("<d:getDepositRequest><d:id>" + id + "</d:id></d:getDepositRequest>");
        assertThat(missing.statusCode()).isEqualTo(500);
        assertThat(missing.body()).contains("Client", "Deposit not found");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM deposits WHERE id = ?")
                .param(depositId).query(Long.class).single()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<d:openDepositRequest><d:customerName>Sara</d:customerName><d:amount>-1</d:amount></d:openDepositRequest>",
            "<d:openDepositRequest><d:customerName> </d:customerName><d:amount>1</d:amount></d:openDepositRequest>",
            "<d:openDepositRequest><d:customerName>Sara</d:customerName><d:amount>1.001</d:amount></d:openDepositRequest>",
            "<d:openDepositRequest><d:customerName>Sara</d:customerName></d:openDepositRequest>",
            "<d:getDepositRequest><d:id>invalid-uuid</d:id></d:getDepositRequest>"
    })
    void rejectsRequestsThatViolateTheSchemaBeforeWritingToPostgres(String payload) throws Exception {
        long before = jdbc.sql("SELECT COUNT(*) FROM deposits").query(Long.class).single();

        var response = soap(payload);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("Fault", "Client");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM deposits").query(Long.class).single()).isEqualTo(before);
    }

    @Test
    void missingDepositProducesAClientFault() throws Exception {
        var response = soap("<d:getDepositRequest><d:id>" + UUID.randomUUID()
                + "</d:id></d:getDepositRequest>");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("Client", "Deposit not found");
    }

    private HttpResponse<String> soap(String payload) throws Exception {
        String envelope = """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                            xmlns:d="urn:learn-cache:deposits:v1">
                  <s:Body>%s</s:Body>
                </s:Envelope>
                """.formatted(payload);
        return http.send(HttpRequest.newBuilder(uri("/soap"))
                        .header("Content-Type", "text/xml; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(envelope)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String elementText(String xml, String localName) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return document.getElementsByTagNameNS(DepositEndpoint.NAMESPACE, localName).item(0).getTextContent();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:16");
        }
    }
}
