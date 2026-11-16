package com.workbench.workshop.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Corpo de POST /auth/staff — login de staff por e-mail/senha. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StaffAuthRequest(String email, String password) {}
