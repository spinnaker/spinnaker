/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.gradle.extension.extensions

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.gradle.extension.PluginObjectMapper
import com.netflix.spinnaker.gradle.extension.compatibility.SpinnakerVersionAlias
import org.gradle.api.Action
import org.gradle.kotlin.dsl.create

/**
 * Configuration for plugin compatibility tests.
 * */
open class SpinnakerCompatibilityExtension {
  /**
   * A list of top-level Spinnaker versions (e.g., 1.21.0, 1.22.0) that this plugin should be tested against.
   *
   * e.g.,
   * spinnakerBundle {
   *   compatibility {
   *     spinnaker = ["1.21.0", "1.22.0"]
   *   }
   * }
   */
  var spinnaker: List<String> = emptyList()

  /**
   * The following "spinnaker" methods provide a DSL for more complex test configuration options.
   * Currently you can only define if a test is "required" or not for a given Spinnaker version; i.e.,
   * should the top-level compatibilityTest task fail should if a test fails?
   * */

  /**
   * For Kotlin build scripts.
   * e.g.,
   * spinnakerBundle {
   *   compatibility {
   *     spinnaker {
   *       test("1.21.1", required = false)
   *       test("1.22.0")
   *     }
   *   }
   * }
   * */
  fun spinnaker(configure: VersionTestConfigExtension.() -> Unit) {
    withExtensions {
      create<VersionTestConfigExtension>(VersionTestConfigExtension.NAME)
      configure(VersionTestConfigExtension.NAME, configure)
    }
  }

  /**
   * For Groovy build scripts.
   * e.g.,
   * spinnakerBundle {
   *   compatibility {
   *     spinnaker {
   *       test(version: "1.21.1", required: false)
   *       test "1.22.0"
   *     }
   *   }
   * }
   * */
  fun spinnaker(configure: Action<VersionTestConfigExtension>) {
    withExtensions {
      create<VersionTestConfigExtension>(VersionTestConfigExtension.NAME)
      configure(VersionTestConfigExtension.NAME, configure)
    }
  }

  internal val versionTestConfigs
    get() = withExtensions {
      val extension = findByName(VersionTestConfigExtension.NAME) as? VersionTestConfigExtension
      spinnaker.map { VersionTestConfig(it) } + (extension?.configs ?: emptyList())
    }

  var halconfigBaseURL: String = "https://storage.googleapis.com/halconfig"

  companion object {
    const val NAME = "compatibility"
  }
}

open class VersionTestConfigExtension {

  internal var configs = emptyList<VersionTestConfig>()

  // For Kotlin DSLs:
  // test("1.21.1", required = true)
  fun test(version: String, required: Boolean = true) {
    configs = configs + VersionTestConfig(version, required)
  }

  // For Groovy DSLs (Groovy can't handle default parameters):
  // test "1.21.1"
  fun test(version: String) {
    configs = configs + VersionTestConfig(version)
  }

  // For Groovy DSLs:
  // test(version: "1.21.1", required: false)
  fun test(config: Map<String, Any>) {
    configs = configs + PluginObjectMapper.mapper.convertValue<VersionTestConfig>(config)
  }

  companion object {
    const val NAME = "spinnaker"
  }
}

data class VersionTestConfig(val version: String, val required: Boolean = true) {
  val alias: SpinnakerVersionAlias? = if (SpinnakerVersionAlias.isAlias(version)) SpinnakerVersionAlias.from(version) else null
}
