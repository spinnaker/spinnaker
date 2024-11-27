package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.internal.KeelService;
import groovy.util.logging.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
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

  @Operation(summary = "Get history for a resource")
  @RequestMapping(value = "/{name}", method = RequestMethod.GET)
  List<Map<String, Object>> getHistory(
      @PathVariable("name") String name,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return keelService.getResourceEvents(name, limit);
  }
}
