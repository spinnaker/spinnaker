/*
 * Copyright 2023 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.cats.redis.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.DefaultAgentIntervalProvider;
import com.netflix.spinnaker.cats.test.TestAgent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class ClusteredSortAgentSchedulerTest {

  private ClusteredSortAgentScheduler clusteredSortAgentScheduler;

  private Jedis jedis = mock(Jedis.class);
  private JedisPool jedisPool = mock(JedisPool.class);
  private Integer parallelism = 2;
  private CachingAgent.CacheExecution agentExecution = mock(CachingAgent.CacheExecution.class);
  private ExecutionInstrumentation executionInstrumentation = mock(ExecutionInstrumentation.class);

  private Optional<Semaphore> runningAgents;

  @BeforeEach
  public void setUp() throws IllegalAccessException {
    when(jedisPool.getResource()).thenReturn(jedis);
    when(jedis.scriptLoad(anyString())).thenReturn("testScriptSha");
    when(jedis.time()).thenReturn(List.of("1678784468", "374338"));
    DefaultAgentIntervalProvider intervalProvider = new DefaultAgentIntervalProvider(6000000);
    clusteredSortAgentScheduler =
        new ClusteredSortAgentScheduler(jedisPool, () -> false, intervalProvider, parallelism);

    runningAgents =
        (Optional<Semaphore>)
            FieldUtils.getDeclaredField(ClusteredSortAgentScheduler.class, "runningAgents", true)
                .get(clusteredSortAgentScheduler);
  }

  @Test
  public void testRunningAgentsSemaphore() {
    when(jedis.zrangeByScore(eq(ClusteredSortAgentScheduler.WORKING_SET), anyString(), anyString()))
        .thenReturn(new ArrayList<>());
    when(jedis.zrangeByScore(eq(ClusteredSortAgentScheduler.WAITING_SET), anyString(), anyString()))
        .thenReturn(List.of("testAgentType"));

    clusteredSortAgentScheduler.saturatePool();

    assertThat(runningAgents)
        .isPresent()
        .hasValueSatisfying(s -> assertThat(s.availablePermits()).isEqualTo(parallelism));
  }

  @Test
  public void testRunningAgentsSemaphoreWithException() throws InterruptedException {
    TestAgent agent1 = new TestAgent();
    TestAgent agent2 = new TestAgent();
    CountDownLatch latch = new CountDownLatch(1);

    when(jedis.zrangeByScore(eq(ClusteredSortAgentScheduler.WORKING_SET), anyString(), anyString()))
        .thenReturn(new ArrayList<>());
    when(jedis.zrangeByScore(eq(ClusteredSortAgentScheduler.WAITING_SET), anyString(), anyString()))
        .thenReturn(List.of(agent1.getAgentType(), agent2.getAgentType()));
    when(jedis.scriptExists(anyString())).thenReturn(true);
    clusteredSortAgentScheduler.schedule(agent1, agentExecution, executionInstrumentation);
    clusteredSortAgentScheduler.schedule(agent2, agentExecution, executionInstrumentation);
    when(jedis.evalsha(anyString(), anyList(), anyList()))
        .thenReturn("testReleaseScore")
        .thenThrow(new RuntimeException("fail"))
        .thenReturn("testReleaseScore");
    when(agentExecution.executeAgentWithoutStore(any()))
        .thenReturn(new DefaultCacheResult(new HashMap<>()));
    doAnswer(
            (invocation) -> {
              latch.countDown();
              return null;
            })
        .when(agentExecution)
        .storeAgentResult(any(), any());

    clusteredSortAgentScheduler.saturatePool();
    latch.await(10, TimeUnit.SECONDS);

    assertThat(runningAgents)
        .isPresent()
        .hasValueSatisfying(s -> assertThat(s.availablePermits()).isEqualTo(parallelism));
  }
}
