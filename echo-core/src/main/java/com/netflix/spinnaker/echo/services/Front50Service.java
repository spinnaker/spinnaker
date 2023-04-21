package com.netflix.spinnaker.echo.services;

import com.netflix.spinnaker.echo.model.Pipeline;
import java.util.List;
import java.util.Map;
import retrofit.http.*;

public interface Front50Service {
  @GET("/pipelines?restricted=false")
  @Headers("Accept: application/json")
  List<Map<String, Object>>
      getPipelines(); // Return Map here so we don't throw away MPT attributes.

  @GET("/pipelines?restricted=false")
  @Headers("Accept: application/json")
  List<Map<String, Object>> getPipelines(
      @Query("enabledPipelines") Boolean enabledPipelines,
      @Query("enabledTriggers") Boolean enabledTriggers,
      @Query("triggerTypes")
          String triggerTypes); // Return Map here so we don't throw away MPT attributes.

  @GET("/pipelines/{application}?refresh=false")
  @Headers("Accept: application/json")
  List<Pipeline> getPipelines(@Path("application") String application);

  @GET("/pipelines/{pipelineId}/get")
  Map<String, Object> getPipeline(@Path("pipelineId") String pipelineId);

  @GET("/pipelines/{application}/name/{name}?refresh=true")
  Map<String, Object> getPipelineByName(
      @Path("application") String application, @Path("name") String name);

  @POST("/graphql")
  @Headers("Accept: application/json")
  GraphQLQueryResponse query(@Body GraphQLQuery body);
}
