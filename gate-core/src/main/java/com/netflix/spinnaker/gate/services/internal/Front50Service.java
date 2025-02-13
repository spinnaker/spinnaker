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

package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface Front50Service {
  @GET("/credentials")
  Call<List<Map>> getCredentials();

  @GET("/v2/applications?restricted=false")
  Call<List<Map>> getAllApplicationsUnrestricted();

  @GET("/v2/applications/{applicationName}")
  Call<Map> getApplication(@Path("applicationName") String applicationName);

  @GET("/v2/applications/{applicationName}/history")
  Call<List<Map>> getApplicationHistory(
      @Path("applicationName") String applicationName, @Query("limit") int limit);

  @GET("/pipelines")
  Call<List<Map>> getAllPipelineConfigs();

  @GET("/pipelines/{app}")
  Call<List<Map>> getPipelineConfigsForApplication(
      @Path("app") String app,
      @Query("pipelineNameFilter") String pipelineNameFilter,
      @Query("refresh") boolean refresh);

  @GET("/pipelines/{app}/name/{name}")
  Call<Map> getPipelineConfigByApplicationAndName(
      @Path("app") String app, @Path("name") String name, @Query("refresh") boolean refresh);

  @GET("/pipelines/{id}/get")
  Call<Map> getPipelineConfigById(@Path("id") String id);

  @DELETE("/pipelines/{app}/{name}")
  Call<ResponseBody> deletePipelineConfig(@Path("app") String app, @Path("name") String name);

  @POST("/pipelines")
  Call<ResponseBody> savePipelineConfig(@Body Map pipelineConfig);

  @POST("/pipelines/move")
  Call<ResponseBody> movePipelineConfig(@Body Map moveCommand);

  @GET("/pipelines/{pipelineConfigId}/history")
  Call<List<Map>> getPipelineConfigHistory(
      @Path("pipelineConfigId") String pipelineConfigId, @Query("limit") int limit);

  @PUT("/pipelines/{pipelineId}")
  Call<Map> updatePipeline(@Path("pipelineId") String pipelineId, @Body Map pipeline);

  @GET("/strategies")
  Call<List<Map>> getAllStrategyConfigs();

  @GET("/strategies/{app}")
  Call<List<Map>> getStrategyConfigs(@Path("app") String app);

  @DELETE("/strategies/{app}/{name}")
  Call<ResponseBody> deleteStrategyConfig(@Path("app") String app, @Path("name") String name);

  @POST("/strategies")
  Call<ResponseBody> saveStrategyConfig(@Body Map strategyConfig);

  @POST("/strategies/move")
  Call<ResponseBody> moveStrategyConfig(@Body Map moveCommand);

  @GET("/strategies/{strategyConfigId}/history")
  Call<List<Map>> getStrategyConfigHistory(
      @Path("strategyConfigId") String strategyConfigId, @Query("limit") int limit);

  @PUT("/strategies/{strategyId}")
  Call<Map> updateStrategy(@Path("strategyId") String strategyId, @Body Map strategy);

  @GET("/pipelineTemplates")
  Call<List<Map>> getPipelineTemplates(@Query("scopes") String... scopes);

  @GET("/pipelineTemplates/{pipelineTemplateId}")
  Call<Map> getPipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId);

  @GET("/pipelineTemplates/{pipelineTemplateId}/dependentPipelines")
  Call<List<Map<String, Object>>> getPipelineTemplateDependents(
      @Path("pipelineTemplateId") String pipelineTemplateId, @Query("recursive") boolean recursive);

  @GET("/v2/pipelineTemplates/{pipelineTemplateId}")
  Call<Map> getV2PipelineTemplate(
      @Path("pipelineTemplateId") String pipelineTemplateId,
      @Query("tag") String tag,
      @Query("digest") String digest);

  @GET("/v2/pipelineTemplates")
  Call<List<Map>> getV2PipelineTemplates(@Query("scopes") String... scopes);

  @GET("/v2/pipelineTemplates/versions")
  Call<Map<String, List<Map>>> getV2PipelineTemplatesVersions(@Query("scopes") String... scopes);

  @GET("/v2/pipelineTemplates/{pipelineTemplateId}/dependentPipelines")
  Call<List<Map<String, Object>>> getV2PipelineTemplateDependents(
      @Path("pipelineTemplateId") String pipelineTemplateId);

  @GET("/notifications/{type}/{app}")
  Call<Map> getNotificationConfigs(@Path("type") String type, @Path("app") String app);

  @DELETE("/notifications/{type}/{app}")
  Call<ResponseBody> deleteNotificationConfig(@Path("type") String type, @Path("app") String app);

  @POST("/notifications/{type}/{app}")
  Call<ResponseBody> saveNotificationConfig(
      @Path("type") String type, @Path("app") String app, @Body Map notificationConfig);

  @GET("/v2/projects")
  Call<List<Map>> getAllProjects();

  @GET("/v2/projects/{projectId}")
  Call<Map> getProject(@Path("projectId") String projectId);

  @GET("/snapshots/{id}")
  Call<Map> getCurrentSnapshot(@Path("id") String id);

  @GET("/snapshots/{id}/history")
  Call<List<Map>> getSnapshotHistory(@Path("id") String id, @Query("limit") int limit);

  @GET("/serviceAccounts")
  Call<List<ServiceAccount>> getServiceAccounts();

  @GET("/deliveries")
  Call<List<Map>> getDeliveries();

  @GET("/deliveries/{id}")
  Call<Map> getDelivery(@Path("id") String id);

  @GET("/pluginInfo")
  Call<List<Map>> getPluginInfo(@Query("service") String service);

  @GET("/installedPlugins")
  Call<List<SpinnakerPluginDescriptor>> getInstalledPlugins();
}
