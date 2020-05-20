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
import java.util.List;
import java.util.Map;
import retrofit.client.Response;
import retrofit.http.*;

public interface Front50Service {
  @GET("/credentials")
  public abstract List<Map> getCredentials();

  @GET("/v2/applications?restricted=false")
  public abstract List<Map> getAllApplicationsUnrestricted();

  @GET("/v2/applications/{applicationName}")
  public abstract Map getApplication(@Path("applicationName") String applicationName);

  @GET("/v2/applications/{applicationName}/history")
  public abstract List<Map> getApplicationHistory(
      @Path("applicationName") String applicationName, @Query("limit") int limit);

  @GET("/pipelines")
  public abstract List<Map> getAllPipelineConfigs();

  @GET("/pipelines/{app}")
  public abstract List<Map> getPipelineConfigsForApplication(
      @Path("app") String app, @Query("refresh") boolean refresh);

  @DELETE("/pipelines/{app}/{name}")
  public abstract Response deletePipelineConfig(@Path("app") String app, @Path("name") String name);

  @POST("/pipelines")
  public abstract Response savePipelineConfig(@Body Map pipelineConfig);

  @POST("/pipelines/move")
  public abstract Response movePipelineConfig(@Body Map moveCommand);

  @GET("/pipelines/{pipelineConfigId}/history")
  public abstract List<Map> getPipelineConfigHistory(
      @Path("pipelineConfigId") String pipelineConfigId, @Query("limit") int limit);

  @PUT("/pipelines/{pipelineId}")
  public abstract Map updatePipeline(@Path("pipelineId") String pipelineId, @Body Map pipeline);

  @GET("/strategies")
  public abstract List<Map> getAllStrategyConfigs();

  @GET("/strategies/{app}")
  public abstract List<Map> getStrategyConfigs(@Path("app") String app);

  @DELETE("/strategies/{app}/{name}")
  public abstract Response deleteStrategyConfig(@Path("app") String app, @Path("name") String name);

  @POST("/strategies")
  public abstract Response saveStrategyConfig(@Body Map strategyConfig);

  @POST("/strategies/move")
  public abstract Response moveStrategyConfig(@Body Map moveCommand);

  @GET("/strategies/{strategyConfigId}/history")
  public abstract List<Map> getStrategyConfigHistory(
      @Path("strategyConfigId") String strategyConfigId, @Query("limit") int limit);

  @PUT("/strategies/{strategyId}")
  public abstract Map updateStrategy(@Path("strategyId") String strategyId, @Body Map strategy);

  @GET("/pipelineTemplates")
  public abstract List<Map> getPipelineTemplates(@Query("scopes") String... scopes);

  @GET("/pipelineTemplates/{pipelineTemplateId}")
  public abstract Map getPipelineTemplate(@Path("pipelineTemplateId") String pipelineTemplateId);

  @GET("/pipelineTemplates/{pipelineTemplateId}/dependentPipelines")
  public abstract List<Map<String, Object>> getPipelineTemplateDependents(
      @Path("pipelineTemplateId") String pipelineTemplateId, @Query("recursive") boolean recursive);

  @GET("/v2/pipelineTemplates/{pipelineTemplateId}")
  public abstract Map getV2PipelineTemplate(
      @Path("pipelineTemplateId") String pipelineTemplateId,
      @Query("tag") String tag,
      @Query("digest") String digest);

  @GET("/v2/pipelineTemplates")
  public abstract List<Map> getV2PipelineTemplates(@Query("scopes") String... scopes);

  @GET("/v2/pipelineTemplates/versions")
  public abstract Map<String, List<Map>> getV2PipelineTemplatesVersions(
      @Query("scopes") String... scopes);

  @GET("/v2/pipelineTemplates/{pipelineTemplateId}/dependentPipelines")
  public abstract List<Map<String, Object>> getV2PipelineTemplateDependents(
      @Path("pipelineTemplateId") String pipelineTemplateId);

  @GET("/notifications/{type}/{app}")
  public abstract Map getNotificationConfigs(@Path("type") String type, @Path("app") String app);

  @DELETE("/notifications/{type}/{app}")
  public abstract Response deleteNotificationConfig(
      @Path("type") String type, @Path("app") String app);

  @POST("/notifications/{type}/{app}")
  public abstract Response saveNotificationConfig(
      @Path("type") String type, @Path("app") String app, @Body Map notificationConfig);

  @GET("/v2/projects")
  public abstract List<Map> getAllProjects();

  @GET("/v2/projects/{projectId}")
  public abstract Map getProject(@Path("projectId") String projectId);

  @GET("/snapshots/{id}")
  public abstract Map getCurrentSnapshot(@Path("id") String id);

  @GET("/snapshots/{id}/history")
  public abstract List<Map> getSnapshotHistory(@Path("id") String id, @Query("limit") int limit);

  @GET("/serviceAccounts")
  public abstract List<ServiceAccount> getServiceAccounts();

  @GET("/deliveries")
  public abstract List<Map> getDeliveries();

  @GET("/deliveries/{id}")
  public abstract Map getDelivery(@Path("id") String id);

  @GET("/pluginInfo")
  public abstract List<Map> getPluginInfo(@Query("service") String service);
}
