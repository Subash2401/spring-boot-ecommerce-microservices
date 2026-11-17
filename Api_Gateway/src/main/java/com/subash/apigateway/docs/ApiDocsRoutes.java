package com.subash.apigateway.docs;

import java.util.List;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

// Routes for the Swagger UI (http://localhost:8080/swagger-ui.html).
// Each business service serves its OpenAPI spec at /v3/api-docs. The Swagger UI here
// loads them through the gateway:  GET /api-docs/order-service  ->  order-service /v3/api-docs
//
// These few routes are in Java, while all other routes are in api-gateway.properties
// (config repo): they never change, and "/api-docs/..." (not "/v3/api-docs/...") avoids a
// clash with the gateway's own springdoc endpoints such as /v3/api-docs/swagger-config.
@Configuration
public class ApiDocsRoutes {

	private static final List<String> SERVICES = List.of("user-service", "product-service", "order-service",
			"payment-service", "notification-service");

	@Bean
	public RouteLocator apiDocsRouteLocator(RouteLocatorBuilder builder) {
		RouteLocatorBuilder.Builder routes = builder.routes();
		for (String service : SERVICES) {
			routes.route("api-docs-" + service, route -> route.method(HttpMethod.GET).and()
					.path("/api-docs/" + service)
					.filters(filter -> filter.setPath("/v3/api-docs"))
					.uri("lb://" + service)); // lb:// = find the service in Eureka
		}
		return routes.build();
	}
}
