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
import com.netflix.spinnaker.front50.ApplicationPermissionsService;
import com.netflix.spinnaker.front50.config.ChaosMonkeyEventListenerConfigurationProperties;
import com.netflix.spinnaker.front50.events.ApplicationEventListener;
import com.netflix.spinnaker.front50.model.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ensures that when Chaos Monkey is enabled (or disabled) on an Application, its permissions are
 * applied correctly.
 *
 * <p>This listens on both Application update events.
 */
@Component
public class ChaosMonkeyApplicationEventListener extends ChaosMonkeyEventListener
    implements ApplicationEventListener {
  private static final Logger log =
      LoggerFactory.getLogger(ChaosMonkeyApplicationEventListener.class);

  private final ApplicationPermissionsService applicationPermissionsService;

  public ChaosMonkeyApplicationEventListener(
      ApplicationPermissionsService applicationPermissionsService,
      ChaosMonkeyEventListenerConfigurationProperties properties,
      ObjectMapper objectMapper) {
    super(properties, objectMapper);
    this.applicationPermissionsService = applicationPermissionsService;
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

    applyNewPermissions(permission, isChaosMonkeyEnabled(updatedApplication));

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
}
