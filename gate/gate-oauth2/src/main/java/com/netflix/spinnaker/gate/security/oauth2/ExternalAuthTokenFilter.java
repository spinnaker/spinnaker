/*
 * Copyright 2017 Google, Inc.
 * Copyright 2025 OpsMx, Inc.
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.security.oauth2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * This class supports the use case of an externally provided OAuth access token, for example, a
 * GitHub-issued personal access token.
 *
 * <p>In Spring Security 6, this filter extracts the bearer token from the Authorization header,
 * calls the configured user-info endpoint to retrieve user details, and establishes an
 * authenticated security context.
 *
 * <p>With a {@link JwtDecoder} (security.oauth2.external-auth-token-filter.jwt.*), the token is
 * first validated locally as a JWT issued for this Gate, and its claims are used as the user
 * details. A token that fails that validation goes to the user-info endpoint only if the user-info
 * fallback is on.
 */
@Slf4j
public class ExternalAuthTokenFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final ClientRegistration clientRegistration;
  private final OAuthUserInfoServiceHelper userInfoServiceHelper;
  private final RestTemplate restTemplate;
  private final String registrationId;
  private final String userInfoUri;
  private final JwtDecoder jwtDecoder;
  private final boolean userInfoFallback;
  private final String usernameClaim;

  /**
   * Constructs a new ExternalAuthTokenFilter that validates bearer tokens at the user-info endpoint
   * only.
   *
   * @param clientRegistrationRepository repository for looking up OAuth2 client registrations
   * @param userInfoServiceHelper helper service for converting OAuth2 user info to Spinnaker users
   * @param registrationId the OAuth2 client registration ID to use for authentication
   * @param restTemplate the RestTemplate to use for making HTTP requests to the user-info endpoint
   */
  public ExternalAuthTokenFilter(
      ClientRegistrationRepository clientRegistrationRepository,
      OAuthUserInfoServiceHelper userInfoServiceHelper,
      String registrationId,
      RestTemplate restTemplate) {
    this(
        clientRegistrationRepository,
        userInfoServiceHelper,
        registrationId,
        restTemplate,
        null,
        true);
  }

  /**
   * Constructs a new ExternalAuthTokenFilter.
   *
   * @param clientRegistrationRepository repository for looking up OAuth2 client registrations
   * @param userInfoServiceHelper helper service for converting OAuth2 user info to Spinnaker users
   * @param registrationId the OAuth2 client registration ID to use for authentication
   * @param restTemplate the RestTemplate to use for making HTTP requests to the user-info endpoint
   * @param jwtDecoder validates bearer tokens locally as JWTs first, or {@code null} to only use
   *     the user-info endpoint
   * @param userInfoFallback whether a token that fails JWT validation is still sent to the
   *     user-info endpoint; ignored without a {@code jwtDecoder}
   */
  public ExternalAuthTokenFilter(
      ClientRegistrationRepository clientRegistrationRepository,
      OAuthUserInfoServiceHelper userInfoServiceHelper,
      String registrationId,
      RestTemplate restTemplate,
      JwtDecoder jwtDecoder,
      boolean userInfoFallback) {
    this(
        clientRegistrationRepository,
        userInfoServiceHelper,
        registrationId,
        restTemplate,
        jwtDecoder,
        userInfoFallback,
        null);
  }

  /**
   * Constructs a new ExternalAuthTokenFilter.
   *
   * @param clientRegistrationRepository repository for looking up OAuth2 client registrations
   * @param userInfoServiceHelper helper service for converting OAuth2 user info to Spinnaker users
   * @param registrationId the OAuth2 client registration ID to use for authentication
   * @param restTemplate the RestTemplate to use for making HTTP requests to the user-info endpoint
   * @param jwtDecoder validates bearer tokens locally as JWTs first, or {@code null} to only use
   *     the user-info endpoint
   * @param userInfoFallback whether a token that fails JWT validation is still sent to the
   *     user-info endpoint; ignored without a {@code jwtDecoder}
   * @param usernameClaim the claim a validated JWT must carry to identify the user (the claim named
   *     by {@code user-info-mapping.username}), or {@code null} to skip the check; a JWT missing
   *     this claim is treated the same as one that fails validation, so Gate rejects it (or falls
   *     back to the user-info endpoint) immediately instead of attempting a login with no username
   */
  public ExternalAuthTokenFilter(
      ClientRegistrationRepository clientRegistrationRepository,
      OAuthUserInfoServiceHelper userInfoServiceHelper,
      String registrationId,
      RestTemplate restTemplate,
      JwtDecoder jwtDecoder,
      boolean userInfoFallback,
      String usernameClaim) {
    this.userInfoServiceHelper = userInfoServiceHelper;
    this.registrationId = registrationId;
    this.restTemplate = restTemplate;
    this.jwtDecoder = jwtDecoder;
    this.userInfoFallback = jwtDecoder == null || userInfoFallback;
    this.usernameClaim = usernameClaim;

    // It's safe to resolve the client registration once at construction time because
    // OAuth2SsoConfig.securityFilterChain only adds this filter when the
    // ClientRegistrationRepository is an InMemoryClientRegistrationRepository, which is
    // configured at startup and does not change.
    ClientRegistration clientRegistration =
        clientRegistrationRepository.findByRegistrationId(registrationId);
    if (clientRegistration == null) {
      throw new IllegalArgumentException(
          "No client registration found for registrationId: " + registrationId);
    }
    this.clientRegistration = clientRegistration;

    String userInfoUri = clientRegistration.getProviderDetails().getUserInfoEndpoint().getUri();
    if (userInfoUri == null || userInfoUri.isEmpty()) {
      throw new IllegalArgumentException(
          "No user-info-uri configured for registrationId: " + registrationId);
    }
    this.userInfoUri = userInfoUri;
  }

  /**
   * Processes the HTTP request to authenticate users via external bearer tokens.
   *
   * <p>If the request contains a valid bearer token in the Authorization header and the user is not
   * already authenticated, this method will attempt to authenticate by calling the configured
   * user-info endpoint and establishing a security context.
   *
   * @param request the HTTP servlet request
   * @param response the HTTP servlet response
   * @param filterChain the filter chain to continue processing
   * @throws ServletException if a servlet error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain filterChain)
      throws ServletException, IOException {

    // Only process if not already authenticated
    if (SecurityContextHolder.getContext().getAuthentication() == null
        || !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {

      String token = extractBearerToken(request);
      if (token != null) {
        try {
          OAuth2AuthenticationToken authentication = authenticateWithToken(token);
          if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Successfully authenticated user via external bearer token");

            // /login has no controller on the oauth2Login() stack, so redirect
            // authenticated users to the base URL instead of continuing into a 404.
            if ("/login".equals(request.getRequestURI())) {
              response.sendRedirect("/");
              return;
            }
          }
        } catch (Exception e) {
          log.debug("Failed to authenticate with external bearer token", e);
        }
      }
    }

    filterChain.doFilter(request, response);
  }

  /**
   * Extracts the bearer token from the Authorization header of the request.
   *
   * @param request the HTTP servlet request
   * @return the bearer token if present and properly formatted, or {@code null} otherwise
   */
  private String extractBearerToken(HttpServletRequest request) {
    String authHeader = request.getHeader(AUTHORIZATION_HEADER);
    if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
      return authHeader.substring(BEARER_PREFIX.length());
    }
    return null;
  }

  /**
   * Authenticates a user using the provided OAuth2 access token.
   *
   * <p>With a {@link JwtDecoder}, the token is first validated as a JWT and its claims become the
   * user details. Otherwise, or if that fails and the user-info fallback is on, this method fetches
   * user information from the configured user-info endpoint. Either way it creates an authenticated
   * OAuth2AuthenticationToken.
   *
   * @param accessToken the OAuth2 access token to authenticate with
   * @return an OAuth2AuthenticationToken if authentication succeeds, or {@code null} if
   *     authentication fails
   */
  private OAuth2AuthenticationToken authenticateWithToken(String accessToken) {
    Map<String, Object> userAttributes = null;
    Instant issuedAt = Instant.now();
    Instant expiresAt = null;

    if (jwtDecoder != null) {
      try {
        Jwt jwt = jwtDecoder.decode(accessToken);
        if (usernameClaim != null && isBlank(jwt.getClaimAsString(usernameClaim))) {
          // The token validates, but it can't identify a user: proceeding would call Fiat with a
          // null username, which only fails after the retry budget is spent (several seconds per
          // request). Treat it like any other JWT that isn't usable for this Gate. This is the
          // common Entra misconfiguration, where access tokens carry preferred_username but not
          // the email claim that user-info-mapping.username defaults to.
          if (handleUnusableJwt("it has no '" + usernameClaim + "' claim to identify the user")) {
            return null;
          }
        } else {
          userAttributes = jwt.getClaims();
          if (jwt.getIssuedAt() != null) {
            issuedAt = jwt.getIssuedAt();
          }
          expiresAt = jwt.getExpiresAt();
        }
      } catch (JwtException e) {
        if (handleUnusableJwt(e.getMessage())) {
          return null;
        }
      } catch (JwtDecoderInitializationException e) {
        // The decoder couldn't be built, typically because issuer discovery failed (issuer down or
        // a wrong issuer-uri). This is a RuntimeException, not a JwtException, so without this
        // catch it would escape to doFilterInternal and skip the user-info fallback, rejecting
        // every opaque token and personal access token while discovery is failing.
        if (handleUnusableJwt("the JWT decoder could not be initialized: " + e.getMessage())) {
          return null;
        }
      }
    }

    if (userAttributes == null) {
      userAttributes = fetchUserInfo(userInfoUri, accessToken);
    }
    if (userAttributes == null || userAttributes.isEmpty()) {
      return null;
    }

    OAuth2AccessToken oAuth2AccessToken =
        new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, accessToken, issuedAt, expiresAt);

    ExternalOAuth2UserRequest userRequest =
        new ExternalOAuth2UserRequest(clientRegistration, oAuth2AccessToken);

    OAuth2User oauth2User =
        userInfoServiceHelper.toOAuth2User(new SimpleOAuth2User(userAttributes), userRequest);

    return new OAuth2AuthenticationToken(oauth2User, oauth2User.getAuthorities(), registrationId);
  }

  /**
   * Handles a bearer token that can't be used as a JWT for this Gate, either because it failed
   * validation, the decoder couldn't be initialized, or it lacks the username claim.
   *
   * @param reason why the token isn't usable as a JWT, for logging
   * @return {@code true} if the caller should reject the token now ({@code return null}); {@code
   *     false} if it should fall back to the user-info endpoint
   */
  private boolean handleUnusableJwt(String reason) {
    if (!userInfoFallback) {
      log.debug("Bearer token is not a valid JWT for this Gate: {}", reason);
      return true;
    }
    log.debug(
        "Bearer token is not a valid JWT for this Gate, trying the user-info endpoint: {}", reason);
    return false;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /**
   * Fetches user information from the OAuth2 provider's user-info endpoint.
   *
   * @param userInfoUri the URI of the user-info endpoint
   * @param accessToken the OAuth2 access token to use for authentication
   * @return a map containing the user attributes, or {@code null} if the request fails
   */
  private Map<String, Object> fetchUserInfo(String userInfoUri, String accessToken) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(accessToken);

      HttpEntity<Void> entity = new HttpEntity<>(headers);

      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              userInfoUri, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

      return response.getBody();
    } catch (RestClientException e) {
      log.warn("Failed to fetch user info from {}", userInfoUri, e);
      return null;
    }
  }
}
