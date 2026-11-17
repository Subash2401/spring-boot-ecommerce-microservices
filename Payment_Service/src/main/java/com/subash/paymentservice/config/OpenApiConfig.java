package com.subash.paymentservice.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

// Swagger / OpenAPI description of this service. springdoc builds the spec from the
// controllers and serves it at /v3/api-docs; the Swagger UI itself runs in the API gateway
// (http://localhost:8080/swagger-ui.html), which loads this spec through /api-docs/<service>.
//
// - servers "/": "Try it out" sends requests to the page's own origin, the gateway,
//   so they get the same routing, rate limits and JWT check as the frontend.
// - bearerAuth: adds the "Authorize" button; paste an access token from POST /users/login
//   and every request carries "Authorization: Bearer <token>".
@Configuration
@OpenAPIDefinition(
		info = @Info(title = "Payment Service API", version = "v1", description = "See and pay the payment of your own order (mock payment gateway)."),
		servers = @Server(url = "/", description = "Through the API gateway"),
		security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
