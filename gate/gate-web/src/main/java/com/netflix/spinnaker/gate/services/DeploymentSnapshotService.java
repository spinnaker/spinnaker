/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 */

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aggregates a deploy-overview snapshot across many applications in a single round trip.
 *
 * <p>Today's dashboards (status page, deploy overview) iterate over ~20 service applications and
 * make three calls per app — server groups (Clouddriver), pipeline configs (Front50), pipeline
 * executions (Orca). That's 60 HTTP round trips and triggers ~60 SQL queries in Orca. This
 * service collapses it to three round trips:
 *
 * <ul>
 *   <li>Clouddriver: one multi-app {@code /serverGroups?applications=…} call (in-memory cache).
 *   <li>Front50: one {@code /pipelines} call (in-memory cache); we filter to the requested apps
 *       here.
 *   <li>Orca: one {@code /deploymentSnapshots} call (one SQL query).
 * </ul>
 *
 * <p>Each shape is projected to the minimal fields a deploy-overview dashboard needs before
 * returning; the projection drops ~90% of bytes off each response. Disabled server groups are
 * dropped entirely (every cluster accumulates a long tail of historical SGs that dashboards
 * filter out anyway), and when {@code pipelineNames} is provided the pipeline configs and
 * executions are restricted to exact-name matches.
 */
@Component
public class DeploymentSnapshotService {

  private static final Logger log = LoggerFactory.getLogger(DeploymentSnapshotService.class);

  private final ClouddriverServiceSelector clouddriverServiceSelector;
  private final OrcaServiceSelector orcaServiceSelector;
  private final Front50Service front50Service;

  public DeploymentSnapshotService(
      ClouddriverServiceSelector clouddriverServiceSelector,
      OrcaServiceSelector orcaServiceSelector,
      Front50Service front50Service) {
    this.clouddriverServiceSelector = clouddriverServiceSelector;
    this.orcaServiceSelector = orcaServiceSelector;
    this.front50Service = front50Service;
  }

  /** Top-level snapshot for one batch of applications. */
  public static class Snapshot {
    public List<AppSnapshot> apps;
  }

  /** Per-application bucket holding the three projected lists. */
  public static class AppSnapshot {
    public String name;
    public List<Map<String, Object>> serverGroups;
    public List<Map<String, Object>> pipelineConfigs;
    public List<Map<String, Object>> executions;
  }

  public Snapshot getSnapshot(
      List<String> applications,
      List<String> pipelineNames,
      Integer pipelineLimit,
      String statuses) {
    Snapshot snapshot = new Snapshot();
    snapshot.apps = new ArrayList<>();
    if (applications == null || applications.isEmpty()) return snapshot;

    final List<String> pipelineNamesOrEmpty =
        pipelineNames == null ? List.of() : pipelineNames;
    final Set<String> pipelineNameSet = new HashSet<>(pipelineNamesOrEmpty);

    // Fan out the three backend calls in parallel. Each one short-circuits to an
    // empty list on failure so a single backend hiccup doesn't blank the dashboard.
    CompletableFuture<List<Map<String, Object>>> sgFuture =
        runAsyncWithAuth(
            () -> {
              @SuppressWarnings("unchecked")
              List<Map<String, Object>> result =
                  Retrofit2SyncCall.execute(
                      clouddriverServiceSelector
                          .select()
                          .getServerGroups(applications, null, null));
              return result;
            },
            "clouddriver serverGroups batch");

    CompletableFuture<List<Map<String, Object>>> pipelinesFuture =
        runAsyncWithAuth(
            () -> {
              @SuppressWarnings({"rawtypes", "unchecked"})
              List<Map<String, Object>> result =
                  (List) Retrofit2SyncCall.execute(front50Service.getAllPipelineConfigs());
              return result;
            },
            "front50 getAllPipelineConfigs");

    final List<String> applicationsForOrca = applications;
    CompletableFuture<List<Map<String, Object>>> execsFuture =
        runAsyncWithAuth(
            () ->
                Retrofit2SyncCall.execute(
                    orcaServiceSelector
                        .select()
                        .getDeploymentSnapshots(
                            applicationsForOrca, pipelineNamesOrEmpty, statuses, pipelineLimit)),
            "orca deploymentSnapshots batch");

    CompletableFuture.allOf(sgFuture, pipelinesFuture, execsFuture).join();
    List<Map<String, Object>> rawServerGroups = sgFuture.join();
    List<Map<String, Object>> rawPipelineConfigs = pipelinesFuture.join();
    List<Map<String, Object>> rawExecutions = execsFuture.join();

    Set<String> wantedApps = new HashSet<>(applications);

    // Bucket each response into per-app slots so the client receives a uniform shape.
    Map<String, AppSnapshot> byApp = new LinkedHashMap<>();
    for (String app : applications) {
      AppSnapshot a = new AppSnapshot();
      a.name = app;
      a.serverGroups = new ArrayList<>();
      a.pipelineConfigs = new ArrayList<>();
      a.executions = new ArrayList<>();
      byApp.put(app, a);
    }

    for (Map<String, Object> sg : rawServerGroups) {
      // Skip disabled server groups outright — every cluster accumulates a long
      // tail of `isDisabled: true` historical SGs and every known caller of this
      // endpoint discards them anyway. Filtering here saves ~85% of the
      // serverGroups payload on a typical fleet snapshot.
      if (Boolean.TRUE.equals(sg.get("isDisabled"))) continue;

      Object appName = sg.get("application");
      // Clouddriver doesn't always echo `application` on its multi-app response, but it does
      // populate `moniker.app` (frigga-derived) and `name` (which starts with the app name).
      // Fall back to those before giving up.
      String resolved = resolveAppForServerGroup(sg, wantedApps);
      if (resolved == null && appName instanceof String && wantedApps.contains(appName))
        resolved = (String) appName;
      if (resolved == null) continue;
      byApp.get(resolved).serverGroups.add(projectServerGroup(sg));
    }

    for (Map<String, Object> p : rawPipelineConfigs) {
      Object app = p.get("application");
      if (!(app instanceof String) || !wantedApps.contains(app)) continue;
      // Apply the same pipelineNames allowlist to configs that Orca applies to executions,
      // so both lists are consistent and the client doesn't receive every pipeline config
      // in the cluster when only specific deploy names were requested.
      if (!pipelineNameSet.isEmpty()) {
        Object name = p.get("name");
        if (!(name instanceof String) || !pipelineNameSet.contains(name)) continue;
      }
      byApp.get(app).pipelineConfigs.add(projectPipelineConfig(p));
    }

    for (Map<String, Object> e : rawExecutions) {
      Object app = e.get("application");
      if (!(app instanceof String) || !wantedApps.contains(app)) continue;
      byApp.get(app).executions.add(e); // already projected by Orca
    }

    snapshot.apps = new ArrayList<>(byApp.values());
    return snapshot;
  }

  /**
   * Best-effort app-name resolution. Clouddriver's multi-app serverGroups payload sometimes
   * omits {@code application} on individual entries; in that case derive it from the Frigga
   * cluster name (everything up to the first "-").
   */
  private static String resolveAppForServerGroup(Map<String, Object> sg, Set<String> wantedApps) {
    Object app = sg.get("application");
    if (app instanceof String && wantedApps.contains(app)) return (String) app;

    Object moniker = sg.get("moniker");
    if (moniker instanceof Map) {
      Object monApp = ((Map<?, ?>) moniker).get("app");
      if (monApp instanceof String && wantedApps.contains(monApp)) return (String) monApp;
    }

    Object cluster = sg.get("cluster");
    if (cluster instanceof String) {
      String c = (String) cluster;
      int dash = c.indexOf('-');
      String head = dash >= 0 ? c.substring(0, dash) : c;
      if (wantedApps.contains(head)) return head;
    }

    Object name = sg.get("name");
    if (name instanceof String) {
      String n = (String) name;
      int dash = n.indexOf('-');
      String head = dash >= 0 ? n.substring(0, dash) : n;
      if (wantedApps.contains(head)) return head;
    }
    return null;
  }

  /**
   * Project a Clouddriver server group response to the surface a deploy-overview dashboard
   * actually consumes. Drops launchConfig blobs, security groups, target group bindings,
   * health-source roll-ups beyond `healthState`, etc.
   */
  private static Map<String, Object> projectServerGroup(Map<String, Object> sg) {
    Map<String, Object> p = new LinkedHashMap<>();
    copy(p, sg, "name");
    copy(p, sg, "cluster");
    copy(p, sg, "region");
    copy(p, sg, "account");
    copy(p, sg, "isDisabled");
    copy(p, sg, "createdTime");
    copy(p, sg, "capacity");
    copy(p, sg, "image");
    copy(p, sg, "buildInfo");

    // Per-instance health is the only thing the dashboard reads off `instances`.
    Object instances = sg.get("instances");
    if (instances instanceof List) {
      List<Map<String, Object>> outInstances = new ArrayList<>();
      for (Object o : (List<?>) instances) {
        if (!(o instanceof Map)) continue;
        Map<?, ?> inst = (Map<?, ?>) o;
        Map<String, Object> pi = new LinkedHashMap<>();
        if (inst.get("healthState") != null) pi.put("healthState", inst.get("healthState"));
        if (inst.get("id") != null) pi.put("id", inst.get("id"));
        outInstances.add(pi);
      }
      p.put("instances", outInstances);
    }

    // The image SHA/version lookup needs ami id from launchConfig if image isn't set.
    Object launchConfig = sg.get("launchConfig");
    if (launchConfig instanceof Map) {
      Object imageId = ((Map<?, ?>) launchConfig).get("imageId");
      if (imageId != null) {
        Map<String, Object> minimalLaunchConfig = new LinkedHashMap<>();
        minimalLaunchConfig.put("imageId", imageId);
        p.put("launchConfig", minimalLaunchConfig);
      }
    }

    // ASG tags carry the docker image version that the deploy stage stamped onto
    // the cluster (Moderne's deploy template writes `Version=<docker-tag>` at
    // createServerGroup time). The bare AWS server-group response carries no
    // docker metadata - the AMI is a generic Ubuntu base, the tag lives only on
    // the ASG itself - so without this projection the dashboard has to backfill
    // imageVersion from the deploy execution, which means pulling executions
    // for every customer tenant just to learn one tag per cell.
    //
    // Clouddriver's ServerGroupViewModel exposes tags as Map<String, Object>
    // (see ServerGroup.getTags); we forward that shape verbatim. The previous
    // List<{key,value}> projection added in #103 was based on the cached AWS
    // shape and never matched the wire shape Clouddriver actually sends - the
    // override on AmazonServerGroup.getTags that surfaces the data lives in
    // this same change.
    Object tags = sg.get("tags");
    if (tags instanceof Map) {
      Map<String, Object> outTags = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : ((Map<?, ?>) tags).entrySet()) {
        if (e.getKey() == null || e.getValue() == null) continue;
        outTags.put(e.getKey().toString(), e.getValue());
      }
      if (!outTags.isEmpty()) p.put("tags", outTags);
    }
    return p;
  }

  /** Pipeline-config projection: just the bits the dashboard needs to identify the pipeline. */
  private static Map<String, Object> projectPipelineConfig(Map<String, Object> pc) {
    Map<String, Object> p = new LinkedHashMap<>();
    copy(p, pc, "id");
    copy(p, pc, "name");
    copy(p, pc, "application");
    copy(p, pc, "disabled");
    return p;
  }

  private static void copy(Map<String, Object> dst, Map<String, Object> src, String key) {
    Object v = src.get(key);
    if (v != null) dst.put(key, v);
  }

  /**
   * Run a fan-out call on the common pool with the caller's auth context propagated, and degrade
   * to an empty list on failure so a single backend hiccup doesn't blank the dashboard. Mirrors
   * the AuthenticatedRequest.propagate pattern used elsewhere in Gate (e.g. ApplicationService).
   */
  private static CompletableFuture<List<Map<String, Object>>> runAsyncWithAuth(
      Callable<List<Map<String, Object>>> work, String label) {
    Callable<List<Map<String, Object>>> propagated = AuthenticatedRequest.propagate(work);
    Supplier<List<Map<String, Object>>> supplier =
        () -> {
          try {
            return propagated.call();
          } catch (Exception e) {
            log.warn("{} failed", label, e);
            return List.of();
          }
        };
    return CompletableFuture.supplyAsync(supplier);
  }
}
