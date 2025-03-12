package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import io.cloudevents.CloudEvent;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface EchoService {

  @Headers("Accept: application/json")
  @POST("/webhooks/{type}/{source}")
  Call<Map> webhooks(@Path("type") String type, @Path("source") String source, @Body Map event);

  @Headers("Accept: application/json")
  @POST("/webhooks/cdevents/{source}")
  Call<ResponseEntity<Void>> webhooks(
      @Path("source") String source,
      @Body CloudEvent cdevent,
      @Header("Ce-Data") String ceDataJsonString,
      @Header("Ce-Id") String cdId,
      @Header("Ce-Specversion") String cdSpecVersion,
      @Header("Ce-Type") String cdType,
      @Header("Ce-Source") String cdSource);

  @Headers("Accept: application/json")
  @POST("/webhooks/{type}/{source}")
  Call<Map> webhooks(
      @Path("type") String type,
      @Path("source") String source,
      @Body Map event,
      @Header("X-Hub-Signature") String gitHubSignature,
      @Header("X-Event-Key") String bitBucketEventType);

  @GET("/validateCronExpression")
  Call<Map> validateCronExpression(@Query("cronExpression") String cronExpression);

  @GET("/pubsub/subscriptions")
  Call<List<Map<String, String>>> getPubsubSubscriptions();

  @POST("/")
  Call<Void> postEvent(@Body Map event);

  @GET("/quietPeriod")
  Call<Map> getQuietPeriodState();

  @GET("/installedPlugins")
  Call<List<SpinnakerPluginDescriptor>> getInstalledPlugins();

  @GET("/notifications/metadata")
  Call<List> getNotificationTypeMetadata();
}
