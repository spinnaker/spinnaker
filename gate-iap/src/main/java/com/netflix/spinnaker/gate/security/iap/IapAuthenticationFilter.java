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

package com.netflix.spinnaker.gate.security.iap;

import com.google.common.base.Preconditions;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.gate.security.iap.IapSsoConfig.IapSecurityConfigProperties;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.security.User;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.net.URL;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import retrofit.RetrofitError;

/**
 * * This filter verifies the request header from Cloud IAP containing a JWT token, after the user
 * has been authenticated and authorized by IAP via Google OAuth2.0 and IAP's authorization service.
 * The user email from the payload used to create the Spinnaker user.
 */
@Slf4j
public class IapAuthenticationFilter extends OncePerRequestFilter {
  private static final String SIGNATURE_ATTRIBUTE = "JWTSignature";

  private static final Clock CLOCK = Clock.systemUTC();

  private IapSecurityConfigProperties configProperties;

  private PermissionService permissionService;

  private Front50Service front50Service;

  private final Map<String, JWK> keyCache = new HashMap<>();

  public IapAuthenticationFilter(
      IapSecurityConfigProperties configProperties,
      PermissionService permissionService,
      Front50Service front50Service) {
    this.configProperties = configProperties;
    this.permissionService = permissionService;
    this.front50Service = front50Service;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpSession session = request.getSession();

    try {
      String token = request.getHeader(configProperties.getJwtHeader());
      Preconditions.checkNotNull(
          token,
          String.format("Request is missing JWT header: %s", configProperties.getJwtHeader()));

      SignedJWT jwt = SignedJWT.parse(token);

      Base64URL signatureInSession = (Base64URL) session.getAttribute(SIGNATURE_ATTRIBUTE);

      if (signatureInSession != null && signatureInSession.equals(jwt.getSignature())) {
        // Signature matches in previous request signatures in current session, skip validation.
        chain.doFilter(request, response);
        return;
      }

      User verifiedUser = verifyJWTAndGetUser(jwt);

      PreAuthenticatedAuthenticationToken authentication =
          new PreAuthenticatedAuthenticationToken(
              verifiedUser,
              null /* credentials */,
              // Need to set authorities list even if empty to get a valid authentication.
              verifiedUser.getAuthorities());

      // Service accounts are already logged in.
      if (!isServiceAccount(verifiedUser.getEmail())) {
        permissionService.login(verifiedUser.getEmail());
      }

      SecurityContextHolder.getContext().setAuthentication(authentication);

      // Save the signature to skip validation for subsequent requests with same token.
      session.setAttribute(SIGNATURE_ATTRIBUTE, jwt.getSignature());

    } catch (Exception e) {
      log.error("Could not verify JWT Token for request {}", request.getPathInfo(), e);
    }
    chain.doFilter(request, response);
  }

  private boolean isServiceAccount(String email) {
    if (email == null || !permissionService.isEnabled()) {
      return false;
    }
    try {
      List<ServiceAccount> serviceAccounts = front50Service.getServiceAccounts();
      for (ServiceAccount serviceAccount : serviceAccounts) {

        if (email.equalsIgnoreCase(serviceAccount.getName())) {
          return true;
        }
      }
      return false;
    } catch (RetrofitError re) {
      log.warn("Could not get list of service accounts.", re);
    }
    return false;
  }

  private User verifyJWTAndGetUser(SignedJWT jwt) throws Exception {
    JWSHeader jwsHeader = jwt.getHeader();

    Preconditions.checkNotNull(jwsHeader.getAlgorithm(), "JWT header is missing algorithm (alg)");
    Preconditions.checkNotNull(jwsHeader.getKeyID(), "JWT header is missing key ID (kid)");

    JWTClaimsSet claims = jwt.getJWTClaimsSet();

    Preconditions.checkArgument(
        claims.getAudience().contains(configProperties.getAudience()),
        String.format(
            "JWT payload audience claim (aud) must contain: %s.", configProperties.getAudience()));
    Preconditions.checkArgument(
        claims.getIssuer().equals(configProperties.getIssuerId()),
        String.format(
            "JWT payload issuer claim (iss) must be: %s", configProperties.getIssuerId()));

    Date currentTime = Date.from(Instant.now(CLOCK));
    Preconditions.checkArgument(
        claims
            .getIssueTime()
            .before(
                new Date(currentTime.getTime() + configProperties.getIssuedAtTimeAllowedSkew())),
        String.format(
            "JWT payloadissued-at time claim (iat) must be before the current time (with %dms allowed clock skew): currentTime=%d, issueTime=%d",
            configProperties.getIssuedAtTimeAllowedSkew(),
            currentTime.getTime(),
            claims.getIssueTime().getTime()));
    Preconditions.checkArgument(
        claims
            .getExpirationTime()
            .after(
                new Date(currentTime.getTime() - configProperties.getExpirationTimeAllowedSkew())),
        String.format(
            "JWT payload expiration time claim (exp) must be after the current time (with %dms allowed clock skew): currentTime=%d, expirationTime=%d",
            configProperties.getExpirationTimeAllowedSkew(),
            currentTime.getTime(),
            claims.getExpirationTime().getTime()));

    Preconditions.checkNotNull(claims.getSubject(), "JWT payload is missing subject (sub)");
    String email = (String) claims.getClaim("email");
    Preconditions.checkNotNull(email, "JWT payload is missing user email (email)");

    ECPublicKey publicKey = getKey(jwsHeader.getKeyID(), jwsHeader.getAlgorithm().getName());
    Preconditions.checkNotNull(publicKey, "Failed to get EC public key");

    JWSVerifier jwsVerifier = new ECDSAVerifier(publicKey);
    Preconditions.checkState(jwt.verify(jwsVerifier), "EC public key failed verification");

    User verifiedUser = new User();
    verifiedUser.setEmail(email);

    return verifiedUser;
  }

  private ECPublicKey getKey(String kid, String alg) throws Exception {
    JWK jwk = keyCache.get(kid);
    if (jwk == null) {
      // update cache loading jwk public key data from url
      JWKSet jwkSet = JWKSet.load(new URL(configProperties.getIapVerifyKeyUrl()));
      for (JWK key : jwkSet.getKeys()) {
        keyCache.put(key.getKeyID(), key);
      }
      jwk = keyCache.get(kid);
    }
    // confirm that algorithm matches
    if (jwk != null && jwk.getAlgorithm() != null && jwk.getAlgorithm().getName().equals(alg)) {
      return ECKey.parse(jwk.toJSONString()).toECPublicKey();
    }
    return null;
  }

  /**
   * Clears the public key cache every 5 hours. This is cleared periodically to capture potential
   * IAP public key changes.
   */
  @Scheduled(fixedDelay = 18000000L)
  void clearKeyCache() {
    log.debug("Clearing IAP public key cache.");
    keyCache.clear();
  }
}
