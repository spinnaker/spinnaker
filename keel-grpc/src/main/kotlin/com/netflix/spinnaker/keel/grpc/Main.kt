/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.grpc

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.ComponentScan

object MainDefaults {
  val PROPS = mapOf(
    "netflix.environment" to "test",
    "netflix.account" to "\${netflix.environment}",
    "netflix.stack" to "test",
    "spring.config.location" to "\${user.home}/.spinnaker/",
    "spring.application.name" to "keel",
    "spring.config.name" to "spinnaker,\${spring.application.name}",
    "spring.profiles.active" to "\${netflix.environment},local"
  )
}

@SpringBootApplication
//@EnableEurekaClient
@ComponentScan(basePackages = [
  "com.netflix.spinnaker.config",
  "com.netflix.spinnaker.keel.api",
  "com.netflix.spinnaker.keel.ec2"
])
class KeelGrpcServiceApp {

  private val Any.log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    @JvmStatic
    fun main(vararg args: String) {
      SpringApplicationBuilder()
        .properties(MainDefaults.PROPS)
        .sources<KeelGrpcServiceApp>()
        .run(*args)
//    SpringApplication.run(DemoApp::class.java, args)
    }
  }
}

inline fun <reified T> SpringApplicationBuilder.sources(): SpringApplicationBuilder =
  sources(T::class.java)
