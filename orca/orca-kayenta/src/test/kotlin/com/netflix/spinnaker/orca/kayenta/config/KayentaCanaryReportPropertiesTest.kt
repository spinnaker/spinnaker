/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.orca.kayenta.config

import com.netflix.spinnaker.orca.kayenta.tasks.CanaryReportLinks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class KayentaCanaryReportPropertiesTest {

  @Configuration
  @EnableConfigurationProperties(KayentaCanaryReportProperties::class)
  class TestConfig {
    @Bean
    fun canaryReportLinks(properties: KayentaCanaryReportProperties) = CanaryReportLinks(properties)
  }

  private val runner = ApplicationContextRunner().withUserConfiguration(TestConfig::class.java)

  @Test
  fun `binds kayenta canary-report url-template from the environment`() {
    runner.withPropertyValues("kayenta.canary-report.url-template=https://deck.example.com/{0}/{1}/{2}")
      .run { context ->
        assertThat(context.getBean(KayentaCanaryReportProperties::class.java).urlTemplate)
          .isEqualTo("https://deck.example.com/{0}/{1}/{2}")
        assertThat(context.getBean(CanaryReportLinks::class.java).reportUrl("app", "cfg", "run"))
          .isEqualTo("https://deck.example.com/app/cfg/run")
      }
  }

  @Test
  fun `binds the camelCase form too (relaxed binding)`() {
    runner.withPropertyValues("kayenta.canaryReport.urlTemplate=https://camel.example.com/{0}")
      .run { context ->
        assertThat(context.getBean(KayentaCanaryReportProperties::class.java).urlTemplate)
          .isEqualTo("https://camel.example.com/{0}")
      }
  }

  @Test
  fun `uses the default template when the property is unset`() {
    runner.run { context ->
      assertThat(context.getBean(KayentaCanaryReportProperties::class.java).urlTemplate)
        .isEqualTo(KayentaCanaryReportProperties.DEFAULT_URL_TEMPLATE)
      assertThat(context.getBean(CanaryReportLinks::class.java).reportUrl("app", "cfg", "run"))
        .isEqualTo("#/applications/app/canary/report/cfg/run")
    }
  }

  @Test
  fun `binds kayenta canary-report documentation-url and leaves it unset by default`() {
    runner.withPropertyValues("kayenta.canary-report.documentation-url=https://docs.example.com/documentation")
      .run { context ->
        assertThat(context.getBean(KayentaCanaryReportProperties::class.java).documentationUrl)
          .isEqualTo("https://docs.example.com/documentation")
      }
    runner.run { context ->
      assertThat(context.getBean(KayentaCanaryReportProperties::class.java).documentationUrl)
        .isNull()
    }
  }

  @Test
  fun `a blank documentation-url binds as blank so the documentation sentence is omitted`() {
    runner.withPropertyValues("kayenta.canary-report.documentation-url=")
      .run { context ->
        val message = context.getBean(CanaryReportLinks::class.java)
          .scoreFailureMessage(76, 80, CanaryReportLinks.ThresholdKind.MARGINAL, null, null, null)
        assertThat(message).doesNotContain("documentation")
      }
  }
}
