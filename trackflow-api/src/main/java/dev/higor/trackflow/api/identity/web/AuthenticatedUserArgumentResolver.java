package dev.higor.trackflow.api.identity.web;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controllers declare {@link AuthenticatedUser} as a plain parameter. Both the OAuth2 login
 * and the development login leave an {@link OAuth2User} with our attributes in the session.
 */
final class AuthenticatedUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(AuthenticatedUser.class);
    }

    @Override
    public AuthenticatedUser resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User principal)) {
            throw new InsufficientAuthenticationException("No signed-in user for " + parameter.getMethod());
        }
        String userId = principal.getAttribute(AuthenticatedUser.USER_ID_ATTRIBUTE);
        String displayName = principal.getAttribute(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE);
        if (userId == null || displayName == null) {
            throw new InsufficientAuthenticationException("Session principal has no Trackflow user");
        }
        return new AuthenticatedUser(UUID.fromString(userId), displayName);
    }
}
