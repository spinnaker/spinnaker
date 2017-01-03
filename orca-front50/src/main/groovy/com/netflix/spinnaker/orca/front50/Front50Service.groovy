/*
 * Copyright 2014 Netflix, Inc.
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




package com.netflix.spinnaker.orca.front50

import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.model.Front50Credential
import retrofit.client.Response
import retrofit.http.*

interface Front50Service {
  @GET("/credentials")
  List<Front50Credential> getCredentials()

  @GET("/v2/applications/{applicationName}")
  Application get(@Path("applicationName") String applicationName)

  @GET("/v2/applications")
  Collection<Application> getAllApplications()

  @POST("/v2/applications")
  Response create(@Body Application application)

  @DELETE("/v2/applications/{applicationName}")
  Response delete(@Path("applicationName") String applicationName)

  @PATCH("/v2/applications/{applicationName}")
  Response update(@Path("applicationName") String applicationName, @Body Application application)

  @DELETE("/permissions/applications/{name}")
  Response deletePermission(@Path("name") String name)

  @PUT("/permissions/applications/{name}")
  Response updatePermission(@Path("name") String name, @Body Application.Permission permission)

  @GET("/pipelines/{application}")
  List<Map<String, Object>> getPipelines(@Path("application") String application)

  @POST("/pipelines")
  Response savePipeline(@Body Map pipeline)

  @GET("/strategies/{application}")
  List<Map<String, Object>> getStrategies(@Path("application") String application)

  @GET("/pipelines?restricted=false")
  List<Map<String, Object>> getAllPipelines()

  @GET("/strategies")
  List<Map<String, Object>> getAllStrategies()

  @POST("/v2/projects")
  Project createProject(@Body Map project)

  @PUT("/v2/projects/{projectId}")
  Project updateProject(@Path("projectId") String projectId, @Body Map project)

  @GET("/v2/projects/{projectId}")
  Project getProject(@Path("projectId") String projectId)

  @DELETE("/v2/projects/{projectId}")
  Response deleteProject(@Path("projectId") String projectId)

  static class Project {
    String id
    String name

    ProjectConfig config = new ProjectConfig()

    static class ProjectConfig {
      Collection<PipelineConfig> pipelineConfigs
    }

    static class PipelineConfig {
      String application
      String pipelineConfigId
    }
  }
}
