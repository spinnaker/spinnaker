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

import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.model.ApplicationNotifications
import com.netflix.spinnaker.orca.front50.model.DeliveryConfig
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

  @DELETE("/permissions/applications/{applicationName}")
  Response deletePermission(@Path("applicationName") String applicationName)

  @PUT("/permissions/applications/{applicationName}")
  Response updatePermission(@Path("applicationName") String applicationName, @Body Application.Permission permission)

  @GET("/pipelines/{applicationName}")
  List<Map<String, Object>> getPipelines(@Path("applicationName") String applicationName)

  @GET("/pipelines/{applicationName}")
  List<Map<String, Object>> getPipelines(@Path("applicationName") String applicationName, @Query("refresh") boolean refresh)

  @GET("/pipelines/{pipelineId}/history")
  List<Map<String, Object>> getPipelineHistory(@Path("pipelineId") String pipelineId, @Query("limit") int limit)

  @POST("/pipelines")
  Response savePipeline(@Body Map pipeline)

  @PUT("/pipelines/{pipelineId}")
  Response updatePipeline(@Path("pipelineId") String pipelineId, @Body Map pipeline)

  @GET("/strategies/{applicationName}")
  List<Map<String, Object>> getStrategies(@Path("applicationName") String applicationName)

  @GET("/pipelines?restricted=false")
  List<Map<String, Object>> getAllPipelines()

  @POST('/actions/pipelines/reorder')
  Response reorderPipelines(@Body ReorderPipelinesCommand reorderPipelinesCommand)

  @POST('/actions/strategies/reorder')
  Response reorderPipelineStrategies(@Body ReorderPipelinesCommand reorderPipelinesCommand)

  // pipeline template related
  @GET("/pipelineTemplates")
  List<Map<String, Object>> getPipelineTemplates(@Query("scopes") List<String> scopes)

  @POST("/pipelineTemplates")
  Response savePipelineTemplate(@Body Map pipelineTemplate)

  @GET("/pipelineTemplates/{pipelineTemplateId}")
  Map<String, Object> getPipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId)

  @PUT("/pipelineTemplates/{pipelineTemplateId}")
  Response updatePipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId, @Body Map pipelineTemplate)

  @DELETE("/pipelineTemplates/{pipelineTemplateId}")
  Response deletePipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId)

  @GET("/pipelineTemplates/{pipelineTemplateId}/dependentPipelines")
  List<Map<String, Object>> getPipelineTemplateDependents(@Path("pipelineTemplateId") String pipelineTemplateId, @Query("recursive") boolean recursive)

  // v2
  @POST("/v2/pipelineTemplates")
  Response saveV2PipelineTemplate(@Query("tag") String tag, @Body Map pipelineTemplate)

  @GET("/v2/pipelineTemplates/{pipelineTemplateId}/dependentPipelines")
  List<Map<String, Object>> getDependentPipelinesForTemplate(@Path("pipelineTemplateId") String pipelineTemplateId)

  @PUT("/v2/pipelineTemplates/{pipelineTemplateId}")
  Response updateV2PipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId, @Query("tag") String tag, @Body Map pipelineTemplate)

  @DELETE("/v2/pipelineTemplates/{pipelineTemplateId}")
  Response deleteV2PipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId,
                                    @Query("tag") String tag,
                                    @Query("digest") String digest)

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

  @GET("/notifications/application/{applicationName}")
  ApplicationNotifications getApplicationNotifications(@Path("applicationName") String applicationName)

  @POST("/serviceAccounts")
  Response saveServiceAccount(@Body ServiceAccount serviceAccount)

  @GET("/deliveries/{id}")
  DeliveryConfig getDeliveryConfig(@Path("id") String id)

  @POST("/deliveries")
  DeliveryConfig createDeliveryConfig(@Body DeliveryConfig deliveryConfig)

  @PUT("/deliveries/{id}")
  DeliveryConfig updateDeliveryConfig(@Path("id") String id, @Body DeliveryConfig deliveryConfig)

  @DELETE("/applications/{application}/deliveries/{id}")
  Response deleteDeliveryConfig(@Path("application") String application, @Path("id") String id)

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

  static class ReorderPipelinesCommand {
    Map<String, Integer> idsToIndices
    String application

    ReorderPipelinesCommand(Map<String, Integer> idsToIndices, String application) {
      this.idsToIndices = idsToIndices
      this.application = application
    }
  }
}
