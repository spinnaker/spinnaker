package com.netflix.spinnaker.echo.services;

import retrofit.http.Body;
import retrofit.http.POST;

import java.util.Map;

public interface KeelService {
  @POST("/artifacts/events")
  Void sendArtifactEvent(@Body Map event);
}
