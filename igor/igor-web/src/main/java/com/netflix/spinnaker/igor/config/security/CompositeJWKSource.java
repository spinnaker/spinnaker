/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.igor.config.security;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link JWKSource} that fans a key selection out across several delegate sources and merges the
 * results. Igor is verifier-only and must trust more than one minter — Gate (interactive-user
 * tokens) and Front50 (run-as tokens) — each exposing its own JWKS endpoint. The {@code kid}-based
 * key selection in {@link com.netflix.spinnaker.security.token.NimbusSpinnakerTokenVerifier} then
 * picks the matching key regardless of which minter published it.
 *
 * <p>A delegate that fails to produce keys (e.g. a temporarily unreachable JWKS endpoint) is logged
 * and skipped so a single unavailable minter does not break verification of tokens minted by the
 * others.
 */
public class CompositeJWKSource<C extends SecurityContext> implements JWKSource<C> {

  private static final Logger log = LoggerFactory.getLogger(CompositeJWKSource.class);

  private final List<JWKSource<C>> delegates;

  public CompositeJWKSource(List<JWKSource<C>> delegates) {
    this.delegates = List.copyOf(delegates);
  }

  @Override
  public List<JWK> get(JWKSelector jwkSelector, C context) throws KeySourceException {
    List<JWK> matches = new ArrayList<>();
    for (JWKSource<C> delegate : delegates) {
      try {
        matches.addAll(delegate.get(jwkSelector, context));
      } catch (KeySourceException e) {
        log.debug("Ignoring JWK source that failed to provide keys: {}", delegate, e);
      }
    }
    return matches;
  }
}
