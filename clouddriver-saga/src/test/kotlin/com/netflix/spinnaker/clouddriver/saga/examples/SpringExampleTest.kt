/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.saga.examples

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.event.config.EventSourceAutoConfiguration
import com.netflix.spinnaker.clouddriver.saga.Action1
import com.netflix.spinnaker.clouddriver.saga.Action2
import com.netflix.spinnaker.clouddriver.saga.Action3
import com.netflix.spinnaker.clouddriver.saga.DoAction1
import com.netflix.spinnaker.clouddriver.saga.SagaService
import com.netflix.spinnaker.clouddriver.saga.ShouldBranchPredicate
import com.netflix.spinnaker.clouddriver.saga.config.SagaAutoConfiguration
import com.netflix.spinnaker.clouddriver.saga.flow.SagaCompletionHandler
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.util.function.Predicate
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

/**
 * Shows an example of how to wire up a Saga using Spring!
 */
class SpringExampleTest : JUnit5Minutests {
  fun tests() = rootContext<ApplicationContextRunner> {
    context("a saga flow") {
      fixture {
        ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(
            SagaAutoConfiguration::class.java,
            EventSourceAutoConfiguration::class.java
          ))
      }

      val flow = SagaFlow()
        .then(Action1::class.java)
        .on(ShouldBranchPredicate::class.java) {
          it.then(Action2::class.java)
        }
        .then(Action3::class.java)
        .completionHandler(MyCompletionHandler::class.java)

      test("completes the saga") {
        withUserConfiguration(SagaAutoConfiguration::class.java, DependencyConfiguration::class.java)
          .run { ctx: AssertableApplicationContext ->
            expectThat(ctx.getBean("sagaService")).isA<SagaService>()

            val result = ctx
              .getBean(SagaService::class.java)
              .applyBlocking<String>("test", "test", flow, DoAction1())

            expectThat(result).isEqualTo("yayyyyy complete!")
          }
      }
    }
  }

  @Configuration
  open class DependencyConfiguration {
    @Bean
    open fun registry(): Registry = NoopRegistry()

    @Bean
    open fun action1(): Action1 = Action1()

    @Bean
    open fun action2(): Action2 = Action2()

    @Bean
    open fun action3(): Action3 = Action3()

    @Bean
    open fun shouldBranchPredicate(): Predicate<Saga> = ShouldBranchPredicate()

    @Bean
    open fun myCompletionHandler(): SagaCompletionHandler<String> = MyCompletionHandler()
  }

  private class MyCompletionHandler : SagaCompletionHandler<String> {
    override fun handle(completedSaga: Saga): String? {
      return "yayyyyy complete!"
    }
  }
}
