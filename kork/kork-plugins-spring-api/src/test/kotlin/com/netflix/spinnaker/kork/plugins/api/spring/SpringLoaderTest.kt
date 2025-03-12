/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.kork.plugins.api.spring

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class SpringLoaderTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("should scan packages and register classes") {
      springLoader.setApplicationContext(appContext)

      io.mockk.verify(exactly = 1) {
        pluginContext.setClassLoader(pluginClassLoader)
        pluginContext.scan(*packagesToScan.toTypedArray())
        pluginContext.register(MyService::class.java)
      }

    }

  }

  private inner class Fixture {
    val pluginContext: AnnotationConfigApplicationContext = mockk(relaxed = true)
    val appContext: AnnotationConfigServletWebServerApplicationContext = mockk(relaxed = true)
    val pluginClassLoader: ClassLoader = javaClass.classLoader
    var packagesToScan = listOf("io.armory.plugin.example.spring")
    val classesToRegister = listOf(
      MyService::class.java
    )
    val springLoader: SpringLoader = SpringLoader(pluginContext, pluginClassLoader, packagesToScan, classesToRegister)
  }

  internal class MyService

}
