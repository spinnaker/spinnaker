/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 */

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.DeploymentSnapshotService;
import com.netflix.spinnaker.gate.services.DeploymentSnapshotService.Snapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single-call deploy-overview endpoint. Dashboards that summarize the state of many
 * applications (server groups + pipeline configs + recent executions) used to fan out 3
 * requests per application, hitting Gate's per-host connection limit and triggering ~3N SQL
 * queries in Orca. This collapses the fan-out into one HTTP round trip and three batched
 * backend calls — see {@link DeploymentSnapshotService} for the full shape.
 *
 * <p>Response: projected to the minimal fields a deploy-overview dashboard consumes.
 */
@RequestMapping("/applications")
@RestController
public class DeploymentSnapshotController {

  private final DeploymentSnapshotService snapshotService;

  public DeploymentSnapshotController(DeploymentSnapshotService snapshotService) {
    this.snapshotService = snapshotService;
  }

  @Operation(
      summary =
          "Retrieve a projected deploy-overview snapshot (server groups + pipeline configs + recent executions) for many applications in one request")
  @GetMapping(value = ":deploymentSnapshot")
  public Snapshot getDeploymentSnapshot(
      @Parameter(
              name = "applications",
              required = true,
              description = "Comma-separated application names")
          @RequestParam("applications")
          String applicationsCsv,
      @Parameter(
              name = "pipelineNameFilter",
              description =
                  "Optional substring match on pipeline name (e.g. `deploy-`) — only matching executions and configs are returned")
          @RequestParam(value = "pipelineNameFilter", required = false)
          String pipelineNameFilter,
      @Parameter(
              name = "pipelineLimit",
              description =
                  "Per-pipeline-config execution limit (default 10) — caps how many recent executions per pipeline are returned")
          @RequestParam(value = "pipelineLimit", required = false, defaultValue = "10")
          Integer pipelineLimit) {
    List<String> applications =
        Arrays.stream(applicationsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    return snapshotService.getSnapshot(applications, pipelineNameFilter, pipelineLimit);
  }
}
