# Consumed by the workbench-gateway repo via terraform_remote_state.

output "issuer_function_name" {
  description = "Name of the auth issuer Lambda."
  value       = aws_lambda_function.issuer.function_name
}

output "issuer_invoke_arn" {
  description = "Invoke ARN of the auth issuer Lambda (for API Gateway integration)."
  value       = aws_lambda_function.issuer.invoke_arn
}

output "staff_issuer_function_name" {
  description = "Name of the staff auth issuer Lambda (email/password login)."
  value       = aws_lambda_function.staff_issuer.function_name
}

output "staff_issuer_invoke_arn" {
  description = "Invoke ARN of the staff auth issuer Lambda (for API Gateway integration)."
  value       = aws_lambda_function.staff_issuer.invoke_arn
}

output "authorizer_function_name" {
  description = "Name of the token authorizer Lambda."
  value       = aws_lambda_function.authorizer.function_name
}

output "authorizer_invoke_arn" {
  description = "Invoke ARN of the token authorizer Lambda (for API Gateway authorizer)."
  value       = aws_lambda_function.authorizer.invoke_arn
}
