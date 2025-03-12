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

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.kork.archaius.ArchaiusAutoConfiguration
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusPublisher
import com.netflix.spinnaker.kork.eureka.EurekaAutoConfiguration
import com.netflix.spinnaker.kork.eureka.EurekaStatusSubscriber
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.util.*

class EurekaSystemTest : JUnit5Minutests {

  fun tests() = rootContext {
    derivedContext<ApplicationContextRunner>("no configuration") {
      fixture {
        ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(
            TestConfiguration::class.java,
            ArchaiusAutoConfiguration::class.java,
            EurekaAutoConfiguration::class.java
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
            ArchaiusAutoConfiguration::class.java,
            EurekaAutoConfiguration::class.java
          )).withUserConfiguration(UserConfiguration::class.java)
      }

      test("configures eureka components") {
        run { ctx: AssertableApplicationContext ->
          expect {
            that(ctx.getBeansOfType(DiscoveryStatusPublisher::class.java)).and {
              get { size }.isEqualTo(1)
              get { values.first() }.isA<EurekaStatusSubscriber>()
            }
          }
          expect {
            that(ctx.getBeansOfType(EurekaClientConsumer::class.java)).and {
              get { size }.isEqualTo(1)
              get { values.first() }.isA<EurekaClientConsumer>()
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

/**
 * This configuration will trigger direct initialization of the beans in EurekaAutoConfiguration
 * and will fail if ArchaiusAutoConfiguration hasn't happened first because the EurekaConfig
 * won't pick up the properties from the spring test environment.
 *
 * Ensures we don't have a startup order regression.
 */
@Configuration
private open class UserConfiguration {

  @Bean
  open fun eurekaClientConsumer(eurekaClient: Optional<EurekaClient>) = EurekaClientConsumer(eurekaClient)
}

private open class EurekaClientConsumer(val eurekaClient: Optional<EurekaClient>)
