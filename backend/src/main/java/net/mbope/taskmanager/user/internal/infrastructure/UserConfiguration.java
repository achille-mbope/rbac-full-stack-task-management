package net.mbope.taskmanager.user.internal.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class UserConfiguration {
    @Bean
    PasswordEncoder userPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    Clock userClock() {
        return Clock.systemUTC();
    }
}
