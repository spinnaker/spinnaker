/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.clouddriver.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.clouddriver.data.task.InMemoryTaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.OperationsSaturatedException;
import com.netflix.spinnaker.clouddriver.orchestration.OperationsService;
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator;
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Orca retries a 503 from clouddriver but fails the stage on a 500, so an instance that is running
 * as many operations as it allows must answer 503 with {@code Retry-After}. Runs the real handler
 * alongside kork's {@link GenericExceptionHandlers}, which would otherwise answer without the
 * header.
 */
class OperationsControllerSaturatedTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    OperationsService operationsService = mock(OperationsService.class);
    when(operationsService.collectAtomicOperations(anyString(), anyList()))
        .thenReturn(List.of(mock(AtomicOperation.class)));
    OrchestrationProcessor processor = mock(OrchestrationProcessor.class);
    when(processor.process(any(), any(), anyString()))
        .thenThrow(new OperationsSaturatedException(500, 5));
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new OperationsController(
                    operationsService, processor, new InMemoryTaskRepository(), 5))
            .setControllerAdvice(
                new OperationsSaturatedExceptionHandler(),
                new GenericExceptionHandlers(
                    new ExceptionMessageDecorator(mock(ObjectProvider.class))))
            .build();
  }

  @Test
  void startingAnOperationReturnsServiceUnavailableWithRetryAfter() throws Exception {
    mockMvc
        .perform(post("/aws/ops").contentType(MediaType.APPLICATION_JSON).content("[]"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "5"));
  }
}
