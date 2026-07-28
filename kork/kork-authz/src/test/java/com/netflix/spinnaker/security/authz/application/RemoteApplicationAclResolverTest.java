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

package com.netflix.spinnaker.security.authz.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RemoteApplicationAclResolverTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Records every application it is asked for, so call counts can be asserted. */
  private static class RecordingSource implements ApplicationPermissionSource {
    private final Map<String, Map<?, ?>> records;
    final List<String> fetched = new ArrayList<>();

    RecordingSource(Map<String, Map<?, ?>> records) {
      this.records = records;
    }

    @Nullable
    @Override
    public Map<?, ?> fetch(String applicationName) {
      fetched.add(applicationName);
      return records.get(applicationName);
    }
  }

  private RemoteApplicationAclResolver resolver(ApplicationPermissionSource source) {
    return new RemoteApplicationAclResolver(source, objectMapper);
  }

  private static Map<?, ?> record(String name, String readRole) {
    return Map.of("name", name, "permissions", Map.of("READ", List.of(readRole)));
  }

  private static void bindRequest() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  @DisplayName("resolves the application's ACL from the source")
  void resolvesFromSource() {
    RecordingSource source = new RecordingSource(Map.of("app", record("app", "team-a")));

    Permissions acl = resolver(source).resolve(ResourceType.APPLICATION, "app");

    assertThat(acl).isNotNull();
    assertThat(acl.get(Authorization.READ)).containsExactly("team-a");
  }

  @Test
  @DisplayName("a record with no permissions block is unrestricted")
  void recordWithoutPermissionsIsUnrestricted() {
    RecordingSource source = new RecordingSource(Map.of("app", Map.of("name", "app")));

    Permissions acl = resolver(source).resolve(ResourceType.APPLICATION, "app");

    assertThat(acl).isNotNull();
    assertThat(acl.isRestricted()).isFalse();
  }

  @Test
  @DisplayName("an application the source has no record for resolves to null")
  void missingRecordResolvesToNull() {
    RecordingSource source = new RecordingSource(Map.of());

    assertThat(resolver(source).resolve(ResourceType.APPLICATION, "ghost")).isNull();
  }

  @Test
  @DisplayName("the source's ACL is used as-is; defaults are the owner's to apply, not ours")
  void doesNotApplyDefaultsLocally() {
    // Front50 serves the effective ACL, so whatever the source returns is already the answer.
    // Re-deriving defaults here is what would let a consumer drift from the owner's decision.
    RecordingSource source = new RecordingSource(Map.of("app", record("app", "team-a")));

    Permissions acl = resolver(source).resolve(ResourceType.APPLICATION, "app");

    assertThat(acl).isNotNull();
    assertThat(acl.get(Authorization.READ)).containsExactly("team-a");
  }

  @Test
  @DisplayName("within one request an application is read once, however many times it is checked")
  void memoizesWithinARequest() {
    bindRequest();
    RecordingSource source = new RecordingSource(Map.of("app", record("app", "team-a")));
    RemoteApplicationAclResolver resolver = resolver(source);

    for (int i = 0; i < 25; i++) {
      assertThat(resolver.resolve(ResourceType.APPLICATION, "app")).isNotNull();
    }

    assertThat(source.fetched).containsExactly("app");
  }

  @Test
  @DisplayName("each distinct application in a request is read exactly once")
  void memoizesPerApplication() {
    bindRequest();
    RecordingSource source =
        new RecordingSource(Map.of("a", record("a", "team-a"), "b", record("b", "team-b")));
    RemoteApplicationAclResolver resolver = resolver(source);

    for (int i = 0; i < 10; i++) {
      resolver.resolve(ResourceType.APPLICATION, "a");
      resolver.resolve(ResourceType.APPLICATION, "b");
    }

    assertThat(source.fetched).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  @DisplayName("an unresolvable application is not re-read for every row of a list response")
  void memoizesMisses() {
    bindRequest();
    RecordingSource source = new RecordingSource(Map.of());
    RemoteApplicationAclResolver resolver = resolver(source);

    for (int i = 0; i < 10; i++) {
      assertThat(resolver.resolve(ResourceType.APPLICATION, "ghost")).isNull();
    }

    assertThat(source.fetched).containsExactly("ghost");
  }

  @Test
  @DisplayName("the memo does not outlive the request, so permission changes are seen immediately")
  void doesNotMemoizeAcrossRequests() {
    RecordingSource source = new RecordingSource(Map.of("app", record("app", "team-a")));
    RemoteApplicationAclResolver resolver = resolver(source);

    bindRequest();
    resolver.resolve(ResourceType.APPLICATION, "app");
    RequestContextHolder.resetRequestAttributes();

    bindRequest();
    resolver.resolve(ResourceType.APPLICATION, "app");

    assertThat(source.fetched).containsExactly("app", "app");
  }

  @Test
  @DisplayName("resolves without a bound request (e.g. background threads), simply unmemoized")
  void resolvesWithoutARequest() {
    RecordingSource source = new RecordingSource(Map.of("app", record("app", "team-a")));
    RemoteApplicationAclResolver resolver = resolver(source);

    assertThat(resolver.resolve(ResourceType.APPLICATION, "app")).isNotNull();
    assertThat(resolver.resolve(ResourceType.APPLICATION, "app")).isNotNull();

    assertThat(source.fetched).containsExactly("app", "app");
  }

  @Test
  @DisplayName("non-application resource types are not resolved here")
  void ignoresOtherResourceTypes() {
    RecordingSource source = new RecordingSource(Map.of());

    assertThat(resolver(source).resolve(ResourceType.ACCOUNT, "prod")).isNull();
    assertThat(source.fetched).isEmpty();
  }
}
