package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.CleanupService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import retrofit.http.Query;

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
  @RequestMapping(method = RequestMethod.PUT,
                  value = "/resources/{namespace}/{resourceId}/optOut",
                  produces= "text/html")
  String optOut(@PathVariable String namespace,
                @PathVariable String resourceId) {
    Map markedResource = cleanupService.optOut(namespace, resourceId);
    if (markedResource.isEmpty()) {
      return getHtmlWithMessage(namespace, resourceId, "Resource does not exist. Please try a valid resource.");
    }

    return getHtmlWithMessage(namespace, resourceId, "Resource has been restored, and opted out of future deletion!");
  }

  @ApiOperation(value = "Get information about a marked resource.", response = String.class)
  @RequestMapping(method = RequestMethod.PUT,
    value = "/resources/{namespace}/{resourceId}")
  Map getMarkedResource(@PathVariable String namespace,
                        @PathVariable String resourceId) {
    Map markedResource = cleanupService.get(namespace, resourceId);
    return markedResource;
  }

  // todo eb: expose once AWS gives us soft delete
//  @ApiOperation(value = "Restore a resource that has been soft deleted.", response = HashMap.class)
//  @RequestMapping(method = RequestMethod.PUT,
//                  value = "/resources/{namespace}/{resourceId}/restore",
//                  produces= "text/html")
  String restore(String namespace, String resourceId) {
    String status = cleanupService.restore(namespace, resourceId);
    if (status.equals("404")) {
      return getHtmlWithMessage(namespace, resourceId, "Resource does not exist. Please try a valid resource.");
    }

    return getHtmlWithMessage(namespace, resourceId, "Resource has been opted out!");
  }

  private String getHtmlWithMessage(String namespace, String resourceId, String message) {
    return "<body>\n" +
      "Swabbie Resource: [namespace=" + namespace + ", resourceId=" + resourceId + "] <br>\n" +
      "Message: " + message + "\n" +
      "</body>";
  }
}
