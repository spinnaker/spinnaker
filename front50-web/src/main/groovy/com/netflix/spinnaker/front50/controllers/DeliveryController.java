package com.netflix.spinnaker.front50.controllers;

import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.exceptions.InvalidRequestException;
import com.netflix.spinnaker.front50.model.delivery.Delivery;
import com.netflix.spinnaker.front50.model.delivery.DeliveryRepository;
import groovy.util.logging.Slf4j;
import io.swagger.annotations.ApiOperation;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@ConditionalOnExpression("${spinnaker.delivery.enabled:false}")
public class DeliveryController {

  private final DeliveryRepository deliveryRepository;

  @Autowired
  public DeliveryController(DeliveryRepository deliveryRepository) {
    this.deliveryRepository = deliveryRepository;
  }

  @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
  @ApiOperation(value = "", notes = "Get all delivery configs")
  @RequestMapping(method = RequestMethod.GET, value = "/deliveries")
  Collection<Delivery> getAllConfigs() {
    return deliveryRepository.getAllConfigs();
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @ApiOperation(value = "", notes = "Get the delivery configs for an application")
  @RequestMapping(method = RequestMethod.GET, value = "/applications/{application}/deliveries")
  Collection<Delivery> getConfigByAppName(@PathVariable String application) {
    return deliveryRepository.getConfigsByApplication(application);
  }

  @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
  @ApiOperation(value = "", notes = "Get a delivery config by id")
  @RequestMapping(method = RequestMethod.GET, value = "deliveries/{id}")
  Delivery getConfigById(@PathVariable String id) {
    return deliveryRepository.findById(id);
  }

  @PreAuthorize("hasPermission(#config.application, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Create a delivery config")
  @RequestMapping(method = RequestMethod.POST, value = "/deliveries")
  Delivery createConfig(@RequestBody Delivery config) {
    return deliveryRepository.upsertConfig(config);
  }

  @PreAuthorize("hasPermission(#config.application, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Update a delivery config")
  @RequestMapping(method = RequestMethod.PUT, value = "/deliveries/{id}")
  Delivery upsertConfig(@PathVariable String id, @RequestBody Delivery config) {
    if (!id.equals(config.getId())) {
      throw new InvalidRequestException(
          "URL id (" + id + ") does not match submitted id (" + config.getId() + ")");
    }
    try {
      Delivery existing = deliveryRepository.findById(id);
      config.setCreateTs(existing.getCreateTs());
    } catch (NotFoundException e) {
      // ignore because we will create config
    }

    return deliveryRepository.upsertConfig(config);
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Delete a delivery config")
  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/applications/{application}/deliveries/{id}")
  void deleteConfig(@PathVariable String application, @PathVariable String id) {
    Delivery config = deliveryRepository.findById(id);
    if (!config.getApplication().equals(application)) {
      throw new InvalidRequestException(
          "No config with id " + id + " found in application " + application);
    }
    deliveryRepository.delete(id);
  }
}
