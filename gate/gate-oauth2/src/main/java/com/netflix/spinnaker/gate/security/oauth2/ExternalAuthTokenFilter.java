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
 */
@Slf4j
public class ExternalAuthTokenFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final ClientRegistrationRepository clientRegistrationRepository;
  private final OAuthUserInfoServiceHelper userInfoServiceHelper;
  private final RestTemplate restTemplate;
  private final String registrationId;

  /**
   * Constructs a new ExternalAuthTokenFilter.
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
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.userInfoServiceHelper = userInfoServiceHelper;
    this.registrationId = registrationId;
    this.restTemplate = restTemplate;
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
   * <p>This method retrieves the client registration, fetches user information from the configured
   * user-info endpoint, and creates an authenticated OAuth2AuthenticationToken.
   *
   * @param accessToken the OAuth2 access token to authenticate with
   * @return an OAuth2AuthenticationToken if authentication succeeds, or {@code null} if
   *     authentication fails due to missing configuration or invalid token
   */
  private OAuth2AuthenticationToken authenticateWithToken(String accessToken) {
    ClientRegistration clientRegistration =
        clientRegistrationRepository.findByRegistrationId(registrationId);

    if (clientRegistration == null) {
      log.warn("No client registration found for registrationId: {}", registrationId);
      return null;
    }

    String userInfoUri = clientRegistration.getProviderDetails().getUserInfoEndpoint().getUri();
    if (userInfoUri == null || userInfoUri.isEmpty()) {
      log.warn("No user-info-uri configured for registrationId: {}", registrationId);
      return null;
    }

    Map<String, Object> userAttributes = fetchUserInfo(userInfoUri, accessToken);
    if (userAttributes == null || userAttributes.isEmpty()) {
      return null;
    }

    OAuth2AccessToken oAuth2AccessToken =
        new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, accessToken, Instant.now(), null);

    ExternalOAuth2UserRequest userRequest =
        new ExternalOAuth2UserRequest(clientRegistration, oAuth2AccessToken);

    OAuth2User oauth2User =
        userInfoServiceHelper.getSpinnakerOAuth2User(
            new SimpleOAuth2User(userAttributes), userRequest);

    return new OAuth2AuthenticationToken(oauth2User, oauth2User.getAuthorities(), registrationId);
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
      log.debug("Failed to fetch user info from {}", userInfoUri, e);
      return null;
    }
  }
}
