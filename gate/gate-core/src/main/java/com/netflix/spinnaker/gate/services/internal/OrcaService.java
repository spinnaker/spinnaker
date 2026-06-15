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
  @POST("ops")
  Call<Map<String, String>> doOperation(@Body Map<String, Object> body);

  @Headers("Accept: application/json")
  @GET("applications/{application}/tasks")
  Call<List<Map<String, Object>>> getTasks(
      @Path("application") String app,
      @Query("page") Integer page,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses);

  @Headers("Accept: application/json")
  @GET("v2/applications/{application}/pipelines")
  Call<List<Map<String, Object>>> getPipelines(
      @Path("application") String app,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses,
      @Query("expand") Boolean expand,
      @Query("pipelineNameFilter") String pipelineNameFilter,
      @Query("pipelineLimit") Integer pipelineLimit);

  /** Retrieve pipeline executions for a project. Orca returns a list of PipelineExecution. */
  @Headers("Accept: application/json")
  @GET("projects/{projectId}/pipelines")
  Call<List<Map<String, Object>>> getPipelinesForProject(
      @Path("projectId") String projectId,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses);

  @Headers("Accept: application/json")
  @GET("tasks/{id}")
  Call<Map<String, Object>> getTask(@Path("id") String id);

  @Headers("Accept: application/json")
  @DELETE("tasks/{id}")
  Call<Void> deleteTask(@Path("id") String id);

  @Headers("Accept: application/json")
  @PUT("tasks/{id}/cancel")
  Call<Void> cancelTask(@Path("id") String id, @Body String ignored);

  @Headers("Accept: application/json")
  @PUT("tasks/cancel")
  Call<Void> cancelTasks(@Body List<String> taskIds);

  /**
   * Retrieve a subset of pipeline executions by config IDs or execution IDs. Orca returns a list of
   * PipelineExecution.
   */
  @Headers("Accept: application/json")
  @GET("pipelines")
  Call<List<Map<String, Object>>> getSubsetOfExecutions(
      @Query("pipelineConfigIds") String pipelineConfigIds,
      @Query("executionIds") String executionIds,
      @Query("limit") Integer limit,
      @Query("statuses") String statuses,
      @Query("expand") boolean expand);

  @GET("/pipelines/failedStages")
  Call<List<Object>> getFailedStagesForPipelineExecution(
      @Query("executionId") String executionId,
      @Query("deckOrigin") String deckOrigin,
      @Query("limit") Integer limit);

  /**
   * Search for pipeline executions by trigger criteria. Orca returns a list of PipelineExecution.
   */
  @Headers("Accept: application/json")
  @GET("applications/{application}/pipelines/search")
  Call<List<Map<String, Object>>> searchForPipelineExecutionsByTrigger(
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

  /** Retrieve a single pipeline execution by ID. Orca returns a PipelineExecution. */
  @Headers("Accept: application/json")
  @GET("pipelines/{id}")
  Call<Map<String, Object>> getPipeline(@Path("id") String id);

  @Headers("Accept: application/json")
  @PUT("pipelines/{id}/cancel")
  Call<Void> cancelPipeline(
      @Path("id") String id,
      @Query("reason") String reason,
      @Query("force") boolean force,
      @Body String ignored);

  @Headers("Accept: application/json")
  @PUT("/admin/forceCancelExecution")
  Call<Void> forceCancelPipeline(
      @Query("executionId") String executionId,
      @Query("executionType") String executionType,
      @Query("canceledBy") String canceledBy);

  /** Rehydrate an execution into the queue. Orca returns a HydrateQueueOutput. */
  @Headers("Accept: application/json")
  @POST("/admin/queue/hydrate")
  Call<Map<String, Object>> rehydrateExecution(
      @Query("executionId") String executionId, @Query("dryRun") boolean dryRun);

  @Headers("Accept: application/json")
  @PUT("pipelines/{id}/pause")
  Call<Void> pausePipeline(@Path("id") String id, @Body String ignored);

  @Headers("Accept: application/json")
  @PUT("pipelines/{id}/resume")
  Call<Void> resumePipeline(@Path("id") String id, @Body String ignored);

  @Headers("Accept: application/json")
  @DELETE("pipelines/{id}")
  Call<Void> deletePipeline(@Path("id") String id);

  /** Restart a pipeline stage. Orca returns a PipelineExecution. */
  @Headers("Accept: application/json")
  @PUT("pipelines/{executionId}/stages/{stageId}/restart")
  Call<Map<String, Object>> restartPipelineStage(
      @Path("executionId") String executionId,
      @Path("stageId") String stageId,
      @Body Map<String, Object> restartDetails);

  /** Start a pipeline execution. Orca returns {@code Map<String, Object>}. */
  @Headers("Accept: application/json")
  @POST("orchestrate")
  Call<Map<String, Object>> startPipeline(
      @Body Map<String, Object> pipelineConfig, @Query("user") String user);

  /** Update a pipeline stage. Orca returns a PipelineExecution. */
  @Headers("Accept: application/json")
  @PATCH("pipelines/{executionId}/stages/{stageId}")
  Call<Map<String, Object>> updatePipelineStage(
      @Path("executionId") String executionId,
      @Path("stageId") String stageId,
      @Body Map<String, Object> context);

  /** Evaluate a SpEL expression against a pipeline execution. Orca returns a raw Map. */
  @Headers("Accept: application/json")
  @GET("pipelines/{id}/evaluateExpression")
  Call<Map<String, Object>> evaluateExpressionForExecution(
      @Path("id") String executionId, @Query("expression") String pipelineExpression);

  /**
   * Evaluate a SpEL expression at a specific stage of a pipeline execution. Orca returns a raw Map.
   */
  @Headers("Accept: application/json")
  @GET("pipelines/{id}/{stageId}/evaluateExpression")
  Call<Map<String, Object>> evaluateExpressionForExecutionAtStage(
      @Path("id") String executionId,
      @Path("stageId") String stageId,
      @Query("expression") String pipelineExpression);

  /**
   * Evaluate variables (like the Evaluate Variables stage) against a pipeline execution. Orca
   * returns a raw Map.
   */
  @Headers("Accept: application/json")
  @POST("pipelines/{id}/evaluateVariables")
  Call<Map<String, Object>> evaluateVariables(
      @Path("id") String id,
      @Query("requisiteStageRefIds") String requisiteStageRefIds,
      @Query("spelVersion") String spelVersionOverride,
      @Body List<Map<String, String>> expressions);

  /** Retrieve preconfigured webhook definitions. Orca returns {@code List<Map<String, Object>>}. */
  @Headers("Accept: application/json")
  @GET("webhooks/preconfigured")
  Call<List<Map<String, Object>>> preconfiguredWebhooks();

  /** Retrieve preconfigured job definitions. Orca returns {@code List<Map<String, Object>>}. */
  @Headers("Accept: application/json")
  @GET("jobs/preconfigured")
  Call<List<Map<String, Object>>> getPreconfiguredJobs();

  /** Resolve a pipeline template. Orca returns a PipelineTemplate. */
  @Headers("Accept: application/json")
  @GET("pipelineTemplate")
  Call<Map<String, Object>> resolvePipelineTemplate(
      @Query("source") String source,
      @Query("executionId") String executionId,
      @Query("pipelineConfigId") String pipelineConfigId);

  @POST("convertPipelineToTemplate")
  Call<ResponseBody> convertToPipelineTemplate(@Body Map<String, Object> pipelineConfig);

  @Headers("Accept: application/json")
  @POST("v2/pipelineTemplates/plan")
  Call<Map<String, Object>> plan(@Body Map<String, Object> pipelineConfig);

  @POST("concourse/stage/start")
  Call<ResponseBody> concourseStageExecution(
      @Query("stageId") String stageId,
      @Query("job") String job,
      @Query("buildNumber") Integer buildNumber,
      @Body String emptyBody);

  @GET("capabilities/deploymentMonitors")
  Call<List<Object>> getDeploymentMonitors();

  /** Retrieve SpEL expression capabilities. Orca returns an ExpressionCapabilityResult. */
  @GET("capabilities/expressions")
  Call<Map<String, Object>> getExpressionCapabilities();

  @GET("installedPlugins")
  Call<List<SpinnakerPluginDescriptor>> getInstalledPlugins();
}
