/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.data.task.jedis;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus;
import com.netflix.spinnaker.clouddriver.data.task.TaskState;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

final class JedisTaskTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String PHASE = "DEPLOY";

  @Test
  void serializationTest() throws Exception {
    RedisTaskRepository taskRepository = mock(RedisTaskRepository.class);
    JedisTask task =
        new JedisTask("123", 100, taskRepository, "owner", "requestId", ImmutableSet.of(), false);

    DefaultTaskStatus oldStatus =
        DefaultTaskStatus.create(PHASE, "Starting deploy", TaskState.STARTED);
    DefaultTaskStatus status =
        DefaultTaskStatus.create(PHASE, "Finished deploy", TaskState.COMPLETED);
    Object results =
        ImmutableMap.of("instances", ImmutableList.of("my-instance-v000", "my-instance-v001"));

    when(taskRepository.getHistory(eq(task))).thenReturn(ImmutableList.of(oldStatus, status));
    when(taskRepository.getResultObjects(eq(task))).thenReturn(ImmutableList.of(results));
    when(taskRepository.currentState(eq(task))).thenReturn(status);

    String result = objectMapper.writeValueAsString(task);
    String expectedResult =
        Resources.toString(JedisTaskTest.class.getResource("task.json"), StandardCharsets.UTF_8);

    // Compare the parsed trees of the two results, which is agnostic to key order
    AssertionsForClassTypes.assertThat(objectMapper.readTree(result))
        .isEqualTo(objectMapper.readTree(expectedResult));
  }

  // See the large comment on the top of JedisTask for this test's rationale
  @Test
  void statusComputedFirst() throws Exception {
    RedisTaskRepository taskRepository = mock(RedisTaskRepository.class);

    JedisTask task =
        new JedisTask("123", 100, taskRepository, "owner", "requestId", ImmutableSet.of(), false);
    when(taskRepository.currentState(task)).thenReturn(new DefaultTaskStatus(TaskState.STARTED));
    objectMapper.writeValueAsString(task);

    InOrder inOrder = Mockito.inOrder(taskRepository);
    inOrder.verify(taskRepository).currentState(eq(task));
    inOrder.verify(taskRepository).getHistory(eq(task));
    inOrder.verify(taskRepository).getResultObjects(eq(task));
  }
}
