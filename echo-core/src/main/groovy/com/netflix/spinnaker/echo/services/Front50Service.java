package com.netflix.spinnaker.echo.services;

import java.util.List;
import com.netflix.spinnaker.echo.model.Pipeline;
import retrofit.http.GET;
import retrofit.http.Headers;
import retrofit.http.Path;
import rx.Observable;

public interface Front50Service {
  @GET("/pipelines?restricted=false")
  @Headers("Accept: application/json")
  Observable<List<Pipeline>> getPipelines();

  @GET("/pipelines/{application}?refresh=false")
  @Headers("Accept: application/json")
  Observable<List<Pipeline>> getPipelines(@Path("application") String application);
}
