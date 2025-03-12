package com.netflix.spinnaker.gate.services.gremlin;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;

public interface GremlinService {
  @GET("/templates/command")
  Call<List> getCommandTemplates(@Header("Authorization") String authHeader);

  @GET("/templates/target")
  Call<List> getTargetTemplates(@Header("Authorization") String authHeader);
}
