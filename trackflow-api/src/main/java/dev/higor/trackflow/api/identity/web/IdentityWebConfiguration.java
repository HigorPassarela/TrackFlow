package dev.higor.trackflow.api.identity.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Session security for the whole API.
 *
 * <p>Implements {@link WebMvcConfigurer} on purpose: {@code @WebMvcTest} slices pick up web
 * configurers, so every controller test runs behind these exact rules without importing anything.
 */
@Configuration(proxyBeanMethods = false)
class IdentityWebConfiguration implements WebMvcConfigurer {

    private static final RequestMatcher API =
            PathPatternRequestMatcher.withDefaults().matcher("/api/**");

    /**
     * API calls get a JSON problem instead of a redirect to the provider. Security exceptions go
     * through the MVC exception resolver so that {@code GlobalExceptionHandler} stays the only place
     * that writes error bodies.
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
            throws Exception {
        AccessDeniedHandler problemOnDenied = (request, response, exception) ->
                exceptionResolver.resolveException(request, response, null, exception);
        http.authorizeHttpRequests(requests -> requests.requestMatchers(
                                "/", "/index.html", "/demo/**", "/favicon.ico", "/error")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // Only exists with the "local" profile; elsewhere it answers 404 like any unknown route.
                        .requestMatchers("/api/dev/login")
                        .permitAll()
                        .requestMatchers("/api/listen/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2Login(login -> login.defaultSuccessUrl("/", true).failureUrl("/?login=failed"))
                .logout(logout -> logout.logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                (request, response, exception) ->
                                        exceptionResolver.resolveException(request, response, null, exception),
                                API)
                        .defaultAccessDeniedHandlerFor(problemOnDenied, API));
        return http.build();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedUserArgumentResolver());
    }
}
