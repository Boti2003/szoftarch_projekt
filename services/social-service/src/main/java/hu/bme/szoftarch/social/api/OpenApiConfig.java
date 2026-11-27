package hu.bme.szoftarch.social.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI metadata for the Social Service.
 *
 * <p>Declares a single global JWT bearer security scheme so the Swagger UI
 * "Authorize" button is wired up: once a token is pasted there, Swagger attaches
 * {@code Authorization: Bearer <token>} to every "try it out" request. This
 * matches the resource-server setup, where all {@code /api/v1/**} routes require
 * a valid RS256 access token issued by Identity.
 *
 * <p>The scheme is only descriptive (it tells Swagger UI how to send the token);
 * actual validation is enforced by {@code SecurityConfig} / the resource server.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Social Service API",
        version = "v1",
        description = "Friends, friend requests, duels, leaderboard and notifications."),
    security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
@SecurityScheme(
    name = OpenApiConfig.BEARER_SCHEME,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Paste a valid access token (the raw JWT, without the \"Bearer \" prefix).")
public class OpenApiConfig {

    /** Name of the global bearer security scheme referenced by Swagger UI. */
    public static final String BEARER_SCHEME = "bearer-jwt";
}
