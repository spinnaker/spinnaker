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
import com.netflix.spinnaker.orca.front50.model.PluginInfo
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query


interface Front50Service {
  @GET("credentials")
  Call<List<Front50Credential>> getCredentials()

  @GET("v2/applications/{applicationName}")
  Call<Application> get(@Path("applicationName") String applicationName)

  @GET("v2/applications")
  Call<Collection<Application>> getAllApplications()

  @POST("v2/applications")
  Call<ResponseBody> create(@Body Application application)

  @DELETE("v2/applications/{applicationName}")
  Call<ResponseBody> delete(@Path("applicationName") String applicationName)

  @PATCH("v2/applications/{applicationName}")
  Call<ResponseBody> update(@Path("applicationName") String applicationName, @Body Application application)

  @DELETE("permissions/applications/{applicationName}")
  Call<ResponseBody> deletePermission(@Path("applicationName") String applicationName)

  @PUT("permissions/applications/{applicationName}")
  Call<ResponseBody> updatePermission(@Path("applicationName") String applicationName, @Body Application.Permission permission)

  @POST("pluginInfo")
  Call<PluginInfo> upsertPluginInfo(@Body PluginInfo pluginInfo)

  @GET("pluginInfo/{pluginId}")
  Call<PluginInfo> getPluginInfo(@Path("pluginId") String pluginId);

  @DELETE("pluginInfo/{pluginId}")
  Call<ResponseBody> deletePluginInfo(@Path("pluginId") String pluginId)

  @PUT("pluginInfo/{pluginId}/releases/{version}")
  Call<ResponseBody> setPreferredPluginVersion(@Path("pluginId") String pluginId, @Path("version") String version, @Query("preferred") boolean preferred)

  @GET("pipelines/{applicationName}")
  Call<List<Map<String, Object>>> getPipelines(@Path("applicationName") String applicationName)

  @GET("pipelines/{applicationName}")
  Call<List<Map<String, Object>>> getPipelines(@Path("applicationName") String applicationName, @Query("refresh") boolean refresh)

  @GET("pipelines/{applicationName}")
  Call<List<Map<String, Object>>> getPipelines(@Path("applicationName") String applicationName, @Query("refresh") boolean refresh, @Query("enabledPipelines") Boolean enabledPipelines)

  @GET("pipelines/{pipelineId}/get")
  Call<Map<String, Object>> getPipeline(@Path("pipelineId") String pipelineId)

  @POST("pipelines")
  Call<ResponseBody> savePipeline(@Body Map pipeline, @Query("staleCheck") boolean staleCheck)

  @POST("pipelines/batchUpdate")
  Call<ResponseBody> savePipelines(@Body List<Map<String, Object>> pipelines, @Query("staleCheck") boolean staleCheck)

  @PUT("pipelines/{pipelineId}")
  Call<ResponseBody> updatePipeline(@Path("pipelineId") String pipelineId, @Body Map pipeline)

  @GET("strategies/{applicationName}")
  Call<List<Map<String, Object>>> getStrategies(@Path("applicationName") String applicationName)

  @GET("pipelines?restricted=false")
  Call<List<Map<String, Object>>> getAllPipelines()

  @GET("pipelines/triggeredBy/{pipelineId}/{status}?restricted=false")
  Call<List<Map<String, Object>>> getTriggeredPipelines(@Path("pipelineId") String pipelineId, @Path("status") String status)

  @POST('actions/pipelines/reorder')
  Call<ResponseBody> reorderPipelines(@Body ReorderPipelinesCommand reorderPipelinesCommand)

  @DELETE("pipelines/{applicationName}/{pipelineName}")
  Call<ResponseBody> deletePipeline(@Path("applicationName") String applicationName, @Path("pipelineName") String pipelineName)

  @POST('actions/strategies/reorder')
  Call<ResponseBody> reorderPipelineStrategies(@Body ReorderPipelinesCommand reorderPipelinesCommand)

  // pipeline template related
  @GET("pipelineTemplates")
  Call<List<Map<String, Object>>> getPipelineTemplates(@Query("scopes") List<String> scopes)

  @POST("pipelineTemplates")
  Call<ResponseBody> savePipelineTemplate(@Body Map pipelineTemplate)

  @GET("pipelineTemplates/{pipelineTemplateId}")
  Call<Map<String, Object>> getPipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId)

  @PUT("pipelineTemplates/{pipelineTemplateId}")
  Call<ResponseBody> updatePipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId, @Body Map pipelineTemplate)

  @DELETE("pipelineTemplates/{pipelineTemplateId}")
  Call<ResponseBody> deletePipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId)

  @GET("pipelineTemplates/{pipelineTemplateId}/dependentPipelines")
  Call<List<Map<String, Object>>> getPipelineTemplateDependents(@Path("pipelineTemplateId") String pipelineTemplateId, @Query("recursive") boolean recursive)

  // v2
  @POST("v2/pipelineTemplates")
  Call<ResponseBody> saveV2PipelineTemplate(@Query("tag") String tag, @Body Map pipelineTemplate)

  @GET("v2/pipelineTemplates/{pipelineTemplateId}/dependentPipelines")
  Call<List<Map<String, Object>>> getDependentPipelinesForTemplate(@Path("pipelineTemplateId") String pipelineTemplateId)

  @PUT("v2/pipelineTemplates/{pipelineTemplateId}")
  Call<ResponseBody> updateV2PipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId, @Query("tag") String tag, @Body Map pipelineTemplate)

  @DELETE("v2/pipelineTemplates/{pipelineTemplateId}")
  Call<ResponseBody> deleteV2PipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId,
                                    @Query("tag") String tag,
                                    @Query("digest") String digest)

  @GET("strategies")
  Call<List<Map<String, Object>>> getAllStrategies()

  @POST("v2/projects")
  Call<Project> createProject(@Body Map project)

  @PUT("v2/projects/{projectId}")
  Call<Project> updateProject(@Path("projectId") String projectId, @Body Map project)

  @GET("v2/projects/{projectId}")
  Call<Project> getProject(@Path("projectId") String projectId)

  @DELETE("v2/projects/{projectId}")
  Call<ResponseBody> deleteProject(@Path("projectId") String projectId)

  @GET("notifications/application/{applicationName}")
  Call<ApplicationNotifications> getApplicationNotifications(@Path("applicationName") String applicationName)

  @POST("serviceAccounts")
  Call<ResponseBody> saveServiceAccount(@Body ServiceAccount serviceAccount)

  @GET("deliveries/{id}")
  Call<DeliveryConfig> getDeliveryConfig(@Path("id") String id)

  @POST("deliveries")
  Call<DeliveryConfig> createDeliveryConfig(@Body DeliveryConfig deliveryConfig)

  @PUT("deliveries/{id}")
  Call<DeliveryConfig> updateDeliveryConfig(@Path("id") String id, @Body DeliveryConfig deliveryConfig)

  @DELETE("applications/{application}/deliveries/{id}")
  Call<ResponseBody> deleteDeliveryConfig(@Path("application") String application, @Path("id") String id)

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
