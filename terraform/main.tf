locals {
  auth_function_name       = "${var.name_prefix}-issuer-${var.environment}"
  staff_auth_function_name = "${var.name_prefix}-staff-issuer-${var.environment}"
  authorizer_function_name = "${var.name_prefix}-authorizer-${var.environment}"
}

# =============================================================================
# Secrets Manager (managed source of truth for sensitive values)
# =============================================================================
# Values are also injected as Lambda environment variables so the function code
# (which reads plain env vars) works unchanged. Fetching these at runtime via the
# AWS SDK is a future hardening step.

# Chave PRIVADA que assina os tokens (RS256). Só os emissores (login de cliente e de staff) a
# recebem; o authorizer e os microsserviços têm apenas a chave pública.
resource "aws_secretsmanager_secret" "jwt" {
  name        = "${var.name_prefix}/jwt-private-key/${var.environment}"
  description = "RSA private key (PKCS#8, Base64 DER) that signs the platform JWTs (RS256)."
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = var.jwt_private_key
}

# O issuer não lê mais o banco (clientes migraram para o Customer Service na Etapa 6);
# resolve o cliente por REST. Guarda apenas a URL base do Customer Service.
resource "aws_secretsmanager_secret" "customer" {
  name        = "${var.name_prefix}/customer-service-url/${var.environment}"
  description = "Base URL of the Customer Service (CPF lookup via REST)."
}

resource "aws_secretsmanager_secret_version" "customer" {
  secret_id     = aws_secretsmanager_secret.customer.id
  secret_string = var.customer_service_url
}

# =============================================================================
# IAM
# =============================================================================

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

# --- Auth issuer role (needs VPC access to reach the Customer Service in-cluster) ---
resource "aws_iam_role" "issuer" {
  name               = "${local.auth_function_name}-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

resource "aws_iam_role_policy_attachment" "issuer_basic" {
  role       = aws_iam_role.issuer.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "issuer_vpc" {
  role       = aws_iam_role.issuer.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy_document" "issuer_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.jwt.arn, aws_secretsmanager_secret.customer.arn]
  }
}

resource "aws_iam_role_policy" "issuer_secrets" {
  name   = "read-secrets"
  role   = aws_iam_role.issuer.id
  policy = data.aws_iam_policy_document.issuer_secrets.json
}

# --- Authorizer role (validates JWT only; no VPC, no DB) ---
resource "aws_iam_role" "authorizer" {
  name               = "${local.authorizer_function_name}-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

resource "aws_iam_role_policy_attachment" "authorizer_basic" {
  role       = aws_iam_role.authorizer.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# O authorizer só verifica tokens com a chave PÚBLICA (variável de ambiente, não é segredo):
# não lê a chave privada.

# =============================================================================
# Lambda functions
# =============================================================================

resource "aws_lambda_function" "issuer" {
  function_name    = local.auth_function_name
  role             = aws_iam_role.issuer.arn
  runtime          = var.lambda_runtime
  handler          = "com.workbench.workshop.auth.AuthHandler::handleRequest"
  filename         = var.lambda_jar_path
  source_code_hash = filebase64sha256(var.lambda_jar_path)
  memory_size      = var.lambda_memory_mb
  timeout          = var.lambda_timeout_seconds

  depends_on = [
    aws_iam_role_policy_attachment.issuer_basic,
    aws_iam_role_policy_attachment.issuer_vpc,
    aws_iam_role_policy.issuer_secrets,
  ]

  vpc_config {
    subnet_ids         = local.subnet_ids
    security_group_ids = local.security_group_ids
  }

  environment {
    variables = {
      CUSTOMER_SERVICE_URL = var.customer_service_url
      JWT_PRIVATE_KEY      = var.jwt_private_key
      JWT_EXPIRATION       = tostring(var.jwt_expiration_ms)
    }
  }
}

# Login de STAFF (e-mail/senha). Reaproveita a role do issuer (VPC + segredos),
# pois também chama o Customer Service e assina o JWT com a chave privada (JWT_PRIVATE_KEY).
resource "aws_lambda_function" "staff_issuer" {
  function_name    = local.staff_auth_function_name
  role             = aws_iam_role.issuer.arn
  runtime          = var.lambda_runtime
  handler          = "com.workbench.workshop.auth.StaffAuthHandler::handleRequest"
  filename         = var.lambda_jar_path
  source_code_hash = filebase64sha256(var.lambda_jar_path)
  memory_size      = var.lambda_memory_mb
  timeout          = var.lambda_timeout_seconds

  depends_on = [
    aws_iam_role_policy_attachment.issuer_basic,
    aws_iam_role_policy_attachment.issuer_vpc,
    aws_iam_role_policy.issuer_secrets,
  ]

  vpc_config {
    subnet_ids         = local.subnet_ids
    security_group_ids = local.security_group_ids
  }

  environment {
    variables = {
      CUSTOMER_SERVICE_URL = var.customer_service_url
      JWT_PRIVATE_KEY      = var.jwt_private_key
      JWT_EXPIRATION       = tostring(var.jwt_expiration_ms)
    }
  }

  lifecycle {
    # A senha do staff nunca trafega em claro fora da VPC.
    precondition {
      condition     = startswith(var.customer_service_url, "https://") || var.customer_service_url_internal
      error_message = "customer_service_url precisa ser https://, ou o NLB interno do customer-service com customer_service_url_internal = true."
    }
  }
}

resource "aws_lambda_function" "authorizer" {
  function_name    = local.authorizer_function_name
  role             = aws_iam_role.authorizer.arn
  runtime          = var.lambda_runtime
  handler          = "com.workbench.workshop.auth.TokenAuthorizerHandler::handleRequest"
  filename         = var.lambda_jar_path
  source_code_hash = filebase64sha256(var.lambda_jar_path)
  memory_size      = 256
  timeout          = var.lambda_timeout_seconds

  depends_on = [
    aws_iam_role_policy_attachment.authorizer_basic,
  ]

  environment {
    variables = {
      JWT_PUBLIC_KEY = var.jwt_public_key
    }
  }
}
