package com.workbench.workshop.auth;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerClientTest {

    @SuppressWarnings("unchecked")
    private CustomerClient clientReturning(int status, String body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        return new CustomerClient("http://customer-service:8080", http);
    }

    @Test
    void parsesClientOn200() throws Exception {
        CustomerClient client = clientReturning(200,
                "{\"id\":7,\"name\":\"JOAO\",\"document\":\"52998224725\",\"active\":true,\"extra\":\"ignored\"}");

        Optional<Client> found = client.findByDocument("52998224725");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(7L);
        assertThat(found.get().name()).isEqualTo("JOAO");
        assertThat(found.get().active()).isTrue();
    }

    @Test
    void emptyOn404() throws Exception {
        CustomerClient client = clientReturning(404, "{\"error\":\"not found\"}");
        assertThat(client.findByDocument("52998224725")).isEmpty();
    }

    @Test
    void throwsOnOtherStatus() throws Exception {
        CustomerClient client = clientReturning(500, "boom");
        assertThatThrownBy(() -> client.findByDocument("52998224725"))
                .isInstanceOf(CustomerClient.CustomerServiceException.class);
    }

    @Test
    void throwsOnIoFailure() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenThrow(new java.io.IOException("connection refused"));
        CustomerClient client = new CustomerClient("http://customer-service:8080/", http);

        assertThatThrownBy(() -> client.findByDocument("52998224725"))
                .isInstanceOf(CustomerClient.CustomerServiceException.class);
    }

    @Test
    void rejectsBlankBaseUrl() {
        assertThatThrownBy(() -> new CustomerClient(""))
                .isInstanceOf(IllegalStateException.class);
    }
}
