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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import retrofit.RetrofitError;

/***
 * This filter verifies the request header from Cloud IAP containing a JWT token, after the user
 * has been authenticated and authorized by IAP via Google OAuth2.0 and IAP's authorization service.
 * The user email from the payload used to create the Spinnaker user.
 */
public class IAPAuthenticationFilter implements Filter {

  private IAPConfig.IAPSecurityConfigProperties configProperties;

  private PermissionService permissionService;

  private Front50Service front50Service;

  private static final String signatureAttribute = "JWTSignature";

  private final Map<String, JWK> keyCache = new HashMap<>();

  private Logger logger = LoggerFactory.getLogger(IAPAuthenticationFilter.class);

  public IAPAuthenticationFilter(
    IAPConfig.IAPSecurityConfigProperties configProperties,
    PermissionService permissionService,
    Front50Service front50Service) {
    this.configProperties = configProperties;
    this.permissionService = permissionService;
    this.front50Service = front50Service;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;
    HttpSession session = req.getSession();

    try {
      String token = req.getHeader(configProperties.jwtHeader);
      Preconditions.checkNotNull(token);

      SignedJWT jwt = SignedJWT.parse(token);

      Base64URL signatureInSession = (Base64URL) session.getAttribute(signatureAttribute);

      if (signatureInSession != null && signatureInSession.equals(jwt.getSignature())) {
        // Signature matches in previous request signatures in current session, skip validation.
        chain.doFilter(req, res);
        return;
      }

      User verifiedUser = verifyJWTAndGetUser(jwt);

      PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
        verifiedUser,
        null /* credentials */,
        // Need to set authorities list even if empty to get a valid authentication.
        verifiedUser.getAuthorities()
      );

      // Service accounts are already logged in.
      if (!isServiceAccount(verifiedUser.getEmail())) {
        permissionService.login(verifiedUser.getEmail());
      }

      SecurityContextHolder.getContext().setAuthentication(authentication);

      // Save the signature to skip validation for subsequent requests with same token.
      session.setAttribute(signatureAttribute, jwt.getSignature());

    } catch (Exception e) {
      logger.error("Could not verify JWT Token", e);
      res.sendError(HttpServletResponse.SC_FORBIDDEN, "Could not verify JWT Token");
      session.invalidate();
      return;
    }

    chain.doFilter(req, res);
  }

  @Override
  public void init(FilterConfig filterConfig) {

  }

  @Override
  public void destroy() {

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
      logger.warn("Could not get list of service accounts.", re);
    }
    return false;
  }

  private User verifyJWTAndGetUser(SignedJWT jwt) throws Exception {
    JWSHeader jwsHeader = jwt.getHeader();

    Preconditions.checkNotNull(jwsHeader.getAlgorithm());
    Preconditions.checkNotNull(jwsHeader.getKeyID());

    JWTClaimsSet claims = jwt.getJWTClaimsSet();

    Preconditions.checkArgument(claims.getAudience().contains(configProperties.audience));
    Preconditions.checkArgument(claims.getIssuer().contains(configProperties.issuerId));

    Date currentTime = new Date();
    Preconditions.checkArgument(claims.getIssueTime().before(currentTime));
    Preconditions.checkArgument(claims.getExpirationTime().after(currentTime));

    Preconditions.checkNotNull(claims.getSubject());
    String email = (String) claims.getClaim("email");
    Preconditions.checkNotNull(email);

    ECPublicKey publicKey = getKey(jwsHeader.getKeyID(), jwsHeader.getAlgorithm().getName());
    Preconditions.checkNotNull(publicKey);

    JWSVerifier jwsVerifier = new ECDSAVerifier(publicKey);
    Preconditions.checkState(jwt.verify(jwsVerifier));

    User verifiedUser = new User();
    verifiedUser.setEmail(email);

    return verifiedUser;
  }

  private ECPublicKey getKey(String kid, String alg) throws Exception {
    JWK jwk = keyCache.get(kid);
    if (jwk == null) {
      // update cache loading jwk public key data from url
      JWKSet jwkSet = JWKSet.load(new URL(configProperties.iapVerifyKeyUrl));
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
    logger.debug("Clearing IAP public key cache.");
    keyCache.clear();
  }
}
