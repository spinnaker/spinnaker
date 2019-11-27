/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.controllers;

import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import com.netflix.spinnaker.orca.model.ExecutionImportResponse;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import retrofit.RetrofitError;

@RestController
@RequestMapping("/admin/executions")
@Slf4j
@ConditionalOnProperty(value = "executions.import.enabled", matchIfMissing = false)
public class ExecutionsImportController {

  private final Front50Service front50Service;

  private final ExecutionRepository executionRepository;

  private Set<ExecutionStatus> ALLOWED_STATUSES =
      Collections.unmodifiableSet(
          Stream.of(ExecutionStatus.CANCELED, ExecutionStatus.SUCCEEDED, ExecutionStatus.TERMINAL)
              .collect(Collectors.toSet()));

  @Autowired
  ExecutionsImportController(
      ExecutionRepository executionRepository, Front50Service front50Service) {
    this.front50Service = front50Service;
    this.executionRepository = executionRepository;
  }

  @PostMapping(value = "")
  @ResponseStatus(HttpStatus.CREATED)
  ExecutionImportResponse createExecution(@RequestBody Execution execution) {

    // Check if app exists before importing execution.
    Application application = null;
    try {
      application = front50Service.get(execution.getApplication());
      log.info("Importing application with name: {}", application.name);
    } catch (RetrofitError e) {
      if (e.getKind() == RetrofitError.Kind.HTTP && e.getResponse().getStatus() != 404) {
        log.warn("Exception received while retrieving application from front50", e);
      }
    }

    if (application == null) {
      log.info(
          "Application {} not found in front50, but still importing it",
          execution.getApplication());
    }

    // Continue importing even if we can't retrieve the APP.
    try {
      executionRepository.retrieve(execution.getType(), execution.getId());
      throw new InvalidRequestException("Execution already exists with id: " + execution.getId());
    } catch (ExecutionNotFoundException e) {
      log.info("Execution not found: {}, Will continue with importing..", execution.getId());
    }

    if (ALLOWED_STATUSES.contains(execution.getStatus())) {
      log.info(
          "Importing execution with id: {}, status: {} , stages: {}",
          execution.getId(),
          execution.getStatus(),
          execution.getStages().size());
      execution
          .getStages()
          .forEach(
              stage -> {
                stage.setExecution(execution);
              });
      executionRepository.store(execution);
      return new ExecutionImportResponse(
          execution.getId(), execution.getStatus(), execution.getStages().size());
    }

    throw new InvalidRequestException(
        "Cannot import provided execution, Status: " + execution.getStatus());
  }
}
