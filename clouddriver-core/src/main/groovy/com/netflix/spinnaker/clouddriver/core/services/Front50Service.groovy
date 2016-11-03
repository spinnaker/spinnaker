/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.core.services

import com.netflix.spinnaker.clouddriver.model.EntityTags
import retrofit.client.Response
import retrofit.http.*

interface Front50Service {
  @GET("/credentials")
  List<Map> getCredentials()

  @GET('/v2/applications')
  List<Map> searchByName(@Query("name") String applicationName, @Query("pageSize") Integer pageSize)

  @GET('/v2/applications/{applicationName}')
  Map getApplication(@Path('applicationName') String applicationName)

  @GET('/v2/projects/{project}')
  Map getProject(@Path('project') String project)

  @GET('/v2/projects/search')
  List<Map> searchForProjects(@Query("q") String query)

  @POST('/snapshots')
  Response saveSnapshot(@Body Map snapshot)

  @GET('/snapshots/{id}/{timestamp}')
  Map getSnapshotVersion(@Path('id') String id, @Path('timestamp') String timestamp)

  @POST('/v2/tags')
  EntityTags saveEntityTags(@Body EntityTags entityTags)

  @GET('/v2/tags/{id}')
  EntityTags getEntityTags(@Path('id') String id)

  @DELETE('/v2/tags/{id}')
  Response deleteEntityTags(@Path('id') String id)
}
