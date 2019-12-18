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
import com.netflix.spinnaker.front50.config.ChaosMonkeyEventListenerConfigurationProperties;
import com.netflix.spinnaker.front50.events.ApplicationEventListener;
import com.netflix.spinnaker.front50.events.ApplicationPermissionEventListener;
import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ensures that when Chaos Monkey is enabled (or disabled) on an Application, its permissions are
 * applied correctly.
 *
 * <p>This listens on both Application events, as well as ApplicationPermission events.
 */
@Component
public class ChaosMonkeyEventListener
    implements ApplicationEventListener, ApplicationPermissionEventListener {
  private static final Logger log = LoggerFactory.getLogger(ChaosMonkeyEventListener.class);

  private final ApplicationDAO applicationDAO;
  private final ChaosMonkeyEventListenerConfigurationProperties properties;
  private final ObjectMapper objectMapper;

  public ChaosMonkeyEventListener(
      ApplicationDAO applicationDAO,
      ChaosMonkeyEventListenerConfigurationProperties properties,
      ObjectMapper objectMapper) {
    this.applicationDAO = applicationDAO;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(ApplicationEventListener.Type type) {
    return properties.isEnabled() && ApplicationEventListener.Type.PRE_UPDATE == type;
  }

  @Override
  public Application call(Application originalApplication, Application updatedApplication) {
    boolean isChaosMonkeyEnabled = isChaosMonkeyEnabled(updatedApplication);
    if (isChaosMonkeyEnabled(originalApplication) == isChaosMonkeyEnabled) {
      // Flag didn't change, we don't need to do anything.
      return updatedApplication;
    }

    Object rawPermissions = updatedApplication.details().get("permissions");
    if (!(rawPermissions instanceof Map)) {
      // Exit early, no permissions config found on the application
      log.warn("No permissions config found on application '{}'", updatedApplication.getName());
      return updatedApplication;
    }

    @SuppressWarnings("unchecked")
    Map<String, List<String>> permissionsMap = (Map<String, List<String>>) rawPermissions;
    if (permissionsMap.isEmpty()) {
      // An empty permissions map means the application is unrestricted - let's not change this.
      return updatedApplication;
    }

    if (isChaosMonkeyEnabled) {
      // Chaos Monkey is enabled, apply permissions
      List<String> readPermissions = permissionsMap.computeIfAbsent("READ", k -> new ArrayList<>());
      if (!readPermissions.contains(properties.getUserRole())) {
        log.info("Chaos Monkey READ permissions applied for '{}'", updatedApplication.getName());
        readPermissions.add(properties.getUserRole());
      }
      List<String> writePermissions = permissionsMap.get("WRITE");
      if (!writePermissions.contains(properties.getUserRole())) {
        log.info("Chaos Monkey WRITE permissions applied for '{}'", updatedApplication.getName());
        writePermissions.add(properties.getUserRole());
      }
    } else {
      // Chaos Monkey is disabled, revoke permissions
      log.info(
          "Revoking Chaos Monkey READ and WRITE permissions for '{}'",
          updatedApplication.getName());
      permissionsMap.get("READ").remove(properties.getUserRole());
      permissionsMap.get("WRITE").remove(properties.getUserRole());
    }

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
    if (updatedPermission == null) {
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

  private void applyNewPermissions(Application.Permission updatedPermission, boolean addRole) {
    Permissions permissions = updatedPermission.getPermissions();

    Map<Authorization, List<String>> unpackedPermissions = permissions.unpack();
    unpackedPermissions.forEach(
        (key, value) -> {
          List<String> roles = new ArrayList<>(value);
          if (key == Authorization.READ || key == Authorization.WRITE) {
            if (addRole) {
              roles.add(properties.getUserRole());
            } else {
              roles.remove(properties.getUserRole());
            }
          }
          unpackedPermissions.put(key, roles);
        });
    Permissions newPermissions = Permissions.factory(unpackedPermissions);

    updatedPermission.setPermissions(newPermissions);
  }

  @Override
  public void rollback(@Nonnull Application.Permission originalPermission) {
    // Do nothing.
  }

  private boolean isChaosMonkeyEnabled(Application application) {
    Object config = application.details().get("chaosMonkey");
    if (config == null) {
      return false;
    }
    return objectMapper.convertValue(config, ChaosMonkeyConfig.class).enabled;
  }

  private static class ChaosMonkeyConfig {
    public boolean enabled;
  }
}
