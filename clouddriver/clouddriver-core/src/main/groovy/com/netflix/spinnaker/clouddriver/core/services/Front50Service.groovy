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
import com.netflix.spinnaker.clouddriver.model.Front50Application
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface Front50Service {
  @GET("/credentials")
  Call<List<Map>> getCredentials()

  @GET('/v2/applications')
  Call<List<Map<String, Object>>> searchByName(@Query("name") String applicationName,
                                         @Query("pageSize") Integer pageSize,
                                         @QueryMap Map<String, String> filters)

  @GET('/v2/applications/{applicationName}')
  Call<Map> getApplication(@Path('applicationName') String applicationName)

  @GET('/v2/applications?restricted=false')
  Call<Set<Front50Application>> getAllApplicationsUnrestricted()

  @GET('/v2/projects/{project}')
  Call<Map> getProject(@Path('project') String project)

  @GET('/v2/projects')
  Call<List<Map<String, Object>>> searchForProjects(@QueryMap Map<String, String> params, @Query("pageSize") Integer pageSize)

  @POST('/snapshots')
  Call<ResponseBody> saveSnapshot(@Body Map snapshot)

  @GET('/snapshots/{id}/{timestamp}')
  Call<Map> getSnapshotVersion(@Path('id') String id, @Path('timestamp') String timestamp)

  @POST('/v2/tags')
  Call<EntityTags> saveEntityTags(@Body EntityTags entityTags)

  @POST('/v2/tags/batchUpdate')
  Call<Collection<EntityTags>> batchUpdate(@Body Collection<EntityTags> entityTags)

  @GET('/v2/tags/{id}')
  Call<EntityTags> getEntityTags(@Path('id') String id)

  @GET('/v2/tags')
  Call<List<EntityTags>> getAllEntityTagsById(@Query("ids") List<String> entityIds)

  @GET('/v2/tags?prefix=')
  Call<Collection<EntityTags>> getAllEntityTags(@Query("refresh") boolean refresh)

  @DELETE('/v2/tags/{id}')
  Call<ResponseBody> deleteEntityTags(@Path('id') String id)

  // v2 MPT APIs
  @GET('/v2/pipelineTemplates/{pipelineTemplateId}')
  Call<Map> getV2PipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId,
                            @Query("tag") String version,
                            @Query("digest") String digest)

  @GET('/v2/pipelineTemplates')
  Call<List<Map>> listV2PipelineTemplates(@Query("scopes") List<String> scopes)
}
