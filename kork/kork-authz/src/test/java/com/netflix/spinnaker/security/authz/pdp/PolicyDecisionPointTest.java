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

package com.netflix.spinnaker.security.authz.pdp;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceType;
import com.netflix.spinnaker.security.authz.pdp.acl.SpringAclPolicyDecisionPoint;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Validates that the default (Spring ACL adapter) and fallback (legacy permissions)
 * PolicyDecisionPoints agree on the core roles x ACL decision, plus the adapter-specific EXECUTE /
 * account-manager / unknown-application behaviors.
 */
class PolicyDecisionPointTest {

  private static final Permissions APP_ACL =
      new Permissions.Builder()
          .add(Authorization.READ, "viewers")
          .add(Authorization.WRITE, "editors")
          .add(Authorization.EXECUTE, "runners")
          .build();

  static Stream<PolicyDecisionPoint> pdps() {
    return Stream.of(
        new SpringAclPolicyDecisionPoint(), new LegacyPermissionsPolicyDecisionPoint());
  }

  @ParameterizedTest
  @MethodSource("pdps")
  void grantsReadToViewers(PolicyDecisionPoint pdp) {
    assertThat(
            pdp.decide(
                List.of("viewers"),
                ResourceType.APPLICATION,
                "spinnaker",
                Authorization.READ,
                APP_ACL))
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("pdps")
  void deniesWriteToViewers(PolicyDecisionPoint pdp) {
    assertThat(
            pdp.decide(
                List.of("viewers"),
                ResourceType.APPLICATION,
                "spinnaker",
                Authorization.WRITE,
                APP_ACL))
        .isFalse();
  }

  @ParameterizedTest
  @MethodSource("pdps")
  void grantsExecuteToRunners(PolicyDecisionPoint pdp) {
    assertThat(
            pdp.decide(
                List.of("runners"),
                ResourceType.APPLICATION,
                "spinnaker",
                Authorization.EXECUTE,
                APP_ACL))
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("pdps")
  void deniesExecuteToViewers(PolicyDecisionPoint pdp) {
    assertThat(
            pdp.decide(
                List.of("viewers"),
                ResourceType.APPLICATION,
                "spinnaker",
                Authorization.EXECUTE,
                APP_ACL))
        .isFalse();
  }

  @ParameterizedTest
  @MethodSource("pdps")
  void roleMatchingIsCaseInsensitive(PolicyDecisionPoint pdp) {
    assertThat(
            pdp.decide(
                List.of("EDITORS"),
                ResourceType.APPLICATION,
                "spinnaker",
                Authorization.WRITE,
                APP_ACL))
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("pdps")
  void unrestrictedResourceGrantsEverything(PolicyDecisionPoint pdp) {
    assertThat(
            pdp.decide(
                List.of("nobody"),
                ResourceType.APPLICATION,
                "open",
                Authorization.WRITE,
                Permissions.EMPTY))
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("pdps")
  void serviceAccountAssumableByMember(PolicyDecisionPoint pdp) {
    Permissions saAcl = new Permissions.Builder().add(Authorization.WRITE, "sa-users").build();
    assertThat(pdp.decide(List.of("sa-users"), ResourceType.SERVICE_ACCOUNT, "svc", null, saAcl))
        .isTrue();
    assertThat(pdp.decide(List.of("strangers"), ResourceType.SERVICE_ACCOUNT, "svc", null, saAcl))
        .isFalse();
  }
}
