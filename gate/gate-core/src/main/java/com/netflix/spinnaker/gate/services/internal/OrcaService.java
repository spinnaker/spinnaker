package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface OrcaService {
  @Headers("Content-type: application/context+json")
  @POST("/ops")
  Call<Map> doOperation(@Body Map<String, Object> body);

  @Headers("Accept: application/json")
  @GET("/applications/{application}/tasks")
  Call<List> getTasks(
      @Path("application") String app,
      @Query("page") Integer page,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses);

  @Headers("Accept: application/json")
  @GET("/v2/applications/{application}/pipelines")
  Call<List> getPipelines(
      @Path("application") String app,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses,
      @Query("expand") Boolean expand);

  @Headers("Accept: application/json")
  @GET("/projects/{projectId}/pipelines")
  Call<List<Map>> getPipelinesForProject(
      @Path("projectId") String projectId,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses);

  @Headers("Accept: application/json")
  @GET("/tasks/{id}")
  Call<Map> getTask(@Path("id") String id);

  @Headers("Accept: application/json")
  @DELETE("/tasks/{id}")
  Call<Map> deleteTask(@Path("id") String id);

  @Headers("Accept: application/json")
  @PUT("/tasks/{id}/cancel")
  Call<Map> cancelTask(@Path("id") String id, @Body String ignored);

  @Headers("Accept: application/json")
  @PUT("/tasks/cancel")
  Call<Map> cancelTasks(@Body List<String> taskIds);

  @Headers("Accept: application/json")
  @GET("/pipelines")
  Call<List> getSubsetOfExecutions(
      @Query("pipelineConfigIds") String pipelineConfigIds,
      @Query("executionIds") String executionIds,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses,
      @Query("expand") boolean expand);

  @Headers("Accept: application/json")
  @GET("/applications/{application}/pipelines/search")
  Call<List> searchForPipelineExecutionsByTrigger(
      @Path("application") String application,
      @Query("triggerTypes") String triggerTypes,
      @Query("pipelineName") String pipelineName,
      @Query("eventId") String eventId,
      @Query("trigger") String trigger,
      @Query("triggerTimeStartBoundary") long triggerTimeStartBoundary,
      @Query("triggerTimeEndBoundary") long triggerTimeEndBoundary,
      @Query("statuses") String statuses,
      @Query("startIndex") int startIndex,
      @Query("size") int size,
      @Query("reverse") boolean reverse,
      @Query("expand") boolean expand);

  @Headers("Accept: application/json")
  @GET("/pipelines/{id}")
  Call<Map> getPipeline(@Path("id") String id);

  @Headers("Accept: application/json")
  @PUT("/pipelines/{id}/cancel")
  Call<Void> cancelPipeline(
      @Path("id") String id,
      @Query("reason") String reason,
      @Query("force") boolean force,
      @Body String ignored);

  @Headers("Accept: application/json")
  @PUT("/pipelines/{id}/pause")
  Call<Void> pausePipeline(@Path("id") String id, @Body String ignored);

  @Headers("Accept: application/json")
  @PUT("/pipelines/{id}/resume")
  Call<Void> resumePipeline(@Path("id") String id, @Body String ignored);

  @Headers("Accept: application/json")
  @DELETE("/pipelines/{id}")
  Call<Void> deletePipeline(@Path("id") String id);

  @Headers("Accept: application/json")
  @PUT("/pipelines/{executionId}/stages/{stageId}/restart")
  Call<Map> restartPipelineStage(
      @Path("executionId") String executionId,
      @Path("stageId") String stageId,
      @Body Map restartDetails);

  @Headers("Accept: application/json")
  @POST("/orchestrate")
  Call<Map> startPipeline(@Body Map pipelineConfig, @Query("user") String user);

  @Headers("Accept: application/json")
  @PATCH("/pipelines/{executionId}/stages/{stageId}")
  Call<Map> updatePipelineStage(
      @Path("executionId") String executionId, @Path("stageId") String stageId, @Body Map context);

  @Headers("Accept: application/json")
  @GET("/pipelines/{id}/evaluateExpression")
  Call<Map> evaluateExpressionForExecution(
      @Path("id") String executionId, @Query("expression") String pipelineExpression);

  @Headers("Accept: application/json")
  @GET("/pipelines/{id}/{stageId}/evaluateExpression")
  Call<Map> evaluateExpressionForExecutionAtStage(
      @Path("id") String executionId,
      @Path("stageId") String stageId,
      @Query("expression") String pipelineExpression);

  @Headers("Accept: application/json")
  @POST("/pipelines/{id}/evaluateVariables")
  Call<Map> evaluateVariables(
      @Path("id") String id,
      @Query("requisiteStageRefIds") String requisiteStageRefIds,
      @Query("spelVersion") String spelVersionOverride,
      @Body List<Map<String, String>> expressions);

  @Headers("Accept: application/json")
  @GET("/webhooks/preconfigured")
  Call<List> preconfiguredWebhooks();

  @Headers("Accept: application/json")
  @GET("/jobs/preconfigured")
  Call<List> getPreconfiguredJobs();

  @Headers("Accept: application/json")
  @GET("/pipelineTemplate")
  Call<Map> resolvePipelineTemplate(
      @Query("source") String source,
      @Query("executionId") String executionId,
      @Query("pipelineConfigId") String pipelineConfigId);

  @POST("/convertPipelineToTemplate")
  Call<ResponseBody> convertToPipelineTemplate(@Body Map<String, ? extends Object> pipelineConfig);

  @Headers("Accept: application/json")
  @POST("/v2/pipelineTemplates/plan")
  Call<Map<String, Object>> plan(@Body Map<String, Object> pipelineConfig);

  @POST("/concourse/stage/start")
  Call<ResponseBody> concourseStageExecution(
      @Query("stageId") String stageId,
      @Query("job") String job,
      @Query("buildNumber") Integer buildNumber,
      @Body String emptyBody);

  @GET("/capabilities/deploymentMonitors")
  Call<List<Object>> getDeploymentMonitors();

  @GET("/capabilities/expressions")
  Call<Map> getExpressionCapabilities();

  @GET("/installedPlugins")
  Call<List<SpinnakerPluginDescriptor>> getInstalledPlugins();
}
