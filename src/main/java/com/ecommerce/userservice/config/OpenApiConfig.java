package com.ecommerce.userservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

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
                                * basic merchant identity - the merchantId shared with Merchant Service
                                  and Order Service

                                ### What it deliberately does not own
                                Authentication, passwords, password hashes, JWTs and OAuth belong to
                                Auth Service.

                                Products, stock, prices, offers, ratings and reviews belong to the
                                Product and Merchant Services.

                                Carts belong to Cart Service; orders and order history belong to
                                Order Service; emails belong to Notification Service.

                                No other service reads user_service_db directly - they call these REST
                                endpoints.
                                """)
                        .contact(new Contact()
                                .name("User Service / Order Service owner")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development")
                ));
    }
}