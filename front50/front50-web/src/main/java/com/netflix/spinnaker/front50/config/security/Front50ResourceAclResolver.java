/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.front50.config.security;

import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.ResourceType;
import com.netflix.spinnaker.security.authz.config.ApplicationDefaultPermissionsProperties;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owner-local {@link ResourceAclResolver} for Front50. Front50 is the authoritative owner of {@code
 * application} and managed {@code service_account} ACLs, so it resolves them straight from its own
 * stores ({@link ApplicationPermissionDAO}, {@link ServiceAccountDAO}) — never from a remote or
 * cached copy of another service's data.
 *
 * <ul>
 *   <li>{@code application} → the {@link Application.Permission} embedded {@link Permissions} ACL.
 *   <li>{@code service_account} → a synthetic ACL whose groups are the SA's {@code memberOf} roles,
 *       so the PDP's "may assume the SA when the caller shares one of its roles" rule applies.
 * </ul>
 *
 * Returning {@code null} signals "no ACL resolvable here" (unknown/absent resource), which the
 * enclosing evaluator handles via {@code allowAccessToUnknownApplications}.
 */
public class Front50ResourceAclResolver implements ResourceAclResolver {

  private static final Logger log = LoggerFactory.getLogger(Front50ResourceAclResolver.class);

  private final Optional<ApplicationPermissionDAO> applicationPermissionDAO;
  private final Optional<ServiceAccountDAO> serviceAccountDAO;
  private final ApplicationDefaultPermissionsProperties applicationDefaultPermissions;

  public Front50ResourceAclResolver(
      Optional<ApplicationPermissionDAO> applicationPermissionDAO,
      Optional<ServiceAccountDAO> serviceAccountDAO,
      ApplicationDefaultPermissionsProperties applicationDefaultPermissions) {
    this.applicationPermissionDAO = applicationPermissionDAO;
    this.serviceAccountDAO = serviceAccountDAO;
    this.applicationDefaultPermissions = applicationDefaultPermissions;
  }

  @Nullable
  @Override
  public Permissions resolve(ResourceType resourceType, String resourceName) {
    if (resourceName == null) {
      return null;
    }
    if (ResourceType.APPLICATION.equals(resourceType)) {
      return resolveApplication(resourceName);
    }
    if (ResourceType.SERVICE_ACCOUNT.equals(resourceType)) {
      return resolveServiceAccount(resourceName);
    }
    return null;
  }

  @Nullable
  private Permissions resolveApplication(String name) {
    Permissions ownAcl = lookupApplicationAcl(name);
    Permissions defaults = applicationDefaultPermissions.toPermissions();
    if (!defaults.isRestricted()) {
      // Feature inert: behave exactly as before — return the app's own ACL (possibly null).
      return ownAcl;
    }
    // Global defaults apply to every application (the owner-local equivalent of the legacy
    // aggregate + prefix("*") provider), so even an app with no own permission record resolves to
    // a restricted ACL of the default roles rather than falling through to
    // allowAccessToUnknownApplications.
    return defaults.merge(ownAcl == null ? Permissions.EMPTY : ownAcl);
  }

  @Nullable
  private Permissions lookupApplicationAcl(String name) {
    if (applicationPermissionDAO.isEmpty()) {
      return null;
    }
    try {
      Application.Permission permission = applicationPermissionDAO.get().findById(name);
      return permission == null ? null : permission.getPermissions();
    } catch (Exception e) {
      // Unknown application (or no permission record): let the evaluator apply
      // allowAccessToUnknownApplications (when no global defaults are configured).
      log.debug("No application permission record for '{}'", name);
      return null;
    }
  }

  @Nullable
  private Permissions resolveServiceAccount(String name) {
    if (serviceAccountDAO.isEmpty()) {
      return null;
    }
    try {
      ServiceAccount serviceAccount = serviceAccountDAO.get().findById(name);
      if (serviceAccount == null) {
        return null;
      }
      List<String> memberOf = serviceAccount.getMemberOf();
      if (memberOf == null || memberOf.isEmpty()) {
        // An SA with no roles is unrestricted: anyone may assume it
        // ("no permissions == everyone can access").
        return Permissions.EMPTY;
      }
      Permissions.Builder builder = new Permissions.Builder();
      builder.add(Authorization.READ, new java.util.LinkedHashSet<>(memberOf));
      return builder.build();
    } catch (Exception e) {
      log.debug("No service account record for '{}'", name);
      return null;
    }
  }
}
