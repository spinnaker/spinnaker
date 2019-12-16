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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.netflix.spinnaker.fiat.model.Authorization;
import java.util.*;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.val;

/**
 * Representation of authorization configuration for a resource. This object is immutable, which
 * makes it challenging when working with Jackson's {@code ObjectMapper} and Spring's
 * {@code @ConfigurationProperties}. The {@link Builder} is a helper class for the latter use case.
 */
@ToString
@EqualsAndHashCode
public class Permissions {

  public static final Permissions EMPTY = Builder.fromMap(Collections.emptyMap());

  private final Map<Authorization, List<String>> permissions;

  private Permissions(Map<Authorization, List<String>> p) {
    this.permissions = Collections.unmodifiableMap(p);
  }

  /**
   * Specifically here for Jackson deserialization. Sends data through the {@link Builder} in order
   * to sanitize the input data (just in case).
   */
  @JsonCreator
  public static Permissions factory(Map<Authorization, List<String>> data) {
    return new Builder().set(data).build();
  }

  /** Here specifically for Jackson serialization. */
  @JsonValue
  private Map<Authorization, List<String>> getPermissions() {
    return permissions;
  }

  public Set<String> allGroups() {
    return permissions.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
  }

  /**
   * Determines whether this Permissions has any Authorizations with associated roles.
   *
   * @return whether this Permissions has any Authorizations with associated roles
   * @deprecated check {@code !isRestricted()} instead
   */
  @Deprecated
  public boolean isEmpty() {
    return !isRestricted();
  }

  public boolean isRestricted() {
    return this.permissions.values().stream().anyMatch(groups -> !groups.isEmpty());
  }

  public boolean isAuthorized(Set<Role> userRoles) {
    return !getAuthorizations(userRoles).isEmpty();
  }

  public Set<Authorization> getAuthorizations(Set<Role> userRoles) {
    val r = userRoles.stream().map(Role::getName).collect(Collectors.toList());
    return getAuthorizations(r);
  }

  public Set<Authorization> getAuthorizations(List<String> userRoles) {
    if (!isRestricted()) {
      return Authorization.ALL;
    }

    return this.permissions.entrySet().stream()
        .filter(entry -> !Collections.disjoint(entry.getValue(), userRoles))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  public List<String> get(Authorization a) {
    return permissions.getOrDefault(a, new ArrayList<>());
  }

  public Map<Authorization, List<String>> unpack() {
    return Arrays.stream(Authorization.values()).collect(toMap(identity(), this::get));
  }

  /**
   * This is a helper class for setting up an immutable Permissions object. It also acts as the
   * target Java Object for Spring's ConfigurationProperties deserialization.
   *
   * <p>Objects should be defined on the account config like:
   *
   * <p>someRoot: name: resourceName permissions: read: - role1 - role2 write: - role1
   *
   * <p>Group/Role names are trimmed of whitespace and lowercased.
   */
  public static class Builder extends LinkedHashMap<Authorization, List<String>> {

    private static Permissions fromMap(Map<Authorization, List<String>> authConfig) {
      final Map<Authorization, List<String>> perms = new EnumMap<>(Authorization.class);
      for (Authorization auth : Authorization.values()) {
        Optional.ofNullable(authConfig.get(auth))
            .map(
                groups ->
                    groups.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .collect(Collectors.toList()))
            .filter(g -> !g.isEmpty())
            .map(Collections::unmodifiableList)
            .ifPresent(roles -> perms.put(auth, roles));
      }
      return new Permissions(perms);
    }

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
      final Permissions result = fromMap(this);
      if (!result.isRestricted()) {
        return Permissions.EMPTY;
      }
      return result;
    }
  }
}
