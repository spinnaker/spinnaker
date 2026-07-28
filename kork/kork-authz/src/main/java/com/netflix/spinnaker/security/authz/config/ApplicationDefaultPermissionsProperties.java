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

package com.netflix.spinnaker.security.authz.config;

import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global default application permissions, bound from the {@code authz.application} property prefix.
 *
 * <p>This is the owner-local replacement for the legacy Fiat {@code aggregate} application
 * permission provider plus its {@code prefix} {@code ResourcePermissionSource} configured for the
 * {@code "*"} prefix — i.e. a single set of role grants per {@link Authorization} that applies to
 * <em>every</em> application. The roles configured here are <strong>additively merged</strong>
 * (union per authorization) onto each application's own embedded ACL by the resource owner
 * (Front50), so:
 *
 * <ul>
 *   <li>{@code READ}/{@code WRITE}/{@code EXECUTE} roles are granted on every application in
 *       addition to whatever the application's own ACL grants (see {@link Permissions#merge}); and
 *   <li>{@code CREATE} roles govern who may create new applications (enforced where the create
 *       decision is made, since an application has no resource-level ACL before it exists).
 * </ul>
 *
 * <p>The feature is inert when unset/empty: an empty {@link #toPermissions()} is unrestricted and a
 * merge with it is a no-op, and {@link #getCreateRoles()} is empty so creation stays permissive.
 *
 * <p>Example:
 *
 * <pre>{@code
 * authz:
 *   application:
 *     default-permissions:
 *       READ:    ["tf-sg - spinnaker service accounts"]
 *       WRITE:   ["tf-sg - spinnaker service accounts"]
 *       EXECUTE: ["tf-sg - spinnaker service accounts"]
 *       CREATE:  ["tf-sg - spinnaker service accounts"]
 * }</pre>
 *
 * <p>This lives in {@code kork-authz} so any service that resolves or embeds application ACLs can
 * reuse the same configuration and merge semantics, keeping a single source of truth. Front50, the
 * authoritative owner of application ACLs, performs the actual merge.
 */
@ConfigurationProperties("authz.application")
public class ApplicationDefaultPermissionsProperties {

  /**
   * Map of {@link Authorization} to the role names granted that authorization on every application.
   * Bound from {@code authz.application.default-permissions}.
   */
  private Map<Authorization, Set<String>> defaultPermissions = new LinkedHashMap<>();

  public Map<Authorization, Set<String>> getDefaultPermissions() {
    return defaultPermissions;
  }

  public void setDefaultPermissions(Map<Authorization, Set<String>> defaultPermissions) {
    this.defaultPermissions =
        defaultPermissions == null ? new LinkedHashMap<>() : defaultPermissions;
  }

  /**
   * The configured global defaults as an immutable, sanitized {@link Permissions} (role names
   * trimmed and lower-cased). Returns {@link Permissions#EMPTY} (unrestricted) when nothing is
   * configured, which makes a merge a no-op.
   */
  public Permissions toPermissions() {
    return new Permissions.Builder().set(defaultPermissions).build();
  }

  /**
   * The (sanitized, lower-cased) set of roles permitted to create applications, i.e. the {@code
   * CREATE} entry of the global defaults. Empty when unconfigured, in which case creation is left
   * permissive by the enforcement point.
   */
  public Set<String> getCreateRoles() {
    return toPermissions().get(Authorization.CREATE);
  }
}
