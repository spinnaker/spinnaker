/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.model.resources;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.fiat.model.Authorization;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.val;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Representation of authorization configuration for a resource. This object is immutable, which
 * makes it challenging when working with Jackson's
 * {@link com.fasterxml.jackson.databind.ObjectMapper} and Spring's {@link ConfigurationProperties}.
 * The {@link Builder} is a helper class for the latter use case.
 */
@ToString
@EqualsAndHashCode
public class Permissions {

  public static Permissions EMPTY = new Permissions.Builder().build();
  private static Set<Authorization> UNRESTRICTED_AUTH = ImmutableSet.copyOf(Authorization.values());

  private final Map<Authorization, List<String>> permissions;

  private Permissions(Map<Authorization, List<String>> p) {
    this.permissions = p;
  }

  /**
   * Specifically here for Jackson deserialization. Sends data through the {@link Builder} in order
   * to sanitize the input data (just in case).
   */
  @JsonCreator
  public static Permissions factory(Map<Authorization, List<String>> data) {
    return new Builder().set(data).build();
  }

  /**
   * Here specifically for Jackson serialization.
   */
  @JsonValue
  private Map<Authorization, List<String>> getPermissions() {
    return permissions;
  }

  public Set<String> allGroups() {
    return permissions.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
  }

  public boolean isRestricted() {
    return this.permissions.values().stream().anyMatch(groups -> !groups.isEmpty());
  }

  public boolean isAuthorized(Set<Role> userRoles) {
    return !getAuthorizations(userRoles).isEmpty();
  }

  public boolean isEmpty() {
    return permissions.isEmpty();
  }

  public Set<Authorization> getAuthorizations(Set<Role> userRoles) {
    val r = userRoles.stream().map(Role::getName).collect(Collectors.toList());
    return getAuthorizations(r);
  }

  public Set<Authorization> getAuthorizations(List<String> userRoles) {
    if (!isRestricted()) {
      return UNRESTRICTED_AUTH;
    }

    return this.permissions
               .entrySet()
               .stream()
               .filter(entry -> !Collections.disjoint(entry.getValue(), userRoles))
               .map(Map.Entry::getKey)
               .collect(Collectors.toSet());
  }

  public List<String> get(Authorization a) {
    return permissions.get(a);
  }


  /**
   * This is a helper class for setting up an immutable Permissions object. It also acts as the
   * target Java Object for Spring's ConfigurationProperties deserialization.
   *
   * Objects should be defined on the account config like:
   *
   * someRoot:
   *   name: resourceName
   *   permissions:
   *     read:
   *     - role1
   *     - role2
   *     write:
   *     - role1
   *
   * Group/Role names are trimmed of whitespace and lowercased.
   */
  public static class Builder extends LinkedHashMap<Authorization, List<String>> {

    @JsonCreator
    public static Builder factory(Map<Authorization, List<String>> data) {
      return new Builder().set(data);
    }

    public Builder set(Map<Authorization, List<String>> p) {
      this.clear();
      this.putAll(p);
      return this;
    }

    public Builder add(Authorization a, String group) {
      this.computeIfAbsent(a, ignored -> new ArrayList<>()).add(group);
      return this;
    }

    public Builder add(Authorization a, List<String> groups) {
      groups.forEach(group -> add(a, group));
      return this;
    }

    public Permissions build() {
      ImmutableMap.Builder<Authorization, List<String>> builder = ImmutableMap.builder();
      this.forEach((auth, groups) -> {
        List<String> lowerGroups = groups.stream()
                                         .map(String::trim)
                                         .map(String::toLowerCase)
                                         .collect(Collectors.toList());
        builder.put(auth, ImmutableList.copyOf(lowerGroups));
      });
      return new Permissions(builder.build());
    }
  }
}
