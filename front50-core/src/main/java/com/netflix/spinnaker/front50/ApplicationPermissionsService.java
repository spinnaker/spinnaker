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

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.front50.config.FiatConfigurationProperties;
import com.netflix.spinnaker.front50.events.ApplicationPermissionEventListener;
import com.netflix.spinnaker.front50.events.ApplicationPermissionEventListener.Type;
import com.netflix.spinnaker.front50.model.application.Application.Permission;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
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
import retrofit.RetrofitError;

/** Wraps the business logic around Application Permissions. */
@Component
public class ApplicationPermissionsService {

  private static final Logger log = LoggerFactory.getLogger(ApplicationPermissionsService.class);

  private final ApplicationDAO applicationDAO;
  private final Optional<FiatService> fiatService;
  private final Optional<ApplicationPermissionDAO> applicationPermissionDAO;
  private final FiatConfigurationProperties fiatConfigurationProperties;
  private final FiatClientConfigurationProperties fiatClientConfigurationProperties;
  private final Collection<ApplicationPermissionEventListener> applicationPermissionEventListeners;

  public ApplicationPermissionsService(
      ApplicationDAO applicationDAO,
      Optional<FiatService> fiatService,
      Optional<ApplicationPermissionDAO> applicationPermissionDAO,
      FiatConfigurationProperties fiatConfigurationProperties,
      FiatClientConfigurationProperties fiatClientConfigurationProperties,
      Collection<ApplicationPermissionEventListener> applicationPermissionEventListeners) {
    this.applicationDAO = applicationDAO;
    this.fiatService = fiatService;
    this.applicationPermissionDAO = applicationPermissionDAO;
    this.fiatConfigurationProperties = fiatConfigurationProperties;
    this.fiatClientConfigurationProperties = fiatClientConfigurationProperties;
    this.applicationPermissionEventListeners = applicationPermissionEventListeners;
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

  public Permission getApplicationPermission(@Nonnull String appName) {
    return applicationPermissionDAO().findById(appName);
  }

  public Permission createApplicationPermission(@Nonnull Permission newPermission) {
    return performWrite(
        supportingEventListeners(Type.PRE_CREATE),
        supportingEventListeners(Type.POST_CREATE),
        (unused, newPerm) -> {
          Permission perm = applicationPermissionDAO().create(newPerm.getId(), newPerm);
          syncUsers(perm, null);
          return perm;
        },
        null,
        newPermission);
  }

  public Permission updateApplicationPermission(
      @Nonnull String appName, @Nonnull Permission newPermission, boolean skipListeners) {
    if (skipListeners) {
      return update(appName, newPermission);
    }
    return performWrite(
        supportingEventListeners(Type.PRE_UPDATE),
        supportingEventListeners(Type.POST_UPDATE),
        (unused, newPerm) -> update(appName, newPerm),
        null,
        newPermission);
  }

  private Permission update(@Nonnull String appName, @Nonnull Permission newPermission) {
    try {
      Permission oldPerm = applicationPermissionDAO().findById(appName);
      applicationPermissionDAO().update(appName, newPermission);
      syncUsers(newPermission, oldPerm);
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
          syncUsers(null, oldPerm);
          return newPerm;
        },
        oldPerm,
        null);
  }

  private void syncUsers(Permission newPermission, Permission oldPermission) {
    if (!fiatClientConfigurationProperties.isEnabled() || !fiatService.isPresent()) {
      return;
    }

    // Specifically using an empty list here instead of null, because an empty list will update
    // the anonymous user's app list.
    Set<String> roles = new HashSet<>();

    Optional.ofNullable(newPermission)
        .ifPresent(
            newPerm -> {
              Permissions permissions = newPerm.getPermissions();
              if (permissions != null && permissions.isRestricted()) {
                roles.addAll(permissions.allGroups());
              }
            });

    Optional.ofNullable(oldPermission)
        .ifPresent(
            oldPerm -> {
              Permissions permissions = oldPerm.getPermissions();
              if (permissions != null && permissions.isRestricted()) {
                roles.addAll(permissions.allGroups());
              }
            });

    if (fiatConfigurationProperties.getRoleSync().isEnabled()) {
      try {
        fiatService.get().sync(new ArrayList<>(roles));
      } catch (RetrofitError e) {
        log.warn("Error syncing users", e);
      }
    }
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
