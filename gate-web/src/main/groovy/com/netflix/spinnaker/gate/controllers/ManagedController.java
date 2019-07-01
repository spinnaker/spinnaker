package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.internal.KeelService;
import com.netflix.spinnaker.kork.manageddelivery.model.Resource;
import groovy.util.logging.Slf4j;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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
  @RequestMapping(value = "/resources/{name}", method = RequestMethod.GET)
  Resource getResource(@PathVariable("name") String name) {
    return keelService.getResource(name);
  }

  @ApiOperation(value = "Create or update a resource", response = Resource.class)
  @RequestMapping(value = "/resources", method = RequestMethod.POST)
  Resource upsertResource(@RequestBody Resource resource) {
    return keelService.upsertResource(resource);
  }

  @ApiOperation(value = "Delete a resource", response = Resource.class)
  @RequestMapping(value = "/resources/{name}", method = RequestMethod.DELETE)
  Resource deleteResource(@PathVariable("name") String name) {
    return keelService.deleteResource(name);
  }
}
