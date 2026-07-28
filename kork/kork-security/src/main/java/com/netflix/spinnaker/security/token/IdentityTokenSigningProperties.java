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

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared identity-token <em>signing</em> configuration, bound from the {@code authz.signing} prefix
 * and consumed by every minter (Gate's interactive-user tokens and Front50's run-as tokens).
 *
 * <p>Because both minters bind the same prefix, a single signing key (or rotating key set) can be
 * declared once in a shared profile and reused by Gate and Front50 — there is no cryptographic need
 * for separate keys per minter; tokens are distinguished by their claims, not their signature.
 *
 * <p><b>Zero-downtime rotation.</b> {@link #keys} is an ordered list of RSA JWKs (each including
 * its private part and a unique {@code kid}). The minter signs with a single <em>active</em> key
 * while the public halves of <em>all</em> configured keys are published on the JWKS endpoint, so a
 * token signed by an outgoing key still verifies during the overlap window. Rotation is therefore:
 *
 * <ol>
 *   <li>Add the new key to {@code keys} (alongside the current one) and roll out — every verifier
 *       now trusts both keys; tokens are still signed by the old key.
 *   <li>Point {@link #activeKeyId} at the new key and roll out — new tokens are signed by the new
 *       key; in-flight tokens signed by the old key still verify (it is still published).
 *   <li>After the token validity window elapses, drop the old key from {@code keys} and roll out.
 * </ol>
 *
 * <pre>{@code
 * authz:
 *   signing:
 *     active-key-id: spinnaker-2026-07
 *     keys:
 *       - ${SPINNAKER_SIGNING_KEY_NEW}   # kid: spinnaker-2026-07 (active)
 *       - ${SPINNAKER_SIGNING_KEY_OLD}   # kid: spinnaker-2026-06 (still published during overlap)
 * }</pre>
 */
@ConfigurationProperties("authz.signing")
public class IdentityTokenSigningProperties {

  /**
   * Ordered list of RSA signing keys as serialized JWK JSON documents (each must include the
   * private part and a unique {@code kid}). The first entry is the active signer unless {@link
   * #activeKeyId} selects another. All keys' public halves are published for verification, enabling
   * overlapping rotation windows.
   */
  private List<String> keys = new ArrayList<>();

  /**
   * The {@code kid} of the key in {@link #keys} to sign with. When unset, the first configured key
   * is used. Set this (without removing the previous key) to perform the cut-over step of a
   * zero-downtime rotation.
   */
  private String activeKeyId;

  public List<String> getKeys() {
    return keys;
  }

  public void setKeys(List<String> keys) {
    this.keys = keys == null ? new ArrayList<>() : keys;
  }

  public String getActiveKeyId() {
    return activeKeyId;
  }

  public void setActiveKeyId(String activeKeyId) {
    this.activeKeyId = activeKeyId;
  }
}
