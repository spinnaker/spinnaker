/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.gate.plugins.web

import com.netflix.spinnaker.gate.services.TaskService
import com.netflix.spinnaker.kork.exceptions.UserException
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.springframework.stereotype.Service

@Service
class PluginService(
  private val taskService: TaskService,
  private val spinnakerExtensionsConfigProperties: SpinnakerExtensionsConfigProperties
) {

  fun getReleaseByVersion(pluginInfo: SpinnakerPluginInfo, version: String): SpinnakerPluginInfo.SpinnakerPluginRelease {
    return pluginInfo.getReleases().find { it.version == version }
      ?: throw UserException("Plugin info does not contain a release that matches release version '$version'")
  }

  fun verifyPluginInfo(pluginInfo: SpinnakerPluginInfo, pluginId: String) {
    if (pluginInfo.id != pluginId) throw UserException("Plugin info ID ${pluginInfo.id} does not match provided plugin ID $pluginId")
  }

  fun upsertPluginInfo(pluginInfo: SpinnakerPluginInfo): Map<String, Any> {
    val jobMap = mapOf(
      Pair("type", "upsertPluginInfo"),
      Pair("pluginInfo", pluginInfo),
      Pair("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"))
    )
    return initiateTask("Upsert plugin info with Id: " + pluginInfo.id, listOf(jobMap))
  }

  fun deletePluginInfo(id: String): Map<String, Any> {
    val jobMap = mapOf(
      Pair("type", "deletePluginInfo"),
      Pair("pluginInfoId", id),
      Pair("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"))
    )
    return initiateTask("Delete Plugin info with Id: $id", listOf(jobMap))
  }

  private fun initiateTask(description: String, jobs: List<Map<String, Any>>): Map<String, Any> {
    val taskMap = mapOf(
      Pair("description", description),
      Pair("application", spinnakerExtensionsConfigProperties.applicationName),
      Pair("job", jobs)
    )

    return taskService.create(taskMap) as Map<String, Any>
  }
}
