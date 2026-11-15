package com.workbench.workshop.auth;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthHandlerTest {

    private CustomerClient customerClient;
    private AuthHandler handler;

    private static final String VALID_CPF = "52998224725";

    @BeforeEach
    void setUp() {
        customerClient = mock(CustomerClient.class);
        JwtIssuer issuer = new JwtIssuer("test-secret-that-is-long-enough-for-hs256-signing!!", 86_400_000L);
        handler = new AuthHandler(customerClient, issuer);
    }

    private APIGatewayProxyResponseEvent invoke(String body) {
        return handler.handleRequest(new APIGatewayProxyRequestEvent().withBody(body), null);
    }

    @Test
    void returns400WhenCpfMissing() {
        assertThat(invoke("{}").getStatusCode()).isEqualTo(400);
    }

    @Test
    void returns400WhenCpfInvalid() {
        assertThat(invoke("{\"cpf\":\"12345678900\"}").getStatusCode()).isEqualTo(400);
    }

    @Test
    void unknownAndInactiveClientsGetTheSameAnswer() {
        // A resposta não pode revelar se o CPF está cadastrado.
        when(customerClient.findByDocument(anyString())).thenReturn(Optional.empty());
        var unknown = invoke("{\"cpf\":\"" + VALID_CPF + "\"}");

        when(customerClient.findByDocument(VALID_CPF))
                .thenReturn(Optional.of(new Client(1L, "JOAO", false)));
        var inactive = invoke("{\"cpf\":\"" + VALID_CPF + "\"}");

        assertThat(unknown.getStatusCode()).isEqualTo(401);
        assertThat(inactive.getStatusCode()).isEqualTo(401);
        assertThat(inactive.getBody()).isEqualTo(unknown.getBody());
    }

    @Test
    void returns200AndTokenOnHappyPath() {
        when(customerClient.findByDocument(VALID_CPF))
                .thenReturn(Optional.of(new Client(1L, "JOAO DA SILVA", true)));

        APIGatewayProxyResponseEvent response = invoke("{\"cpf\":\"529.982.247-25\"}");

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"token\":");
        assertThat(response.getBody()).contains("\"JOAO DA SILVA\"");
    }

    @Test
    void returns500WhenRepositoryFails() {
        when(customerClient.findByDocument(anyString()))
                .thenThrow(new CustomerClient.CustomerServiceException("customer down", new RuntimeException()));
        assertThat(invoke("{\"cpf\":\"" + VALID_CPF + "\"}").getStatusCode()).isEqualTo(500);
    }
}
