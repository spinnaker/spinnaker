/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.security.saml

import com.netflix.spinnaker.gate.services.PermissionService
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.gate.security.AuthConfig
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig
import com.netflix.spinnaker.gate.security.rolesprovider.UserRolesProvider
import com.netflix.spinnaker.gate.services.CredentialsService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.security.User
import org.opensaml.saml2.core.Assertion
import org.opensaml.saml2.core.Attribute
import org.opensaml.xml.schema.XSAny
import org.opensaml.xml.schema.XSString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.annotation.web.servlet.configuration.EnableWebMvcSecurity
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.saml.SAMLCredential
import org.springframework.security.saml.userdetails.SAMLUserDetailsService
import org.springframework.security.web.authentication.RememberMeServices
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.security.KeyStore

import static org.springframework.security.extensions.saml2.config.SAMLConfigurer.saml

@ConditionalOnExpression('${saml.enabled:false}')
@Configuration
@SpinnakerAuthConfig
@EnableWebMvcSecurity
@Import(SecurityAutoConfiguration)
@Slf4j
class SamlSsoConfig extends WebSecurityConfigurerAdapter {

  @Autowired
  ServerProperties serverProperties

  @Autowired
  AuthConfig authConfig

  @Component
  @ConfigurationProperties("saml")
  static class SAMLSecurityConfigProperties {
    String keyStore
    String keyStorePassword
    String keyStoreAliasName

    // SAML DSL uses a metadata URL instead of hard coding a certificate/issuerId/redirectBase into the config.
    String metadataUrl
    // The parts of this endpoint passed to/used by the SAML IdP.
    String redirectProtocol = "https"
    String redirectHostname
    String redirectBasePath = "/"
    // The application identifier given to the IdP for this app.
    String issuerId

    List<String> requiredRoles
    UserAttributeMapping userAttributeMapping = new UserAttributeMapping()

    /**
     * Ensure that the keystore exists and can be accessed with the given keyStorePassword and keyStoreAliasName
     */
    @PostConstruct
    void validate() {
      def keyStoreToValidate = keyStore
      if (!keyStoreToValidate) {
        return
      }

      if (!keyStoreToValidate.startsWith("file:")) {
        keyStoreToValidate = "file:" + keyStoreToValidate
      }

      new File(new URI(keyStoreToValidate)).withInputStream { is ->
        def keystore = KeyStore.getInstance(KeyStore.getDefaultType())

        // will throw an exception if `keyStorePassword` is invalid
        keystore.load(is, keyStorePassword.toCharArray());

        if (keyStoreAliasName && !keystore.aliases().find { it.equalsIgnoreCase(keyStoreAliasName) }) {
          throw new IllegalStateException("Keystore '${keyStore}' does not contain alias '${keyStoreAliasName}'")
        }
      }
    }
  }

  static class UserAttributeMapping {
    String firstName = "User.FirstName"
    String lastName = "User.LastName"
    String roles = "memberOf"
    String username
  }

  @Autowired
  SAMLSecurityConfigProperties samlSecurityConfigProperties

  @Autowired
  SAMLUserDetailsService samlUserDetailsService

  @Autowired(required = false)
  List<SamlSsoConfigurer> configurers

  @Override
  void configure(HttpSecurity http) {
    http
      .rememberMe()
        .rememberMeServices(rememberMeServices(userDetailsService()))
        .and()
      .authorizeRequests()
        .antMatchers("/saml/**").permitAll()

    // @formatter:off
    http
      .apply(saml())
        .userDetailsService(samlUserDetailsService)
        .identityProvider()
          .metadataFilePath(samlSecurityConfigProperties.metadataUrl)
          .discoveryEnabled(false)
          .and()
        .serviceProvider()
          .entityId(samlSecurityConfigProperties.issuerId)
          .protocol(samlSecurityConfigProperties.redirectProtocol)
          .hostname(samlSecurityConfigProperties.redirectHostname ?: serverProperties?.address?.hostName)
          .basePath(samlSecurityConfigProperties.redirectBasePath)
          .keyStore()
          .storeFilePath(samlSecurityConfigProperties.keyStore)
          .password(samlSecurityConfigProperties.keyStorePassword)
          .keyname(samlSecurityConfigProperties.keyStoreAliasName)
          .keyPassword(samlSecurityConfigProperties.keyStorePassword)
    // @formatter:on

    // We must do the default auth configuration AFTER the SAML one, because otherwise an HTTP Basic
    // (if enabled) auth window might appear in the browser before the SAML filter is hit.
    authConfig.configure(http)

    configurers?.each {
      it.configure(http)
    }
  }

  @Bean
  public RememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
    TokenBasedRememberMeServices rememberMeServices = new TokenBasedRememberMeServices("password", userDetailsService)
    rememberMeServices.setCookieName("cookieName")
    rememberMeServices.setParameter("rememberMe")
    rememberMeServices
  }

  @Bean
  SAMLUserDetailsService samlUserDetailsService() {
    // TODO(ttomsu): This is a NFLX specific user extractor. Make a more generic one?
    new SAMLUserDetailsService() {

      @Autowired
      CredentialsService credentialsService

      @Autowired
      ClouddriverService clouddriverService

      @Autowired
      UserRolesProvider userRolesProvider

      @Autowired
      PermissionService permissionService

      @Override
      User loadUserBySAML(SAMLCredential credential) throws UsernameNotFoundException {
        def assertion = credential.authenticationAssertion
        def attributes = extractAttributes(assertion)
        def userAttributeMapping = samlSecurityConfigProperties.userAttributeMapping

        def email = assertion.getSubject().nameID.value
        String username = attributes[userAttributeMapping.username] ?: email
        def roles = extractRoles(email, attributes, userAttributeMapping)

        if (samlSecurityConfigProperties.requiredRoles) {
          if (!samlSecurityConfigProperties.requiredRoles.any { it in roles }) {
            throw new BadCredentialsException("User $email does not have all roles $samlSecurityConfigProperties.requiredRoles")
          }
        }

        permissionService.loginWithRoles(username, roles)

        new User(email: email,
          firstName: attributes[userAttributeMapping.firstName]?.get(0),
          lastName: attributes[userAttributeMapping.lastName]?.get(0),
          roles: roles,
          allowedAccounts: credentialsService.getAccountNames(roles),
          username: username)
      }

      Set<String> extractRoles(String email,
                               Map<String, List<String>> attributes,
                               UserAttributeMapping userAttributeMapping) {
        def assertionRoles = attributes[userAttributeMapping.roles].collect { String roles ->
          def commonNames = roles.split(";")
          commonNames.collect {
            return it.indexOf("CN=") < 0 ? it : it.substring(it.indexOf("CN=") + 3, it.indexOf(","))
          }
        }.flatten()*.toLowerCase() as Set<String>

        return assertionRoles + userRolesProvider.loadRoles(email)
      }

      static Map<String, List<String>> extractAttributes(Assertion assertion) {
        def attributes = [:]
        assertion.attributeStatements*.attributes.flatten().each { Attribute attribute ->
          def name = attribute.name
          def values = attribute.attributeValues.findResults {
            switch (it) {
              case XSString:
                return (it as XSString)?.value
              case XSAny:
                return (it as XSAny)?.textContent
            }
            return null
          } ?: []
          attributes[name] = values
        }

        return attributes
      }
    }
  }
}
