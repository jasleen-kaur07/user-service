package com.ecommerce.userservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;


@Configuration
public class OpenApiConfig {

    private static final String IDENTITY_SCHEME = "gatewayUserId";
    private static final String USER_TYPE_SCHEME = "gatewayUserType";

    @Value("${server.port:8082}")
    private int serverPort;

    @Bean
    public OpenAPI userServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .version("v1")
                        .description("""
                                User profiles, customer addresses and basic merchant identity for the
                                Ecommerce Platform.

                                ### What this service owns
                                * the user profile - email, name, phone, userType (CUSTOMER or MERCHANT)
                                * customer addresses, including the single-default-address rule
                                * basic merchant identity - **the merchantId** shared with Merchant Service
                                  and Order Service

                                ### What it deliberately does not own
                                Authentication, passwords, password hashes, JWTs and OAuth belong to
                                **Auth Service**. Products, stock, prices, offers, ratings and reviews
                                belong to **Product Service** and **Merchant Service**. Carts belong to
                                **Cart Service**; orders and order history belong to **Order Service**;
                                emails belong to **Notification Service**.

                                No other service reads `user_service_db` directly - they call these REST
                                endpoints. This service publishes no Kafka events.

                                ### Authentication
                                The API Gateway validates the JWT and forwards the identity as headers:
                                `X-User-Id`, `X-User-Type` and optionally `X-User-Email`. Endpoints under
                                `/api/users` require that identity and additionally enforce that the caller
                                is the user named in the path. `/api/internal/**` and `GET /api/merchants/**`
                                are service-to-service and must not be exposed through the gateway.

                                Use **Authorize** to set the two headers and try the endpoints below.""")
                        .contact(new Contact().name("User Service / Order Service owner")))
                .servers(List.of(new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local development")))
                .components(new Components()
                        .addSecuritySchemes(IDENTITY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Id")
                                .description("Authenticated user's UUID, injected by the API Gateway after it validates the JWT."))
                        .addSecuritySchemes(USER_TYPE_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Type")
                                .description("CUSTOMER or MERCHANT, injected by the API Gateway.")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(IDENTITY_SCHEME)
                        .addList(USER_TYPE_SCHEME));
    }
}
