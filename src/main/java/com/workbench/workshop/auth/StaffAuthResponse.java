package com.workbench.workshop.auth;

import java.util.Set;

/** Corpo de sucesso do login de staff. */
public record StaffAuthResponse(String token, long expiresIn, String email, Set<String> roles) {}
