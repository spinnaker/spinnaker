package com.netflix.spinnaker.front50.controllers;

import com.netflix.spinnaker.front50.ApplicationPermissionsService;
import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.security.authz.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/permissions")
public class PermissionsController {

  private final ApplicationPermissionsService permissionsService;

  public PermissionsController(ApplicationPermissionsService permissionsService) {
    this.permissionsService = permissionsService;
  }

  @Operation(summary = "", description = "Get all application permissions. Internal use only.")
  @RequestMapping(method = RequestMethod.GET, value = "/applications")
  public Set<Application.Permission> getAllApplicationPermissions() {
    return permissionsService.getAllApplicationPermissions();
  }

  /**
   * The global default application permissions. Deliberately not under {@code
   * /applications/defaults}, which the {@code {appName:.+}} mapping below would also match.
   */
  @Operation(
      summary = "",
      description = "Get the permissions every application is granted by configuration.")
  @RequestMapping(method = RequestMethod.GET, value = "/defaults")
  public Permissions getDefaultApplicationPermissions() {
    return permissionsService.getDefaultApplicationPermissions();
  }

  /**
   * @param effective when true, the returned ACL has the global default application permissions
   *     merged in — the ACL an authorization decision must be made against. Services that resolve
   *     application ACLs from Front50 use this so {@code authz.application.default-permissions}
   *     stays configured in Front50 alone. It is opt-in because the default response is also the
   *     read side of permission editing, and returning a merged view there would let a
   *     read-modify-write persist the defaults as explicit grants.
   */
  @RequestMapping(method = RequestMethod.GET, value = "/applications/{appName:.+}")
  public Application.Permission getApplicationPermission(
      @PathVariable String appName,
      @RequestParam(value = "effective", required = false, defaultValue = "false")
          boolean effective) {
    return permissionsService.getApplicationPermission(appName, effective);
  }

  @Operation(summary = "", description = "Create an application permission.")
  @RequestMapping(method = RequestMethod.POST, value = "/applications")
  public Application.Permission createApplicationPermission(
      @RequestBody Application.Permission newPermission) {
    return permissionsService.createApplicationPermission(newPermission);
  }

  @RequestMapping(method = RequestMethod.PUT, value = "/applications/{appName:.+}")
  public Application.Permission updateApplicationPermission(
      @PathVariable String appName, @RequestBody Application.Permission newPermission) {
    return permissionsService.updateApplicationPermission(appName, newPermission, false);
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/applications/{appName:.+}")
  public void deleteApplicationPermission(@PathVariable String appName) {
    permissionsService.deleteApplicationPermission(appName);
  }
}
