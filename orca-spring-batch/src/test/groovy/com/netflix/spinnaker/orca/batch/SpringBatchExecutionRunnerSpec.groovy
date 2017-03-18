/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.batch.listeners.SpringBatchExecutionListenerProvider
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.ExecutionRunnerSpec
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.tasks.NoOpTask
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.spockframework.spring.xml.SpockMockFactoryBean
import org.springframework.beans.factory.FactoryBean
import org.springframework.context.annotation.Bean
import org.springframework.retry.backoff.Sleeper
import org.springframework.test.context.ContextConfiguration

@ContextConfiguration(
  classes = [
    BatchTestConfiguration, TaskTaskletAdapterImpl,
    SpringBatchExecutionListenerProvider, Config, NoOpTask
  ]
)
class SpringBatchExecutionRunnerSpec extends ExecutionRunnerSpec {

  @Override
  ExecutionRunner create(StageDefinitionBuilder... stageDefBuilders) {
    applicationContext.with {
      stageDefBuilders.each {
        beanFactory.registerSingleton(it.type, it)
      }
      autowireCapableBeanFactory.createBean(SpringBatchExecutionRunner)
    }
  }

  @CompileStatic
  static class Config {
    @Bean
    FactoryBean<ExceptionHandler> exceptionHandler() {
      new SpockMockFactoryBean(ExceptionHandler)
    }

    @Bean
    FactoryBean<Sleeper> sleeper() { new SpockMockFactoryBean(Sleeper) }
  }
}
