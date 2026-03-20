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

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

class ExternalOAuth2UserRequestTest {

  @Test
  void shouldCreateUserRequestWithClientRegistrationAndAccessToken() {
    ClientRegistration clientRegistration =
        ClientRegistration.withRegistrationId("github")
            .clientId("client-id")
            .clientSecret("client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://github.com/login/oauth/authorize")
            .tokenUri("https://github.com/login/oauth/access_token")
            .userInfoUri("https://api.github.com/user")
            .build();

    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "test-token", Instant.now(), null);

    ExternalOAuth2UserRequest userRequest =
        new ExternalOAuth2UserRequest(clientRegistration, accessToken);

    assertThat(userRequest.getClientRegistration()).isEqualTo(clientRegistration);
    assertThat(userRequest.getAccessToken()).isEqualTo(accessToken);
    assertThat(userRequest.getAccessToken().getTokenValue()).isEqualTo("test-token");
  }

  @Test
  void shouldInheritFromOAuth2UserRequest() {
    ClientRegistration clientRegistration =
        ClientRegistration.withRegistrationId("test")
            .clientId("client-id")
            .clientSecret("client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://example.com/authorize")
            .tokenUri("https://example.com/token")
            .build();

    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "my-token", Instant.now(), null);

    ExternalOAuth2UserRequest userRequest =
        new ExternalOAuth2UserRequest(clientRegistration, accessToken);

    assertThat(userRequest.getClientRegistration().getRegistrationId()).isEqualTo("test");
    assertThat(userRequest.getAccessToken().getTokenType())
        .isEqualTo(OAuth2AccessToken.TokenType.BEARER);
  }
}
