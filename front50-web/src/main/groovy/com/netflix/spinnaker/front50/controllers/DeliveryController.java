package com.netflix.spinnaker.front50.controllers;

import com.netflix.spinnaker.front50.exceptions.InvalidRequestException;
import com.netflix.spinnaker.front50.model.delivery.Delivery;
import com.netflix.spinnaker.front50.model.delivery.DeliveryRepository;
import groovy.util.logging.Slf4j;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@Slf4j
@RestController
@RequestMapping("")
@ConditionalOnExpression("${spinnaker.delivery.enabled:false}")
public class DeliveryController {

  @Autowired
  DeliveryRepository deliveryRepository;

  @ApiOperation(value = "", notes = "Get all managed delivery configs")
  @RequestMapping(method = RequestMethod.GET, value = "/delivery")
  Collection<Delivery> getAllConfigs() {
    return deliveryRepository.getAllConfigs();
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @ApiOperation(value = "", notes = "Get the managed delivery config for an application")
  @RequestMapping(method = RequestMethod.GET, value = "/applications/{application}/delivery")
  Collection<Delivery> getConfigByAppName(@PathVariable String application) {
    return deliveryRepository.getConfigsByApplication(application);
  }

  @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
  @ApiOperation(value = "", notes = "Get a managed delivery config by id")
  @RequestMapping(method = RequestMethod.GET, value = "delivery/id/{id}")
  Delivery getConfigById(@PathVariable String id) {
    return deliveryRepository.findById(id);
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Create a managed delivery config for an application")
  @RequestMapping(method = RequestMethod.POST, value = "/applications/{application}/delivery")
  Delivery createConfig(@PathVariable String application, @RequestBody Delivery config) {
    if (!config.getApplication().equals(application)) {
      throw new InvalidRequestException("Application in url (" + application + ") and " +
        "application in config (" + config.getApplication() + ") don't match." );
    }
    return deliveryRepository.upsertConfig(config);
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Update a managed delivery config for an application")
  @RequestMapping(method = RequestMethod.PUT, value = "/applications/{application}/delivery")
  Delivery upsertConfig(@PathVariable String application, @RequestBody Delivery config) {
    if (!config.getApplication().equals(application)) {
      throw new InvalidRequestException("Application in url (" + application + ") and " +
        "application in config (" + config.getApplication() + ") don't match." );
    }
    return deliveryRepository.upsertConfig(config);
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Delete a managed delivery config")
  @RequestMapping(method = RequestMethod.DELETE, value = "/applications/{application}/delivery/id/{id}")
  void deleteConfig(@PathVariable String application, @PathVariable String id) {
    Delivery config = deliveryRepository.findById(id);
    if (!config.getApplication().equals(application)) {
      throw new InvalidRequestException("No config with id " + id + " found in application " + application);
    }
    deliveryRepository.delete(id);
  }
}
