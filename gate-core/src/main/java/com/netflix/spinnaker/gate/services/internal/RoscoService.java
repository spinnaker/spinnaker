package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import java.util.List;
import java.util.Map;
import retrofit.http.GET;
import retrofit.http.Headers;
import retrofit.http.Path;

public interface RoscoService {
  @GET("/bakeOptions")
  List bakeOptions();

  @GET("/bakeOptions/{cloudProvider}")
  Map bakeOptions(@Path("cloudProvider") String cloudProvider);

  @Headers("Accept: application/json")
  @GET("/api/v1/{region}/logs/{statusId}")
  Map lookupLogs(@Path("region") String region, @Path("statusId") String statusId);

  @GET("/installedPlugins")
  List<SpinnakerPluginDescriptor> getInstalledPlugins();
}
