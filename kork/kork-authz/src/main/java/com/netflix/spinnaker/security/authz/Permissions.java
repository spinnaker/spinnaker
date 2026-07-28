/*
 * Copyright 2026 Netflix, Inc.
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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.security.core.GrantedAuthority;

/**
 * Representation of authorization configuration for a resource (the resource's embedded ACL). This
 * object is immutable.
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

  public boolean isRestricted() {
    return this.permissions.values().stream().anyMatch(groups -> !groups.isEmpty());
  }

  public boolean isAuthorized(Set<Role> userRoles) {
    return !getAuthorizations(userRoles).isEmpty();
  }

  public Set<Authorization> getAuthorizations(Set<Role> userRoles) {
    Set<String> r = userRoles.stream().map(Role::getName).collect(Collectors.toSet());
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

    Set<String> normalized =
        userRoles.stream().map(r -> r.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    return this.permissions.entrySet().stream()
        .filter(entry -> !Collections.disjoint(entry.getValue(), normalized))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  public Set<String> get(Authorization a) {
    return permissions.getOrDefault(a, new HashSet<>());
  }

  /**
   * Additively merge this ACL with {@code other}, producing a new immutable {@link Permissions}
   * whose roles, for each {@link Authorization}, are the union of the two operands' roles. This is
   * the building block for applying global default application permissions on top of an
   * application's own embedded ACL (the owner-local equivalent of the legacy {@code
   * aggregate}+{@code prefix("*")} permission provider, which additively merged default permissions
   * onto every application's effective ACL).
   *
   * <p>Merge is commutative and treats an unrestricted ({@link #EMPTY}) operand as contributing no
   * roles, so {@code EMPTY.merge(x)} and {@code x.merge(EMPTY)} both yield {@code x}. If both
   * operands are unrestricted the result is {@link #EMPTY} (still unrestricted).
   *
   * @param other the ACL to union into this one; {@code null} is treated as {@link #EMPTY}
   * @return a new {@link Permissions} containing the per-authorization union of roles
   */
  public Permissions merge(@Nullable Permissions other) {
    if (other == null) {
      return this;
    }
    Builder builder = new Builder();
    for (Authorization a : Authorization.values()) {
      builder.add(a, this.get(a));
      builder.add(a, other.get(a));
    }
    return builder.build();
  }

  /**
   * The inverse of {@link #merge}: remove {@code other}'s roles, per {@link Authorization}, from
   * this ACL.
   *
   * <p>This is what lets a resource store only the grants that are its own. Consumers are served
   * the <em>effective</em> ACL (the resource's own grants merged with the global default
   * application permissions), so a client that reads an ACL, edits one row and writes the whole
   * list back would otherwise persist the defaults as if they had been granted explicitly —
   * indistinguishable thereafter from a deliberate grant, and no longer revocable by changing the
   * defaults. Subtracting the defaults on write keeps "stored" meaning "own grants only".
   *
   * @param other the ACL to remove; {@code null} or unrestricted removes nothing
   * @return a new {@link Permissions} containing the per-authorization difference
   */
  public Permissions subtract(@Nullable Permissions other) {
    if (other == null || !other.isRestricted()) {
      return this;
    }
    Builder builder = new Builder();
    for (Authorization a : Authorization.values()) {
      Set<String> remaining = new HashSet<>(this.get(a));
      remaining.removeAll(other.get(a));
      builder.add(a, remaining);
    }
    return builder.build();
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

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    return "Permissions(permissions=" + this.getPermissions() + ")";
  }

  /**
   * Helper for setting up an immutable {@link Permissions} object. Also acts as the target Java
   * object for Spring's {@code @ConfigurationProperties} deserialization. Group/role names are
   * trimmed of whitespace and lower-cased.
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
                        .map(s -> s.toLowerCase(Locale.ROOT))
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
