package com.gtublog.auth;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class AdminBootstrapInitializer {

    @Bean
    ApplicationRunner bootstrapAdminUser(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            AuthProperties authProperties) {
        return args -> {
            if (adminUserRepository.findByUsername(authProperties.bootstrap().username()).isEmpty()) {
                var adminUser = AdminUser.bootstrap(
                        authProperties.bootstrap().username(),
                        passwordEncoder.encode(authProperties.bootstrap().password()),
                        authProperties.bootstrap().displayName());
                adminUserRepository.save(adminUser);
            }
        };
    }
}
