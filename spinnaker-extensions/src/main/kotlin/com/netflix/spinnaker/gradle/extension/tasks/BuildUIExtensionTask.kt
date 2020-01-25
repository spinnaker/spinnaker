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
package com.netflix.spinnaker.gradle.extension.tasks

import com.netflix.spinnaker.gradle.extension.Plugins
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class BuildUIExtensionTask : DefaultTask() {

  override fun getGroup(): String? = Plugins.GROUP

  init {
    this.dependsOn("yarn", "yarn_install", "yarn_build")
  }

  @TaskAction
  fun doAction() {
    // Do nothing, this is just a synthetic task.
  }
}
