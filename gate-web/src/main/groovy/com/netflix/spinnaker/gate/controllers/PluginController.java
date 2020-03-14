/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import com.netflix.spinnaker.gate.config.SpinnakerExtensionsConfigProperties;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.swagger.annotations.ApiOperation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * EndPoints supporting CRUD operations for PluginInfo objects.
 *
 * <p>TODO: Currently write operations are secured using application as a resource and in future we
 * need to move more concrete security model with a specific resource type for plugin management.
 */
@RestController
@RequestMapping(value = "/pluginInfo")
@Slf4j
public class PluginController {

  /**
   * If not configured, this application will be used to record orca tasks against, plus also used
   * to verify permissions against.
   */
  private static final String DEFAULT_APPLICATION_NAME = "spinnakerplugins";

  private TaskService taskService;
  private Front50Service front50Service;
  private SpinnakerExtensionsConfigProperties spinnakerExtensionsConfigProperties;

  @Autowired
  public PluginController(
      TaskService taskService,
      Front50Service front50Service,
      SpinnakerExtensionsConfigProperties spinnakerExtensionsConfigProperties) {
    this.taskService = taskService;
    this.front50Service = front50Service;
    this.spinnakerExtensionsConfigProperties = spinnakerExtensionsConfigProperties;
  }

  @ApiOperation(value = "Persist plugin metadata information")
  @PreAuthorize("hasPermission(#this.this.appName, 'APPLICATION', 'WRITE')")
  @RequestMapping(
      method = {RequestMethod.POST, RequestMethod.PUT},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  Map persistPluginInfo(@RequestBody Map pluginInfo) {
    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "upsertPluginInfo");
    job.put("pluginInfo", pluginInfo);
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    jobs.add(job);

    return initiateTask("Upsert plugin info with Id: " + pluginInfo.get("id"), jobs);
  }

  @ApiOperation(value = "Delete plugin info with the provided Id")
  @PreAuthorize("hasPermission(#this.this.appName, 'APPLICATION', 'WRITE')")
  @RequestMapping(
      value = "/{id:.+}",
      method = {RequestMethod.DELETE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  Map deletePluginInfo(@PathVariable String id) {
    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "deletePluginInfo");
    job.put("pluginInfoId", id);
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    jobs.add(job);

    return initiateTask("Delete Plugin info with Id: " + id, jobs);
  }

  @ApiOperation(value = "Get all plugin info objects")
  @RequestMapping(method = RequestMethod.GET)
  List<Map> getAllPluginInfo(@RequestParam(value = "service", required = false) String service) {
    return front50Service.getPluginInfo(service);
  }

  private Map initiateTask(String description, List<Map<String, Object>> jobs) {
    Map<String, Object> operation = new HashMap<>();
    operation.put("description", description);
    operation.put("application", getAppName());
    operation.put("job", jobs);
    return taskService.create(operation);
  }

  public String getAppName() {
    String applicationName = spinnakerExtensionsConfigProperties.getApplicationName();
    if (StringUtils.isEmpty(applicationName)) {
      applicationName = DEFAULT_APPLICATION_NAME;
    }
    return applicationName;
  }
}
