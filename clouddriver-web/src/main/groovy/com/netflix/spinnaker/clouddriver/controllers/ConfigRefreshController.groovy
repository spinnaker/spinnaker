/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.Main
import com.netflix.spinnaker.clouddriver.events.ConfigRefreshedEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.Banner
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/config-refresh")
class ConfigRefreshController {

  @Autowired
  ConfigurableEnvironment environment

  @Autowired
  ApplicationContext appContext

  @Autowired
  ApplicationEventPublisher publisher

  @RequestMapping(method = RequestMethod.POST)
  void refresh() {
    def env = new StandardEnvironment()
    def app = new SpringApplicationBuilder()
      .properties(Main.DEFAULT_PROPS)
      .sources(NoBeans)
      .web(false)
      .headless(true)
      .bannerMode(Banner.Mode.OFF)
      .addCommandLineProperties(true)
      .logStartupInfo(false)
      .environment(env)
      .build()

    def ctx = app.run(Main.launchArgs)
    def currentProps = ctx.environment.propertySources

    for (PropertySource ps : environment.propertySources) {
      if (currentProps.get(ps.name)) {
        environment.propertySources.replace(ps.name, currentProps.get(ps.name))
      }
    }

    ctx.close()

    publisher.publishEvent(new ConfigRefreshedEvent(appContext))
  }

  @Configuration
  private static class NoBeans {}
}
