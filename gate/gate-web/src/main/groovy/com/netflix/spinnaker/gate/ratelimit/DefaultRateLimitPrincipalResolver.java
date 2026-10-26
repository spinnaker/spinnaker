/*
 * Copyright 2026 Spinnaker Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.gate.ratelimit;

import com.netflix.spinnaker.gate.security.RequestIdentityExtractor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Resolves who is making the request. This runs after the spring security filter chain, so every
 * supported authentication mechanism (basic, ldap, header, x509, oauth2/oidc, saml, api tokens) has
 * already put its {@link Authentication} in the {@link SecurityContextHolder}.
 *
 * <p>All anonymous callers share a single bucket. Keying them by IP or by any client-supplied value
 * would let a caller obtain a fresh bucket per request by changing it.
 */
@Slf4j
public class DefaultRateLimitPrincipalResolver implements RateLimitPrincipalResolver {

  private final List<RequestIdentityExtractor> requestIdentityExtractors;

  public DefaultRateLimitPrincipalResolver(List<RequestIdentityExtractor> extractors) {
    this.requestIdentityExtractors =
        Objects.requireNonNullElseGet(extractors, Collections::emptyList);
  }

  @Override
  public Optional<String> resolve(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null) {
      return extractIdentity(request);
    }

    if (authentication instanceof AnonymousAuthenticationToken) {
      return Optional.of(RateLimitPrincipalProvider.ANONYMOUS);
    }

    String name =
        authentication.getPrincipal() instanceof UserDetails userDetails
            ? userDetails.getUsername()
            : authentication.getName();

    if (name == null || name.isBlank()) {
      return Optional.of(RateLimitPrincipalProvider.ANONYMOUS);
    }

    return Optional.of(name);
  }

  private Optional<String> extractIdentity(HttpServletRequest request) {
    for (RequestIdentityExtractor extractor : requestIdentityExtractors) {
      if (!extractor.supports(request)) {
        continue;
      }

      try {
        String identity = extractor.extractIdentity(request);
        if (identity != null) {
          return Optional.of(identity);
        }
      } catch (Throwable t) {
        log.warn(
            "Failure extracting identity from RequestIdentityExtractor {}",
            extractor.getClass().getSimpleName(),
            t);
      }
    }

    return Optional.empty();
  }
}
