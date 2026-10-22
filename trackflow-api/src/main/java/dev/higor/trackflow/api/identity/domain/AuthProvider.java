package dev.higor.trackflow.api.identity.domain;

/** Where an account comes from. {@code DEV} exists only for the local development login. */
public enum AuthProvider {
    GITHUB,
    GOOGLE,
    DEV
}
