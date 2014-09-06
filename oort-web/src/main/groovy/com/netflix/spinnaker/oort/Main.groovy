/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.oort

import com.codahale.metrics.JmxReporter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import com.netflix.appinfo.InstanceInfo
import com.ryantenney.metrics.spring.config.annotation.EnableMetrics
import com.ryantenney.metrics.spring.config.annotation.MetricsConfigurer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component
import org.springframework.web.filter.ShallowEtagHeaderFilter

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.servlet.Filter

@Configuration
@ComponentScan("com.netflix.spinnaker.oort")
@EnableAutoConfiguration
@EnableScheduling
@EnableAsync
@EnableMetrics
class Main extends SpringBootServletInitializer {

  static {
    //imposeSpinnakerFileConfig("oort-internal.yml")
    //imposeSpinnakerFileConfig("oort-local.yml")
    //imposeSpinnakerClasspathConfig("oort-internal.yml")
    //imposeSpinnakerClasspathConfig("oort-local.yml")
  }

  static void main(_) {
    System.setProperty("netflix.environment", System.getProperty("netflix.environment", "test"))
    SpringApplication.run this, [] as String
  }

  static void imposeSpinnakerFileConfig(String file) {
    def internalConfig = new File("${System.properties['user.home']}/.spinnaker/${file}")
    if (internalConfig.exists()) {
      System.setProperty("spring.config.location", "${System.properties["spring.config.location"]},${internalConfig.canonicalPath}")
    }
  }

  static void imposeSpinnakerClasspathConfig(String resource) {
    def internalConfig = getClass().getResourceAsStream("/${resource}")
    if (internalConfig) {
      System.setProperty("spring.config.location", "${System.properties["spring.config.location"]},classpath:/${resource}")
    }
  }

  @Bean
  InstanceInfo.InstanceStatus instanceStatus() {
    InstanceInfo.InstanceStatus.UNKNOWN
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    System.setProperty("netflix.environment", System.getProperty("netflix.environment", "test"))
    application.sources Main
  }

  @Bean
  Filter eTagFilter() {
    new ShallowEtagHeaderFilter()
  }

  @Component
  static class JmxReporterBean {
    @Autowired
    MetricRegistry metricRegistry

    JmxReporter reporter

    @PostConstruct
    void init() {
      reporter = JmxReporter.forRegistry(metricRegistry).build()
      reporter.start()
    }

    @PreDestroy
    void destroy() {
      reporter.stop()
      reporter = null
    }
  }

  @Configuration
  static class MetricsConfigurerConfig {

    @Bean
    MetricsConfigurer metricsConfigurer(final MetricRegistry metricRegistry) {
      new MetricsConfigurer() {
        @Override
        void configureReporters(MetricRegistry reg) {

        }

        @Override
        MetricRegistry getMetricRegistry() {
          return metricRegistry
        }

        @Override
        HealthCheckRegistry getHealthCheckRegistry() {
          return null
        }
      }
    }
  }
}
