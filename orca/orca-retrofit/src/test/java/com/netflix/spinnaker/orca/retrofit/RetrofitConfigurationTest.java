/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.retrofit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Mockito.mock;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.OkHttpClientComponents;
import com.netflix.spinnaker.orca.config.OrcaConfiguration;
import com.netflix.spinnaker.orca.exceptions.DefaultExceptionHandler;
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler;
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.retrofit.exceptions.SpinnakerServerExceptionHandler;
import java.util.List;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class RetrofitConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(NoopRegistry.class)
          .withBean(TaskExecutorBuilder.class)
          .withAllowBeanDefinitionOverriding(true)
          .withConfiguration(
              UserConfigurations.of(
                  OrcaConfiguration.class,
                  RetrofitConfiguration.class,
                  OkHttpClientComponents.class,
                  TestDependencyConfiguration.class));

  @Test
  void testExceptionHandlerPrecedence() {
    runner
        .withPropertyValues("spring.application.name=orca")
        .run(
            ctx -> {
              AllExceptionHandlers allExceptionHandlers =
                  ctx.getBean("allExceptionHandlers", AllExceptionHandlers.class);
              List<ExceptionHandler> exceptionHandlers =
                  allExceptionHandlers.getExceptionHandlers();

              assertThat(exceptionHandlers).hasSize(2);

              Integer spinnakerServerExceptionHandlerIndex = null;
              Integer defaultExceptionHandlerIndex = null;

              // Figure out where each handler is in the list
              for (ExceptionHandler exceptionHandler : exceptionHandlers) {
                int thisIndex = exceptionHandlers.indexOf(exceptionHandler);
                if (exceptionHandler instanceof SpinnakerServerExceptionHandler) {
                  // Make sure there's exactly one SpinnakerServerExceptionHandler
                  assertThat(spinnakerServerExceptionHandlerIndex).isNull();
                  spinnakerServerExceptionHandlerIndex = thisIndex;
                } else if (exceptionHandler instanceof DefaultExceptionHandler) {
                  // Make sure there's exactly one DefaultExceptionHandler
                  assertThat(defaultExceptionHandlerIndex).isNull();
                  defaultExceptionHandlerIndex = thisIndex;
                } else {
                  fail("Unknown exception handler class: " + exceptionHandler.getClass());
                }
              }

              // Make sure we found each handler
              assertThat(spinnakerServerExceptionHandlerIndex).isNotNull();
              assertThat(defaultExceptionHandlerIndex).isNotNull();

              // Verify that the position of SpinnakerServerExceptionHandler is before
              // DefaultExceptionHandler
              assertThat(spinnakerServerExceptionHandlerIndex)
                  .isLessThan(defaultExceptionHandlerIndex);
            });
  }

  @TestConfiguration
  static class TestDependencyConfiguration {
    @Bean
    ExecutionRepository executionRepository() {
      return mock(ExecutionRepository.class);
    }

    @Bean
    ExecutionRunner executionRunner() {
      return mock(ExecutionRunner.class);
    }

    @Bean
    HttpLoggingInterceptor.Level logLevel() {
      return HttpLoggingInterceptor.Level.NONE;
    }

    @Bean
    AllExceptionHandlers allExceptionHandlers(List<ExceptionHandler> exceptionHandlers) {
      // Store the ExceptionHandler beans in a container object
      return new AllExceptionHandlers(exceptionHandlers);
    }
  }

  /** A place to store the ExceptionHandlers beans */
  static class AllExceptionHandlers {
    private final List<ExceptionHandler> exceptionHandlers;

    public AllExceptionHandlers(List<ExceptionHandler> exceptionHandlers) {
      this.exceptionHandlers = exceptionHandlers;
    }

    public List<ExceptionHandler> getExceptionHandlers() {
      return exceptionHandlers;
    }
  }
}
