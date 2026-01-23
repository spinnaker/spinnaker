/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.services.internal.OrcaService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
public class ExecutionHistoryServiceTest {
  @Mock private OrcaServiceSelector orcaServiceSelector;

  @Mock private OrcaService orcaService;

  @InjectMocks private ExecutionHistoryService executionHistoryService;

  @BeforeEach
  public void setup() {
    when(orcaServiceSelector.select()).thenReturn(orcaService);
  }

  @Test
  public void getTasksReturnsTasksFromOrcaServiceSelector() {
    String app = "myApp";
    Integer page = 0;
    Integer limit = 10;
    String statuses = "SUCCESS,FAILED";
    List<Map<String, Object>> expectedTasks = List.of(Map.of("testTaskKey", "testTaskValue"));
    when(orcaService.getTasks(app, page, limit, statuses))
        .thenReturn(Calls.response(expectedTasks));

    List<Map<String, Object>> tasks = executionHistoryService.getTasks(app, page, limit, statuses);

    assertEquals(expectedTasks, tasks);
  }

  @Test
  public void getPipelinesPassesParamsToOrca() {
    String app = "myApp";
    Integer limit = 10;
    String statuses = "SUCCESS,FAILED";
    Boolean expand = false;
    String pipelineNameFilter = "name1";
    Integer pipelineLimit = 1;
    List<Map<String, Object>> expectedPipelines =
        List.of(Map.of("name", "testName1"), Map.of("name", "testName2"));
    when(orcaService.getPipelines(app, limit, statuses, expand, pipelineNameFilter, pipelineLimit))
        .thenReturn(Calls.response(expectedPipelines));

    List<Map<String, Object>> pipelines =
        executionHistoryService.getPipelines(
            app, limit, statuses, expand, pipelineNameFilter, pipelineLimit);

    assertEquals(expectedPipelines, pipelines);
    verify(orcaService)
        .getPipelines(app, limit, statuses, expand, pipelineNameFilter, pipelineLimit);
    verifyNoMoreInteractions(orcaService);
  }
}
