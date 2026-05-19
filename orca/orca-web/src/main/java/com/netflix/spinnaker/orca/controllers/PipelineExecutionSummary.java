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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import com.netflix.spinnaker.orca.pipeline.model.DockerTrigger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal projection of a {@link PipelineExecution} for dashboards that fan out across many
 * applications and only need surface-level status. Drops outputs/context/tasks/notifications and
 * keeps just the fields needed to render a deploy-pipeline overview: ids, status, timestamps,
 * trigger summary, and a stage-summary list.
 *
 * <p>Serializing the projection rather than the full {@code PipelineExecution} cuts each
 * snapshot's wire size by ~20-50x in practice and keeps the dashboard's payload below the
 * point where browser JSON parsing becomes the bottleneck.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineExecutionSummary {

  public String id;
  public String name;
  public String application;
  public String pipelineConfigId;
  public ExecutionStatus status;
  public Long startTime;
  public Long endTime;
  public TriggerView trigger;
  public List<StageView> stages;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class TriggerView {
    public String type;
    public String user;
    /** Parameters bound at trigger time. Carries the docker `tag` for deploy-* pipelines. */
    public Map<String, Object> parameters;
    /** Top-level `tag` field — only set on docker-trigger executions. */
    public String tag;
    public List<ArtifactView> artifacts;
    public List<ExpectedArtifactView> resolvedExpectedArtifacts;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ArtifactView {
    public String type;
    public String name;
    public String version;
    public String reference;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ExpectedArtifactView {
    public ArtifactView boundArtifact;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class StageView {
    public String id;
    public String name;
    public String type;
    public ExecutionStatus status;
    public Long startTime;
    public Long endTime;
    public String refId;
    public Collection<String> requisiteStageRefIds;
    public String parentStageId;
    public String syntheticStageOwner;
    /** Failure message, lifted from `context.exception.details.errors[0]` when not set directly. */
    public String failureMessage;
  }

  public static PipelineExecutionSummary from(PipelineExecution exec) {
    PipelineExecutionSummary v = new PipelineExecutionSummary();
    v.id = exec.getId();
    v.name = exec.getName();
    v.application = exec.getApplication();
    v.pipelineConfigId = exec.getPipelineConfigId();
    v.status = exec.getStatus();
    v.startTime = exec.getStartTime();
    v.endTime = exec.getEndTime();
    v.trigger = projectTrigger(exec.getTrigger());
    v.stages = projectStages(exec.getStages());
    return v;
  }

  private static TriggerView projectTrigger(Trigger t) {
    if (t == null) return null;
    TriggerView tv = new TriggerView();
    tv.type = t.getType();
    tv.user = t.getUser();
    Map<String, Object> params = t.getParameters();
    if (params != null && !params.isEmpty()) {
      tv.parameters = new LinkedHashMap<>(params);
    }
    // DockerTrigger stores `tag` as a typed field (not in `other`), so the generic
    // Trigger interface doesn't expose it. Pull it off the concrete type before
    // falling back to `other` for trigger subclasses that do stash extras there.
    if (t instanceof DockerTrigger) {
      tv.tag = ((DockerTrigger) t).getTag();
    } else {
      Object rawTag = t.getOther() == null ? null : t.getOther().get("tag");
      if (rawTag instanceof String) tv.tag = (String) rawTag;
    }

    if (t.getArtifacts() != null && !t.getArtifacts().isEmpty()) {
      tv.artifacts = new ArrayList<>(t.getArtifacts().size());
      for (var a : t.getArtifacts()) {
        ArtifactView av = new ArtifactView();
        av.type = a.getType();
        av.name = a.getName();
        av.version = a.getVersion();
        av.reference = a.getReference();
        tv.artifacts.add(av);
      }
    }
    if (t.getResolvedExpectedArtifacts() != null && !t.getResolvedExpectedArtifacts().isEmpty()) {
      tv.resolvedExpectedArtifacts = new ArrayList<>(t.getResolvedExpectedArtifacts().size());
      for (var ea : t.getResolvedExpectedArtifacts()) {
        ExpectedArtifactView eav = new ExpectedArtifactView();
        if (ea.getBoundArtifact() != null) {
          ArtifactView av = new ArtifactView();
          av.type = ea.getBoundArtifact().getType();
          av.name = ea.getBoundArtifact().getName();
          av.version = ea.getBoundArtifact().getVersion();
          av.reference = ea.getBoundArtifact().getReference();
          eav.boundArtifact = av;
        }
        tv.resolvedExpectedArtifacts.add(eav);
      }
    }
    return tv;
  }

  private static List<StageView> projectStages(List<StageExecution> stages) {
    if (stages == null || stages.isEmpty()) return null;
    List<StageView> out = new ArrayList<>(stages.size());
    for (StageExecution s : stages) {
      StageView sv = new StageView();
      sv.id = s.getId();
      sv.name = s.getName();
      sv.type = s.getType();
      sv.status = s.getStatus();
      sv.startTime = s.getStartTime();
      sv.endTime = s.getEndTime();
      sv.refId = s.getRefId();
      sv.requisiteStageRefIds = s.getRequisiteStageRefIds();
      sv.parentStageId = s.getParentStageId();
      if (s.getSyntheticStageOwner() != null) {
        sv.syntheticStageOwner = s.getSyntheticStageOwner().toString();
      }
      sv.failureMessage = extractFailureMessage(s);
      out.add(sv);
    }
    return out;
  }

  /**
   * Pull a human-readable failure string off a stage. Spinnaker stores it in two places
   * depending on which task failed: clouddriver tasks set `outputs.failureMessage` directly,
   * while pipeline-level exceptions land under `context.exception.details.errors[0]`. Prefer
   * the explicit outputs.failureMessage and fall back to the exception detail.
   */
  private static String extractFailureMessage(StageExecution s) {
    Map<String, Object> outputs = s.getOutputs();
    if (outputs != null) {
      Object out = outputs.get("failureMessage");
      if (out instanceof String && !((String) out).isEmpty()) return (String) out;
    }
    Map<String, Object> ctx = s.getContext();
    if (ctx == null) return null;
    Object exception = ctx.get("exception");
    if (!(exception instanceof Map)) return null;
    Object details = ((Map<?, ?>) exception).get("details");
    if (!(details instanceof Map)) return null;
    Object errors = ((Map<?, ?>) details).get("errors");
    if (!(errors instanceof List)) return null;
    return Optional.of((List<?>) errors)
        .filter(l -> !l.isEmpty())
        .map(l -> l.get(0))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .orElse(null);
  }
}
