package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.orca.clouddriver.model.OperationContext;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Collection;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * An interface to the Kato REST API for Amazon cloud. See {@link
 * http://kato.test.netflix.net:7001/manual/index.html}.
 */
public interface KatoRestService {
  /** @deprecated Use {@code /{cloudProvider}/ops} instead */
  @Deprecated
  @POST("/ops")
  Call<TaskId> requestOperations(
      @Query("clientRequestId") String clientRequestId,
      @Body Collection<Map<String, Map>> operations);

  @POST("/{cloudProvider}/ops")
  Call<TaskId> requestOperations(
      @Query("clientRequestId") String clientRequestId,
      @Path("cloudProvider") String cloudProvider,
      @Body Collection<Map<String, Map>> operations);

  @POST("/{cloudProvider}/ops/{operationName}")
  Call<ResponseBody> submitOperation(
      @Query("clientRequestId") String clientRequestId,
      @Path("cloudProvider") String cloudProvider,
      @Path("operationName") String operationName,
      @Body OperationContext operation);

  @PATCH("/{cloudProvider}/task/{id}")
  Call<TaskId> updateTask(
      @Path("cloudProvider") String cloudProvider, @Path("id") String id, @Body Map details);

  @POST("/{cloudProvider}/task/{id}/restart")
  @Retry(name = "katoRetrofitServiceWriter")
  Call<TaskId> restartTaskViaOperations(
      @Path("cloudProvider") String cloudProvider,
      @Path("id") String id,
      @Body Collection<Map<String, Map>> operations);

  @GET("/applications/{app}/jobs/{account}/{region}/{id}")
  Call<ResponseBody> collectJob(
      @Path("app") String app,
      @Path("account") String account,
      @Path("region") String region,
      @Path("id") String id);

  @DELETE("/applications/{app}/jobs/{account}/{location}/{id}")
  Call<ResponseBody> cancelJob(
      @Path("app") String app,
      @Path("account") String account,
      @Path("location") String region,
      @Path("id") String id);

  @GET("/applications/{app}/jobs/{account}/{region}/{id}/{fileName}")
  Call<Map<String, Object>> getFileContents(
      @Path("app") String app,
      @Path("account") String account,
      @Path("region") String region,
      @Path("id") String id,
      @Path("fileName") String fileName);

  @GET("/applications/{app}/kubernetes/pods/{account}/{namespace}/{podName}/{fileName}")
  Call<Map<String, Object>> getFileContentsFromKubernetesPod(
      @Path("app") String app,
      @Path("account") String account,
      @Path("namespace") String namespace,
      @Path("podName") String podName,
      @Path("fileName") String fileName);

  /**
   * This should _only_ be called if there is a problem retrieving the Task from
   * CloudDriverTaskStatusService (ie. a clouddriver replica).
   */
  @GET("/task/{id}")
  Call<Task> lookupTask(@Path("id") String id);

  @POST("/task/{id}:resume")
  @Retry(name = "katoRetrofitServiceWriter")
  Call<TaskId> resumeTask(@Path("id") String id);
}
