variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "aws_account_id" {
  description = "Expected AWS account ID. Guards the provider against deploying to the wrong account. Empty = no restriction (local validation)."
  type        = string
  default     = ""
}

variable "environment" {
  description = "Deployment environment. Matches the branch name: develop or main."
  type        = string
}

variable "name_prefix" {
  description = "Prefix for resource names."
  type        = string
  default     = "os-auth"
}

variable "lambda_jar_path" {
  description = "Path to the built Lambda fat jar."
  type        = string
  default     = "../target/workbench-lambda.jar"
}

variable "lambda_runtime" {
  description = "Lambda Java runtime."
  type        = string
  default     = "java21"
}

variable "lambda_memory_mb" {
  description = "Memory (MB) for the auth issuer Lambda."
  type        = number
  default     = 512
}

variable "lambda_timeout_seconds" {
  description = "Timeout (seconds) for the Lambda functions."
  type        = number
  default     = 15
}

# --- Customer Service (CPF lookup via REST) ------------------------------

variable "customer_service_url" {
  description = "Base URL do Customer Service. Use https:// ou o NLB INTERNO do customer-service (http://<dns-do-nlb-interno>:8080, só alcançável dentro da VPC). O DNS *.svc.cluster.local não resolve fora do cluster."
  type        = string

  validation {
    condition     = can(regex("^https?://[^/]+", var.customer_service_url))
    error_message = "customer_service_url deve ser uma URL absoluta http:// ou https://."
  }
}

variable "customer_service_url_internal" {
  description = "true quando customer_service_url é o NLB interno (tráfego só dentro da VPC). Com false, só https:// é aceito: a Lambda de staff envia e-mail e senha por essa URL."
  type        = bool
  default     = false
}

# --- JWT ------------------------------------------------------------------

variable "jwt_secret" {
  description = "Shared HMAC secret used to sign and verify JWTs. Must match the platform value."
  type        = string
  sensitive   = true
}

variable "jwt_expiration_ms" {
  description = "JWT lifetime in milliseconds."
  type        = number
  default     = 86400000
}

# --- VPC (auth issuer needs to reach the Customer Service in-cluster) -----

variable "vpc_subnet_ids" {
  description = "Private subnet IDs for the auth issuer Lambda."
  type        = list(string)
}

variable "vpc_security_group_ids" {
  description = "Security group IDs for the Lambda (must reach the Customer Service)."
  type        = list(string)
}

# --- Protected backend (target of the authorized routes) ------------------
# (Removed: the API Gateway and its backend proxy now live in workbench-gateway.)
