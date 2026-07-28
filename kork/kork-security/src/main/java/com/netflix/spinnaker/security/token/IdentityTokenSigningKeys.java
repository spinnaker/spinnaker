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

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;

/**
 * Resolved signing-key material for a minter: the full ordered set of RSA keys (each with its
 * private part) together with the single <em>active</em> key the minter signs with. The non-active
 * keys are still {@linkplain #publicJwkSet() published} so tokens signed by an outgoing key keep
 * verifying during a rotation overlap window.
 */
public final class IdentityTokenSigningKeys {

  private final List<RSAKey> keys;
  private final RSAKey active;

  public IdentityTokenSigningKeys(List<RSAKey> keys, RSAKey active) {
    this.keys = List.copyOf(keys);
    this.active = active;
  }

  /** All configured signing keys (private), including the active one. */
  public List<RSAKey> getKeys() {
    return keys;
  }

  /**
   * The key the minter currently signs with, or {@code null} when no signing key is configured and
   * authorization is disabled — in which case no minter is created and no tokens are minted.
   */
  public RSAKey getActive() {
    return active;
  }

  /** The public JWK set (private material stripped) to publish for verifiers. */
  public JWKSet publicJwkSet() {
    return IdentityTokenKeys.publicJwkSet(keys);
  }
}
