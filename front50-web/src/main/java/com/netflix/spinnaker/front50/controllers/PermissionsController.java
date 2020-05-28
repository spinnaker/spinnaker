package com.netflix.spinnaker.front50.controllers;

import com.netflix.spinnaker.front50.ApplicationPermissionsService;
import com.netflix.spinnaker.front50.model.application.Application;
import io.swagger.annotations.ApiOperation;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/permissions")
public class PermissionsController {

  private final ApplicationPermissionsService permissionsService;

  public PermissionsController(ApplicationPermissionsService permissionsService) {
    this.permissionsService = permissionsService;
  }

  @ApiOperation(value = "", notes = "Get all application permissions. Internal use only.")
  @RequestMapping(method = RequestMethod.GET, value = "/applications")
  public Set<Application.Permission> getAllApplicationPermissions() {
    return permissionsService.getAllApplicationPermissions();
  }

  @RequestMapping(method = RequestMethod.GET, value = "/applications/{appName:.+}")
  public Application.Permission getApplicationPermission(@PathVariable String appName) {
    return permissionsService.getApplicationPermission(appName);
  }

  @ApiOperation(value = "", notes = "Create an application permission.")
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
