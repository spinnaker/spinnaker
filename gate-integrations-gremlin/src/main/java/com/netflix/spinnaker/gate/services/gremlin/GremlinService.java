package com.netflix.spinnaker.gate.services.gremlin;

import java.util.List;
import retrofit.http.*;

public interface GremlinService {
  @GET("/templates/command")
  List getCommandTemplates(@Header("Authorization") String authHeader);

  @GET("/templates/target")
  List getTargetTemplates(@Header("Authorization") String authHeader);
}
