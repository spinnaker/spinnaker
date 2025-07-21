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

package com.netflix.spinnaker.kork.plugins.update.internal

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * List and get plugin info objects from Front50. Used in conjunction with [Front50UpdateRepository]
 * to populate a services plugin info cache to determine which plugins to load.
 */
interface Front50Service {

  /**
   * Get [SpinnakerPluginInfo] by the plugin ID.
   */
  @GET("pluginInfo/{id}")
  fun getById(@Path("id") id: String): Call<SpinnakerPluginInfo>

  /**
   * List all registered [SpinnakerPluginInfo] from front50.
   */
  @GET("pluginInfo")
  fun listAll(): Call<Collection<SpinnakerPluginInfo>>

  /**
   * Pin a service's plugins to a particular set of plugin/plugin version tuples.
   *
   * This ensures that, even through instance replacement, a server group's installed plugins will remain homogenous.
   */
  @PUT("pluginVersions/{serverGroupName}")
  fun pinVersions(
    @Path("serverGroupName") serverGroupName: String,
    @Query("serviceName") serviceName: String,
    @Query("location") location: String,
    @Body versions: Map<String, String>
  ): Call<PinnedVersions>
}

typealias PinnedVersions = Map<String, SpinnakerPluginInfo.SpinnakerPluginRelease>
