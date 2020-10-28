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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.gate.config.AuthConfig
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.opensaml.saml2.core.Assertion
import org.opensaml.saml2.core.Attribute
import org.opensaml.xml.schema.XSAny
import org.opensaml.xml.schema.XSString
import org.opensaml.xml.security.BasicSecurityConfiguration
import org.opensaml.xml.signature.SignatureConstants
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.extensions.saml2.config.SAMLConfigurer
import org.springframework.security.saml.websso.WebSSOProfileConsumerImpl
import org.springframework.security.saml.SAMLCredential
import org.springframework.security.saml.userdetails.SAMLUserDetailsService
import org.springframework.security.web.authentication.RememberMeServices
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices
import org.springframework.session.web.http.DefaultCookieSerializer
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.security.KeyStore

import static org.springframework.security.extensions.saml2.config.SAMLConfigurer.saml

@ConditionalOnExpression('${saml.enabled:false}')
@Configuration
@SpinnakerAuthConfig
@EnableWebSecurity
@Slf4j
class SamlSsoConfig extends WebSecurityConfigurerAdapter {

  @Autowired
  ServerProperties serverProperties

  @Autowired
  DefaultCookieSerializer defaultCookieSerializer

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
    boolean sortRoles = false
    boolean forceLowercaseRoles = true
    UserAttributeMapping userAttributeMapping = new UserAttributeMapping()
    long maxAuthenticationAge = 7200

    String signatureDigest = "SHA1" // SHA1 is the default registered in DefaultSecurityConfigurationBootstrap.populateSignatureParams

    /**
     * Ensure that the keystore exists and can be accessed with the given keyStorePassword and keyStoreAliasName
     */
    @PostConstruct
    void validate() {
      if (metadataUrl && metadataUrl.startsWith("/")) {
        metadataUrl = "file:" + metadataUrl
      }

      if (keyStore) {
        if (!keyStore.startsWith("file:")) {
          keyStore = "file:" + keyStore
        }
        new File(new URI(keyStore)).withInputStream { is ->
          def keystore = KeyStore.getInstance(KeyStore.getDefaultType())

          // will throw an exception if `keyStorePassword` is invalid
          keystore.load(is, keyStorePassword.toCharArray())

          if (keyStoreAliasName && !keystore.aliases().find { it.equalsIgnoreCase(keyStoreAliasName) }) {
            throw new IllegalStateException("Keystore '${keyStore}' does not contain alias '${keyStoreAliasName}'")
          }
        }
      }

      // Validate signature digest algorithm
      if (SignatureAlgorithms.fromName(signatureDigest) == null) {
        throw new IllegalStateException("Invalid saml.signatureDigest value '${signatureDigest}'. Valid values are ${SignatureAlgorithms.values()}")
      }
    }
  }

  static class UserAttributeMapping {
    String firstName = "User.FirstName"
    String lastName = "User.LastName"
    String roles = "memberOf"
    String rolesDelimiter = ";"
    String username
    String email
  }

  @Autowired
  SAMLSecurityConfigProperties samlSecurityConfigProperties

  @Autowired
  SAMLUserDetailsService samlUserDetailsService

  @Override
  void configure(HttpSecurity http) {
    //We need our session cookie to come across when we get redirected back from the IdP:
    defaultCookieSerializer.setSameSite(null)
    authConfig.configure(http)

    http
      .rememberMe()
        .rememberMeServices(rememberMeServices(userDetailsService()))

    // @formatter:off
      SAMLConfigurer saml = saml()
      saml
        .userDetailsService(samlUserDetailsService)
        .identityProvider()
          .metadataFilePath(samlSecurityConfigProperties.metadataUrl)
          .discoveryEnabled(false)
          .and()
        .webSSOProfileConsumer(getWebSSOProfileConsumerImpl())
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

      saml.init(http)
      initSignatureDigest() // Need to be after SAMLConfigurer initializes the global SecurityConfiguration

    // @formatter:on

  }

  private void initSignatureDigest() {
    def secConfig = org.opensaml.Configuration.getGlobalSecurityConfiguration()
    if (secConfig != null && secConfig instanceof BasicSecurityConfiguration) {
      BasicSecurityConfiguration basicSecConfig = (BasicSecurityConfiguration) secConfig
      def algo = SignatureAlgorithms.fromName(samlSecurityConfigProperties.signatureDigest)
      log.info("Using ${algo} digest for signing SAML messages")
      basicSecConfig.registerSignatureAlgorithmURI("RSA", algo.rsaSignatureMethod)
      basicSecConfig.setSignatureReferenceDigestMethod(algo.digestMethod)
    } else {
      log.warn("Unable to find global BasicSecurityConfiguration (found '${secConfig}'). Ignoring signatureDigest configuration value.")
    }
  }

  void configure(WebSecurity web) throws Exception {
    authConfig.configure(web)
  }

  public WebSSOProfileConsumerImpl getWebSSOProfileConsumerImpl() {
    WebSSOProfileConsumerImpl profileConsumer = new WebSSOProfileConsumerImpl();
    profileConsumer.setMaxAuthenticationAge(samlSecurityConfigProperties.maxAuthenticationAge);
    return profileConsumer;
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
      PermissionService permissionService

      @Autowired
      AllowedAccountsSupport allowedAccountsSupport

      @Autowired
      FiatClientConfigurationProperties fiatClientConfigurationProperties

      @Autowired
      Registry registry

      RetrySupport retrySupport = new RetrySupport()

      @Override
      User loadUserBySAML(SAMLCredential credential) throws UsernameNotFoundException {
        def assertion = credential.authenticationAssertion
        def attributes = extractAttributes(assertion)
        def userAttributeMapping = samlSecurityConfigProperties.userAttributeMapping

        def subjectNameId = assertion.getSubject().nameID.value
        def email = attributes[userAttributeMapping.email]?.get(0) ?: subjectNameId
        String username = attributes[userAttributeMapping.username]?.get(0) ?: subjectNameId
        def roles = extractRoles(email, attributes, userAttributeMapping, samlSecurityConfigProperties.forceLowercaseRoles)

        if (samlSecurityConfigProperties.sortRoles) {
          roles = roles.sort()
        }

        if (samlSecurityConfigProperties.requiredRoles) {
          if (!samlSecurityConfigProperties.requiredRoles.any { it in roles }) {
            throw new BadCredentialsException("User $email does not have all roles $samlSecurityConfigProperties.requiredRoles")
          }
        }

        def id = registry
            .createId("fiat.login")
            .withTag("type", "saml")

        try {
          retrySupport.retry({ ->
            permissionService.loginWithRoles(username, roles)
          }, 5, 2000, false)

          log.debug("Successful SAML authentication (user: {}, roleCount: {}, roles: {})", username, roles.size(), roles)
          id = id.withTag("success", true).withTag("fallback", "none")
        } catch (Exception e) {
          log.debug(
              "Unsuccessful SAML authentication (user: {}, roleCount: {}, roles: {}, legacyFallback: {})",
              username,
              roles.size(),
              roles,
              fiatClientConfigurationProperties.legacyFallback,
              e
          )
          id = id.withTag("success", false).withTag("fallback", fiatClientConfigurationProperties.legacyFallback)

          if (!fiatClientConfigurationProperties.legacyFallback) {
            throw e
          }
        } finally {
          registry.counter(id).increment()
        }

        return new User(
          email: email,
          firstName: attributes[userAttributeMapping.firstName]?.get(0),
          lastName: attributes[userAttributeMapping.lastName]?.get(0),
          roles: roles,
          allowedAccounts: allowedAccountsSupport.filterAllowedAccounts(username, roles),
          username: username
        )
      }

      Set<String> extractRoles(String email,
                               Map<String, List<String>> attributes,
                               UserAttributeMapping userAttributeMapping,
                               boolean forceLowercaseRoles) {
        def assertionRoles = attributes[userAttributeMapping.roles].collect { String roles ->
          def commonNames = roles.split(userAttributeMapping.rolesDelimiter)
          commonNames.collect {
            return it.indexOf("CN=") < 0 ? it : it.substring(it.indexOf("CN=") + 3, it.indexOf(","))
          }
        }.flatten() as Set<String>

        if (forceLowercaseRoles) {
          assertionRoles = assertionRoles*.toLowerCase()
        }

        return assertionRoles
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

  // Available digests taken from org.opensaml.xml.signature.SignatureConstants (RSA signatures)
  private enum SignatureAlgorithms {
    SHA1(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA1, SignatureConstants.ALGO_ID_DIGEST_SHA1),
    SHA256(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256, SignatureConstants.ALGO_ID_DIGEST_SHA256),
    SHA384(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA384, SignatureConstants.ALGO_ID_DIGEST_SHA384),
    SHA512(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA512, SignatureConstants.ALGO_ID_DIGEST_SHA512),
    RIPEMD160(SignatureConstants.ALGO_ID_SIGNATURE_RSA_RIPEMD160, SignatureConstants.ALGO_ID_DIGEST_RIPEMD160),
    MD5(SignatureConstants.ALGO_ID_SIGNATURE_NOT_RECOMMENDED_RSA_MD5, SignatureConstants.ALGO_ID_DIGEST_NOT_RECOMMENDED_MD5)

    String rsaSignatureMethod
    String digestMethod
    SignatureAlgorithms(String rsaSignatureMethod, String digestMethod) {
      this.rsaSignatureMethod = rsaSignatureMethod
      this.digestMethod = digestMethod
    }

    static SignatureAlgorithms fromName(String digestName) {
      SignatureAlgorithms.find { it -> (it.name() == digestName.toUpperCase()) } as SignatureAlgorithms
    }
  }

}
