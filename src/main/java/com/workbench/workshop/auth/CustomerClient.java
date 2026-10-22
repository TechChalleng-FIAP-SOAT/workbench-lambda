package com.workbench.workshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Resolve o cliente pelo documento (CPF) chamando o <b>Customer Service</b> por REST,
 * em vez de ler a tabela {@code clients} diretamente (ADR-0003: nenhum serviço acessa
 * o banco de outro). Os clientes foram extraídos para o Customer Service na Etapa 6.
 *
 * <p>Contrato consumido: {@code GET /api/clients/document/{document}} →
 * {@code 200 {id,name,active,...}} | {@code 404} (não encontrado).
 */
public class CustomerClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String baseUrl;
    private final HttpClient httpClient;

    public CustomerClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    CustomerClient(String baseUrl, HttpClient httpClient) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.httpClient = httpClient;
    }

    /** Monta o cliente a partir das variáveis de ambiente. */
    public static CustomerClient fromEnv() {
        return new CustomerClient(requireEnv("CUSTOMER_SERVICE_URL"));
    }

    /** Busca por documento (dígitos). Vazio em 404; exceção em falha de comunicação. */
    public Optional<Client> findByDocument(String documentDigits) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/clients/document/" + documentDigits))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 404) {
                return Optional.empty();
            }
            if (status != 200) {
                throw new CustomerServiceException("Customer Service respondeu HTTP " + status, null);
            }
            CustomerPayload payload = MAPPER.readValue(response.body(), CustomerPayload.class);
            return Optional.of(new Client(payload.id(), payload.name(), payload.active()));
        } catch (CustomerServiceException e) {
            throw e;
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new CustomerServiceException("Falha ao consultar o Customer Service", e);
        }
    }

    /**
     * Valida credenciais de staff chamando {@code POST /api/users/auth/validate}.
     * Retorna o e-mail + roles quando as credenciais conferem; vazio em 401
     * (credenciais inválidas); exceção em falha de comunicação.
     */
    public Optional<StaffPrincipal> validateStaff(String email, String password) {
        String body;
        try {
            body = MAPPER.writeValueAsString(new StaffCredentials(email, password));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new CustomerServiceException("Falha ao serializar credenciais", e);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users/auth/validate"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401) {
                return Optional.empty();
            }
            if (status != 200) {
                throw new CustomerServiceException("Customer Service respondeu HTTP " + status, null);
            }
            StaffPayload payload = MAPPER.readValue(response.body(), StaffPayload.class);
            return Optional.of(new StaffPrincipal(payload.email(),
                    payload.roles() == null ? java.util.Set.of() : payload.roles()));
        } catch (CustomerServiceException e) {
            throw e;
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new CustomerServiceException("Falha ao validar staff no Customer Service", e);
        }
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("CUSTOMER_SERVICE_URL vazio");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    /** Projeção do JSON do Customer Service (ignora campos extras). */
    private record CustomerPayload(long id, String name, boolean active) {}

    /** Corpo enviado ao endpoint de validação de staff. */
    private record StaffCredentials(String email, String password) {}

    /** Projeção da resposta de validação de staff. */
    private record StaffPayload(String email, java.util.Set<String> roles) {}

    /** Staff autenticado (e-mail + roles) devolvido pela validação. */
    public record StaffPrincipal(String email, java.util.Set<String> roles) {}

    /** Falha de comunicação com o Customer Service (mapeada a 500 pelo handler). */
    public static class CustomerServiceException extends RuntimeException {
        public CustomerServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
