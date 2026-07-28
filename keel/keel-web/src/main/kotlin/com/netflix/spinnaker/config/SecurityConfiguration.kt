package com.netflix.spinnaker.config

import com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator
import com.netflix.spinnaker.security.authz.config.AuthzPolicyConfiguration
import com.netflix.spinnaker.security.authz.filter.ApiTokenExchangeFilter
import com.netflix.spinnaker.security.authz.filter.ApiTokenExchangeProperties
import com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationConverter
import com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationFilter
import com.netflix.spinnaker.security.token.AuthorizationProperties
import com.netflix.spinnaker.security.token.IdentityTokenKeys
import com.netflix.spinnaker.security.token.IdentityTokenVerifierProperties
import com.netflix.spinnaker.security.token.NimbusSpinnakerTokenVerifier
import com.netflix.spinnaker.security.token.SpinnakerTokenSettings
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.authentication.AuthenticationConverter

/**
 * Replaces Fiat's `@EnableFiatAutoConfig` in keel with the kork verifier-only authorization chain
 * (the same shape echo/igor/orca adopted in the Fiat-removal migration).
 *
 * Keel is a token *verifier*, not a minter: it never issues identity tokens. It verifies inbound
 * identity tokens minted by Gate (interactive users) and Front50 (run-as), maps the verified roles
 * to `ROLE_*` authorities in the [org.springframework.security.core.context.SecurityContext], and
 * delegates per-resource decisions to the shared [PolicyDecisionPointPermissionEvaluator] (imported
 * via [AuthzPolicyConfiguration]). Method-level `@PreAuthorize`/`hasPermission` checks bind to that
 * evaluator unchanged.
 *
 * The master switch is `authz.enabled` (default `false` = authorization disabled): with it
 * off, an absent or invalid token falls back to the legacy unsigned `X-SPINNAKER-USER` header and
 * every authorization check is allowed (matching the old `services.fiat.enabled=false`). Keel's
 * [com.netflix.spinnaker.keel.auth.AuthorizationSupport] is gated on the same flag.
 *
 * Netflix-internal deployments that supply their own security wiring set `keel.security.custom=true`
 * to disable this open-source configuration.
 */
@Configuration
@ConditionalOnProperty(name = ["keel.security.custom"], havingValue = "false", matchIfMissing = true)
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(
  AuthorizationProperties::class,
  SpinnakerTokenSettings::class,
  IdentityTokenVerifierProperties::class,
  ApiTokenExchangeProperties::class
)
@Import(AuthzPolicyConfiguration::class)
class SecurityConfiguration {

  /**
   * Re-exposes the shared [PolicyDecisionPointPermissionEvaluator] under the SpEL bean name
   * `spinnakerPermissionEvaluator` so any `@spinnakerPermissionEvaluator`/`hasPermission` SpEL
   * resolves unchanged.
   */
  @Bean(name = ["spinnakerPermissionEvaluator"])
  fun spinnakerPermissionEvaluator(permissionEvaluator: PolicyDecisionPointPermissionEvaluator): PermissionEvaluator =
    permissionEvaluator

  /**
   * Trusts the minters' JWKS endpoints (Gate + Front50 run-as), derived from `services.gate.baseUrl`
   * / `services.front50.baseUrl` (the service URLs keel is already configured with) by appending
   * `/auth/jwks`. When neither can be resolved no token verifies, and permissive mode falls back to
   * the unsigned identity headers.
   */
  @Bean
  fun identityTokenKeySource(
    properties: IdentityTokenVerifierProperties,
    authz: AuthorizationProperties,
    @Value("\${services.gate.baseUrl:}") gateBaseUrl: String,
    @Value("\${services.front50.baseUrl:}") front50BaseUrl: String
  ): JWKSource<SecurityContext> =
    IdentityTokenKeys.verificationKeySource(
      properties, authz.isEnabled, listOf(gateBaseUrl, front50BaseUrl)
    )

  @Bean
  fun identityTokenVerifier(
    identityTokenKeySource: JWKSource<SecurityContext>,
    settings: SpinnakerTokenSettings
  ): SpinnakerTokenVerifier =
    NimbusSpinnakerTokenVerifier(identityTokenKeySource, settings)

  @Bean
  fun identityTokenAuthenticationConverter(
    identityTokenVerifier: SpinnakerTokenVerifier,
    authz: AuthorizationProperties
  ): AuthenticationConverter =
    IdentityTokenAuthenticationConverter(identityTokenVerifier, authz)

  @Bean
  fun keelWebSecurityConfigurerAdapter(
    identityTokenAuthenticationConverter: AuthenticationConverter,
    apiTokenExchangeProperties: ApiTokenExchangeProperties,
    @Value("\${services.gate.baseUrl:}") gateBaseUrl: String
  ): WebSecurityConfigurerAdapter =
    IdentityTokenWebSecurityConfigurerAdapter(
      identityTokenAuthenticationConverter,
      ApiTokenExchangeFilter.createIfEnabled(apiTokenExchangeProperties, gateBaseUrl)
    )

  /**
   * Installs the [IdentityTokenAuthenticationFilter] ahead of the anonymous filter. When direct
   * API-token support is enabled, an [ApiTokenExchangeFilter] runs just before it to swap an opaque
   * `spk_` token for the signed identity token Gate would have minted. URL-level access is left
   * open; authorization is enforced at the method level via `@PreAuthorize`.
   */
  private class IdentityTokenWebSecurityConfigurerAdapter(
    private val authenticationConverter: AuthenticationConverter,
    private val apiTokenExchangeFilter: ApiTokenExchangeFilter?
  ) : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) {
      http.csrf().disable()
        .servletApi().and()
        .exceptionHandling().and()
        .anonymous().and()
        .addFilterBefore(
          IdentityTokenAuthenticationFilter(authenticationConverter),
          AnonymousAuthenticationFilter::class.java
        )
      apiTokenExchangeFilter?.let {
        http.addFilterBefore(it, IdentityTokenAuthenticationFilter::class.java)
      }
    }
  }
}
