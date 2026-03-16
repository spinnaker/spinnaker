package com.netflix.spinnaker.gate.security.apitoken;

import com.netflix.spinnaker.security.User;
import groovy.util.logging.Slf4j;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class APIAuthTokenFilter extends OncePerRequestFilter {
  @Autowired APITokenHelper provider;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");

    if (header != null && header.startsWith("Bearer ")) {
      // This may return null in the case of none of the methods were able to authenticate the
      // token.  Needed for when
      // you're doing BOTH Oauth & our API Token auth methods.

      ApiTokenAccount apiTokenAccount = provider.fetchAccount(header.substring(7));
      if (apiTokenAccount != null) {
        // vs. Principal vs. Authentication types...
        User user = new User();
        user.setEmail(
            apiTokenAccount.getUsername()); // we assume that it's the username in this case
        user.setRoles(apiTokenAccount.getRoles());
        PreAuthenticatedAuthenticationToken authenticated =
            new PreAuthenticatedAuthenticationToken(user, null, user.getAuthorities());
        try {
          HttpSession session = request.getSession(false);
          if (session != null && session.getAttribute("SPRING_SECURITY_CONTEXT") != null) {
            ((SecurityContextImpl) session.getAttribute("SPRING_SECURITY_CONTEXT"))
                .setAuthentication(authenticated);
          }
          SecurityContextHolder.getContext().setAuthentication(authenticated);
        } catch (Exception e) {
          logger.warn(
              "Error dealing with the bearer token... possible revocation or possibly OAuth2 token",
              e);
        }
      }
    }
    filterChain.doFilter(request, response);
  }
}
