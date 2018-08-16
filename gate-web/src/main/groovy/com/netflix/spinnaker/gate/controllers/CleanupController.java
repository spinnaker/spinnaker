package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.CleanupService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/cleanup")
public class CleanupController {

  private CleanupService cleanupService;

  @Autowired
  public CleanupController(CleanupService cleanupService) {
    this.cleanupService = cleanupService;
  }

  @ApiOperation(value = "Opt out of clean up for a marked resource.", response = HashMap.class)
  @RequestMapping(method = RequestMethod.PUT, value = "/resources/{namespace}/{resourceId}/optOut")
  Map optOut(@PathVariable String namespace,
             @PathVariable String resourceId) {
    return cleanupService.optOut(namespace, resourceId);
  }
}
