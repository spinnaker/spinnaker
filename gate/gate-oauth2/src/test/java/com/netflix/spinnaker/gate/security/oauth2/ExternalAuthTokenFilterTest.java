/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class ExternalAuthTokenFilterTest {

  private static final String REGISTRATION_ID = "github";

  @Mock private ClientRegistrationRepository clientRegistrationRepository;
  @Mock private OAuthUserInfoServiceHelper userInfoServiceHelper;
  @Mock private RestTemplate restTemplate;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;

  private ExternalAuthTokenFilter filter;

  @BeforeEach
  void setUp() {
    filter =
        new ExternalAuthTokenFilter(
            clientRegistrationRepository, userInfoServiceHelper, REGISTRATION_ID, restTemplate);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldContinueFilterChainWhenNoBearerToken() throws Exception {
    when(request.getHeader("Authorization")).thenReturn(null);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldContinueFilterChainWhenAuthorizationHeaderIsNotBearer() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldSkipAuthenticationWhenAlreadyAuthenticated() throws Exception {
    Authentication existingAuth = mock(Authentication.class);
    when(existingAuth.isAuthenticated()).thenReturn(true);
    SecurityContextHolder.getContext().setAuthentication(existingAuth);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(clientRegistrationRepository, never()).findByRegistrationId(any());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existingAuth);
  }

  @Test
  void shouldNotAuthenticateWhenClientRegistrationNotFound() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer test-token");
    when(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID)).thenReturn(null);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldNotAuthenticateWhenUserInfoUriNotConfigured() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer test-token");

    ClientRegistration clientRegistration = createClientRegistrationWithoutUserInfoUri();
    when(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
        .thenReturn(clientRegistration);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldAuthenticateSuccessfullyWithValidToken() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");

    ClientRegistration clientRegistration = createClientRegistrationWithUserInfoUri();
    when(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
        .thenReturn(clientRegistration);

    Map<String, Object> userAttributes = Map.of("login", "testuser", "email", "test@example.com");
    ResponseEntity<Map<String, Object>> responseEntity = ResponseEntity.ok(userAttributes);
    when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(responseEntity);

    OAuth2User spinnakerUser = mock(OAuth2User.class);
    when(spinnakerUser.getAuthorities()).thenReturn(List.of());
    when(userInfoServiceHelper.getSpinnakerOAuth2User(any(OAuth2User.class), any()))
        .thenReturn(spinnakerUser);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
  }

  @Test
  void shouldNotAuthenticateWhenUserInfoEndpointFails() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");

    ClientRegistration clientRegistration = createClientRegistrationWithUserInfoUri();
    when(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
        .thenReturn(clientRegistration);

    when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenThrow(new RestClientException("Connection refused"));

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldNotAuthenticateWhenUserInfoReturnsEmptyResponse() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");

    ClientRegistration clientRegistration = createClientRegistrationWithUserInfoUri();
    when(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
        .thenReturn(clientRegistration);

    ResponseEntity<Map<String, Object>> responseEntity = ResponseEntity.ok(Map.of());
    when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(responseEntity);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private ClientRegistration createClientRegistrationWithoutUserInfoUri() {
    return ClientRegistration.withRegistrationId(REGISTRATION_ID)
        .clientId("client-id")
        .clientSecret("client-secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("https://github.com/login/oauth/authorize")
        .tokenUri("https://github.com/login/oauth/access_token")
        .build();
  }

  private ClientRegistration createClientRegistrationWithUserInfoUri() {
    return ClientRegistration.withRegistrationId(REGISTRATION_ID)
        .clientId("client-id")
        .clientSecret("client-secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("https://github.com/login/oauth/authorize")
        .tokenUri("https://github.com/login/oauth/access_token")
        .userInfoUri("https://api.github.com/user")
        .build();
  }
}
