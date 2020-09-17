package com.netflix.spinnaker.front50.migrations;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.front50.ApplicationPermissionsService;
import com.netflix.spinnaker.front50.config.ChaosMonkeyEventListenerConfigurationProperties;
import com.netflix.spinnaker.front50.model.application.Application;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RemoveChaosMonkeyUserMigration implements Migration {

  private static final Logger log = LoggerFactory.getLogger(RemoveChaosMonkeyUserMigration.class);

  // Only valid until February 1st, 2021
  private static final Date VALID_UNTIL = new GregorianCalendar(2021, 2, 1).getTime();

  private final ApplicationPermissionsService applicationPermissionsService;

  private final ChaosMonkeyEventListenerConfigurationProperties properties;

  private Clock clock = Clock.systemDefaultZone();

  public RemoveChaosMonkeyUserMigration(
      ApplicationPermissionsService applicationPermissionsService,
      ChaosMonkeyEventListenerConfigurationProperties properties) {
    this.applicationPermissionsService = applicationPermissionsService;
    this.properties = properties;
  }

  @Override
  public boolean isValid() {
    return clock.instant().toEpochMilli() < VALID_UNTIL.getTime();
  }

  @Override
  public void run() {
    if (!properties.isEnabled()) {

      Set<Application.Permission> applicationPermissions =
          applicationPermissionsService.getAllApplicationPermissions();

      for (Application.Permission applicationPermission : applicationPermissions) {
        if (applicationPermission.getPermissions().isRestricted()) {
          Map<Authorization, List<String>> unpackedPermission =
              applicationPermission.getPermissions().unpack();

          boolean permissionModified = false;
          for (Map.Entry<Authorization, List<String>> authorizationRoleEntry :
              unpackedPermission.entrySet()) {
            List<String> roles = new ArrayList<>(authorizationRoleEntry.getValue());
            if (roles.removeAll(Collections.singletonList(properties.getUserRole()))) {
              log.debug(
                  "Removing {} role from {} authorization on {} application",
                  properties.getUserRole(),
                  authorizationRoleEntry.getKey(),
                  applicationPermission.getName());

              unpackedPermission.put(authorizationRoleEntry.getKey(), roles);
              permissionModified = true;
            }
          }

          if (permissionModified) {
            Permissions newPermissions = Permissions.factory(unpackedPermission);
            applicationPermission.setPermissions(newPermissions);
            applicationPermissionsService.updateApplicationPermission(
                applicationPermission.getName(), applicationPermission, true);

            log.debug(
                "Deleted {} role from permissions on {} application",
                properties.getUserRole(),
                applicationPermission.getName());
          }
        }
      }
    } else {
      log.debug("Chaos monkey event listener is enabled, skipping RemoveChaosMonkeyUserMigration.");
    }
  }
}
