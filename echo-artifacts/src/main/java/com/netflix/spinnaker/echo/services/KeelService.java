package com.netflix.spinnaker.echo.services;

import java.util.Map;
import retrofit.http.Body;
import retrofit.http.POST;

public interface KeelService {
  @POST("/artifacts/events")
  Void sendArtifactEvent(@Body Map event);
}
