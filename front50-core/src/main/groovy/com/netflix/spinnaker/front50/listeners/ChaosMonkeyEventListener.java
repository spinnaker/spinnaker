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
package com.netflix.spinnaker.front50.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.front50.ApplicationPermissionsService;
import com.netflix.spinnaker.front50.config.ChaosMonkeyEventListenerConfigurationProperties;
import com.netflix.spinnaker.front50.events.ApplicationEventListener;
import com.netflix.spinnaker.front50.events.ApplicationPermissionEventListener;
import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ensures that when Chaos Monkey is enabled (or disabled) on an Application, its permissions are
 * applied correctly.
 *
 * <p>This listens on both Application events.
 */
@Component
public class ChaosMonkeyEventListener
    implements ApplicationEventListener, ApplicationPermissionEventListener {
  private static final Logger log = LoggerFactory.getLogger(ChaosMonkeyEventListener.class);

  private final ApplicationDAO applicationDAO;
  private final ApplicationPermissionsService applicationPermissionsService;
  private final ChaosMonkeyEventListenerConfigurationProperties properties;
  private final ObjectMapper objectMapper;

  public ChaosMonkeyEventListener(
      ApplicationDAO applicationDAO,
      ApplicationPermissionsService applicationPermissionsService,
      ChaosMonkeyEventListenerConfigurationProperties properties,
      ObjectMapper objectMapper) {
    this.applicationDAO = applicationDAO;
    this.applicationPermissionsService = applicationPermissionsService;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(ApplicationEventListener.Type type) {
    return properties.isEnabled() && ApplicationEventListener.Type.PRE_UPDATE == type;
  }

  @Override
  public Application call(Application originalApplication, Application updatedApplication) {
    Application.Permission permission =
        applicationPermissionsService.getApplicationPermission(updatedApplication.getName());

    if (!permission.getPermissions().isRestricted()) {
      return updatedApplication;
    }

    if (isChaosMonkeyEnabled(updatedApplication)) {
      applyNewPermissions(permission, true);
    } else {
      applyNewPermissions(permission, false);
    }

    Application.Permission updatedPermission =
        applicationPermissionsService.updateApplicationPermission(
            updatedApplication.getName(), permission, true);

    log.debug(
        "Updated application `{}` with permissions `{}`",
        updatedApplication.getName(),
        updatedPermission.getPermissions().toString());

    return updatedApplication;
  }

  @Override
  public void rollback(Application originalApplication) {
    // Do nothing.
  }

  @Override
  public boolean supports(ApplicationPermissionEventListener.Type type) {
    return properties.isEnabled()
        && Arrays.asList(
                ApplicationPermissionEventListener.Type.PRE_CREATE,
                ApplicationPermissionEventListener.Type.PRE_UPDATE)
            .contains(type);
  }

  @Nullable
  @Override
  public Application.Permission call(
      @Nullable Application.Permission originalPermission,
      @Nullable Application.Permission updatedPermission) {
    if (updatedPermission == null || !updatedPermission.getPermissions().isRestricted()) {
      return null;
    }

    Application application = applicationDAO.findByName(updatedPermission.getName());

    if (isChaosMonkeyEnabled(application)) {
      applyNewPermissions(updatedPermission, true);
    } else {
      applyNewPermissions(updatedPermission, false);
    }

    return updatedPermission;
  }

  @Override
  public void rollback(@Nonnull Application.Permission originalPermission) {
    // Do nothing.
  }

  private void applyNewPermissions(Application.Permission updatedPermission, boolean addRole) {
    Permissions permissions = updatedPermission.getPermissions();

    Map<Authorization, List<String>> unpackedPermissions = permissions.unpack();
    unpackedPermissions.forEach(
        (key, value) -> {
          List<String> roles = new ArrayList<>(value);
          if (key == Authorization.READ || key == Authorization.WRITE) {
            if (addRole && !onlyChaosMonkeyPermissions(updatedPermission, key)) {
              // Only add the chaos monkey role if it doesn't already exist
              if (!hasChaosMonkeyPermissions(updatedPermission, key)) {
                roles.add(properties.getUserRole());
              }
            } else {
              roles.removeAll(Collections.singletonList(properties.getUserRole()));
            }
          }
          unpackedPermissions.put(key, roles);
        });
    Permissions newPermissions = Permissions.factory(unpackedPermissions);

    updatedPermission.setPermissions(newPermissions);
  }

  private boolean isChaosMonkeyEnabled(Application application) {
    Object config = application.details().get("chaosMonkey");
    if (config == null) {
      return false;
    }
    return objectMapper.convertValue(config, ChaosMonkeyConfig.class).enabled;
  }

  private boolean hasChaosMonkeyPermissions(
      Application.Permission updatedPermission, Authorization authorizationType) {
    return updatedPermission
        .getPermissions()
        .get(authorizationType)
        .contains(properties.getUserRole());
  }

  private boolean onlyChaosMonkeyPermissions(
      Application.Permission updatedPermission, Authorization authorizationType) {
    return updatedPermission.getPermissions().get(authorizationType).stream()
            .filter(role -> role.equals(properties.getUserRole()))
            .distinct()
            .count()
        == 1;
  }

  private static class ChaosMonkeyConfig {
    public boolean enabled;
  }
}
