/*
 * Copyright 2026 Spinnaker.io, Inc.
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

package com.netflix.spinnaker.fiat.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Application;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourcePrefixPermissionSourceTest {

  private static ResourcePrefixPermissionSource.PrefixEntry<Application> entry(
      String prefix, String role) {
    return new ResourcePrefixPermissionSource.PrefixEntry<Application>()
        .setPrefix(prefix)
        .setPermissions(Map.of(Authorization.READ, Set.of(role)));
  }

  private static ResourcePrefixPermissionSource<Application> mostSpecific(
      List<ResourcePrefixPermissionSource.PrefixEntry<Application>> prefixes) {
    ResourcePrefixPermissionSource<Application> source = new ResourcePrefixPermissionSource<>();
    source.setPrefixes(prefixes);
    source.setResolutionStrategy(ResourcePrefixPermissionSource.ResolutionStrategy.MOST_SPECIFIC);
    return source;
  }

  private static Set<String> readRoles(
      ResourcePrefixPermissionSource<Application> source, String appName) {
    return source.getPermissions(new Application().setName(appName)).get(Authorization.READ);
  }

  /**
   * A full application name is more specific than a wildcard whose stem is that same name, so it
   * has to win no matter how the two entries were ordered in the configured prefix list.
   */
  @Test
  void mostSpecificMatchDoesNotDependOnTheConfiguredPrefixOrder() {
    var wildcardFirst =
        mostSpecific(List.of(entry("foo*", "wildcard-role"), entry("foo", "exact-role")));
    var exactFirst =
        mostSpecific(List.of(entry("foo", "exact-role"), entry("foo*", "wildcard-role")));

    assertEquals(Set.of("exact-role"), readRoles(wildcardFirst, "foo"));
    assertEquals(Set.of("exact-role"), readRoles(exactFirst, "foo"));
  }

  @Test
  void fullApplicationNameWinsOverEveryMatchingWildcard() {
    var source =
        mostSpecific(
            List.of(
                entry("*", "catch-all-role"),
                entry("gotham*", "wildcard-role"),
                entry("GOTHAM-JOKER", "exact-role")));

    assertEquals(Set.of("exact-role"), readRoles(source, "gotham-joker"));
    assertEquals(Set.of("wildcard-role"), readRoles(source, "gotham-criminals"));
    assertEquals(Set.of("catch-all-role"), readRoles(source, "metropolis"));
  }

  @Test
  void longestWildcardWinsAmongWildcards() {
    var source =
        mostSpecific(
            List.of(
                entry("*", "catch-all-role"),
                entry("foo*", "short-role"),
                entry("foo-bar*", "long-role")));

    assertEquals(Set.of("long-role"), readRoles(source, "foo-bar-baz"));
    assertEquals(Set.of("short-role"), readRoles(source, "foo-qux"));
  }
}
