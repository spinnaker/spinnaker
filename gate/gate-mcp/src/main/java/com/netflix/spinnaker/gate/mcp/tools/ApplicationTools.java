/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.mcp.tools;

import com.netflix.spinnaker.gate.mcp.support.OrchestrationJobs;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.http.HttpStatus;

/**
 * MCP tools for listing and managing Spinnaker applications. {@code list_applications}/{@code
 * get_application} read directly from front50 (Spinnaker's source of truth for application
 * metadata); {@code create_application}/{@code delete_application} submit the same Orca job types
 * ({@code createApplication}/{@code deleteApplication}) that Deck submits (see {@link
 * OrchestrationJobs}).
 */
public class ApplicationTools {

  private final Front50Service front50Service;
  private final OrchestrationJobs orchestrationJobs;

  public ApplicationTools(Front50Service front50Service, OrchestrationJobs orchestrationJobs) {
    this.front50Service = front50Service;
    this.orchestrationJobs = orchestrationJobs;
  }

  @McpTool(
      name = "list_applications",
      description =
          "List Spinnaker applications visible to the caller, optionally filtered by owner email.")
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> listApplications(
      @McpToolParam(
              description = "Only include applications owned by this email address",
              required = false)
          String owner) {
    List<Map> applications =
        Retrofit2SyncCall.execute(front50Service.getAllApplicationsUnrestricted());
    return applications.stream()
        .map(app -> (Map<String, Object>) app)
        .filter(app -> owner == null || owner.equalsIgnoreCase(String.valueOf(app.get("email"))))
        .collect(Collectors.toList());
  }

  @McpTool(
      name = "get_application",
      description =
          "Get front50 metadata for a single Spinnaker application (name, email, description, owner, ...).")
  @SuppressWarnings("unchecked")
  public Map<String, Object> getApplication(
      @McpToolParam(description = "Application name", required = true) String application) {
    try {
      return (Map<String, Object>)
          Retrofit2SyncCall.execute(front50Service.getApplication(application));
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() == HttpStatus.NOT_FOUND.value()) {
        throw new NotFoundException("Application not found (id: " + application + ")");
      }
      throw e;
    }
  }

  @McpTool(
      name = "create_application",
      description =
          "Create a new Spinnaker application (or update it, if an application with this name already exists).")
  public Map<String, Object> createApplication(
      @McpToolParam(description = "Application name", required = true) String name,
      @McpToolParam(description = "Owner/point-of-contact email address", required = true)
          String email,
      @McpToolParam(description = "Human-readable description of the application", required = false)
          String description,
      @McpToolParam(
              description =
                  "Comma-separated list of cloud providers this application will use, e.g. 'aws,kubernetes'",
              required = false)
          String cloudProviders) {
    orchestrationJobs.requireWriteAccess("create_application");

    Map<String, Object> applicationAttributes = new LinkedHashMap<>();
    applicationAttributes.put("name", name);
    applicationAttributes.put("email", email);
    if (description != null) {
      applicationAttributes.put("description", description);
    }
    if (cloudProviders != null) {
      applicationAttributes.put("cloudProviders", cloudProviders);
    }

    Map<String, Object> job = new LinkedHashMap<>();
    job.put("type", "createApplication");
    job.put("application", applicationAttributes);
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));

    return orchestrationJobs.submit(name, "Create application '" + name + "'", List.of(job));
  }

  @McpTool(
      name = "delete_application",
      description =
          "Permanently delete a Spinnaker application's metadata from front50 (and its Managed Delivery config, if any). "
              + "This does not delete any deployed infrastructure (server groups, load balancers, etc.) belonging to the application.")
  public Map<String, Object> deleteApplication(
      @McpToolParam(description = "Application name", required = true) String name) {
    orchestrationJobs.requireWriteAccess("delete_application");

    Map<String, Object> applicationAttributes = new LinkedHashMap<>();
    applicationAttributes.put("name", name);

    Map<String, Object> job = new LinkedHashMap<>();
    job.put("type", "deleteApplication");
    job.put("application", applicationAttributes);
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));

    return orchestrationJobs.submit(name, "Delete application '" + name + "'", List.of(job));
  }
}
