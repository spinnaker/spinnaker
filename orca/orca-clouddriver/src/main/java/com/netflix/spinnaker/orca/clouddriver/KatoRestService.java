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
<<<<<<< HEAD
  @POST("/ops")
  TaskId requestOperations(
=======
  @POST("ops")
  Call<TaskId> requestOperations(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Query("clientRequestId") String clientRequestId,
      @Body Collection<? extends Map<String, Map>> operations);

<<<<<<< HEAD
  @POST("/{cloudProvider}/ops")
  TaskId requestOperations(
=======
  @POST("{cloudProvider}/ops")
  Call<TaskId> requestOperations(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Query("clientRequestId") String clientRequestId,
      @Path("cloudProvider") String cloudProvider,
      @Body Collection<? extends Map<String, Map>> operations);

<<<<<<< HEAD
  @POST("/{cloudProvider}/ops/{operationName}")
  Response submitOperation(
=======
  @POST("{cloudProvider}/ops/{operationName}")
  Call<ResponseBody> submitOperation(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Query("clientRequestId") String clientRequestId,
      @Path("cloudProvider") String cloudProvider,
      @Path("operationName") String operationName,
      @Body OperationContext operation);

<<<<<<< HEAD
  @PATCH("/{cloudProvider}/task/{id}")
  TaskId updateTask(
=======
  @PATCH("{cloudProvider}/task/{id}")
  Call<TaskId> updateTask(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("cloudProvider") String cloudProvider, @Path("id") String id, @Body Map details);

  @POST("{cloudProvider}/task/{id}/restart")
  @Retry(name = "katoRetrofitServiceWriter")
  TaskId restartTaskViaOperations(
      @Path("cloudProvider") String cloudProvider,
      @Path("id") String id,
      @Body Collection<? extends Map<String, Map>> operations);

<<<<<<< HEAD
  @GET("/applications/{app}/jobs/{account}/{region}/{id}")
  Response collectJob(
=======
  @GET("applications/{app}/jobs/{account}/{region}/{id}")
  Call<ResponseBody> collectJob(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("app") String app,
      @Path("account") String account,
      @Path("region") String region,
      @Path("id") String id);

<<<<<<< HEAD
  @DELETE("/applications/{app}/jobs/{account}/{location}/{id}")
  Response cancelJob(
=======
  @DELETE("applications/{app}/jobs/{account}/{location}/{id}")
  Call<ResponseBody> cancelJob(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("app") String app,
      @Path("account") String account,
      @Path("location") String region,
      @Path("id") String id);

<<<<<<< HEAD
  @GET("/applications/{app}/jobs/{account}/{region}/{id}/{fileName}")
  Map<String, Object> getFileContents(
=======
  @GET("applications/{app}/jobs/{account}/{region}/{id}/{fileName}")
  Call<Map<String, Object>> getFileContents(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("app") String app,
      @Path("account") String account,
      @Path("region") String region,
      @Path("id") String id,
      @Path("fileName") String fileName);

<<<<<<< HEAD
  @GET("/applications/{app}/kubernetes/pods/{account}/{namespace}/{podName}/{fileName}")
  Map<String, Object> getFileContentsFromKubernetesPod(
=======
  @GET("applications/{app}/kubernetes/pods/{account}/{namespace}/{podName}/{fileName}")
  Call<Map<String, Object>> getFileContentsFromKubernetesPod(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("app") String app,
      @Path("account") String account,
      @Path("namespace") String namespace,
      @Path("podName") String podName,
      @Path("fileName") String fileName);

  /**
   * This should _only_ be called if there is a problem retrieving the Task from
   * CloudDriverTaskStatusService (ie. a clouddriver replica).
   */
<<<<<<< HEAD
  @GET("/task/{id}")
  Task lookupTask(@Path("id") String id);
=======
  @GET("task/{id}")
  Call<Task> lookupTask(@Path("id") String id);
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))

  @POST("task/{id}:resume")
  @Retry(name = "katoRetrofitServiceWriter")
  TaskId resumeTask(@Path("id") String id);
}
