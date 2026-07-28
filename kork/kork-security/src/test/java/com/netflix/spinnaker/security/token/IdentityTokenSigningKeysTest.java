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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityTokenSigningKeysTest {

  private static IdentityTokenSigningProperties props(String activeKeyId, RSAKey... keys) {
    IdentityTokenSigningProperties properties = new IdentityTokenSigningProperties();
    properties.setActiveKeyId(activeKeyId);
    List<String> serialized = new java.util.ArrayList<>();
    for (RSAKey key : keys) {
      serialized.add(key.toJSONString());
    }
    properties.setKeys(serialized);
    return properties;
  }

  @Test
  void hardFailsWhenEnabledWithNoConfiguredKey() {
    assertThatThrownBy(
            () ->
                IdentityTokenKeys.resolveSigningKeys(
                    new IdentityTokenSigningProperties(), /* enabled= */ true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("authz.enabled=true")
        .hasMessageContaining("authz.signing.keys");
  }

  @Test
  void producesNoSigningKeyWhenDisabledWithNoConfiguredKey() {
    IdentityTokenSigningKeys resolved =
        IdentityTokenKeys.resolveSigningKeys(
            new IdentityTokenSigningProperties(), /* enabled= */ false);

    assertThat(resolved.getKeys()).isEmpty();
    assertThat(resolved.getActive()).isNull();
    assertThat(resolved.publicJwkSet().getKeys()).isEmpty();
  }

  @Test
  void signsWithFirstKeyByDefaultAndPublishesAll() {
    RSAKey first = IdentityTokenKeys.generateRsaKey("k1");
    RSAKey second = IdentityTokenKeys.generateRsaKey("k2");

    IdentityTokenSigningKeys resolved =
        IdentityTokenKeys.resolveSigningKeys(props(null, first, second), true);

    assertThat(resolved.getActive().getKeyID()).isEqualTo("k1");
    assertThat(resolved.publicJwkSet().getKeys()).hasSize(2);
    assertThat(resolved.publicJwkSet().getKeys().stream().allMatch(k -> !k.isPrivate())).isTrue();
  }

  @Test
  void activeKeyIdSelectsTheSignerWithoutDroppingOtherKeys() {
    RSAKey oldKey = IdentityTokenKeys.generateRsaKey("old");
    RSAKey newKey = IdentityTokenKeys.generateRsaKey("new");

    IdentityTokenSigningKeys resolved =
        IdentityTokenKeys.resolveSigningKeys(props("new", oldKey, newKey), true);

    assertThat(resolved.getActive().getKeyID()).isEqualTo("new");
    assertThat(resolved.publicJwkSet().getKeys()).hasSize(2);
  }

  @Test
  void rejectsActiveKeyIdThatMatchesNoConfiguredKey() {
    RSAKey key = IdentityTokenKeys.generateRsaKey("k1");

    assertThatThrownBy(
            () -> IdentityTokenKeys.resolveSigningKeys(props("does-not-exist", key), true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active-key-id");
  }

  @Test
  void rejectsPublicOnlyKey() {
    RSAKey publicOnly = IdentityTokenKeys.generateRsaKey("k1").toPublicJWK();
    IdentityTokenSigningProperties properties = new IdentityTokenSigningProperties();
    properties.setKeys(List.of(publicOnly.toJSONString()));

    assertThatThrownBy(() -> IdentityTokenKeys.resolveSigningKeys(properties, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no private part");
  }
}
