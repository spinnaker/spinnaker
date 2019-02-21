package com.netflix.spinnaker.gate.services.gremlin;

import retrofit.http.*;

import java.util.List;

public interface GremlinService {
  @GET("/templates/command")
  List getCommandTemplates(@Header("Authorization") String authHeader);

  @GET("/templates/target")
  List getTargetTemplates(@Header("Authorization") String authHeader);
}
