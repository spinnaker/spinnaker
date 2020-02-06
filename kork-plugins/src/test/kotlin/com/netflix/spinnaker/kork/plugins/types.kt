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
package com.netflix.spinnaker.kork.plugins

import com.netflix.spinnaker.kork.plugins.api.SpinnakerExtension
import com.netflix.spinnaker.kork.plugins.api.spring.SpringPlugin
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

internal class TestSpringPlugin(wrapper: PluginWrapper) : SpringPlugin(wrapper) {
  override fun initApplicationContext() {
    applicationContext.register(TestSpringPluginConfiguration::class.java)
  }

  @SpinnakerExtension(id = "kork.test")
  class TestExtension {
    @Autowired
    lateinit var myConfig: MyObject
  }

  @Configuration
  internal class TestSpringPluginConfiguration {
    @Bean
    fun myConfig(): MyObject = MyObject(10)
  }

  internal class MyObject(val something: Int)
}
