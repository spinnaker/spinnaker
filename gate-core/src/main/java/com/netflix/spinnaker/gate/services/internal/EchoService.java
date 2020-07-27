package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import java.util.List;
import java.util.Map;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.Header;
import retrofit.http.Headers;
import retrofit.http.POST;
import retrofit.http.Path;
import retrofit.http.Query;

public interface EchoService {

  @Headers("Accept: application/json")
  @POST("/webhooks/{type}/{source}")
  Map webhooks(@Path("type") String type, @Path("source") String source, @Body Map event);

  @Headers("Accept: application/json")
  @POST("/webhooks/{type}/{source}")
  Map webhooks(
      @Path("type") String type,
      @Path("source") String source,
      @Body Map event,
      @Header("X-Hub-Signature") String gitHubSignature,
      @Header("X-Event-Key") String bitBucketEventType);

  @GET("/validateCronExpression")
  Map validateCronExpression(@Query("cronExpression") String cronExpression);

  @GET("/pubsub/subscriptions")
  List<Map<String, String>> getPubsubSubscriptions();

  @POST("/")
  String postEvent(@Body Map event);

  @GET("/quietPeriod")
  Map getQuietPeriodState();

  @GET("/installedPlugins")
  List<SpinnakerPluginDescriptor> getInstalledPlugins();

  @GET("/notifications/metadata")
  List getNotificationTypeMetadata();
}
