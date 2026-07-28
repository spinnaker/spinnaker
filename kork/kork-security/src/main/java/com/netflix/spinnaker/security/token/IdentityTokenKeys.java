/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helpers for distributing the identity-token signing keys via JWKS and for building the {@link
 * JWKSource} that verifiers consult.
 *
 * <p>Asymmetric (RSA) signing is used so only the minter (Gate / run-as minter) can mint while
 * every service can verify using the published public keys. Rotation is handled by publishing
 * multiple keys (selected by {@code kid}) in the JWK set; verifiers either cache an immutable set
 * or refresh from a remote JWKS endpoint, tolerating overlapping rotation windows.
 */
public final class IdentityTokenKeys {

  private static final Logger log = LoggerFactory.getLogger(IdentityTokenKeys.class);

  /**
   * Path, relative to a minter's service base URL, at which its public JWK set is published (Gate's
   * {@code IdentityTokenJwksController} and Front50's {@code RunAsTokenController#jwks()}). Used to
   * derive a verifier's trusted endpoints from {@code services.gate.baseUrl} / {@code
   * services.front50.baseUrl}.
   */
  public static final String JWKS_PATH = "/auth/jwks";

  private IdentityTokenKeys() {}

  /**
   * Generates a fresh RSA signing key with the given key id. Primarily intended for tests and
   * bootstrap; production deployments load a persisted key.
   */
  public static RSAKey generateRsaKey(String keyId) {
    try {
      return new RSAKeyGenerator(2048).keyID(keyId).keyUse(KeyUse.SIGNATURE).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("Unable to generate RSA signing key", e);
    }
  }

  /**
   * Builds the public JWK set (private parts stripped) that a minter publishes via a JWKS endpoint
   * or shared keystore. Include the active key plus any keys still inside their rotation window.
   */
  public static JWKSet publicJwkSet(RSAKey... keys) {
    return publicJwkSet(Arrays.asList(keys));
  }

  /**
   * Builds the public JWK set (private parts stripped) that a minter publishes via a JWKS endpoint
   * from a collection of keys. Include the active key plus any keys still inside their rotation
   * window.
   */
  public static JWKSet publicJwkSet(Collection<RSAKey> keys) {
    List<RSAKey> publicKeys = keys.stream().map(RSAKey::toPublicJWK).collect(Collectors.toList());
    return new JWKSet(List.copyOf(publicKeys));
  }

  /**
   * Resolves the signing-key material for a minter (Gate / Front50 run-as) from the shared {@link
   * IdentityTokenSigningProperties} ({@code authz.signing}).
   *
   * <ul>
   *   <li>When one or more keys are configured they are parsed (each must include its private part
   *       and a unique {@code kid}); the active signer is the key matching {@code activeKeyId}, or
   *       the first key when unset.
   *   <li>When <b>no</b> key is configured and authorization is {@code enabled}, startup <b>fails
   *       fast</b> — enforcement with no signing key would mint tokens no verifier can trust, so
   *       refusing to start is safer than minting untrustworthy tokens.
   *   <li>When no key is configured and authorization is disabled ({@code enabled=false}, the
   *       default), no signing material is produced: {@link IdentityTokenSigningKeys#getActive()}
   *       is {@code null} and no minter is created, so the service mints no identity tokens.
   *       Authorization is not enforced in this mode, so downstream simply falls back to the
   *       unsigned identity headers. Configure {@code authz.signing.keys} if you want to exercise
   *       the signed-token path while authorization is disabled.
   * </ul>
   *
   * @param properties the shared {@code authz.signing} configuration
   * @param enabled the {@code authz.enabled} master switch
   * @throws IllegalStateException if authorization is enabled with no configured key, if a
   *     configured key is malformed, lacks a private part or a {@code kid}, or if {@code
   *     activeKeyId} matches no configured key
   */
  public static IdentityTokenSigningKeys resolveSigningKeys(
      IdentityTokenSigningProperties properties, boolean enabled) {
    List<RSAKey> parsed = new ArrayList<>();
    for (String jwkJson : properties.getKeys()) {
      if (jwkJson == null || jwkJson.isBlank()) {
        continue;
      }
      RSAKey key;
      try {
        key = RSAKey.parse(jwkJson);
      } catch (ParseException e) {
        throw new IllegalStateException("authz.signing.keys contains an invalid RSA JWK", e);
      }
      if (!key.isPrivate()) {
        throw new IllegalStateException(
            "authz.signing.keys entry '"
                + key.getKeyID()
                + "' has no private part; a signing key must include the private key material");
      }
      if (key.getKeyID() == null || key.getKeyID().isBlank()) {
        throw new IllegalStateException(
            "authz.signing.keys entry is missing a 'kid'; each signing key needs a unique key id "
                + "so verifiers can select it (required for rotation)");
      }
      parsed.add(key);
    }

    if (parsed.isEmpty()) {
      if (enabled) {
        throw new IllegalStateException(
            "authz.enabled=true but no signing key is configured (authz.signing.keys). Refusing to "
                + "start: with authorization enabled a minter with no key could only sign tokens "
                + "that no other replica or service can verify. Configure a persisted, shared RSA "
                + "JWK.");
      }
      log.info(
          "No authz.signing.keys configured; no signing key will be created. Configure "
              + "authz.signing.keys to exercise the signed path.");
      return new IdentityTokenSigningKeys(List.of(), null);
    }

    RSAKey active = selectActiveKey(parsed, properties.getActiveKeyId());
    return new IdentityTokenSigningKeys(parsed, active);
  }

  private static RSAKey selectActiveKey(List<RSAKey> keys, String activeKeyId) {
    if (activeKeyId == null || activeKeyId.isBlank()) {
      return keys.get(0);
    }
    return keys.stream()
        .filter(k -> activeKeyId.equals(k.getKeyID()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "authz.signing.active-key-id='"
                        + activeKeyId
                        + "' does not match any configured authz.signing.keys 'kid'"));
  }

  /**
   * Builds an in-memory {@link JWKSource} over the supplied (public) JWK set. Suitable for
   * verifiers that load keys from a shared keystore and refresh the set on rotation.
   */
  public static JWKSource<SecurityContext> immutableKeySource(JWKSet publicJwkSet) {
    return new ImmutableJWKSet<>(publicJwkSet);
  }

  /**
   * Builds a {@link JWKSource} that fetches and caches keys from a remote JWKS endpoint, refreshing
   * on rotation. This is the recommended verifier configuration for live key distribution.
   *
   * @param jwksUrl the minter's JWKS endpoint
   * @param connectTimeoutMillis HTTP connect timeout
   * @param readTimeoutMillis HTTP read timeout
   * @param sizeLimitBytes maximum response size
   */
  public static JWKSource<SecurityContext> remoteKeySource(
      URL jwksUrl, int connectTimeoutMillis, int readTimeoutMillis, int sizeLimitBytes) {
    return new RemoteJWKSet<>(
        jwksUrl,
        new DefaultResourceRetriever(connectTimeoutMillis, readTimeoutMillis, sizeLimitBytes));
  }

  /**
   * Builds the inbound-token verification {@link JWKSource} shared by every verifier service. The
   * trusted endpoints are derived from the supplied {@code minterBaseUrls} — typically {@code
   * services.gate.baseUrl} (interactive-user tokens) and {@code services.front50.baseUrl} (run-as
   * tokens) — by appending {@link #JWKS_PATH}, so operators need not restate URLs Spinnaker already
   * knows for its service-to-service config. Blank base URLs are ignored and duplicates collapsed.
   *
   * <p>Plus any {@code additionalSources} the service contributes locally (e.g. Front50 trusting
   * its own run-as public key). The result aggregates all sources via {@link CompositeJWKSource} so
   * a token minted by any trusted issuer verifies against the right key.
   *
   * <p>When neither the derived endpoints nor any local source yield a key: when authorization is
   * enabled ({@code enabled=true}) startup fails fast (a verifier with no trusted keys would reject
   * every request); when disabled a warning is logged and an empty (but valid) source is returned,
   * so verification fails and requests fall back to the legacy unsigned identity headers.
   *
   * @param properties the shared {@code authz.verifier} fetch tuning (timeouts / size limit)
   * @param enabled the {@code authz.enabled} master switch
   * @param minterBaseUrls zero or more minter service base URLs (e.g. {@code
   *     services.gate.baseUrl}, {@code services.front50.baseUrl}); each non-blank value contributes
   *     a {@code baseUrl + /auth/jwks} endpoint
   * @param additionalSources zero or more locally-built sources to union with the derived remote
   *     endpoints (e.g. a minter's own published public key)
   * @throws IllegalStateException if a derived JWKS URI is malformed, or if authorization is
   *     enabled with no resolvable JWKS source
   */
  @SafeVarargs
  public static JWKSource<SecurityContext> verificationKeySource(
      IdentityTokenVerifierProperties properties,
      boolean enabled,
      Collection<String> minterBaseUrls,
      JWKSource<SecurityContext>... additionalSources) {
    Set<String> jwksUris = new LinkedHashSet<>();
    if (minterBaseUrls != null) {
      for (String baseUrl : minterBaseUrls) {
        String derived = authJwksUriFromBaseUrl(baseUrl);
        if (derived != null) {
          jwksUris.add(derived);
        }
      }
    }

    List<JWKSource<SecurityContext>> sources = new ArrayList<>();
    for (String uri : jwksUris) {
      try {
        sources.add(
            remoteKeySource(
                new URL(uri),
                properties.getConnectTimeoutMillis(),
                properties.getReadTimeoutMillis(),
                properties.getSizeLimitBytes()));
        log.info("Trusting identity-token signing keys from JWKS endpoint {}", uri);
      } catch (Exception e) {
        throw new IllegalStateException("Invalid identity-token JWKS URI: " + uri, e);
      }
    }
    for (JWKSource<SecurityContext> additional : additionalSources) {
      if (additional != null) {
        sources.add(additional);
      }
    }
    if (sources.isEmpty()) {
      if (enabled) {
        throw new IllegalStateException(
            "authz.enabled=true but no JWKS source could be resolved. Refusing to start: "
                + "with authorization enabled a verifier with no trusted keys rejects every "
                + "request. Set services.gate.baseUrl / services.front50.baseUrl so the minters' "
                + "JWKS endpoints can be derived.");
      }
      log.warn(
          "No identity-token JWKS source could be resolved (services.gate.baseUrl / "
              + "services.front50.baseUrl unset); inbound identity tokens cannot be verified. With "
              + "authorization disabled (authz.enabled=false) requests fall back to unsigned "
              + "identity headers.");
    }
    return new CompositeJWKSource(sources);
  }

  /**
   * Derives a minter's JWKS endpoint from its service base URL by appending {@link #JWKS_PATH}
   * (after trimming any trailing slashes). Returns {@code null} for a blank base URL.
   */
  private static String authJwksUriFromBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return null;
    }
    String trimmed = baseUrl.strip();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed + JWKS_PATH;
  }
}
