package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.internal.KeelService;
import com.netflix.spinnaker.kork.manageddelivery.model.ResourceEvent;
import groovy.util.logging.Slf4j;
import io.swagger.annotations.ApiOperation;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/history")
@RestController
@Slf4j
@ConditionalOnProperty("services.keel.enabled")
public class HistoryController {

  private static final Logger log = LoggerFactory.getLogger(HistoryController.class);
  private final KeelService keelService;

  @Autowired
  public HistoryController(KeelService keelService) {
    this.keelService = keelService;
  }

  @ApiOperation(value = "Get history for a resource", response = List.class)
  @RequestMapping(value = "/{name}", method = RequestMethod.GET)
  List<ResourceEvent> getHistory(
      @PathVariable("name") String name,
      @RequestParam(value = "since", required = false) Instant since) {
    return keelService.getResourceEvents(name, since);
  }
}
