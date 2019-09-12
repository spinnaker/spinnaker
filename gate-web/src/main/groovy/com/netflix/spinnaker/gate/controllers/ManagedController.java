package com.netflix.spinnaker.gate.controllers;

import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import com.netflix.spinnaker.gate.services.internal.KeelService;
import com.netflix.spinnaker.kork.manageddelivery.model.DeliveryConfig;
import com.netflix.spinnaker.kork.manageddelivery.model.Resource;
import groovy.util.logging.Slf4j;
import io.swagger.annotations.ApiOperation;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/managed")
@RestController
@Slf4j
@ConditionalOnProperty("services.keel.enabled")
public class ManagedController {

  private static final Logger log = LoggerFactory.getLogger(ManagedController.class);
  private final KeelService keelService;

  @Autowired
  public ManagedController(KeelService keelService) {
    this.keelService = keelService;
  }

  @ApiOperation(value = "Get a resource", response = Resource.class)
  @RequestMapping(value = "/resources/{name}", method = GET)
  Resource getResource(@PathVariable("name") String name) {
    return keelService.getResource(name);
  }

  @ApiOperation(value = "Get status of a resource", response = Resource.class)
  @RequestMapping(value = "/resources/{name}/status", method = GET)
  String getResourceStatus(@PathVariable("name") String name) {
    return keelService.getResourceStatus(name);
  }

  @ApiOperation(value = "Create or update a resource", response = Resource.class)
  @RequestMapping(value = "/resources", method = POST)
  Resource upsertResource(@RequestBody Resource resource) {
    return keelService.upsertResource(resource);
  }

  @ApiOperation(value = "Delete a resource", response = Resource.class)
  @RequestMapping(value = "/resources/{name}", method = DELETE)
  Resource deleteResource(@PathVariable("name") String name) {
    return keelService.deleteResource(name);
  }

  @ApiOperation(value = "Get a delivery config manifest", response = DeliveryConfig.class)
  @RequestMapping(value = "/delivery-configs/{name}", method = GET)
  DeliveryConfig getManifest(@PathVariable("name") String name) {
    return keelService.getManifest(name);
  }

  @ApiOperation(
      value = "Create or update a delivery config manifest",
      response = DeliveryConfig.class)
  @RequestMapping(value = "/delivery-configs", method = POST)
  DeliveryConfig upsertManifest(@RequestBody DeliveryConfig manifest) {
    return keelService.upsertManifest(manifest);
  }

  @ApiOperation(value = "Get managed details about an application", response = Map.class)
  @RequestMapping(value = "/application/{application}", method = GET)
  Map getApplicationDetails(
      @PathVariable("application") String application,
      @RequestParam(value = "includeDetails", required = false, defaultValue = "false")
          Boolean includeDetails) {
    return keelService.getApplicationDetails(application, includeDetails);
  }
}
