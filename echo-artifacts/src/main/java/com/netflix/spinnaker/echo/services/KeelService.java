package com.netflix.spinnaker.echo.services;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface KeelService {
  @POST("/artifacts/events")
  Call<Void> sendArtifactEvent(@Body Map event);
}
