/*
 * Copyright 2020 Netflix, Inc.
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
import com.netflix.spinnaker.front50.model.application.Application;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Common routines for chaos monkey event listeners. */
public abstract class ChaosMonkeyEventListener {

  protected final ChaosMonkeyEventListenerConfigurationProperties properties;
  protected final ObjectMapper objectMapper;

  protected ChaosMonkeyEventListener(
      ChaosMonkeyEventListenerConfigurationProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  protected void applyNewPermissions(Application.Permission updatedPermission, boolean addRole) {
    Permissions permissions = updatedPermission.getPermissions();

    Map<Authorization, List<String>> unpackedPermissions = permissions.unpack();
    unpackedPermissions.forEach(
        (key, value) -> {
          List<String> roles = new ArrayList<>(value);
          if (key == Authorization.READ || key == Authorization.WRITE) {
            if (addRole && shouldAddChaosMonkeyPermission(updatedPermission, key)) {
              roles.add(properties.getUserRole());
            } else {
              roles.removeAll(Collections.singletonList(properties.getUserRole()));
            }
          }
          unpackedPermissions.put(key, roles);
        });
    Permissions newPermissions = Permissions.factory(unpackedPermissions);

    updatedPermission.setPermissions(newPermissions);
  }

  protected boolean isChaosMonkeyEnabled(Application application) {
    Object config = application.details().get("chaosMonkey");
    if (config == null) {
      return false;
    }
    return objectMapper.convertValue(config, ChaosMonkeyConfig.class).enabled;
  }

  /**
   * We only want to add the chaos monkey role if it's missing from the permission and the
   * permission is not otherwise empty.
   */
  private boolean shouldAddChaosMonkeyPermission(
      Application.Permission updatedPermission, Authorization authorizationType) {
    return !updatedPermission
            .getPermissions()
            .get(authorizationType)
            .contains(properties.getUserRole())
        && !updatedPermission.getPermissions().get(authorizationType).isEmpty();
  }

  private static class ChaosMonkeyConfig {
    public boolean enabled;
  }
}
