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
import com.netflix.spinnaker.front50.config.ChaosMonkeyEventListenerConfigurationProperties;
import com.netflix.spinnaker.front50.events.ApplicationPermissionEventListener;
import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ensures that when Chaos Monkey is enabled (or disabled) on an Application, its permissions are
 * applied correctly.
 *
 * <p>This listens on Application Permission update events.
 */
@Component
public class ChaosMonkeyApplicationPermissionEventListener extends ChaosMonkeyEventListener
    implements ApplicationPermissionEventListener {
  private static final Logger log =
      LoggerFactory.getLogger(ChaosMonkeyApplicationEventListener.class);

  private final ApplicationDAO applicationDAO;

  public ChaosMonkeyApplicationPermissionEventListener(
      ApplicationDAO applicationDAO,
      ChaosMonkeyEventListenerConfigurationProperties properties,
      ObjectMapper objectMapper) {
    super(properties, objectMapper);
    this.applicationDAO = applicationDAO;
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
      return updatedPermission;
    }

    Application application = applicationDAO.findByName(updatedPermission.getName());

    applyNewPermissions(updatedPermission, isChaosMonkeyEnabled(application));

    return updatedPermission;
  }

  @Override
  public void rollback(@Nonnull Application.Permission originalPermission) {
    // Do nothing.
  }
}
