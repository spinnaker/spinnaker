/*
 * Copyright 2026 Harness, Inc.
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

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.gate.config.FleetConfigurationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Keeps ordinary users on a Spinnaker fleet's global URL.
 *
 * <p>When a session-authenticated <em>non-admin</em> reaches this instance's own hostname directly
 * rather than arriving through the fleet edge, they are redirected (302) to the equivalent path on
 * {@code fleet.global-base-url}. Admins are left alone, so they can always work against an instance
 * directly.
 *
 * <p><strong>This is URL/routing hygiene, not a security boundary.</strong> A per-instance SAML
 * Assertion Consumer Service requires every user's browser to be able to reach every instance host,
 * so instance ports cannot be restricted to the edge, and a determined caller can forge {@code
 * X-Forwarded-Host} to look like edge traffic. Nothing is gained by doing so: Fiat still authorizes
 * every request against the caller's real permissions. See {@code gate/docs/fleet.md}.
 *
 * <p>The guardrail deliberately applies only to <em>browser sessions</em> — see {@link
 * #isBrowserSession}, which keys on the presence of a session rather than on any one {@link
 * Authentication} type, so that every browser login mechanism (basic, LDAP, SAML, OAuth2) is
 * covered. API-token, x509 and other stateless machine clients legitimately target a specific
 * instance, and unauthenticated traffic (Kubernetes probes, inbound webhooks) must never be
 * redirected — a 302 would fail a probe, and most webhook senders either ignore redirects or
 * re-issue a POST as a GET.
 */
@Log4j2
public class FleetDirectAccessFilter extends OncePerRequestFilter {

  private final FleetConfigurationProperties properties;
  private final FiatPermissionEvaluator permissionEvaluator;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();
  private final UrlPathHelper urlPathHelper = new UrlPathHelper();

  private final URI globalUri;
  private final List<String> exemptPaths;

  public FleetDirectAccessFilter(
      FleetConfigurationProperties properties,
      FiatPermissionEvaluator permissionEvaluator,
      String samlLoginProcessingUrl) {
    this.properties = properties;
    this.permissionEvaluator = permissionEvaluator;

    if (!StringUtils.hasText(properties.getGlobalBaseUrl())) {
      throw new IllegalArgumentException(
          "fleet.global-base-url must be set when fleet.enabled is true");
    }
    this.globalUri = URI.create(properties.getGlobalBaseUrl().trim());
    if (!StringUtils.hasText(globalUri.getScheme()) || !StringUtils.hasText(globalUri.getHost())) {
      throw new IllegalArgumentException(
          "fleet.global-base-url must be an absolute URL including scheme and host, but was: "
              + properties.getGlobalBaseUrl());
    }

    // The configured SAML ACS path is always exempt, in addition to the configured list, so that
    // customising saml.login-processing-url does not silently lock non-admins out of login.
    var paths = new ArrayList<>(properties.getExemptPaths());
    if (StringUtils.hasText(samlLoginProcessingUrl)) {
      paths.add(samlLoginProcessingUrl.trim());
    }
    this.exemptPaths = List.copyOf(paths);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (!shouldRedirect(request)) {
      chain.doFilter(request, response);
      return;
    }

    String target = globalUrlFor(request);
    log.debug(
        "Redirecting non-admin direct-instance request to the fleet global URL. instance={}, from={}, to={}",
        properties.getInstanceId(),
        request.getRequestURL(),
        target);
    response.sendRedirect(target);
  }

  /**
   * Rebuilds the request against the global base URL, preserving the path and query verbatim
   * (including any {@code X-Forwarded-Prefix} the instance's own front door applied) and swapping
   * only scheme/host/port. Built by concatenation rather than {@code UriComponentsBuilder} so the
   * already-encoded request URI and query string are passed through untouched, with no risk of
   * double-encoding.
   */
  private String globalUrlFor(HttpServletRequest request) {
    StringBuilder target = new StringBuilder(globalUri.getScheme()).append("://");
    target.append(globalUri.getHost());
    if (globalUri.getPort() != -1) {
      target.append(':').append(globalUri.getPort());
    }
    target.append(request.getRequestURI());
    if (StringUtils.hasLength(request.getQueryString())) {
      target.append('?').append(request.getQueryString());
    }
    return target.toString();
  }

  private boolean shouldRedirect(HttpServletRequest request) {
    if (!properties.isEnabled()) {
      return false;
    }
    if (HttpMethod.OPTIONS.matches(request.getMethod())) {
      return false;
    }
    if (isExempt(request)) {
      return false;
    }
    if (arrivedViaGlobalUrl(request)) {
      return false;
    }
    // Browser sessions only; machine clients may legitimately address an instance directly.
    if (!isBrowserSession(request)) {
      return false;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      // Never pre-empt the normal authentication flow.
      return false;
    }

    // NB: FiatPermissionEvaluator.isAdmin returns true when Fiat is disabled, so a deployment
    // without Fiat has no admin concept and this guardrail correctly never fires.
    return !permissionEvaluator.isAdmin(authentication);
  }

  /**
   * True when this looks like a browser carrying a Spinnaker session, as opposed to a stateless
   * machine client.
   *
   * <p>Deliberately keyed on the <em>presence of a session</em> rather than on a particular {@link
   * Authentication} implementation. Gate's browser login mechanisms do not agree on a token type —
   * gate-basic and gate-ldap produce {@code UsernamePasswordAuthenticationToken}, gate-saml and
   * gate-iap produce {@code PreAuthenticatedAuthenticationToken}, gate-oauth2 produces {@code
   * OAuth2AuthenticationToken} — so matching on any one of them silently excludes the others.
   * (Notably {@link AuthTypeResolver#TYPE_SESSION} means "username/password token", which it was
   * coined for as a low-cardinality <em>metrics</em> tag; it is not a statement about browser
   * sessions, and using it here left the guardrail inert for SAML.)
   *
   * <p>A resolvable server-side session means the caller presented a valid session cookie — the
   * very cookie the fleet edge routes on, which makes it exactly the right signal. Machine clients
   * do not have one: the API-token filter and gate-header both disable session creation, and x509
   * is authenticated per request. Those two are additionally excluded explicitly, so a machine
   * client that happens to also carry a session cookie is still left alone.
   */
  private boolean isBrowserSession(HttpServletRequest request) {
    if (Boolean.TRUE.equals(request.getAttribute(AuthRequestAttributes.IS_API_TOKEN))) {
      return false;
    }

    String authType = AuthTypeResolver.resolveAuthType(request);
    if (AuthTypeResolver.TYPE_API_TOKEN.equals(authType)
        || AuthTypeResolver.TYPE_X509.equals(authType)) {
      return false;
    }

    return request.getSession(false) != null;
  }

  /**
   * Matches against the path <em>within the application</em>, i.e. with any context path or {@code
   * X-Forwarded-Prefix} (such as the {@code /gate} prefix used when Gate is exposed under a path on
   * the global host) stripped. Matching the raw request URI instead would silently stop matching
   * these exemptions as soon as a prefix is in play.
   */
  private boolean isExempt(HttpServletRequest request) {
    String path = urlPathHelper.getPathWithinApplication(request);
    return exemptPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  /**
   * True when the request reached us through the fleet edge. The edge is required to overwrite
   * {@code X-Forwarded-Host} (NGINX's {@code proxy_set_header X-Forwarded-Host $host} replaces
   * rather than appends), so a client-supplied value cannot survive a real edge hop.
   */
  private boolean arrivedViaGlobalUrl(HttpServletRequest request) {
    // With server.forward-headers-strategy=framework, ForwardedHeaderFilter has already applied
    // X-Forwarded-* to the request, so getServerName() is the effective public host.
    return globalUri.getHost().equalsIgnoreCase(request.getServerName());
  }
}
