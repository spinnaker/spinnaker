package com.netflix.spinnaker.gate.services.internal;

import java.util.List;
import java.util.Map;
import retrofit.client.Response;
import retrofit.http.*;

public interface OrcaService {
  @Headers("Content-type: application/context+json")
  @POST("/ops")
  public abstract Map doOperation(@Body Map<String, ? extends Object> body);

  @Headers("Accept: application/json")
  @GET("/applications/{application}/tasks")
  public abstract List getTasks(
      @Path("application") String app,
      @Query("page") Integer page,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses);

  @Headers("Accept: application/json")
  @GET("/v2/applications/{application}/pipelines")
  public abstract List getPipelines(
      @Path("application") String app,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses,
      @Query("expand") Boolean expand);

  @Headers("Accept: application/json")
  @GET("/projects/{projectId}/pipelines")
  public abstract List<Map> getPipelinesForProject(
      @Path("projectId") String projectId,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses);

  @Headers("Accept: application/json")
  @GET("/tasks/{id}")
  public abstract Map getTask(@Path("id") String id);

  @Headers("Accept: application/json")
  @DELETE("/tasks/{id}")
  public abstract Map deleteTask(@Path("id") String id);

  @Headers("Accept: application/json")
  @PUT("/tasks/{id}/cancel")
  public abstract Map cancelTask(@Path("id") String id, @Body String ignored);

  @Headers("Accept: application/json")
  @PUT("/tasks/cancel")
  public abstract Map cancelTasks(@Body List<String> taskIds);

  @Headers("Accept: application/json")
  @GET("/pipelines")
  public abstract List getSubsetOfExecutions(
      @Query("pipelineConfigIds") String pipelineConfigIds,
      @Query("executionIds") String executionIds,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses,
      @Query("expand") boolean expand);

  @Headers("Accept: application/json")
  @GET("/applications/{application}/pipelines/search")
  public abstract List searchForPipelineExecutionsByTrigger(
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
  public abstract Map getPipeline(@Path("id") String id);

  @Headers("Accept: application/json")
  @PUT("/pipelines/{id}/cancel")
  public abstract Map cancelPipeline(
      @Path("id") String id,
      @Query("reason") String reason,
      @Query("force") boolean force,
      @Body String ignored);

  @Headers("Accept: application/json")
  @PUT("/pipelines/{id}/pause")
  public abstract Map pausePipeline(@Path("id") String id, @Body String ignored);

  @Headers("Accept: application/json")
  @PUT("/pipelines/{id}/resume")
  public abstract Map resumePipeline(@Path("id") String id, @Body String ignored);

  @Headers("Accept: application/json")
  @DELETE("/pipelines/{id}")
  public abstract Map deletePipeline(@Path("id") String id);

  @Headers("Accept: application/json")
  @PUT("/pipelines/{executionId}/stages/{stageId}/restart")
  public abstract Map restartPipelineStage(
      @Path("executionId") String executionId,
      @Path("stageId") String stageId,
      @Body Map restartDetails);

  @Headers("Accept: application/json")
  @POST("/orchestrate")
  public abstract Map startPipeline(@Body Map pipelineConfig, @Query("user") String user);

  @Headers("Accept: application/json")
  @PATCH("/pipelines/{executionId}/stages/{stageId}")
  public abstract Map updatePipelineStage(
      @Path("executionId") String executionId, @Path("stageId") String stageId, @Body Map context);

  @Headers("Accept: application/json")
  @GET("/pipelines/{id}/evaluateExpression")
  public abstract Map evaluateExpressionForExecution(
      @Path("id") String executionId, @Query("expression") String pipelineExpression);

  @Headers("Accept: application/json")
  @GET("/pipelines/{id}/{stageId}/evaluateExpression")
  public abstract Map evaluateExpressionForExecutionAtStage(
      @Path("id") String executionId,
      @Path("stageId") String stageId,
      @Query("expression") String pipelineExpression);

  @Headers("Accept: application/json")
  @POST("/pipelines/{id}/evaluateVariables")
  public abstract Map evaluateVariables(
      @Path("id") String id,
      @Query("requisiteStageRefIds") String requisiteStageRefIds,
      @Query("spelVersion") String spelVersionOverride,
      @Body List<Map<String, String>> expressions);

  @Headers("Accept: application/json")
  @GET("/webhooks/preconfigured")
  public abstract List preconfiguredWebhooks();

  @Headers("Accept: application/json")
  @GET("/jobs/preconfigured")
  public abstract List getPreconfiguredJobs();

  @Headers("Accept: application/json")
  @GET("/pipelineTemplate")
  public abstract Map resolvePipelineTemplate(
      @Query("source") String source,
      @Query("executionId") String executionId,
      @Query("pipelineConfigId") String pipelineConfigId);

  @POST("/convertPipelineToTemplate")
  public abstract Response convertToPipelineTemplate(
      @Body Map<String, ? extends Object> pipelineConfig);

  @Headers("Accept: application/json")
  @POST("/v2/pipelineTemplates/plan")
  public abstract Map<String, Object> plan(@Body Map<String, Object> pipelineConfig);

  @POST("/concourse/stage/start")
  public abstract Response concourseStageExecution(
      @Query("stageId") String stageId,
      @Query("job") String job,
      @Query("buildNumber") Integer buildNumber,
      @Body String emptyBody);

  @GET("/capabilities/deploymentMonitors")
  public abstract List<Object> getDeploymentMonitors();

  @GET("/capabilities/expressions")
  public abstract Map getExpressionCapabilities();
}
