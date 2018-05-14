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

package com.netflix.spinnaker.orca.controllers;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import static java.util.stream.Collectors.toSet;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
public class ExecutionQueryController {

  private final ExecutionRepository repository;

  @Autowired
  public ExecutionQueryController(ExecutionRepository repository) {this.repository = repository;}

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/v2/applications/{application}/tasks/", method = GET)
  public Iterable<Execution> list(
    @PathVariable String application,
    @RequestParam(value = "page", defaultValue = "1") int page,
    @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
    @RequestParam(value = "statuses", required = false) Set<ExecutionStatus> statuses
  ) {
    ExecutionCriteria criteria = new ExecutionCriteria()
      .setPage(page)
      .setPageSize(pageSize)
      .setStatuses(statuses.stream().map(ExecutionStatus::name).collect(toSet()));
    return repository
      .retrieveOrchestrationsForApplication(application, criteria)
      .toBlocking()
      .toIterable();
  }

}
