package com.netflix.spinnaker.echo.services;

import com.netflix.spinnaker.echo.model.Pipeline;
import java.util.Map;
import retrofit.http.*;

import java.util.List;

public interface Front50Service {
  @GET("/pipelines?restricted=false")
  @Headers("Accept: application/json")
  List<Map<String, Object>> getPipelines(); // Return Map here so we don't throw away MPT attributes.

  @GET("/pipelines/{application}?refresh=false")
  @Headers("Accept: application/json")
  List<Pipeline> getPipelines(@Path("application") String application);

  // either an empty list or a singleton of a raw pipeline config
  @GET("/pipelines/{pipelineId}/history?limit=1")
  List<Map<String, Object>> getLatestVersion(@Path("pipelineId") String pipelineId);

  @POST("/graphql")
  @Headers("Accept: application/json")
  GraphQLQueryResponse query(@Body GraphQLQuery body);
}
