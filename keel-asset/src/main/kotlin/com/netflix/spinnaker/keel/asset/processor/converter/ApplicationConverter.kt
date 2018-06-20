/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.keel.asset.processor.converter

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.asset.ANY_MAP_TYPE
import com.netflix.spinnaker.keel.asset.BaseApplicationSpec
import com.netflix.spinnaker.keel.asset.ConvertToJobCommand
import com.netflix.spinnaker.keel.asset.SpecConverter
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.model.Job
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ApplicationConverter(
  private val objectMapper: ObjectMapper
) : SpecConverter<BaseApplicationSpec, Application> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun convertToState(spec: BaseApplicationSpec): Application {

    val application = Application(
      name = spec.name,
      description = spec.description.orEmpty(),
      email = spec.email,
      platformHealthOnly = spec.platformHealthOnly,
      platformHealthOnlyShowOverride = spec.platformHealthOnlyShowOverride,
      owner = spec.owner
    )

    val initializedParams: List<String> = listOf("name", "description", "email",
      "platformHealthOnly", "platformHealthOnlyShowOverride", "owner")
    var specMap: Map<String, Any?> = convertToMap(spec)
    specMap = specMap.filterNot { initializedParams.contains(it.key) }
    specMap.forEach { key, value ->  application.set(key,value) }

    return application
  }

  override fun convertFromState(state: Application): BaseApplicationSpec? {
    TODO("not implemented ")
  }

  override fun <C : ConvertToJobCommand<BaseApplicationSpec>> convertToJob(command: C, changeSummary: ChangeSummary): List<Job> {
    throw UnsupportedOperationException("not implemented")
  }

  /*
   * Unfortunately, we need to compare things as maps.
   * Need to flatten all objects in details to top level objects to
   * match the Application Spec
  */
  fun convertToMap(state: Application): MutableMap<String, Any?> {
    val convertedMap: MutableMap<String, Any?> = mutableMapOf()
    convertedMap.apply {
      putAll(state.details)
      put("name", state.name)
      put("description", state.description)
      put("email", state.email)
      put("owner", state.owner)
      put("platformHealthOnly", state.platformHealthOnly)
      put("platformHealthOnlyShowOverride", state.platformHealthOnlyShowOverride)
    }
    return convertedMap
  }

  fun convertToMap(spec: BaseApplicationSpec): MutableMap<String, Any?> {
      return objectMapper.convertValue<MutableMap<String, Any?>>(spec, ANY_MAP_TYPE)
  }
}
