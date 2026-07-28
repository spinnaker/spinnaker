/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.front50;

import com.netflix.spinnaker.front50.events.ApplicationPermissionEventListener;
import com.netflix.spinnaker.front50.events.ApplicationPermissionEventListener.Type;
import com.netflix.spinnaker.front50.model.application.Application.Permission;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.config.ApplicationDefaultPermissionsProperties;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Wraps the business logic around Application Permissions. */
@Component
public class ApplicationPermissionsService {

  private static final Logger log = LoggerFactory.getLogger(ApplicationPermissionsService.class);

  private final ApplicationDAO applicationDAO;
  private final Optional<ApplicationPermissionDAO> applicationPermissionDAO;
  private final Collection<ApplicationPermissionEventListener> applicationPermissionEventListeners;
  private final ApplicationDefaultPermissionsProperties applicationDefaultPermissions;

  public ApplicationPermissionsService(
      ApplicationDAO applicationDAO,
      Optional<ApplicationPermissionDAO> applicationPermissionDAO,
      Collection<ApplicationPermissionEventListener> applicationPermissionEventListeners,
      ApplicationDefaultPermissionsProperties applicationDefaultPermissions) {
    this.applicationDAO = applicationDAO;
    this.applicationPermissionDAO = applicationPermissionDAO;
    this.applicationPermissionEventListeners = applicationPermissionEventListeners;
    this.applicationDefaultPermissions = applicationDefaultPermissions;
  }

  public Set<Permission> getAllApplicationPermissions() {
    Map<String, Permission> actualPermissions =
        applicationPermissionDAO().all().stream()
            .map(permission -> new SimpleEntry<>(permission.getName().toLowerCase(), permission))
            .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

    applicationDAO.all().stream()
        .filter(app -> !actualPermissions.containsKey(app.getName().toLowerCase()))
        .forEach(
            app -> {
              Permission p = new Permission();
              p.setName(app.getName());
              p.setLastModified(-1L);
              p.setLastModifiedBy("auto-generated");
              actualPermissions.put(app.getName().toLowerCase(), p);
            });

    return new HashSet<>(actualPermissions.values());
  }

  /**
   * The grants every application receives regardless of its own ACL. Exposed so an editor can show
   * them as inherited — including when creating an application, where there is no record to read
   * them from yet.
   */
  public Permissions getDefaultApplicationPermissions() {
    return applicationDefaultPermissions.toPermissions();
  }

  /** The application's own stored grants, exactly as persisted. */
  public Permission getApplicationPermission(@Nonnull String appName) {
    return getApplicationPermission(appName, false);
  }

  /**
   * @param effective when true, return the ACL that authorization decisions must be made against —
   *     the application's own grants with the global default application permissions merged in.
   *     Front50 is the authoritative owner of both halves, so serving the effective ACL here keeps
   *     {@code authz.application.default-permissions} configured in exactly one service; consumers
   *     that resolve application ACLs remotely no longer need a copy of it to reach the same
   *     decision Front50 would.
   */
  public Permission getApplicationPermission(@Nonnull String appName, boolean effective) {
    if (!effective) {
      return applicationPermissionDAO().findById(appName);
    }

    Permissions defaults = applicationDefaultPermissions.toPermissions();
    Permission stored;
    try {
      stored = applicationPermissionDAO().findById(appName);
    } catch (NotFoundException e) {
      // No ACL of its own. If defaults are configured the application is still restricted to them,
      // so answer with those; otherwise there is genuinely nothing here and the caller's
      // unknown-application handling should decide.
      if (!defaults.isRestricted()) {
        throw e;
      }
      Permission defaulted = new Permission();
      defaulted.setName(appName);
      defaulted.setPermissions(defaults);
      return defaulted;
    }

    Permission result = stored.copy();
    result.setPermissions(defaults.merge(stored.getPermissions()));
    return result;
  }

  public Permission createApplicationPermission(@Nonnull Permission newPermission) {
    return performWrite(
        supportingEventListeners(Type.PRE_CREATE),
        supportingEventListeners(Type.POST_CREATE),
        (unused, newPerm) -> applicationPermissionDAO().create(newPerm.getId(), newPerm),
        null,
        storableGrants(newPermission));
  }

  public Permission updateApplicationPermission(
      @Nonnull String appName, @Nonnull Permission newPermission, boolean skipListeners) {
    Permission storable = storableGrants(newPermission);
    if (skipListeners) {
      return update(appName, storable);
    }
    return performWrite(
        supportingEventListeners(Type.PRE_UPDATE),
        supportingEventListeners(Type.POST_UPDATE),
        (unused, newPerm) -> update(appName, newPerm),
        null,
        storable);
  }

  /**
   * Strips the global default application permissions from an incoming ACL so only the
   * application's own grants are persisted.
   *
   * <p>Clients read the effective ACL (own grants plus defaults) and submit the whole list back on
   * save — Deck's application edit form does exactly this, and it posts the permissions it loaded
   * whether or not the user touched them. Without this, every save would bake the defaults into the
   * application's own record, where they could no longer be told apart from a deliberate grant and
   * would survive being removed from the defaults.
   */
  private Permission storableGrants(@Nonnull Permission permission) {
    Permissions defaults = applicationDefaultPermissions.toPermissions();
    if (!defaults.isRestricted() || permission.getPermissions() == null) {
      return permission;
    }
    Permission stripped = permission.copy();
    stripped.setPermissions(permission.getPermissions().subtract(defaults));
    return stripped;
  }

  private Permission update(@Nonnull String appName, @Nonnull Permission newPermission) {
    try {
      applicationPermissionDAO().findById(appName);
      applicationPermissionDAO().update(appName, newPermission);
    } catch (NotFoundException e) {
      createApplicationPermission(newPermission);
    }
    return newPermission;
  }

  public void deleteApplicationPermission(@Nonnull String appName) {
    Permission oldPerm;
    try {
      oldPerm = applicationPermissionDAO().findById(appName);
    } catch (NotFoundException e) {
      // Nothing to see here, we're all done already.
      return;
    }

    performWrite(
        supportingEventListeners(Type.PRE_DELETE),
        supportingEventListeners(Type.POST_DELETE),
        (unused, newPerm) -> {
          applicationPermissionDAO().delete(appName);
          return newPerm;
        },
        oldPerm,
        null);
  }

  private ApplicationPermissionDAO applicationPermissionDAO() {
    if (!applicationPermissionDAO.isPresent()) {
      throw new SystemException(
          "Configured storage service does not support application permissions");
    }
    return applicationPermissionDAO.get();
  }

  private Permission performWrite(
      @Nonnull List<ApplicationPermissionEventListener> preEventListeners,
      @Nonnull List<ApplicationPermissionEventListener> postEventListeners,
      @Nonnull BiFunction<Permission, Permission, Permission> action,
      @Nullable Permission originalPermission,
      @Nullable Permission updatedPermission) {

    try {
      for (ApplicationPermissionEventListener preEventListener : preEventListeners) {
        updatedPermission =
            preEventListener.call(
                (originalPermission == null) ? null : originalPermission.copy(),
                (updatedPermission == null) ? null : updatedPermission.copy());
      }

      updatedPermission =
          action.apply(
              (originalPermission == null) ? null : originalPermission.copy(),
              (updatedPermission == null) ? null : updatedPermission.copy());

      for (ApplicationPermissionEventListener postEventListener : postEventListeners) {
        updatedPermission =
            postEventListener.call(
                (originalPermission == null) ? null : originalPermission.copy(),
                (updatedPermission == null) ? null : updatedPermission.copy());
      }

      return updatedPermission;
    } catch (Exception e) {
      String name =
          (originalPermission == null)
              ? (updatedPermission == null) ? "unknown" : updatedPermission.getName()
              : originalPermission.getName();
      log.error("Failed to perform action (name: {})", name, e);
      throw e;
    }
  }

  private List<ApplicationPermissionEventListener> supportingEventListeners(Type type) {
    return applicationPermissionEventListeners.stream()
        .filter(it -> it.supports(type))
        .collect(Collectors.toList());
  }
}
