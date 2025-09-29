/*
 * Copyright 2025 OpsMx, Inc.
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

import com.netflix.spinnaker.gate.config.AuthConfig;
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import java.util.HashMap;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.stereotype.Component;

@EnableWebSecurity
@SpinnakerAuthConfig
@Conditional(OAuthConfigEnabled.class)
public class OAuth2SsoConfig extends WebSecurityConfigurerAdapter {

  @Autowired private AuthConfig authConfig;
  @Autowired private SpinnakerOAuth2UserInfoService customOAuth2UserService;
  @Autowired private SpinnakerOIDCUserInfoService oidcUserInfoService;
  @Autowired private DefaultCookieSerializer defaultCookieSerializer;

  @Autowired
  private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenResponseClient;

  @Override
  public void configure(HttpSecurity httpSecurity) throws Exception {
    defaultCookieSerializer.setSameSite(null);
    authConfig.configure(httpSecurity);
    httpSecurity
        .authorizeRequests(auth -> auth.anyRequest().authenticated())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .userInfoEndpoint(
                        userInfo ->
                            userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(oidcUserInfoService))
                    // Using same token response client that get sets by default this is to allows
                    // injection of a mock or test implementation
                    // for unit/integration tests, so we don't need to call GitHub (or any real
                    // OAuth2 provider)
                    .tokenEndpoint()
                    .accessTokenResponseClient(tokenResponseClient));
  }

  /**
   * Use this class to specify how to map fields from the userInfoUri response to what's expected to
   * be in the User.
   */
  @Component
  @ConfigurationProperties("spring.security.oauth2.client.registration.user-info-mapping")
  @Data
  public static class UserInfoMapping {
    private String email = "email";
    private String firstName = "given_name";
    private String lastName = "family_name";
    private String username = "email";
    private String serviceAccountEmail = "client_email";
    private String roles = null;
  }

  @Component
  @ConfigurationProperties("spring.security.oauth2.client.registration.user-info-requirements")
  public static class UserInfoRequirements extends HashMap<String, String> {}
}
