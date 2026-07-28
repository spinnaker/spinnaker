/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.authz.filter;

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import com.netflix.spinnaker.security.token.TokenValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Builds a Spring {@link Authentication} from the signed identity token carried in the {@link
 * Header#IDENTITY_TOKEN} header. On a verified token, the caller's roles become {@code ROLE_*}
 * authorities and the admin / account-manager flags become the corresponding Spinnaker authorities
 * — so each service acts only on roles Gate (or the run-as minter) cryptographically vouched for.
 *
 * <p>Honors the {@code authz.enabled} flag: when {@code false} (authorization disabled, the
 * default), an absent or invalid token falls back to the legacy unsigned {@link
 * AuthenticatedRequest} identity (for audit only — enforcement is off); when {@code true}
 * (enforced), an absent or invalid token yields an anonymous authentication.
 */
public class IdentityTokenAuthenticationConverter implements AuthenticationConverter {

  private static final Logger log =
      LoggerFactory.getLogger(IdentityTokenAuthenticationConverter.class);

  private final SpinnakerTokenVerifier tokenVerifier;
  private final AuthorizationProperties authz;

  public IdentityTokenAuthenticationConverter(
      SpinnakerTokenVerifier tokenVerifier, AuthorizationProperties authz) {
    this.tokenVerifier = tokenVerifier;
    this.authz = authz;
  }

  @Override
  public Authentication convert(HttpServletRequest request) {
    String token = request.getHeader(Header.IDENTITY_TOKEN.getHeader());

    if (StringUtils.isNotBlank(token)) {
      try {
        return fromClaims(tokenVerifier.verify(token));
      } catch (TokenValidationException e) {
        if (authz.isEnabled()) {
          log.debug("Rejecting request with invalid identity token (authz enabled)", e);
          return anonymous();
        }
        log.debug("Ignoring invalid identity token (authz disabled); falling back to headers", e);
      }
    } else if (authz.isEnabled()) {
      log.debug("Rejecting request with no identity token (authz enabled)");
      return anonymous();
    }

    return fromUnsignedHeaders();
  }

  /** Builds an {@link Authentication} from verified token claims. */
  public static Authentication fromClaims(SpinnakerTokenClaims claims) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (String role : claims.getRoles()) {
      authorities.add(SpinnakerAuthorities.forRoleName(role));
    }
    if (claims.isAdmin()) {
      authorities.add(SpinnakerAuthorities.ADMIN_AUTHORITY);
    }
    if (claims.isAccountManager()) {
      authorities.add(SpinnakerAuthorities.ACCOUNT_MANAGER_AUTHORITY);
    }
    return new PreAuthenticatedAuthenticationToken(claims.getSubject(), "N/A", authorities);
  }

  private Authentication fromUnsignedHeaders() {
    return AuthenticatedRequest.getSpinnakerUser()
        .map(
            user ->
                (Authentication) new PreAuthenticatedAuthenticationToken(user, "N/A", List.of()))
        .orElseGet(IdentityTokenAuthenticationConverter::anonymous);
  }

  private static Authentication anonymous() {
    return new AnonymousAuthenticationToken(
        "anonymous", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
  }
}
