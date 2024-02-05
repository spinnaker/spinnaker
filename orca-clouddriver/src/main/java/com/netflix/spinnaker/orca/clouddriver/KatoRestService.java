package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.orca.clouddriver.model.OperationContext;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Collection;
import java.util.Map;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.GET;
import retrofit.http.PATCH;
import retrofit.http.POST;
import retrofit.http.Path;
import retrofit.http.Query;

/**
 * An interface to the Kato REST API for Amazon cloud. See {@link
 * http://kato.test.netflix.net:7001/manual/index.html}.
 */
public interface KatoRestService {
  /** @deprecated Use {@code /{cloudProvider}/ops} instead */
  @Deprecated
  @POST("/ops")
  TaskId requestOperations(
      @Query("clientRequestId") String clientRequestId,
      @Body Collection<? extends Map<String, Map>> operations);

  @POST("/{cloudProvider}/ops")
  TaskId requestOperations(
      @Query("clientRequestId") String clientRequestId,
      @Path("cloudProvider") String cloudProvider,
      @Body Collection<? extends Map<String, Map>> operations);

  @POST("/{cloudProvider}/ops/{operationName}")
  Response submitOperation(
      @Query("clientRequestId") String clientRequestId,
      @Path("cloudProvider") String cloudProvider,
      @Path("operationName") String operationName,
      @Body OperationContext operation);

  @PATCH("/{cloudProvider}/task/{id}")
  TaskId updateTask(
      @Path("cloudProvider") String cloudProvider, @Path("id") String id, @Body Map details);

  @POST("/{cloudProvider}/task/{id}/restart")
  @Retry(name = "katoRetrofitServiceWriter")
  TaskId restartTaskViaOperations(
      @Path("cloudProvider") String cloudProvider,
      @Path("id") String id,
      @Body Collection<? extends Map<String, Map>> operations);

  @GET("/applications/{app}/jobs/{account}/{region}/{id}")
  Response collectJob(
      @Path("app") String app,
      @Path("account") String account,
      @Path("region") String region,
      @Path("id") String id);

  @DELETE("/applications/{app}/jobs/{account}/{location}/{id}")
  Response cancelJob(
      @Path("app") String app,
      @Path("account") String account,
      @Path("location") String region,
      @Path("id") String id);

  @GET("/applications/{app}/jobs/{account}/{region}/{id}/{fileName}")
  Map<String, Object> getFileContents(
      @Path("app") String app,
      @Path("account") String account,
      @Path("region") String region,
      @Path("id") String id,
      @Path("fileName") String fileName);

  /**
   * This should _only_ be called if there is a problem retrieving the Task from
   * CloudDriverTaskStatusService (ie. a clouddriver replica).
   */
  @GET("/task/{id}")
  Task lookupTask(@Path("id") String id);

  @POST("/task/{id}:resume")
  @Retry(name = "katoRetrofitServiceWriter")
  TaskId resumeTask(@Path("id") String id);
}
