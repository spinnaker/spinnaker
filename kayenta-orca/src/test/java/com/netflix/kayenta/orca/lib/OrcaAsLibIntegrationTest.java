/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.orca.lib;

import com.netflix.discovery.StatusChangeEvent;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.QueueConfiguration;
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.batch.exceptions.DefaultExceptionHandler;
import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator;
import com.netflix.spinnaker.orca.q.DummyTask;
import com.netflix.spinnaker.orca.q.ExecutionLatchKt;
import com.netflix.spinnaker.orca.q.PipelineBuilderKt;
import com.netflix.spinnaker.orca.q.Queue;
import com.netflix.spinnaker.orca.q.QueueExecutionRunner;
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Duration;

import static com.netflix.appinfo.InstanceInfo.InstanceStatus.OUT_OF_SERVICE;
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.STARTING;
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {TestConfig.class})
public class OrcaAsLibIntegrationTest {

  Logger log = LoggerFactory.getLogger(OrcaAsLibIntegrationTest.class);

  @Autowired
  Queue queue;

  @Autowired
  QueueExecutionRunner runner;

  @Autowired
  ExecutionRepository repository;

  @Autowired
  DummyTask dummyTask;

  @Autowired
  ConfigurableApplicationContext context;

  @Before
  public void discoveryUp() {
    context.publishEvent(new RemoteStatusChangedEvent(new StatusChangeEvent(STARTING, UP)));
  }

  @After
  public void discoveryDown() {
    context.publishEvent(new RemoteStatusChangedEvent(new StatusChangeEvent(UP, OUT_OF_SERVICE)));
  }

  @After
  public void resetMocks() {
    reset(dummyTask);
  }

  @Test
  public void canRunASimplePipeline() {
    Pipeline pipeline = PipelineBuilderKt.pipeline(p -> {
      p.setApplication("spinnaker");

      PipelineBuilderKt.stage(p, s -> {
        s.setRefId("1");
        s.setType("dummy");

        return null;
      });

      return null;
    });

    repository.store(pipeline);

    when(dummyTask.getTimeout()).thenReturn(Duration.ofSeconds(2).toMillis());
    when(dummyTask.execute(any())).thenReturn(TaskResult.SUCCEEDED);

    ExecutionLatchKt.runToCompletion(
      context,
      pipeline,
      (p) -> {
        runner.start(p);

        return null;
      },
      repository);

    assertEquals(repository.retrievePipeline(pipeline.getId()).getStatus(), ExecutionStatus.SUCCEEDED);
  }
}

@Configuration
@Import({
  PropertyPlaceholderAutoConfiguration.class,
  QueueConfiguration.class,
  EmbeddedRedisConfiguration.class,
  JedisExecutionRepository.class,
  StageNavigator.class,
  RestrictExecutionDuringTimeWindow.class
})

class TestConfig {

  @Bean
  Registry registry() {
    return new NoopRegistry();
  }

  @Bean
  DummyTask dummyTask() {
    DummyTask dummyTaskMock = mock(DummyTask.class);

    when(dummyTaskMock.getTimeout()).thenReturn(Duration.ofMinutes(2).toMillis());

    return dummyTaskMock;
  }

  @Bean
  StageDefinitionBuilder dummyStage(){
    return new StageDefinitionBuilder() {
      @Override
      public <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
        builder.withTask("dummy", DummyTask.class);
      }

      @Override
      public String getType() {
        return "dummy";
      }
    };
  }

  @Bean
  String currentInstanceId() {
    return "localhost";
  }

  @Bean
  ContextParameterProcessor contextParameterProcessor() {
    return new ContextParameterProcessor();
  }

  @Bean
  DefaultExceptionHandler defaultExceptionHandler() {
    return new DefaultExceptionHandler();
  }
}
