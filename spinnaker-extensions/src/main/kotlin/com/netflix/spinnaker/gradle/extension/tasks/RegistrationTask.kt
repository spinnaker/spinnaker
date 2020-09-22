/*
 * Copyright 2019 Netflix, Inc.
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
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Task to register a spinnaker plugin.
 * This task will invoke a spinnaker API to register 'spinnaker plugin' metadata with Front50.
 */
open class RegistrationTask : DefaultTask() {

  @Internal
  override fun getGroup(): String = Plugins.GROUP

  @TaskAction
  fun doAction() {
    project.logger.log(LogLevel.INFO, "Registration with spinnaker is not complete!!")
  }
}
