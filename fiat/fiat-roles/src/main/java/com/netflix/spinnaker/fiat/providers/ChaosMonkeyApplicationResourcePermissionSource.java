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

package com.netflix.spinnaker.fiat.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.Set;
import javax.annotation.Nonnull;

public class ChaosMonkeyApplicationResourcePermissionSource
    implements ResourcePermissionSource<Application> {

  private final Set<String> roles;
  private final ObjectMapper objectMapper;

  public ChaosMonkeyApplicationResourcePermissionSource(
      Set<String> roles, ObjectMapper objectMapper) {
    this.roles = roles;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public Permissions getPermissions(@Nonnull Application application) {
    Permissions.Builder builder = new Permissions.Builder();
    Permissions permissions = application.getPermissions();

    if (permissions.isRestricted()) {
      if (isChaosMonkeyEnabled(application)) {
        builder.add(Authorization.READ, roles).add(Authorization.WRITE, roles).build();
      }
    }

    return builder.build();
  }

  protected boolean isChaosMonkeyEnabled(Application application) {
    Object config = application.getDetails().get("chaosMonkey");
    if (config == null) {
      return false;
    }
    return objectMapper.convertValue(config, ChaosMonkeyConfig.class).enabled;
  }

  private static class ChaosMonkeyConfig {
    public boolean enabled;
  }
}
