package com.workbench.workshop.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

/**
 * Handler de login de STAFF por e-mail/senha (paridade com o {@code POST /auth/login}
 * do monólito). Espelha o {@link AuthHandler} (login de cliente por CPF):
 *
 * <ol>
 *   <li>Parseia e-mail/senha do corpo.</li>
 *   <li>Valida as credenciais no Customer Service
 *       ({@code POST /api/users/auth/validate}).</li>
 *   <li>401 quando inválidas; 200 com o JWT (roles do usuário) quando válidas.</li>
 * </ol>
 *
 * O token emitido NÃO tem a claim {@code clientId}; carrega as roles reais
 * (ADMIN/TECHNICIAN), de modo que os serviços o tratem como token de staff.
 */
public class StaffAuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CustomerClient customerClient;
    private final JwtIssuer jwtIssuer;

    /** Usado pelo runtime da Lambda. */
    public StaffAuthHandler() {
        this(CustomerClient.fromEnv(), JwtIssuer.fromEnv());
    }

    /** Usado pelos testes. */
    public StaffAuthHandler(CustomerClient customerClient, JwtIssuer jwtIssuer) {
        this.customerClient = customerClient;
        this.jwtIssuer = jwtIssuer;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        StaffAuthRequest request;
        try {
            request = MAPPER.readValue(Optional.ofNullable(event.getBody()).orElse("{}"), StaffAuthRequest.class);
        } catch (Exception e) {
            return error(400, "Invalid request body");
        }

        if (request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return error(400, "Email and password are required");
        }

        Optional<CustomerClient.StaffPrincipal> principal;
        try {
            principal = customerClient.validateStaff(request.email(), request.password());
        } catch (CustomerClient.CustomerServiceException e) {
            JsonLog.warn(context, "staff.auth.error", Map.of("reason", "customer_service_failure"));
            return error(500, "Internal error");
        }

        if (principal.isEmpty()) {
            JsonLog.info(context, "staff.auth.denied", Map.of("reason", "invalid_credentials", "status", 401));
            return error(401, "Invalid credentials");
        }

        CustomerClient.StaffPrincipal staff = principal.get();
        String token = jwtIssuer.issueStaff(staff.email(), staff.roles());
        JsonLog.info(context, "staff.auth.issued", Map.of("email", staff.email(), "status", 200));
        return json(200, new StaffAuthResponse(token, jwtIssuer.getExpirationMillis(),
                staff.email(), staff.roles()));
    }

    private APIGatewayProxyResponseEvent json(int status, Object body) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(MAPPER.writeValueAsString(body));
        } catch (Exception e) {
            return error(500, "Serialization error");
        }
    }

    private APIGatewayProxyResponseEvent error(int status, String message) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody("{\"error\":\"" + message + "\"}");
    }
}
