/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.gradle.extension

import org.gradle.api.Project

object Plugins {
  const val GROUP = "Spinnaker Plugins"

  const val BUNDLE_PLUGINS_TASK_NAME = "bundlePlugins"
  const val ASSEMBLE_PLUGIN_TASK_NAME = "assemblePlugin"
  const val RELEASE_BUNDLE_TASK_NAME = "releaseBundle"
  const val CHECKSUM_BUNDLE_TASK_NAME = "checksumBundle"
  const val COLLECT_PLUGIN_ZIPS_TASK_NAME = "collectPluginZips"

  internal fun hasDeckPlugin(project: Project): Boolean =
    project.rootProject.subprojects.any { it.plugins.hasPlugin(SpinnakerUIExtensionPlugin::class.java) }
}
