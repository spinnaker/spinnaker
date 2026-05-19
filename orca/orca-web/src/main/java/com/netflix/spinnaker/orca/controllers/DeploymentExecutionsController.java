/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 */

package com.netflix.spinnaker.orca.controllers;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Batch endpoint that returns recent pipeline executions across many applications in a single
 * SQL round trip. Companion to {@code TaskController#getPipelinesForApplication}, which only
 * accepts one application per request — fine for Deck's single-app pages but a 60×-round-trip
 * pattern for dashboards that need to summarize the deploy state of every service at once.
 *
 * <p>Projection: returns {@link DeploymentExecutionView}, a slim summary of each execution
 * (ids, status, timestamps, trigger summary, stage list). Drops outputs/context/tasks/
 * notifications, which typically account for 90%+ of an execution's serialized weight but
 * aren't needed for top-level dashboards.
 *
 * <p>Auth: results are filtered post-hoc to executions whose application the caller has
 * READ permission on, matching the per-app guard on {@code getPipelinesForApplication}.
 */
@RestController
public class DeploymentExecutionsController {

  private final ExecutionRepository executionRepository;
  @Nullable private final Front50Service front50Service;

  public DeploymentExecutionsController(
      ExecutionRepository executionRepository, @Nullable Front50Service front50Service) {
    this.executionRepository = executionRepository;
    this.front50Service = front50Service;
  }

  /** Reject pathologically large batches before they hit the database. */
  static final int MAX_APPLICATIONS = 100;

  static final int MAX_LIMIT = 100;

  /**
   * {@code @PreFilter} drops any application the caller doesn't have READ permission on
   * BEFORE the SQL query runs (unlike {@code @PostFilter}, which runs after). Combined with
   * the post-filter on the result list, unauthorized apps are gone from both the input
   * predicate and the output rows.
   */
  @PreFilter(
      value = "hasPermission(filterObject, 'APPLICATION', 'READ')",
      filterTarget = "applications")
  @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
  @GetMapping(value = "/v2/applications:deploymentExecutions", produces = APPLICATION_JSON_VALUE)
  public List<DeploymentExecutionView> getDeploymentExecutions(
      @RequestParam("applications") List<String> applications,
      @RequestParam(value = "pipelineNameFilter", required = false) String pipelineNameFilter,
      @RequestParam(value = "statuses", required = false) String statuses,
      @RequestParam(value = "limit", defaultValue = "5") int limit,
      @RequestParam(value = "queryTimeoutSeconds", defaultValue = "10") int queryTimeoutSeconds) {

    if (applications.size() > MAX_APPLICATIONS) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "applications must contain at most " + MAX_APPLICATIONS + " entries");
    }
    if (limit > MAX_LIMIT) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "limit must be <= " + MAX_LIMIT);
    }
    // Strip empty / whitespace-only entries that come from sloppy CSV (e.g. "a,,b"
    // or "a, b"). @PreFilter has already dropped unauthorized apps; this is a
    // separate hygiene pass on the input shape itself.
    applications =
        applications.stream()
            .filter(a -> a != null)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    if (applications.isEmpty()) return List.of();

    ExecutionCriteria criteria = new ExecutionCriteria();
    criteria.setPageSize(limit);
    if (statuses != null && !statuses.isBlank()) {
      criteria.setStatuses(
          Arrays.stream(statuses.split(","))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .collect(Collectors.toList()));
    } else {
      criteria.setStatuses(
          Arrays.stream(ExecutionStatus.values()).map(Enum::name).collect(Collectors.toList()));
    }

    // Resolve pipelineNameFilter via front50's getAllPipelines once (in-memory at front50),
    // then filter to the requested apps + name prefix. The resulting config_ids are the
    // restriction we pass to the SQL repo; passing an empty list means "no config_id filter".
    //
    // If front50 isn't wired up (e.g. test contexts), we simply skip the filter and let the
    // repo return everything for the apps — matches getPipelinesForApplication's behavior.
    // If front50 IS wired up but fails, we let the exception propagate: a 503 is honest, and
    // silently returning empty results would make front50 outages look like "no deploys."
    List<String> configIds = List.of();
    if (front50Service != null && pipelineNameFilter != null && !pipelineNameFilter.isBlank()) {
      configIds = resolveConfigIds(applications, pipelineNameFilter);
      if (configIds.isEmpty()) {
        // Filter matches zero pipelines — short-circuit before hitting SQL.
        return List.of();
      }
    }

    Collection<PipelineExecution> executions =
        executionRepository.retrievePipelineExecutionsForApplications(
            applications, configIds, criteria, queryTimeoutSeconds);

    List<DeploymentExecutionView> out = new ArrayList<>(executions.size());
    for (PipelineExecution e : executions) out.add(DeploymentExecutionView.from(e));
    // Sort newest first by startTime then id, matching getPipelinesForApplication.
    out.sort(
        (a, b) -> {
          long as = a.startTime == null ? 0L : a.startTime;
          long bs = b.startTime == null ? 0L : b.startTime;
          if (as != bs) return Long.compare(bs, as);
          return b.id.compareTo(a.id);
        });
    return out;
  }

  private List<String> resolveConfigIds(List<String> applications, String pipelineNameFilter) {
    // Don't swallow front50 failures: a 503 here is honest. The Gate aggregator's
    // catch-and-degrade still lets the dashboard render with empty execution data,
    // but the underlying error gets logged at the right layer instead of being
    // misread as "no deploys match this filter."
    List<Map<String, Object>> allPipelines =
        Retrofit2SyncCall.execute(front50Service.getAllPipelines());
    Set<String> wantedApps = new HashSet<>(applications);
    Set<String> ids = new LinkedHashSet<>();
    for (Map<String, Object> p : allPipelines) {
      Object app = p.get("application");
      Object name = p.get("name");
      Object id = p.get("id");
      if (!(app instanceof String) || !(name instanceof String) || !(id instanceof String)) continue;
      if (!wantedApps.contains(app)) continue;
      if (!((String) name).contains(pipelineNameFilter)) continue;
      ids.add((String) id);
    }
    return new ArrayList<>(ids);
  }
}
