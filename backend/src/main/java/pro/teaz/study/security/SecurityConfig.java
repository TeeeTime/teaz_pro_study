package pro.teaz.study.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Production security: every request needs a valid Keycloak access token
 * (Bearer JWT) issued by the realm configured in application-prod.properties,
 * and the realm role {@value #USER_ROLE}: every lecture generated costs money,
 * so Study is opt-in per user. Courses are private to their owner (Access).
 */
@Configuration
@Profile("prod")
public class SecurityConfig {

	public static final String USER_ROLE = "study-user";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth.anyRequest().hasRole(USER_ROLE))
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable());
		return http.build();
	}

	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());
		return converter;
	}
}
