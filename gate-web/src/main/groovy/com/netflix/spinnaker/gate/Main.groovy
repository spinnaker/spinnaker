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

package com.netflix.spinnaker.gate
import com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsStreamServlet
import com.netflix.spinnaker.hystrix.spectator.HystrixSpectatorConfig
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.embedded.ServletRegistrationBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@Configuration
@EnableConfigurationProperties
@Import([HystrixSpectatorConfig])
@ComponentScan(["com.netflix.spinnaker.gate", "com.netflix.spinnaker.config"])
@EnableAutoConfiguration(exclude = [SecurityAutoConfiguration, GroovyTemplateAutoConfiguration])
class Main extends SpringBootServletInitializer {
  static final Map<String, String> DEFAULT_PROPS = [
          'netflix.environment': 'test',
          'netflix.account': '${netflix.environment}',
          'netflix.stack': 'test',
          'spring.config.location': '${user.home}/.spinnaker/',
          'spring.application.name': 'gate',
          'spring.config.name': 'spinnaker,${spring.application.name}',
          'spring.profiles.active': '${netflix.environment},local'
  ]

  static void main(String... args) {
    new SpringApplicationBuilder().properties(DEFAULT_PROPS).sources(Main).run(args)
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
    builder.properties(DEFAULT_PROPS).sources(Main)
  }

  @Bean
  ServletRegistrationBean hystrixEventStream() {
    new ServletRegistrationBean(new HystrixMetricsStreamServlet(), '/hystrix.stream')
  }
}
