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
@RestController
public class DeploymentSnapshotController {

  private final DeploymentSnapshotService snapshotService;

  public DeploymentSnapshotController(DeploymentSnapshotService snapshotService) {
    this.snapshotService = snapshotService;
  }

  @Operation(
      summary =
          "Retrieve a deploy-overview status snapshot (server groups + pipeline configs + recent executions) for many applications in one request")
  @GetMapping(value = "/deploymentSnapshot")
  public Snapshot getDeploymentSnapshot(
      @Parameter(
              name = "applications",
              required = true,
              description = "Comma-separated application names")
          @RequestParam("applications")
          String applicationsCsv,
      @Parameter(
              name = "pipelineNames",
              description =
                  "Optional comma-separated allowlist of exact pipeline names (e.g. `deploy-dev,deploy-devaz`). When set, only executions and configs whose name matches exactly are returned; when omitted, no name filter is applied.")
          @RequestParam(value = "pipelineNames", required = false)
          String pipelineNamesCsv,
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
    List<String> pipelineNames =
        pipelineNamesCsv == null || pipelineNamesCsv.isBlank()
            ? List.of()
            : Arrays.stream(pipelineNamesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    return snapshotService.getSnapshot(applications, pipelineNames, pipelineLimit);
  }
}
