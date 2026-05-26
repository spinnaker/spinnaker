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

package com.netflix.spinnaker.gate.filters;

import com.netflix.spinnaker.gate.security.apitoken.ApiTokenAuthenticationFilter;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Centralised classification of the auth mechanism for an inbound request, used by {@link
 * RequestMetricsFilter} to tag {@code gate.requests}. Auth filters can publish an authoritative tag
 * via {@link #AUTH_TYPE_ATTRIBUTE} (low-cardinality string); otherwise we fall back to heuristics
 * over {@link SecurityContextHolder}. {@link ApiTokenAuthenticationFilter} sets {@link
 * #TYPE_API_TOKEN}.
 */
public final class AuthTypeResolver {

  /**
   * Request attribute key under which an auth filter may publish a low-cardinality {@code String}
   * naming the authentication mechanism it just applied.
   */
  public static final String AUTH_TYPE_ATTRIBUTE = "gate.authType";

  /**
   * Request attribute key under which an auth filter may publish a low-cardinality {@code String}
   * describing the kind of principal (e.g. {@code user} vs {@code service_account}).
   */
  public static final String PRINCIPAL_KIND_ATTRIBUTE = "gate.principalKind";

  public static final String TYPE_API_TOKEN = "api_token";
  public static final String TYPE_SESSION = "session";
  public static final String TYPE_OAUTH2 = "oauth2";
  public static final String TYPE_X509 = "x509";
  public static final String TYPE_PRE_AUTHENTICATED = "pre_authenticated";
  public static final String TYPE_ANONYMOUS = "anonymous";
  public static final String TYPE_NONE = "none";

  public static final String PRINCIPAL_KIND_USER = "user";
  public static final String PRINCIPAL_KIND_SERVICE_ACCOUNT = "service_account";
  public static final String PRINCIPAL_KIND_UNKNOWN = "unknown";

  private AuthTypeResolver() {}

  /**
   * Returns the auth-type tag value for the given request. Resolution order:
   *
   * <ol>
   *   <li>The {@link #AUTH_TYPE_ATTRIBUTE} request attribute, if set to a {@code String} by an auth
   *       filter.
   *   <li>Heuristic inspection of {@link SecurityContextHolder#getContext()}'s {@link
   *       Authentication}.
   * </ol>
   *
   * <p>Never returns {@code null}; an unauthenticated request yields {@link #TYPE_NONE} or {@link
   * #TYPE_ANONYMOUS}.
   */
  public static String resolveAuthType(HttpServletRequest request) {
    Object explicit = request.getAttribute(AUTH_TYPE_ATTRIBUTE);
    if (explicit instanceof String) {
      return (String) explicit;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return TYPE_NONE;
    }
    if (auth instanceof AnonymousAuthenticationToken) {
      return TYPE_ANONYMOUS;
    }
    String simpleName = auth.getClass().getSimpleName();
    String lower = simpleName.toLowerCase(Locale.ROOT);
    if (lower.contains("oauth2")) {
      return TYPE_OAUTH2;
    }
    if (lower.contains("x509")) {
      return TYPE_X509;
    }
    if (auth instanceof PreAuthenticatedAuthenticationToken) {
      // IAP / header / X509 all share this class; filters wanting finer tags set
      // AUTH_TYPE_ATTRIBUTE.
      return TYPE_PRE_AUTHENTICATED;
    }
    if (auth instanceof UsernamePasswordAuthenticationToken && auth.isAuthenticated()) {
      return TYPE_SESSION;
    }
    return simpleName;
  }

  /**
   * Returns the principal-kind tag for the request. Only {@link ApiTokenAuthenticationFilter}
   * populates this today; everything else is {@link #PRINCIPAL_KIND_UNKNOWN}.
   */
  public static String resolvePrincipalKind(HttpServletRequest request) {
    Object explicit = request.getAttribute(PRINCIPAL_KIND_ATTRIBUTE);
    if (explicit instanceof String) {
      String s = (String) explicit;
      if (!s.isEmpty()) {
        return s.toLowerCase(Locale.ROOT);
      }
    }
    return PRINCIPAL_KIND_UNKNOWN;
  }
}
