package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.internal.Front50Service;
import groovy.util.logging.Slf4j;
import io.swagger.annotations.ApiOperation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/deliveryConfigs")
@RestController
@Slf4j
public class DeliveryController {

  private static final Logger log = LoggerFactory.getLogger(DeliveryController.class);
  private final Front50Service front50Service;

  @Autowired
  public DeliveryController(Front50Service front50Service) {
    this.front50Service = front50Service;
  }

  @ApiOperation(value = "Get all delivery configs", response = HashMap.class)
  @RequestMapping(value = "", method = RequestMethod.GET)
  List<Map> getDeliveries() {
    return front50Service.getDeliveries();
  }

  @ApiOperation(value = "Get a delivery config", response = HashMap.class)
  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  Map getDeliveryConfig(@PathVariable("id") String id) {
    return front50Service.getDelivery(id);
  }
}
