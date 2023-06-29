/*
 * Copyright 2014 Netflix, Inc.
 * Copyright 2023 Apple, Inc.
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

package com.netflix.spinnaker.gate.security.saml;

import static org.springframework.security.extensions.saml2.config.SAMLConfigurer.saml;

import com.netflix.spinnaker.gate.config.AuthConfig;
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import java.net.InetAddress;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensaml.xml.security.BasicSecurityConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.saml.userdetails.SAMLUserDetailsService;
import org.springframework.security.saml.websso.WebSSOProfileConsumerImpl;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.util.StringUtils;

/**
 * Configures SAML2 authentication for Spinnaker.
 *
 * @see <a href="https://spinnaker.io/docs/setup/other_config/security/authentication/saml/">SAML
 *     2.0 configuration docs</a>
 */
@Log4j2
@ConditionalOnExpression("${saml.enabled:false}")
@Configuration
@SpinnakerAuthConfig
@EnableWebSecurity
@EnableConfigurationProperties(SAMLSecurityConfigProperties.class)
@ComponentScan
@RequiredArgsConstructor
public class SAMLSSOConfig extends WebSecurityConfigurerAdapter {
  private final DefaultCookieSerializer defaultCookieSerializer;
  private final AuthConfig authConfig;
  private final SAMLUserDetailsService samlUserDetailsService;
  private final SAMLSecurityConfigProperties samlSecurityConfigProperties;
  @Nullable private final ServerProperties serverProperties;

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    // We need our session cookie to come across when we get redirected back from the IdP:
    defaultCookieSerializer.setSameSite(null);
    authConfig.configure(http);

    http.rememberMe()
        .key("password")
        .rememberMeCookieName("cookieName")
        .rememberMeParameter("rememberMe");

    var webSSOProfileConsumer = new WebSSOProfileConsumerImpl();
    webSSOProfileConsumer.setMaxAuthenticationAge(
        samlSecurityConfigProperties.getMaxAuthenticationAge());

    var hostname = samlSecurityConfigProperties.getRedirectHostname();
    if (!StringUtils.hasLength(hostname) && serverProperties != null) {
      InetAddress address = serverProperties.getAddress();
      if (address != null) {
        hostname = address.getHostName();
      }
    }

    // @formatter:off

    saml()
        .userDetailsService(samlUserDetailsService)
        .identityProvider()
        .metadataFilePath(samlSecurityConfigProperties.getMetadataUrl())
        .discoveryEnabled(false)
        .and()
        .webSSOProfileConsumer(webSSOProfileConsumer)
        .serviceProvider()
        .entityId(samlSecurityConfigProperties.getIssuerId())
        .protocol(samlSecurityConfigProperties.getRedirectProtocol())
        .hostname(hostname)
        .basePath(samlSecurityConfigProperties.getRedirectBasePath())
        .keyStore()
        .storeFilePath(samlSecurityConfigProperties.getKeyStore())
        .password(samlSecurityConfigProperties.getKeyStorePassword())
        .keyname(samlSecurityConfigProperties.getKeyStoreAliasName())
        .keyPassword(samlSecurityConfigProperties.getKeyStorePassword())
        .and()
        .and()
        .init(http);

    // @formatter:on

    // Need to be after SAMLConfigurer initializes the global SecurityConfiguration
    var secConfig = org.opensaml.Configuration.getGlobalSecurityConfiguration();
    if (secConfig instanceof BasicSecurityConfiguration) {
      var config = (BasicSecurityConfiguration) secConfig;
      var digest = samlSecurityConfigProperties.signatureDigest();
      log.info("Using {} digest for signing SAML messages", digest);
      config.registerSignatureAlgorithmURI("RSA", digest.getSignatureMethod());
      config.setSignatureReferenceDigestMethod(digest.getDigestMethod());
    } else {
      log.warn(
          "Unable to find global BasicSecurityConfiguration (found '{}'). Ignoring signatureDigest configuration value.",
          secConfig);
    }
  }
}
