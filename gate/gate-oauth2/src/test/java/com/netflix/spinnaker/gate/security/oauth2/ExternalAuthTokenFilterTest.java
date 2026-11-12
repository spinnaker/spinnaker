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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
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
    ClientRegistration clientRegistration = createClientRegistrationWithUserInfoUri();
    when(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
        .thenReturn(clientRegistration);
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
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existingAuth);
  }

  @Test
  void shouldThrowWhenClientRegistrationNotFound() {
    ClientRegistrationRepository emptyRepo = mock(ClientRegistrationRepository.class);
    when(emptyRepo.findByRegistrationId(REGISTRATION_ID)).thenReturn(null);

    assertThatThrownBy(
            () ->
                new ExternalAuthTokenFilter(
                    emptyRepo, userInfoServiceHelper, REGISTRATION_ID, restTemplate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "No client registration found for registrationId: " + REGISTRATION_ID);
  }

  @Test
  void shouldThrowWhenUserInfoUriNotConfigured() {
    ClientRegistration clientRegistration = createClientRegistrationWithoutUserInfoUri();
    ClientRegistrationRepository repoWithoutUserInfo = mock(ClientRegistrationRepository.class);
    when(repoWithoutUserInfo.findByRegistrationId(REGISTRATION_ID)).thenReturn(clientRegistration);

    assertThatThrownBy(
            () ->
                new ExternalAuthTokenFilter(
                    repoWithoutUserInfo, userInfoServiceHelper, REGISTRATION_ID, restTemplate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No user-info-uri configured for registrationId: " + REGISTRATION_ID);
  }

  @Test
  void shouldAuthenticateSuccessfullyWithValidToken() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");

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
    when(userInfoServiceHelper.toOAuth2User(any(OAuth2User.class), any()))
        .thenReturn(spinnakerUser);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
  }

  @Test
  void shouldNotAuthenticateWhenUserInfoEndpointFails() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");

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

  @Test
  void shouldAuthenticateFromJwtClaimsWithoutCallingUserInfo() throws Exception {
    JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    ExternalAuthTokenFilter jwtFilter = jwtFilter(jwtDecoder, false);
    when(request.getHeader("Authorization")).thenReturn("Bearer header.payload.signature");
    Instant issuedAt = Instant.now().minusSeconds(60);
    Jwt jwt =
        Jwt.withTokenValue("header.payload.signature")
            .header("alg", "RS256")
            .claim("preferred_username", "user@example.com")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(3600))
            .build();
    when(jwtDecoder.decode("header.payload.signature")).thenReturn(jwt);
    OAuth2User spinnakerUser = mock(OAuth2User.class);
    when(spinnakerUser.getAuthorities()).thenReturn(List.of());
    ArgumentCaptor<OAuth2User> providerUser = ArgumentCaptor.forClass(OAuth2User.class);
    ArgumentCaptor<OAuth2UserRequest> userRequest =
        ArgumentCaptor.forClass(OAuth2UserRequest.class);
    when(userInfoServiceHelper.toOAuth2User(providerUser.capture(), userRequest.capture()))
        .thenReturn(spinnakerUser);

    jwtFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
    assertThat(providerUser.getValue().getAttributes())
        .containsEntry("preferred_username", "user@example.com");
    assertThat(userRequest.getValue().getAccessToken().getExpiresAt())
        .isEqualTo(issuedAt.plusSeconds(3600));
    verify(restTemplate, never())
        .exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));
  }

  @Test
  void shouldFallBackToUserInfoWhenJwtValidationFailsAndFallbackIsOn() throws Exception {
    JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    ExternalAuthTokenFilter jwtFilter = jwtFilter(jwtDecoder, true);
    when(request.getHeader("Authorization")).thenReturn("Bearer opaque-token");
    when(jwtDecoder.decode("opaque-token")).thenThrow(new BadJwtException("not a JWT"));
    when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(ResponseEntity.ok(Map.<String, Object>of("login", "testuser")));
    OAuth2User spinnakerUser = mock(OAuth2User.class);
    when(spinnakerUser.getAuthorities()).thenReturn(List.of());
    when(userInfoServiceHelper.toOAuth2User(any(OAuth2User.class), any()))
        .thenReturn(spinnakerUser);

    jwtFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
  }

  @Test
  void shouldNotAuthenticateWhenJwtValidationFailsAndFallbackIsOff() throws Exception {
    JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    ExternalAuthTokenFilter jwtFilter = jwtFilter(jwtDecoder, false);
    when(request.getHeader("Authorization")).thenReturn("Bearer graph-token");
    when(jwtDecoder.decode("graph-token")).thenThrow(new BadJwtException("wrong audience"));

    jwtFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(restTemplate, never())
        .exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));
    verify(userInfoServiceHelper, never()).toOAuth2User(any(), any());
  }

  @Test
  void shouldFallBackToUserInfoWhenJwtDecoderInitializationFailsAndFallbackIsOn() throws Exception {
    // Reproduces the issuer-discovery failure: a SupplierJwtDecoder built with issuer-uri only
    // fetches the issuer metadata on the first token, and a failure there surfaces as a
    // JwtDecoderInitializationException (a RuntimeException, not a JwtException). The fallback must
    // still run so opaque tokens keep working while discovery is down.
    JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    ExternalAuthTokenFilter jwtFilter = jwtFilter(jwtDecoder, true);
    when(request.getHeader("Authorization")).thenReturn("Bearer opaque-token");
    when(jwtDecoder.decode("opaque-token"))
        .thenThrow(
            new JwtDecoderInitializationException(
                "issuer discovery failed", new RuntimeException("Connection refused")));
    when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(ResponseEntity.ok(Map.<String, Object>of("login", "testuser")));
    OAuth2User spinnakerUser = mock(OAuth2User.class);
    when(spinnakerUser.getAuthorities()).thenReturn(List.of());
    when(userInfoServiceHelper.toOAuth2User(any(OAuth2User.class), any()))
        .thenReturn(spinnakerUser);

    jwtFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
  }

  @Test
  void shouldNotAuthenticateWhenJwtDecoderInitializationFailsAndFallbackIsOff() throws Exception {
    JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    ExternalAuthTokenFilter jwtFilter = jwtFilter(jwtDecoder, false);
    when(request.getHeader("Authorization")).thenReturn("Bearer header.payload.signature");
    when(jwtDecoder.decode("header.payload.signature"))
        .thenThrow(
            new JwtDecoderInitializationException(
                "issuer discovery failed", new RuntimeException("Connection refused")));

    jwtFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(restTemplate, never())
        .exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));
    verify(userInfoServiceHelper, never()).toOAuth2User(any(), any());
  }

  @Test
  void shouldNotAuthenticateWhenJwtMissingUsernameClaimAndFallbackIsOff() throws Exception {
    // Reproduces the Entra case: the token validates but carries preferred_username instead of the
    // email claim that user-info-mapping.username defaults to. The filter must reject it right away
    // rather than calling Fiat with a null username and stalling through the retry budget.
    JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    ExternalAuthTokenFilter jwtFilter = jwtFilter(jwtDecoder, false, "email");
    when(request.getHeader("Authorization")).thenReturn("Bearer header.payload.signature");
    Instant issuedAt = Instant.now().minusSeconds(60);
    Jwt jwt =
        Jwt.withTokenValue("header.payload.signature")
            .header("alg", "RS256")
            .claim("preferred_username", "user@example.com")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(3600))
            .build();
    when(jwtDecoder.decode("header.payload.signature")).thenReturn(jwt);

    jwtFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(restTemplate, never())
        .exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));
    verify(userInfoServiceHelper, never()).toOAuth2User(any(), any());
  }

  @Test
  void shouldFallBackToUserInfoWhenJwtMissingUsernameClaimAndFallbackIsOn() throws Exception {
    JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    ExternalAuthTokenFilter jwtFilter = jwtFilter(jwtDecoder, true, "email");
    when(request.getHeader("Authorization")).thenReturn("Bearer header.payload.signature");
    Instant issuedAt = Instant.now().minusSeconds(60);
    Jwt jwt =
        Jwt.withTokenValue("header.payload.signature")
            .header("alg", "RS256")
            .claim("preferred_username", "user@example.com")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(3600))
            .build();
    when(jwtDecoder.decode("header.payload.signature")).thenReturn(jwt);
    when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(ResponseEntity.ok(Map.<String, Object>of("login", "testuser")));
    OAuth2User spinnakerUser = mock(OAuth2User.class);
    when(spinnakerUser.getAuthorities()).thenReturn(List.of());
    when(userInfoServiceHelper.toOAuth2User(any(OAuth2User.class), any()))
        .thenReturn(spinnakerUser);

    jwtFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
  }

  @Test
  void shouldAuthenticateFromJwtWhenUsernameClaimIsPresent() throws Exception {
    JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    ExternalAuthTokenFilter jwtFilter = jwtFilter(jwtDecoder, false, "email");
    when(request.getHeader("Authorization")).thenReturn("Bearer header.payload.signature");
    Instant issuedAt = Instant.now().minusSeconds(60);
    Jwt jwt =
        Jwt.withTokenValue("header.payload.signature")
            .header("alg", "RS256")
            .claim("email", "user@example.com")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(3600))
            .build();
    when(jwtDecoder.decode("header.payload.signature")).thenReturn(jwt);
    OAuth2User spinnakerUser = mock(OAuth2User.class);
    when(spinnakerUser.getAuthorities()).thenReturn(List.of());
    when(userInfoServiceHelper.toOAuth2User(any(OAuth2User.class), any()))
        .thenReturn(spinnakerUser);

    jwtFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
    verify(restTemplate, never())
        .exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));
  }

  private ExternalAuthTokenFilter jwtFilter(JwtDecoder jwtDecoder, boolean userInfoFallback) {
    return new ExternalAuthTokenFilter(
        clientRegistrationRepository,
        userInfoServiceHelper,
        REGISTRATION_ID,
        restTemplate,
        jwtDecoder,
        userInfoFallback);
  }

  private ExternalAuthTokenFilter jwtFilter(
      JwtDecoder jwtDecoder, boolean userInfoFallback, String usernameClaim) {
    return new ExternalAuthTokenFilter(
        clientRegistrationRepository,
        userInfoServiceHelper,
        REGISTRATION_ID,
        restTemplate,
        jwtDecoder,
        userInfoFallback,
        usernameClaim);
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
