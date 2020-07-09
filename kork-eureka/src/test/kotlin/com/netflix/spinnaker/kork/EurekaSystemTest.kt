/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.kork

import com.netflix.spinnaker.kork.discovery.DiscoveryStatusPublisher
import com.netflix.spinnaker.kork.discovery.NoDiscoveryStatusPublisher
import com.netflix.spinnaker.kork.eureka.EurekaStatusSubscriber
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class EurekaSystemTest : JUnit5Minutests {

  fun tests() = rootContext {
    derivedContext<ApplicationContextRunner>("no configuration") {
      fixture {
        ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(
            TestConfiguration::class.java,
            EurekaDiscoveryAutoConfiguration::class.java
          ))
      }

      test("does nothing when disabled") {
        run { ctx: AssertableApplicationContext ->
          expect {
            that(ctx.getBeansOfType(DiscoveryStatusPublisher::class.java)).get { size }.isEqualTo(0)
          }
        }
      }
    }

    derivedContext<ApplicationContextRunner>("eureka enabled") {
      fixture {
        ApplicationContextRunner()
          .withPropertyValues(
            "archaius.enabled=true",
            "eureka.enabled=true",
            // needed to avoid trying to get instance metadata during tests
            "netflix.appinfo.validateInstanceId=false"
          )
          .withConfiguration(AutoConfigurations.of(
            TestConfiguration::class.java,
            HealthEndpointAutoConfiguration::class.java,
            EurekaDiscoveryAutoConfiguration::class.java
          ))
      }

      test("configures eureka components") {
        run { ctx: AssertableApplicationContext ->
          expect {
            that(ctx.getBeansOfType(DiscoveryStatusPublisher::class.java)).and {
              get { size }.isEqualTo(1)
              get { values.first() }.isA<EurekaStatusSubscriber>()
            }
          }
        }
      }
    }
  }
}

@Configuration
private open class TestConfiguration {

  @Bean
  open fun meterRegistry() = LoggingMeterRegistry()
}
