package com.workbench.workshop.auth;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffAuthHandlerTest {

    private CustomerClient customerClient;
    private StaffAuthHandler handler;

    @BeforeEach
    void setUp() {
        customerClient = mock(CustomerClient.class);
        JwtIssuer issuer = new JwtIssuer("test-secret-that-is-long-enough-for-hs256-signing!!", 86_400_000L);
        handler = new StaffAuthHandler(customerClient, issuer);
    }

    private APIGatewayProxyResponseEvent invoke(String body) {
        return handler.handleRequest(new APIGatewayProxyRequestEvent().withBody(body), null);
    }

    @Test
    void returns400WhenCredentialsMissing() {
        assertThat(invoke("{}").getStatusCode()).isEqualTo(400);
    }

    @Test
    void returns401WhenCredentialsInvalid() {
        when(customerClient.validateStaff(anyString(), anyString())).thenReturn(Optional.empty());
        assertThat(invoke("{\"email\":\"a@x.com\",\"password\":\"wrong\"}").getStatusCode()).isEqualTo(401);
    }

    @Test
    void returns500OnCustomerServiceFailure() {
        when(customerClient.validateStaff(anyString(), anyString()))
                .thenThrow(new CustomerClient.CustomerServiceException("down", null));
        assertThat(invoke("{\"email\":\"a@x.com\",\"password\":\"x\"}").getStatusCode()).isEqualTo(500);
    }

    @Test
    void returns200WithTokenWhenValid() {
        when(customerClient.validateStaff("admin@x.com", "segredo123"))
                .thenReturn(Optional.of(new CustomerClient.StaffPrincipal("admin@x.com", Set.of("ADMIN"))));

        APIGatewayProxyResponseEvent res = invoke("{\"email\":\"admin@x.com\",\"password\":\"segredo123\"}");

        assertThat(res.getStatusCode()).isEqualTo(200);
        assertThat(res.getBody()).contains("token").contains("ADMIN");
    }
}
