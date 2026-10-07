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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.clouddriver.data.task.jedis.RedisTaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.OperationsService;
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator;
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Orca retries a 503 from clouddriver but fails the stage on a 500, so a task store outage must
 * surface as 503. Runs the real {@link RedisTaskRepository} retry policy and kork's {@link
 * GenericExceptionHandlers} rather than asserting on the exception type.
 */
class OperationsControllerTaskStoreUnavailableTest {

  private RedisClientDelegate redisClientDelegate;
  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    redisClientDelegate = mock(RedisClientDelegate.class);
    OperationsController controller =
        new OperationsController(
            mock(OperationsService.class),
            mock(OrchestrationProcessor.class),
            new RedisTaskRepository(redisClientDelegate, Optional.empty()),
            5);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GenericExceptionHandlers(
                    new ExceptionMessageDecorator(mock(ObjectProvider.class))))
            .build();
  }

  @Test
  void getTaskReturnsServiceUnavailableWhenRedisIsUnreachable() throws Exception {
    doThrow(new JedisConnectionException("Redis unreachable"))
        .when(redisClientDelegate)
        .withCommandsClient(any(Function.class));

    mockMvc.perform(get("/task/abc")).andExpect(status().isServiceUnavailable());
  }

  @Test
  void getTaskStillReturnsNotFoundWhenRedisIsHealthyAndTheTaskIsMissing() throws Exception {
    doReturn(Map.of()).when(redisClientDelegate).withCommandsClient(any(Function.class));

    mockMvc.perform(get("/task/abc")).andExpect(status().isNotFound());
  }
}
