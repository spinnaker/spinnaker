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

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link JWKSource} that concatenates the keys offered by several delegate sources, so a single
 * verifier can trust more than one minter's keys (e.g. Gate's interactive-user JWKS, Front50's
 * run-as JWKS, and a service's own published public key). The right key is then selected by {@code
 * kid} across the union.
 *
 * <p>Tolerant of an individual delegate being temporarily unavailable: a failing source is logged
 * at debug and skipped rather than failing the whole verification, which matters during rollout and
 * key rotation when one endpoint may be briefly unreachable.
 */
public class CompositeJWKSource implements JWKSource<SecurityContext> {

  private static final Logger log = LoggerFactory.getLogger(CompositeJWKSource.class);

  private final List<JWKSource<SecurityContext>> delegates;

  public CompositeJWKSource(List<JWKSource<SecurityContext>> delegates) {
    this.delegates = List.copyOf(delegates);
  }

  @Override
  public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) {
    List<JWK> matches = new ArrayList<>();
    for (JWKSource<SecurityContext> delegate : delegates) {
      try {
        List<JWK> delegateMatches = delegate.get(jwkSelector, context);
        if (delegateMatches != null) {
          matches.addAll(delegateMatches);
        }
      } catch (Exception e) {
        log.debug("A JWKS source was unavailable while selecting verification keys", e);
      }
    }
    return matches;
  }
}
