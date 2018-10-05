package com.netflix.spinnaker.echo.services;

import com.netflix.spinnaker.echo.model.Pipeline;
import retrofit.http.*;
import rx.Observable;

import java.util.List;

public interface Front50Service {
  @GET("/pipelines?restricted=false")
  @Headers("Accept: application/json")
  Observable<List<Pipeline>> getPipelines();

  @GET("/pipelines/{application}?refresh=false")
  @Headers("Accept: application/json")
  Observable<List<Pipeline>> getPipelines(@Path("application") String application);

  @POST("/graphql")
  @Headers("Accept: application/json")
  GraphQLQueryResponse query(@Body GraphQLQuery body);
}
