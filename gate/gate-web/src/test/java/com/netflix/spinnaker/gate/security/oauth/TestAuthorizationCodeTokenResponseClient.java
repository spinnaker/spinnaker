/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.gate.security.oauth;

import java.util.HashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

/**
 * A test implementation of {@link OAuth2AccessTokenResponseClient} that simulates an OAuth2
 * Authorization Code Grant token response without making any external HTTP calls.
 *
 * <p>This class is primarily used in unit and integration tests to mock the behavior of an OAuth2
 * provider (like GitHub, Google, etc.) by returning a fixed access token, refresh token, and
 * additional parameters. It allows testing the full OAuth2 login flow in a controlled environment.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Bean
 * @Primary
 * OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> mockTokenResponseClient() {
 *     return new TestAuthorizationCodeTokenResponseClient();
 * }
 * }</pre>
 *
 * <p>The returned token response uses:
 *
 * <ul>
 *   <li>Access token: "someToken"
 *   <li>Token type: BEARER
 *   <li>Refresh token: "refreshToken"
 *   <li>Additional parameters: empty map
 * </ul>
 */
public class TestAuthorizationCodeTokenResponseClient
    implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

  @Override
  public OAuth2AccessTokenResponse getTokenResponse(
      OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
    Map<String, Object> additionalParameters = new HashMap<>();
    return OAuth2AccessTokenResponse.withToken("someToken")
        .tokenType(OAuth2AccessToken.TokenType.BEARER)
        .refreshToken("refreshToken")
        .additionalParameters(additionalParameters)
        .build();
  }
}
