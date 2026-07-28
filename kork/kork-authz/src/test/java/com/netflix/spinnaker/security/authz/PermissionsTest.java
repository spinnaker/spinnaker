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

package com.netflix.spinnaker.security.authz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Exercises the additive merge semantics used to apply global default application permissions. */
class PermissionsTest {

  @Test
  void mergeUnionsRolesPerAuthorization() {
    Permissions appAcl =
        new Permissions.Builder()
            .add(Authorization.READ, "team-a")
            .add(Authorization.WRITE, "team-a")
            .build();
    Permissions defaults =
        new Permissions.Builder()
            .add(Authorization.READ, "global")
            .add(Authorization.EXECUTE, "global")
            .build();

    Permissions merged = defaults.merge(appAcl);

    assertThat(merged.get(Authorization.READ)).containsExactlyInAnyOrder("team-a", "global");
    assertThat(merged.get(Authorization.WRITE)).containsExactly("team-a");
    assertThat(merged.get(Authorization.EXECUTE)).containsExactly("global");
    assertThat(merged.get(Authorization.CREATE)).isEmpty();
  }

  @Test
  void mergeIsCommutative() {
    Permissions a = new Permissions.Builder().add(Authorization.READ, "a").build();
    Permissions b = new Permissions.Builder().add(Authorization.READ, "b").build();

    assertThat(a.merge(b)).isEqualTo(b.merge(a));
    assertThat(a.merge(b).get(Authorization.READ)).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  void mergeWithEmptyDefaultsIsNoOp() {
    Permissions appAcl =
        new Permissions.Builder()
            .add(Authorization.READ, "team-a")
            .add(Authorization.WRITE, "team-a")
            .build();

    assertThat(Permissions.EMPTY.merge(appAcl)).isEqualTo(appAcl);
    assertThat(appAcl.merge(Permissions.EMPTY)).isEqualTo(appAcl);
  }

  @Test
  void mergingTwoUnrestrictedYieldsUnrestricted() {
    Permissions merged = Permissions.EMPTY.merge(Permissions.EMPTY);
    assertThat(merged).isEqualTo(Permissions.EMPTY);
    assertThat(merged.isRestricted()).isFalse();
  }

  @Test
  void mergeWithNullReturnsThis() {
    Permissions appAcl = new Permissions.Builder().add(Authorization.READ, "team-a").build();
    assertThat(appAcl.merge(null)).isSameAs(appAcl);
  }

  @Test
  void mergeAppliesDefaultsOntoUnrestrictedApp() {
    // An app with no own ACL (unrestricted) becomes restricted to the default roles, mirroring the
    // legacy aggregate + prefix("*") behavior where every application inherited the prefix grants.
    Permissions defaults =
        new Permissions.Builder()
            .add(Authorization.READ, "global")
            .add(Authorization.WRITE, "global")
            .add(Authorization.EXECUTE, "global")
            .build();

    Permissions merged = defaults.merge(Permissions.EMPTY);

    assertThat(merged.isRestricted()).isTrue();
    assertThat(merged.get(Authorization.READ)).containsExactly("global");
    assertThat(merged.get(Authorization.WRITE)).containsExactly("global");
    assertThat(merged.get(Authorization.EXECUTE)).containsExactly("global");
  }

  @Test
  void mergeNormalizesAndDeduplicatesRoles() {
    Permissions appAcl = new Permissions.Builder().add(Authorization.READ, "Team-A").build();
    Permissions defaults = new Permissions.Builder().add(Authorization.READ, "team-a").build();

    Permissions merged = defaults.merge(appAcl);

    assertThat(merged.get(Authorization.READ)).containsExactly("team-a");
  }

  @Test
  void subtractRemovesRolesPerAuthorization() {
    Permissions submitted =
        new Permissions.Builder()
            .add(Authorization.READ, "team-a")
            .add(Authorization.READ, "default-role")
            .add(Authorization.WRITE, "team-a")
            .add(Authorization.WRITE, "default-role")
            .build();
    Permissions defaults =
        new Permissions.Builder().add(Authorization.READ, "default-role").build();

    Permissions own = submitted.subtract(defaults);

    assertThat(own.get(Authorization.READ)).containsExactly("team-a");
    // WRITE is not defaulted, so an explicit WRITE grant for the same role is a real grant.
    assertThat(own.get(Authorization.WRITE)).containsExactlyInAnyOrder("team-a", "default-role");
  }

  @Test
  void subtractUndoesMerge() {
    Permissions own = new Permissions.Builder().add(Authorization.READ, "team-a").build();
    Permissions defaults =
        new Permissions.Builder().add(Authorization.READ, "default-role").build();

    // The round trip a client makes: read the effective ACL, submit it back untouched.
    assertThat(defaults.merge(own).subtract(defaults)).isEqualTo(own);
  }

  @Test
  void subtractCanEmptyAnAclEntirely() {
    Permissions defaults =
        new Permissions.Builder().add(Authorization.READ, "default-role").build();

    Permissions own = defaults.subtract(defaults);

    assertThat(own.isRestricted()).isFalse();
  }

  @Test
  void subtractNormalizesRoles() {
    Permissions submitted =
        new Permissions.Builder().add(Authorization.READ, "Default-Role").build();
    Permissions defaults =
        new Permissions.Builder().add(Authorization.READ, "default-role").build();

    assertThat(submitted.subtract(defaults).isRestricted()).isFalse();
  }

  @Test
  void subtractWithNoDefaultsIsNoOp() {
    Permissions appAcl = new Permissions.Builder().add(Authorization.READ, "team-a").build();

    assertThat(appAcl.subtract(null)).isSameAs(appAcl);
    assertThat(appAcl.subtract(Permissions.EMPTY)).isSameAs(appAcl);
  }
}
