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

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.gate.config.AuthConfig;
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import com.netflix.spinnaker.gate.security.oauth2.provider.SpinnakerProviderTokenServices;
import com.netflix.spinnaker.gate.services.CredentialsService;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import java.util.HashMap;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.client.EnableOAuth2Sso;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2SsoProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoTokenServices;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.stereotype.Component;

@Configuration
@SpinnakerAuthConfig
@EnableWebSecurity
@EnableOAuth2Sso
@EnableConfigurationProperties
@ConditionalOnProperty(name = "security.oauth2.client.clientId")
@Slf4j
public class OAuth2SsoConfig extends WebSecurityConfigurerAdapter {

  @Autowired private AuthConfig authConfig;
  @Autowired private ExternalAuthTokenFilter externalAuthTokenFilter;
  @Autowired private ExternalSslAwareEntryPoint entryPoint;
  @Autowired private DefaultCookieSerializer defaultCookieSerializer;

  @Primary
  @Bean
  @ConditionalOnProperty(
      prefix = "security.oauth2.resource.spinnaker-user-info-token-services",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ResourceServerTokenServices spinnakerUserInfoTokenServices(
      ResourceServerProperties sso,
      UserInfoTokenServices userInfoTokenServices,
      CredentialsService credentialsService,
      OAuth2SsoConfig.UserInfoMapping userInfoMapping,
      OAuth2SsoConfig.UserInfoRequirements userInfoRequirements,
      PermissionService permissionService,
      Front50Service front50Service,
      Optional<SpinnakerProviderTokenServices> providerTokenServices,
      AllowedAccountsSupport allowedAccountsSupport,
      FiatClientConfigurationProperties fiatClientConfigurationProperties,
      Registry registry) {
    return new SpinnakerUserInfoTokenServices(
        sso,
        userInfoTokenServices,
        credentialsService,
        userInfoMapping,
        userInfoRequirements,
        permissionService,
        front50Service,
        providerTokenServices,
        allowedAccountsSupport,
        fiatClientConfigurationProperties,
        registry);
  }

  @Override
  public void configure(HttpSecurity http) throws Exception {
    defaultCookieSerializer.setSameSite(null);
    authConfig.configure(http);

    http.exceptionHandling().authenticationEntryPoint(entryPoint);
    http.addFilterBefore(
        new BasicAuthenticationFilter(authenticationManager()),
        UsernamePasswordAuthenticationFilter.class);
    http.addFilterBefore(externalAuthTokenFilter, AbstractPreAuthenticatedProcessingFilter.class);
  }

  /**
   * Use this class to specify how to map fields from the userInfoUri response to what's expected to
   * be in the User.
   */
  @Component
  @ConfigurationProperties("security.oauth2.user-info-mapping")
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
  @ConfigurationProperties("security.oauth2.user-info-requirements")
  public static class UserInfoRequirements extends HashMap<String, String> {}

  /**
   * This class exists to change the login redirect (to /login) to the same URL as the
   * preEstablishedRedirectUri, if set, where the SSL is terminated outside of this server.
   */
  @Component
  @ConditionalOnProperty(name = "security.oauth2.client.client-id")
  public static class ExternalSslAwareEntryPoint extends LoginUrlAuthenticationEntryPoint {
    @Autowired private AuthorizationCodeResourceDetails details;

    @Autowired
    public ExternalSslAwareEntryPoint(OAuth2SsoProperties sso) {
      super(sso.getLoginPath());
    }

    @Override
    protected String determineUrlToUseForThisRequest(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception) {
      final String uri = details.getPreEstablishedRedirectUri();
      return uri != null
          ? uri
          : super.determineUrlToUseForThisRequest(request, response, exception);
    }
  }
}
