/*
 * Copyright 2025 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.web.testconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.TaskControllerConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.web.filters.ProvidedIdRequestFilterConfigurationProperties;
import com.netflix.spinnaker.orca.capabilities.CapabilitiesService;
import com.netflix.spinnaker.orca.commands.ForceExecutionCancellationCommand;
import com.netflix.spinnaker.orca.config.ExecutionConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator;
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher;
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplateService;
import com.netflix.spinnaker.orca.web.config.WebConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class WebConfigurationTest {

  // WebConfiguration has an @EnableFiatAutoConfig annotation, which brings in
  // fiat's FiatAuthenticationConfig's class.  FiatAuthenticationConfig has
  //
  // @ComponentScan("com.netflix.spinnaker.fiat.shared")
  //
  // which creates a FiatAccessDeniedExceptionHandler bean because it has
  // a @ControllerAdvice annotation.
  //
  // FiatAuthenticationConfig also provides a FiatAccessDeniedExceptionHandler
  // bean.  Until that's fixed, use
  //
  // .allowBeanDefinitionOverriding(true).
  //
  // Because WebConfiguration also brings in all of orca's controllers, supply
  // all the beans they depend on.
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(NoopRegistry.class)
          .withAllowBeanDefinitionOverriding(true)
          .withConfiguration(
              UserConfigurations.of(TestDependencyConfiguration.class, WebConfiguration.class));

  @Test
  void metricsInterceptorPresent() {
    // A basic assertion about a bean that WebConfiguration provides.
    runner.run(ctx -> assertThat(ctx).hasBean("metricsInterceptor"));
  }

  @Test
  void testProvidedIdRequestFilterBeanCreatedWhenPropertyEnabled() {
    runner
        .withPropertyValues("provided-id-request-filter.enabled=true")
        .run(
            ctx -> {
              assertThat(ctx).hasBean("providedIdRequestFilter");
              assertThat(ctx).hasSingleBean(ProvidedIdRequestFilterConfigurationProperties.class);
            });
  }

  @Test
  void testProvidedIdRequestFilterBeanNotCreatedWhenPropertyDisabled() {
    runner
        .withPropertyValues("provided-id-request-filter.enabled=false")
        .run(
            ctx -> {
              assertThat(ctx).doesNotHaveBean("providedIdRequestFilter");
              assertThat(ctx).hasSingleBean(ProvidedIdRequestFilterConfigurationProperties.class);
            });
  }

  @Test
  void testProvidedIdRequestFilterBeanNotCreatedWhenPropertyNotSet() {
    runner.run(
        ctx -> {
          assertThat(ctx).doesNotHaveBean("providedIdRequestFilter");
          assertThat(ctx).hasSingleBean(ProvidedIdRequestFilterConfigurationProperties.class);
        });
  }

  static class TestDependencyConfiguration {
    @Bean
    ExecutionRepository executionRepository() {
      return mock(ExecutionRepository.class);
    }

    @Bean
    ForceExecutionCancellationCommand forceExecutionCancellationCommand() {
      return mock(ForceExecutionCancellationCommand.class);
    }

    @Bean
    CapabilitiesService capabilitiesService() {
      return mock(CapabilitiesService.class);
    }

    @Bean
    ExecutionLauncher executionLauncher() {
      return mock(ExecutionLauncher.class);
    }

    @Bean
    ContextParameterProcessor contextParameterProcessor() {
      return mock(ContextParameterProcessor.class);
    }

    @Bean
    FiatService fiatService() {
      return mock(FiatService.class);
    }

    @Bean
    DynamicConfigService dynamicConfigService() {
      return mock(DynamicConfigService.class);
    }

    @Bean
    PipelineTemplateService pipelineTemplateService() {
      return mock(PipelineTemplateService.class);
    }

    @Bean
    ExecutionRunner executionRunner() {
      return mock(ExecutionRunner.class);
    }

    @Bean
    CompoundExecutionOperator compoundExecutionOperator() {
      return mock(CompoundExecutionOperator.class);
    }

    @Bean
    StageDefinitionBuilderFactory stageDefinitionBuilderFactory() {
      return mock(StageDefinitionBuilderFactory.class);
    }

    @Bean
    TaskControllerConfigurationProperties taskControllerConfigurationProperties() {
      return mock(TaskControllerConfigurationProperties.class);
    }

    @Bean
    ExecutionConfigurationProperties executionConfigurationProperties() {
      return mock(ExecutionConfigurationProperties.class);
    }
  }
}
