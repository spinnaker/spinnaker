/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.gate.security.x509

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.User
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

import javax.naming.ldap.LdapName
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * This class is similar to a UserDetailService, but instead of passing in a username to loadUserDetails,
 * it passes in a token containing the x509 certificate. A user can control the principal through the
 * `spring.x509.subjectPrincipalRegex` property.
 */
@Component
@Slf4j
class X509AuthenticationUserDetailsService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {

  private static final String RFC822_NAME_ID = "1"

  @Autowired
  PermissionService permissionService

  @Autowired
  AllowedAccountsSupport allowedAccountsSupport

  @Autowired(required = false)
  X509RolesExtractor rolesExtractor

  @Autowired(required = false)
  X509UserIdentifierExtractor userIdentifierExtractor

  @Autowired
  DynamicConfigService dynamicConfigService

  @Autowired
  FiatPermissionEvaluator fiatPermissionEvaluator

  @Autowired
  FiatClientConfigurationProperties fiatClientConfigurationProperties

  @Autowired
  FiatStatus fiatStatus

  @Autowired
  Registry registry

  RetrySupport retrySupport = new RetrySupport()

  @Value('${x509.required-roles:}#{T(java.util.Collections).emptyList()}')
  List<String> requiredRoles = []

  final Cache<String, Instant> loginDebounce = Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build()
  final Clock clock

  X509AuthenticationUserDetailsService() {
    this(Clock.systemUTC())
  }

  @PackageScope
  X509AuthenticationUserDetailsService(Clock clock) {
    this.clock = clock
  }


  @Override
  UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) throws UsernameNotFoundException {

    if (!(token.credentials instanceof X509Certificate)) {
      return null
    }

    def x509 = (X509Certificate) token.credentials

    String email = identityFromCertificate(x509) ?: token.principal?.toString()

    if (email == null) {
      return null
    }

    def roles = handleLogin(email, x509)

    if (requiredRoles) {
      if (!requiredRoles.any { it in roles }) {
        String errorMessage = "User $email with roles $roles does not have any of the required roles $requiredRoles"
        log.debug(errorMessage)
        throw new BadCredentialsException(errorMessage)
      }
    }

    log.debug("Roles for user {}: {}", email, roles)
    return new User(
        email: email,
        allowedAccounts: allowedAccountsSupport.filterAllowedAccounts(email, roles),
        roles: roles
    )
  }

  @PackageScope
  Collection<String> handleLogin(String email, X509Certificate x509) {

    return AuthenticatedRequest.allowAnonymous({ ->
      final Instant now = clock.instant()
      final boolean loginDebounceEnabled = dynamicConfigService.isEnabled('x509.loginDebounce', false)
      boolean shouldLogin

      if (loginDebounceEnabled) {
        final Duration debounceWindow = Duration.ofSeconds(dynamicConfigService.getConfig(Long, 'x509.loginDebounce.debounceWindowSeconds', TimeUnit.MINUTES.toSeconds(5)))
        final Optional<Instant> lastDebounced = Optional.ofNullable(loginDebounce.getIfPresent(email))
        boolean needsCachedPermission = !fiatPermissionEvaluator.hasCachedPermission(email)
        shouldLogin = needsCachedPermission ||
          lastDebounced.map({ now.isAfter(it.plus(debounceWindow)) }).orElse(true)
      } else {
        shouldLogin = true
      }

      def roles = [email]

      if (rolesExtractor) {
        def extractedRoles = rolesExtractor.fromCertificate(x509)
        log.debug("Extracted roles from certificate for user {}: {}", email, extractedRoles)
        roles += extractedRoles
      }

      if (shouldLogin) {
        def id = registry
          .createId("fiat.login")
          .withTag("type", "x509")

        try {
          retrySupport.retry({ ->
            if (rolesExtractor) {
              permissionService.loginWithRoles(email, roles)
              log.debug("Successful X509 authentication (user: {}, roleCount: {}, roles: {})", email, roles.size(), roles)
            } else {
              permissionService.login(email)
              log.debug("Successful X509 authentication (user: {})", email)
            }
          }, 5, Duration.ofSeconds(2), false)

          id = id.withTag("success", true).withTag("fallback", "none")
        } catch (Exception e) {
          log.debug(
            "Unsuccessful X509 authentication (user: {}, roleCount: {}, roles: {}, legacyFallback: {})",
            email,
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

        if (loginDebounceEnabled) {
          loginDebounce.put(email, now)
        }
      }

      if (fiatStatus.isEnabled()) {
        def permission = fiatPermissionEvaluator.getPermission(email)
        def roleNames = permission?.getRoles()?.collect { it -> it.getName() }
        log.debug("Extracted roles from fiat permissions for user {}: {}", email, roleNames)
        if (roleNames) {
          roles.addAll(roleNames)
        }
      }

      return roles.unique(/* mutate = */false)
    })
  }

  /**
   * https://tools.ietf.org/html/rfc3280#section-4.2.1.7
   *
   *  SubjectAltName ::= GeneralNames
   *  GeneralNames ::= SEQUENCE SIZE (1..MAX) OF GeneralName
   *  GeneralName ::= CHOICE {
   *    otherName                       [0]
   *    rfc822Name                      [1]
   *    dNSName                         [2]
   *    x400Address                     [3]
   *    directoryName                   [4]
   *    ediPartyName                    [5]
   *    uniformResourceIdentifier       [6]
   *    iPAddress                       [7]
   *    registeredID                    [8]
   *  }
   */
  static String emailFromSubjectAlternativeName(X509Certificate cert) {
    cert.getSubjectAlternativeNames().find {
      it.find { it.toString() == RFC822_NAME_ID }
    }?.get(1)
  }

  /**
   * Extract identity from an x509 certificate.
   *
   * Strategies (in priority order):
   * - X509UserIdentifierExtractor (when supplied)
   * - The certificates RFC822 name
   * - The certificates common name
   *
   * @param x509Certificate
   * @return Extracted identity or null if none available
   */
  String identityFromCertificate(X509Certificate x509Certificate) {
    String identity

    if (userIdentifierExtractor) {
      identity = userIdentifierExtractor.fromCertificate(x509Certificate)
    }
    if (identity == null) {
      identity = emailFromSubjectAlternativeName(x509Certificate)
      if (identity == null) {
        // no subject alternative name, fallback to subject common name
        String dn = x509Certificate.getSubjectX500Principal().getName();
        LdapName ldapName = new LdapName(dn)
        identity = ldapName.getRdns().find { it.getType().equalsIgnoreCase("CN")}?.value?.toString()
      }
    }

    return identity
  }
}
