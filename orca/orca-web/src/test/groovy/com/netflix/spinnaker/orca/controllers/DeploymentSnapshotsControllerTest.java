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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {DeploymentSnapshotsController.class}, webEnvironment = MOCK)
@AutoConfigureMockMvc
@EnableWebMvc
@WithMockUser("dashboard")
class DeploymentSnapshotsControllerTest {

  @MockBean ExecutionRepository executionRepository;
  @MockBean com.netflix.spinnaker.orca.front50.Front50Service front50Service;

  @Autowired MockMvc mvc;

  @Test
  void returnsProjectedSummaryForBatchOfApplications() throws Exception {
    when(executionRepository.retrievePipelineExecutionsForApplications(any(), any(), any(), anyInt()))
        .thenReturn(List.of(buildExecution("svc-a", "exec-1"), buildExecution("svc-b", "exec-2")));

    mvc.perform(get("/deploymentSnapshots").param("applications", "svc-a,svc-b"))
        .andExpect(status().is2xxSuccessful())
        // Both apps are represented in the response.
        .andExpect(jsonPath("$[*].application").value(
            org.hamcrest.Matchers.containsInAnyOrder("svc-a", "svc-b")))
        // The projection drops outputs/context but keeps stages + trigger.
        .andExpect(jsonPath("$[0].stages").isArray())
        .andExpect(jsonPath("$[0].trigger.type").value("manual"));
  }

  @Test
  void emptyApplicationsListShortCircuits() throws Exception {
    mvc.perform(get("/deploymentSnapshots").param("applications", ""))
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().json("[]"));

    // Repo must not be touched when no apps are requested — we don't want to
    // accidentally trigger a full-table scan.
    verify(executionRepository, never())
        .retrievePipelineExecutionsForApplications(any(), any(), any(), anyInt());
  }

  @Test
  void projectsFailureMessageFromStageContext() throws Exception {
    PipelineExecution exec = buildExecution("svc-a", "exec-3");
    StageExecutionImpl failedStage = (StageExecutionImpl) exec.getStages().get(0);
    failedStage.setStatus(ExecutionStatus.TERMINAL);
    failedStage.setContext(
        Map.of(
            "exception",
            Map.of("details", Map.of("errors", List.of("ASG never reached desired capacity")))));

    when(executionRepository.retrievePipelineExecutionsForApplications(any(), any(), any(), anyInt()))
        .thenReturn(List.of(exec));

    mvc.perform(get("/deploymentSnapshots").param("applications", "svc-a"))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$[0].stages[0].failureMessage")
            .value("ASG never reached desired capacity"));
  }

  private PipelineExecution buildExecution(String application, String id) {
    PipelineExecutionImpl exec = new PipelineExecutionImpl(ExecutionType.PIPELINE, application);
    exec.setId(id);
    exec.setName("deploy-" + application);
    exec.setStatus(ExecutionStatus.SUCCEEDED);
    exec.setStartTime(1_000L);
    exec.setEndTime(2_000L);
    exec.setTrigger(new DefaultTrigger("manual", null, "alice"));

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setExecution(exec);
    stage.setId("stage-1");
    stage.setRefId("1");
    stage.setName("Deploy");
    stage.setType("deploy");
    stage.setStatus(ExecutionStatus.SUCCEEDED);
    exec.getStages().add(stage);
    return exec;
  }
}
