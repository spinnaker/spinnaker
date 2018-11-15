package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.CleanupService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cleanup")
public class CleanupController {

  private CleanupService cleanupService;

  @Autowired
  public CleanupController(CleanupService cleanupService) {
    this.cleanupService = cleanupService;
  }

  @ApiOperation(value = "Opt out of clean up for a marked resource.", response = Map.class)
  @RequestMapping(method = RequestMethod.GET,
                  value = "/resources/{namespace}/{resourceId}/optOut",
                  produces = "application/json")
  Map optOut(@PathVariable String namespace,
                @PathVariable String resourceId) {
    Map markedResource = cleanupService.optOut(namespace, resourceId);
    if (markedResource.isEmpty()) {
      return getJsonWithMessage(namespace, resourceId, "Resource does not exist. Please try a valid resource.");
    }

    return getJsonWithMessage(namespace, resourceId, "Resource has been restored, and opted out of future deletion!");
  }

  @ApiOperation(value = "Get information about a marked resource.", response = Map.class)
  @RequestMapping(method = RequestMethod.GET,
    value = "/resources/{namespace}/{resourceId}",
    produces = "application/json")
  Map getMarkedResource(@PathVariable String namespace,
                        @PathVariable String resourceId) {
    Map markedResource = cleanupService.get(namespace, resourceId);
    if (markedResource.isEmpty()) {
      return getJsonWithMessage(namespace, resourceId, "Resource does not exist. Please try a valid resource.");
    }
    return markedResource;
  }

  @ApiOperation(value = "Get all marked resources.", response = List.class)
  @RequestMapping(method = RequestMethod.GET, value = "/resources/marked", produces = "application/json")
  List getAllMarkedResources() {
    return cleanupService.getMarkedList();
  }

  @ApiOperation(value = "Get all deleted resources.", response = List.class)
  @RequestMapping(method = RequestMethod.GET, value = "/resources/deleted", produces = "application/json")
  List getAllDeletedResources() {
    return cleanupService.getDeletedList();
  }

  private Map<String, String> getJsonWithMessage(String namespace, String resourceId, String message) {
    Map<String, String> json = new HashMap();

    json.put("swabbieNamespace", namespace);
    json.put("swabbieResourceId", resourceId);
    json.put("message", message);
    return json;
  }
}
