/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.gate.security.iap

import com.google.common.io.BaseEncoding
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import javax.servlet.FilterChain
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey

class IapAuthenticationFilterSpec extends Specification {

  /**
   * The security context Authentication is set in the filter. If this is not cleared between tests,
   * it could affect other tests in run in the same thread.
   */
  def cleanup() {
    SecurityContextHolder.clearContext()
  }

  def "should verify JWT Token and login to fiat using the email from payload"() {

    def request = new MockHttpServletRequest()
    def response = new MockHttpServletResponse()
    def chain = Mock(FilterChain)
    def permissionService = Mock(PermissionService)
    def front50Service = Mock(Front50Service)
    def config = new IapSsoConfig.IapSecurityConfigProperties()
    config.audience = "test_audience"

    // Create key to sign JWT Token
    KeyPairGenerator gen = KeyPairGenerator.getInstance("EC")
    gen.initialize(Curve.P_256.toECParameterSpec())
    KeyPair keyPair = gen.generateKeyPair()
    def publicKey = BaseEncoding.base64().encode(keyPair.public.encoded)

    // Create the JWT Token
    def header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(publicKey).build()
    def claims = createValidClaimsBuilder().build()
    def jwt = new SignedJWT(header, claims)

    jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.private))

    request.addHeader(config.jwtHeader, jwt.serialize())

    @Subject IapAuthenticationFilter filter = new IapAuthenticationFilter(
      config, permissionService, front50Service)

    // Add public key to key cache
    ECKey key = new ECKey.Builder(Curve.P_256, (ECPublicKey) keyPair.public)
      .algorithm(JWSAlgorithm.ES256)
      .build()
    filter.keyCache.put(publicKey, key)

    when:
    filter.doFilterInternal(request, response, chain)

    then:
    1 * chain.doFilter(request, response)
    1 * permissionService.login("test-email")
  }

  def "subsequent requests in same session with same valid signature should skip validation"() {

    def request = new MockHttpServletRequest()
    def response = new MockHttpServletResponse()
    def chain = Mock(FilterChain)
    def permissionService = Mock(PermissionService)
    def front50Service = Mock(Front50Service)
    def config = new IapSsoConfig.IapSecurityConfigProperties()
    config.audience = "test_audience"

    // Create key to sign JWT Token
    KeyPairGenerator gen = KeyPairGenerator.getInstance("EC")
    gen.initialize(Curve.P_256.toECParameterSpec())
    KeyPair keyPair = gen.generateKeyPair()
    def publicKey = BaseEncoding.base64().encode(keyPair.public.encoded)

    // Create the JWT Token
    def header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(publicKey).build()
    def claims = createValidClaimsBuilder().build()
    def jwt = new SignedJWT(header, claims)

    jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.private))

    request.addHeader(config.jwtHeader, jwt.serialize())

    @Subject IapAuthenticationFilter filter = new IapAuthenticationFilter(
      config, permissionService, front50Service)

    // Add public key to key cache
    ECKey key = new ECKey.Builder(Curve.P_256, (ECPublicKey) keyPair.public)
      .algorithm(JWSAlgorithm.ES256)
      .build()
    filter.keyCache.put(publicKey, key)

    when:
    filter.doFilterInternal(request, response, chain)

    then:
    1 * chain.doFilter(request, response)
    1 * permissionService.isEnabled()
    1 * permissionService.login("test-email")
    request.getSession(false)
      .getAttribute(IapAuthenticationFilter.SIGNATURE_ATTRIBUTE) == jwt.signature

    when:
    filter.doFilterInternal(request, response, chain)

    then:
    1 * chain.doFilter(request, response)
    0 * permissionService.isEnabled()
    0 * permissionService.login("test-email")
  }

  @Unroll
  def "requests with invalid JWT payloads should not login user"() {

    def request = new MockHttpServletRequest()
    def response = new MockHttpServletResponse()
    def chain = Mock(FilterChain)
    def permissionService = Mock(PermissionService)
    def front50Service = Mock(Front50Service)
    def config = new IapSsoConfig.IapSecurityConfigProperties()
    config.audience = "test_audience"

    // Create key to sign JWT Token
    KeyPairGenerator gen = KeyPairGenerator.getInstance("EC")
    gen.initialize(Curve.P_256.toECParameterSpec())
    KeyPair keyPair = gen.generateKeyPair()
    def publicKey = BaseEncoding.base64().encode(keyPair.public.encoded)
    def header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(publicKey).build()

    @Subject IapAuthenticationFilter filter = new IapAuthenticationFilter(
      config, permissionService, front50Service)

    // Add public key to key cache
    ECKey key = new ECKey.Builder(Curve.P_256, (ECPublicKey) keyPair.public)
      .algorithm(JWSAlgorithm.ES256)
      .build()
    filter.keyCache.put(publicKey, key)

    when:
    // Create valid JWT Token header and claims
    def claims = createValidClaimsBuilder().build()

    def jwt = new SignedJWT(header, claims)
    jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.private))
    request.addHeader(config.jwtHeader, jwt.serialize())

    filter.doFilterInternal(request, response, chain)

    then:
    1 * chain.doFilter(request, response)
    1 * permissionService.login("test-email")

    when:
    def invalidJwt = new SignedJWT(header, invalidClaims)
    invalidJwt.sign(new ECDSASigner((ECPrivateKey) keyPair.private))
    request.addHeader(config.jwtHeader, invalidJwt.serialize())

    filter.doFilterInternal(request, response, chain)

    then:
    1 * chain.doFilter(request, response)
    0 * permissionService.login("test-email")

    where:
    invalidClaims                                                       | _
    createValidClaimsBuilder().issueTime(new Date() + 100).build()      | _
    createValidClaimsBuilder().expirationTime(new Date() - 100).build() | _
    createValidClaimsBuilder().audience(null).build()                   | _
    createValidClaimsBuilder().issuer(null).build()                     | _
    createValidClaimsBuilder().subject(null).build()                    | _
    createValidClaimsBuilder().claim("email", null).build()             | _
  }

  def "validations for should take clock skew into account"() {

    def request = new MockHttpServletRequest()
    def response = new MockHttpServletResponse()
    def chain = Mock(FilterChain)
    def permissionService = Mock(PermissionService)
    def front50Service = Mock(Front50Service)
    def config = new IapSsoConfig.IapSecurityConfigProperties()
    config.audience = "test_audience"
    config.issuedAtTimeAllowedSkew = 30000L
    config.expirationTimeAllowedSkew = 30000L

    // Create key to sign JWT Token
    KeyPairGenerator gen = KeyPairGenerator.getInstance("EC")
    gen.initialize(Curve.P_256.toECParameterSpec())
    KeyPair keyPair = gen.generateKeyPair()
    def publicKey = BaseEncoding.base64().encode(keyPair.public.encoded)

    // Create the JWT Token
    def header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(publicKey).build()
    def claims = createValidClaimsBuilder()
      .issueTime(use (groovy.time.TimeCategory) {new Date() + 15.second})
      .expirationTime(use (groovy.time.TimeCategory) {new Date() - 15.second})
      .build()
    def jwt = new SignedJWT(header, claims)

    jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.private))

    request.addHeader(config.jwtHeader, jwt.serialize())

    @Subject IapAuthenticationFilter filter = new IapAuthenticationFilter(
      config, permissionService, front50Service)

    // Add public key to key cache
    ECKey key = new ECKey.Builder(Curve.P_256, (ECPublicKey) keyPair.public)
      .algorithm(JWSAlgorithm.ES256)
      .build()
    filter.keyCache.put(publicKey, key)

    when:
    filter.doFilterInternal(request, response, chain)

    then:
    1 * chain.doFilter(request, response)
  }

  JWTClaimsSet.Builder createValidClaimsBuilder() {
    return new JWTClaimsSet.Builder()
      .issueTime(new Date() - 1)
      .expirationTime(new Date() + 1)
      .audience("test_audience")
      .issuer("https://cloud.google.com/iap")
      .subject("subject")
      .claim("email", "test-email")
  }
}
