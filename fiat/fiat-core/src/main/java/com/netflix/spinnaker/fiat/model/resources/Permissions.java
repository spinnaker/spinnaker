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
import lombok.val;
import org.springframework.security.core.GrantedAuthority;

/**
 * Representation of authorization configuration for a resource. This object is immutable, which
 * makes it challenging when working with Jackson's {@code ObjectMapper} and Spring's
 * {@code @ConfigurationProperties}. The {@link Builder} is a helper class for the latter use case.
 */
public class Permissions {

  public static final Permissions EMPTY = Builder.fromMap(Collections.emptyMap());

  private final Map<Authorization, Set<String>> permissions;

  private final int hashCode;

  private Permissions(Map<Authorization, Set<String>> p) {
    this.permissions = Collections.unmodifiableMap(p);
    this.hashCode = Objects.hash(this.permissions);
  }

  /**
   * Specifically here for Jackson deserialization. Sends data through the {@link Builder} in order
   * to sanitize the input data (just in case).
   */
  @JsonCreator
  public static Permissions factory(Map<Authorization, Set<String>> data) {
    return new Builder().set(data).build();
  }

  /** Here specifically for Jackson serialization. */
  @JsonValue
  private Map<Authorization, Set<String>> getPermissions() {
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
    val r = userRoles.stream().map(Role::getName).collect(Collectors.toSet());
    return getAuthorizationsFromRoles(r);
  }

  public Set<Authorization> getAuthorizations(List<String> userRoles) {
    return getAuthorizationsFromRoles(new LinkedHashSet<>(userRoles));
  }

  public Set<Authorization> getAuthorizations(
      Collection<? extends GrantedAuthority> userAuthorities) {
    Set<String> userRoles =
        userAuthorities.stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring("ROLE_".length()))
            .collect(Collectors.toSet());
    return getAuthorizationsFromRoles(userRoles);
  }

  private Set<Authorization> getAuthorizationsFromRoles(Set<String> userRoles) {
    if (!isRestricted()) {
      return Authorization.ALL;
    }

    return this.permissions.entrySet().stream()
        .filter(entry -> !Collections.disjoint(entry.getValue(), userRoles))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  public Set<String> get(Authorization a) {
    return permissions.getOrDefault(a, new HashSet<>());
  }

  public Map<Authorization, Set<String>> unpack() {
    return Arrays.stream(Authorization.values()).collect(toMap(identity(), this::get));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Permissions that = (Permissions) o;
    return Objects.equals(this.permissions, that.permissions);
  }

  public int hashCode() {
    return hashCode;
  }

  public String toString() {
    return "Permissions(permissions=" + this.getPermissions() + ")";
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
  public static class Builder extends LinkedHashMap<Authorization, Set<String>> {

    private static Permissions fromMap(Map<Authorization, Set<String>> authConfig) {
      final Map<Authorization, Set<String>> perms = new EnumMap<>(Authorization.class);
      for (Authorization auth : Authorization.values()) {
        Optional.ofNullable(authConfig.get(auth))
            .map(
                groups ->
                    groups.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet()))
            .filter(g -> !g.isEmpty())
            .map(Collections::unmodifiableSet)
            .ifPresent(roles -> perms.put(auth, roles));
      }
      return new Permissions(perms);
    }

    @JsonCreator
    public static Builder factory(Map<Authorization, Set<String>> data) {
      return new Builder().set(data);
    }

    public Builder set(Map<Authorization, Set<String>> p) {
      this.clear();
      this.putAll(p);
      return this;
    }

    public Builder add(Authorization a, String group) {
      this.computeIfAbsent(a, ignored -> new LinkedHashSet<>()).add(group);
      return this;
    }

    public Builder add(Authorization a, Set<String> groups) {
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
