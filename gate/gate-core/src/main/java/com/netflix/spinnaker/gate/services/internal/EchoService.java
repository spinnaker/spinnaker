package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import io.cloudevents.CloudEvent;
import java.util.List;
import java.util.Map;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.HeaderMap;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface EchoService {

  /**
   * Forwards a webhook to echo. {@code event} is sent as-is: echo verifies the signature of git
   * webhooks against the exact bytes the sender signed, so it must not be parsed and re-serialized.
   * {@code headers} are the sender's headers that echo consumes (see {@code WebhookService}).
   */
  @Headers("Accept: application/json")
  @POST("webhooks/{type}/{source}")
  Call<Map> webhooks(
      @Path("type") String type,
      @Path("source") String source,
      @Body RequestBody event,
      @HeaderMap Map<String, String> headers);

  @Headers("Accept: application/json")
  @POST("webhooks/cdevents/{source}")
  Call<Map> webhooks(
      @Path("source") String source,
      @Body CloudEvent cdevent,
      @Header("Ce-Data") String ceDataJsonString,
      @Header("Ce-Id") String cdId,
      @Header("Ce-Specversion") String cdSpecVersion,
      @Header("Ce-Type") String cdType,
      @Header("Ce-Source") String cdSource);

  @GET("validateCronExpression")
  Call<Map> validateCronExpression(@Query("cronExpression") String cronExpression);

  @GET("pubsub/subscriptions")
  Call<List<Map<String, String>>> getPubsubSubscriptions();

  @POST(".")
  Call<Void> postEvent(@Body Map event);

  @GET("quietPeriod")
  Call<Map> getQuietPeriodState();

  @GET("installedPlugins")
  Call<List<SpinnakerPluginDescriptor>> getInstalledPlugins();

  @GET("notifications/metadata")
  Call<List> getNotificationTypeMetadata();
}
