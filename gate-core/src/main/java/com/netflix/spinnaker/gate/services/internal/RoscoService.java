package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Path;

public interface RoscoService {
  @GET("/bakeOptions")
  Call<List> bakeOptions();

  @GET("/bakeOptions/{cloudProvider}")
  Call<Map> bakeOptions(@Path("cloudProvider") String cloudProvider);

  @Headers("Accept: application/json")
  @GET("/api/v1/{region}/logs/{statusId}")
  Call<Map> lookupLogs(@Path("region") String region, @Path("statusId") String statusId);

  @GET("/installedPlugins")
  Call<List<SpinnakerPluginDescriptor>> getInstalledPlugins();
}
