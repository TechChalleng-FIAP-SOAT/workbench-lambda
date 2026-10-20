# workbench-lambda

Serverless **CPF authentication** for the workbench-monolith platform (FIAP SOAT — Tech Challenge Fase 3).

This repository contains the AWS Lambda functions and the Terraform/CI‑CD needed to:

1. **Issue tokens** — validate a client's CPF, confirm the client exists and is **active** in the database, and return a signed **JWT**.
2. **Authorize requests** — validate that JWT on protected API Gateway routes before traffic reaches the backend.

---

## Responsibilities (two functions, one contract)

| Function | Handler | Role |
|---|---|---|
| **Auth issuer** | `com.workbench.workshop.auth.AuthHandler` | `POST /auth` — validates CPF, checks client status via the **Customer Service (REST)**, **issues** a JWT. Runs in the VPC to reach the in-cluster service. |
| **Token authorizer** | `com.workbench.workshop.auth.TokenAuthorizerHandler` | API Gateway `TOKEN` authorizer — **validates** the JWT on protected routes. No DB access. |

Both sign/verify with the **same shared `JWT_SECRET`** (HS256), so tokens issued here are verifiable across the platform.

> The issuer only *issues*; the authorizer only *validates*. A client authenticates once at `/auth`, then sends `Authorization: Bearer <token>` on every protected call.

---

## Technologies

- **Java 21**, Maven (fat jar via `maven-shade-plugin`)
- **jjwt 0.13.0** (HS256) — same library/version as the main app
- **Customer Service (REST)** — client lookup via `GET /api/clients/document/{cpf}`
  (the clients table moved to the Customer Service in Fase 4, Etapa 6; the Lambda
  no longer touches any database — ADR-0003)
- **AWS Lambda** (functions) — provisioned with **Terraform**. The API Gateway that
  fronts these functions lives in the **`workbench-gateway`** repo and references
  them via `terraform_remote_state`.
- **AWS Secrets Manager** — managed store for `JWT_SECRET` and DB credentials
- **GitHub Actions** — CI (test + `terraform validate`) and CD (`develop` and `main` both deploy)

---

## Architecture

```mermaid
flowchart LR
    client([Client])
    subgraph AWS
      apigw[API Gateway REST]
      issuer[Lambda: Auth Issuer]
      authz[Lambda: Token Authorizer]
      sm[(Secrets Manager)]
      customer[Customer Service on Kubernetes]
      backend[Backend API on Kubernetes]
    end

    client -- "POST /auth {cpf}" --> apigw
    apigw -- AWS_PROXY --> issuer
    issuer -- "GET /api/clients/document/{cpf}" --> customer
    issuer -- "signed JWT" --> client

    client -- "ANY /* (Bearer JWT)" --> apigw
    apigw -- "validate token" --> authz
    authz -- "Allow / Deny" --> apigw
    apigw -- "HTTP_PROXY (if allowed)" --> backend

    issuer -. reads .-> sm
    authz -. reads .-> sm
```

### Authentication sequence

```mermaid
sequenceDiagram
    participant C as Client
    participant G as API Gateway
    participant I as Auth Issuer (Lambda)
    participant D as Customer Service
    participant Z as Authorizer (Lambda)
    participant B as Backend API

    C->>G: POST /auth { cpf }
    G->>I: proxy event
    I->>I: validate CPF (check digits)
    I->>D: GET /api/clients/document/{cpf}
    alt 404 not found
        I-->>C: 404 Client not found
    else inactive
        I-->>C: 403 Client is inactive
    else valid & active
        I-->>C: 200 { token, expiresIn, client }
    end

    C->>G: GET /orders (Authorization: Bearer <token>)
    G->>Z: TOKEN authorizer
    Z->>Z: verify signature + expiration
    alt valid
        Z-->>G: Allow (principal = CPF)
        G->>B: forward request
        B-->>C: 200 protected response
    else invalid/expired
        Z-->>G: 401 Unauthorized
        G-->>C: 401
    end
```

---

## API

### `POST /auth`

Request:

```json
{ "cpf": "529.982.247-25" }
```

CPF is accepted formatted or as raw digits.

Responses:

| Status | Body | Meaning |
|---|---|---|
| `200` | `{ "token": "...", "expiresIn": 86400000, "client": { "id": 1, "name": "JOAO DA SILVA" } }` | Authenticated |
| `400` | `{ "error": "Invalid CPF" }` / `{ "error": "CPF is required" }` | Bad/missing CPF |
| `404` | `{ "error": "Client not found" }` | No client with that document |
| `403` | `{ "error": "Client is inactive" }` | Client exists but is deactivated |

The JWT payload:

```json
{
  "sub": "52998224725",
  "clientId": 1,
  "name": "JOAO DA SILVA",
  "roles": ["CLIENT"],
  "iat": 1690000000,
  "exp": 1690086400
}
```

### Postman / Bruno

The platform API collection lives in the main `workbench-monolith` repo under `bruno/workbench-api` (`01 - Auth`). Point the `baseUrl` environment variable at the API Gateway stage URL (the `auth_endpoint` output of the **`workbench-gateway`** repo).

---

## Build & test locally

```bash
./mvnw clean verify        # compile + run unit tests
./mvnw package             # build the Lambda fat jar (target/workbench-lambda.jar)
```

## Deploy (Terraform)

Prerequisites: an S3 bucket for remote state, a VPC with subnets/security groups that can reach the database, and the backend URL.

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars   # fill in real values (never commit)

terraform init \
  -backend-config="bucket=<state-bucket>" \
  -backend-config="key=lambda/develop/terraform.tfstate" \
  -backend-config="region=us-east-1"

terraform apply
```

Key outputs: `issuer_invoke_arn`, `authorizer_invoke_arn` (consumed by the gateway repo).

### Environment variables consumed by the functions

| Variable | Function | Purpose |
|---|---|---|
| `CUSTOMER_SERVICE_URL` | issuer | base URL of the Customer Service (CPF lookup via REST) |
| `JWT_SECRET` | issuer + authorizer | HS256 sign/verify (shared) |
| `JWT_EXPIRATION` | issuer | token lifetime (ms, default 86400000) |

---

## CI/CD

- **Branch protection:** `main` and `develop` — no direct commits; merges via Pull Request.
- **CI** (`.github/workflows/ci.yml`): on PRs to `develop`/`main` and on `feature/**` pushes — runs Java tests and `terraform fmt`/`validate`.
- **CD** (`.github/workflows/cd.yml`): on push to `develop` and on push to `main` — packages the jar and runs `terraform apply`. The branch name is used as the environment (no GitHub Environments needed), matching the workbench-monolith flat repo-secret convention.

### Network

The issuer runs inside the VPC (subnets + security group passed as explicit inputs)
so it can reach the **Customer Service** running in the EKS cluster. The security
group must allow egress to the Customer Service endpoint (in-cluster Service DNS or
an internal LoadBalancer). The authorizer needs no VPC (it only verifies JWTs).

### Required GitHub configuration (reused from workbench-monolith)

Repo **variables**: `TF_STATE_BUCKET`, `AWS_REGION`.

Repo **secrets**: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `JWT_SECRET`,
`CUSTOMER_SERVICE_URL_DEVELOP`/`CUSTOMER_SERVICE_URL_MAIN`,
`SUBNET_IDS_*`, `VPC_SECURITY_GROUP_IDS_*`.

> `JWT_SECRET`, `TF_STATE_BUCKET`, `AWS_REGION` and the AWS keys use the **same
> names and values** as workbench-monolith — reuse them. The database secrets
> (`DB_*`) are **no longer needed**: the issuer resolves the client via the Customer
> Service REST API. The backend URL (`ORIGIN_URL`) lives in the **`workbench-gateway`** repo.

---

## Observability

Both functions emit **one-line JSON logs** to CloudWatch (`level`, `event`, `requestId`, `function`, …).
`requestId` is the Lambda request id and lets a single call be traced across the API Gateway
access log → authorizer → issuer. CPF/CNPJ values are always masked (`529******25`).

The token authorizer returns an Allow policy scoped to the whole API stage (`…/<stage>/*/*`)
rather than the single method ARN, so API Gateway can cache the authorizer result
(`authorizer_result_ttl_in_seconds`) without denying every route after the first call.

## Backend integration (required)

A protected call only succeeds if **workbench-monolith** knows how to accept this token. The main
app runs a filter that recognises the CPF token (subject = CPF/CNPJ, `clientId` claim, no
`users` row), grants it `ROLE_CLIENT`, and resolves the client by document. Deploy a version
of workbench-monolith that includes that change (`feature/cpf-auth-integration` or later) — see its
`SecurityConfig` / `JwtFilter`.

## Notes

- Remember to add the **`soat-architecture`** user to this repository (Tech Challenge delivery requirement).
- Secrets are managed in AWS Secrets Manager and injected as Lambda environment variables so the function code stays runtime-agnostic; fetching them at runtime via the AWS SDK is a straightforward future hardening.
- `iss` / `aud` claims are not stamped yet: tokens are distinguished from staff tokens by the `clientId` claim. Adding `iss`/`aud` is a low-effort hardening if the platform ever gains a third token issuer.
