/*
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.config.AuthConfig;
import com.netflix.spinnaker.gate.config.PermissionRevokingLogoutSuccessHandler;
import com.netflix.spinnaker.gate.config.RequestMatcherProvider;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletResponse;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Regression tests for <a href="https://github.com/spinnaker/spinnaker/issues/7994">issue
 * #7994</a>.
 *
 * <p>Under Spring Security 6 the default {@code requireExplicitSave=true} stopped persisting the
 * {@code SecurityContext} that {@link ExternalAuthTokenFilter} establishes, so every Bearer-token
 * request repeated the provider's user-info round-trip and API clients never received a session.
 * {@link OAuth2SsoConfig} restores the implicit save, and these tests drive the real filter chain
 * to prove it: the first Bearer-token request hits the user-info endpoint exactly once and stores
 * the authentication in the session, and subsequent requests served from that session never call
 * the user-info endpoint again.
 */
class OAuth2SsoSessionPersistenceTest {

  private static final AtomicInteger USER_INFO_PORT = new AtomicInteger();
  private static final AtomicInteger USER_INFO_HITS = new AtomicInteger();

  private HttpServer userInfoServer;

  @BeforeEach
  void startFakeUserInfoEndpoint() throws Exception {
    USER_INFO_HITS.set(0);
    userInfoServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    userInfoServer.createContext(
        "/userinfo",
        exchange -> {
          USER_INFO_HITS.incrementAndGet();
          byte[] body = "{\"email\": \"user@example.com\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    userInfoServer.start();
    USER_INFO_PORT.set(userInfoServer.getAddress().getPort());
  }

  @AfterEach
  void stopFakeUserInfoEndpoint() {
    SecurityContextHolder.clearContext();
    if (userInfoServer != null) {
      userInfoServer.stop(0);
    }
  }

  private ApplicationContextRunner sessionPersistenceRunner() {
    return new ApplicationContextRunner()
        .withPropertyValues(
            "spring.security.oauth2.client.registration.github.client-id=client-id",
            "spring.security.oauth2.client.registration.github.client-secret=client-secret")
        .withConfiguration(
            UserConfigurations.of(
                SessionPersistenceTestConfiguration.class,
                FiatClientConfigurationProperties.class,
                PermissionRevokingLogoutSuccessHandler.class,
                AuthConfig.class,
                OAuth2BeanConfiguration.class,
                OAuth2SsoConfig.class));
  }

  @Test
  void bearerAuthenticationIsSavedToTheSessionAndReusedWithoutAnotherUserInfoCall() {
    sessionPersistenceRunner()
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(SecurityFilterChain.class);
              FilterChainProxy proxy = new FilterChainProxy(ctx.getBean(SecurityFilterChain.class));

              // Stub the helper from the context: the filter needs it to turn the
              // user-info attributes into a Spinnaker user.
              OAuthUserInfoServiceHelper helper = ctx.getBean(OAuthUserInfoServiceHelper.class);
              when(helper.getSpinnakerOAuth2User(any(), any()))
                  .thenAnswer(
                      invocation ->
                          new SimpleOAuth2User(
                              ((OAuth2User) invocation.getArgument(0)).getAttributes()));

              // First request: a Bearer token and no session. The filter authenticates via
              // the user-info endpoint, and the chain must persist the result.
              MockHttpServletRequest firstRequest =
                  new MockHttpServletRequest("GET", "/applications");
              firstRequest.addHeader("Authorization", "Bearer first-token");
              MockHttpSession session = new MockHttpSession();
              firstRequest.setSession(session);
              MockHttpServletResponse firstResponse = new MockHttpServletResponse();
              proxy.doFilter(
                  firstRequest,
                  firstResponse,
                  (request, response) -> ((HttpServletResponse) response).setStatus(200));

              assertThat(firstResponse.getStatus())
                  .as("an authenticated request should reach the downstream chain")
                  .isEqualTo(200);
              assertThat(USER_INFO_HITS.get())
                  .as("the first request should call the user-info endpoint exactly once")
                  .isEqualTo(1);
              assertThat(
                      session.getAttribute(
                          HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                  .as("the authentication must be persisted to the session")
                  .isNotNull();

              // Second request: same session, no Bearer token. The chain must load the saved
              // authentication instead of calling the user-info endpoint again.
              MockHttpServletRequest secondRequest =
                  new MockHttpServletRequest("GET", "/applications");
              secondRequest.setSession(session);
              MockHttpServletResponse secondResponse = new MockHttpServletResponse();
              AtomicReference<Authentication> seenAuthentication = new AtomicReference<>();
              proxy.doFilter(
                  secondRequest,
                  secondResponse,
                  (request, response) -> {
                    seenAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
                    ((HttpServletResponse) response).setStatus(200);
                  });

              assertThat(secondResponse.getStatus()).isEqualTo(200);
              assertThat(USER_INFO_HITS.get())
                  .as("a request carrying the session must not repeat the user-info round-trip")
                  .isEqualTo(1);
              assertThat(seenAuthentication.get())
                  .as("the downstream chain should still see the session's authentication")
                  .isNotNull();
              assertThat(seenAuthentication.get().getName()).isEqualTo("user@example.com");
            });
  }

  @Test
  void requestWithoutBearerTokenOrSessionIsStillRejected() {
    sessionPersistenceRunner()
        .run(
            ctx -> {
              FilterChainProxy proxy = new FilterChainProxy(ctx.getBean(SecurityFilterChain.class));

              OAuthUserInfoServiceHelper helper = ctx.getBean(OAuthUserInfoServiceHelper.class);
              when(helper.getSpinnakerOAuth2User(any(), any()))
                  .thenAnswer(
                      invocation ->
                          new SimpleOAuth2User(
                              ((OAuth2User) invocation.getArgument(0)).getAttributes()));

              MockHttpServletRequest request = new MockHttpServletRequest("GET", "/applications");
              MockHttpServletResponse response = new MockHttpServletResponse();
              proxy.doFilter(
                  request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));

              assertThat(USER_INFO_HITS.get())
                  .as("no Bearer token means no user-info call")
                  .isZero();
              assertThat(response.getStatus())
                  .as("an unauthenticated request must not reach the downstream chain")
                  .isIn(302, 401, 403);
            });
  }

  /** Collaborators for {@link OAuth2SsoConfig}, matching what Gate provides at runtime. */
  static class SessionPersistenceTestConfiguration {

    @Bean
    PermissionService permissionService() {
      return mock(PermissionService.class);
    }

    @Bean
    FiatStatus fiatStatus() {
      return mock(FiatStatus.class);
    }

    @Bean
    FiatPermissionEvaluator fiatPermissionEvaluator() {
      return mock(FiatPermissionEvaluator.class);
    }

    @Bean
    RequestMatcherProvider requestMatcherProvider() {
      RequestMatcherProvider provider = mock(RequestMatcherProvider.class);
      when(provider.requestMatcher()).thenReturn(request -> true);
      return provider;
    }

    @Bean
    SpinnakerOAuth2UserInfoService spinnakerOAuth2UserInfoService() {
      return mock(SpinnakerOAuth2UserInfoService.class);
    }

    @Bean
    SpinnakerOIDCUserInfoService spinnakerOIDCUserInfoService() {
      return mock(SpinnakerOIDCUserInfoService.class);
    }

    @Bean
    DynamicConfigService dynamicConfigService() {
      return mock(DynamicConfigService.class);
    }

    @Bean
    OAuthUserInfoServiceHelper oAuthUserInfoServiceHelper() {
      return mock(OAuthUserInfoServiceHelper.class);
    }

    @Bean
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
        oAuth2AccessTokenResponseClient() {
      return mock(OAuth2AccessTokenResponseClient.class);
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("github")
              .clientId("client-id")
              .clientSecret("client-secret")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/{action}/oauth2/code/github")
              .authorizationUri("https://example.com/oauth/authorize")
              .tokenUri("https://example.com/oauth/token")
              .userInfoUri("http://127.0.0.1:" + USER_INFO_PORT.get() + "/userinfo")
              .clientName("Test")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
