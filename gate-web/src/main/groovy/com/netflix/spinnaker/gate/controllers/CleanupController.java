/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.CleanupService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cleanup")
public class CleanupController {

  private CleanupService cleanupService;

  @Autowired
  public CleanupController(CleanupService cleanupService) {
    this.cleanupService = cleanupService;
  }

  @Operation(summary = "Opt out of clean up for a marked resource.")
  @RequestMapping(
      method = RequestMethod.GET,
      value = "/resources/{namespace}/{resourceId}/optOut",
      produces = "application/json")
  Map optOut(@PathVariable String namespace, @PathVariable String resourceId) {
    Map markedResource = cleanupService.optOut(namespace, resourceId);
    if (markedResource.isEmpty()) {
      return getJsonWithMessage(
          namespace, resourceId, "Resource does not exist. Please try a valid resource.");
    }

    return getJsonWithMessage(
        namespace, resourceId, "Resource has been opted out of automated deletion.");
  }

  @Operation(summary = "Get information about a marked resource.")
  @RequestMapping(
      method = RequestMethod.GET,
      value = "/resources/{namespace}/{resourceId}",
      produces = "application/json")
  Map getMarkedResource(@PathVariable String namespace, @PathVariable String resourceId) {
    Map markedResource = cleanupService.get(namespace, resourceId);
    if (markedResource.isEmpty()) {
      return getJsonWithMessage(
          namespace, resourceId, "Resource does not exist. Please try a valid resource.");
    }
    return markedResource;
  }

  @Operation(summary = "Get all marked resources.")
  @RequestMapping(
      method = RequestMethod.GET,
      value = "/resources/marked",
      produces = "application/json")
  List getAllMarkedResources() {
    return cleanupService.getMarkedList();
  }

  @Operation(summary = "Get all deleted resources.")
  @RequestMapping(
      method = RequestMethod.GET,
      value = "/resources/deleted",
      produces = "application/json")
  List getAllDeletedResources() {
    return cleanupService.getDeletedList();
  }

  private Map<String, String> getJsonWithMessage(
      String namespace, String resourceId, String message) {
    Map<String, String> json = new HashMap();

    json.put("swabbieNamespace", namespace);
    json.put("swabbieResourceId", resourceId);
    json.put("message", message);
    return json;
  }
}
