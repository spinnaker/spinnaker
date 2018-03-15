package com.netflix.kayenta.datadog.service;

import retrofit.http.GET;
import retrofit.http.Query;

public interface DatadogRemoteService {
  @GET("/api/v1/query")
  DatadogTimeSeries getTimeSeries(@Query("api_key") String apiKey,
                                  @Query("application_key") String applicationKey,
                                  @Query("from") int startTimestamp,
                                  @Query("to") int endTimestamp,
                                  @Query("query") String query);
}
