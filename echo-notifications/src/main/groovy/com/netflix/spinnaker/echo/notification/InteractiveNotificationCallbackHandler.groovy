/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.notification;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.echo.api.Notification;
import com.netflix.spinnaker.echo.api.Notification.InteractiveActionCallback
import com.netflix.spinnaker.echo.util.RetrofitUtils
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Implements the flow of interactive notification processing as described in {@link InteractiveNotificationService}.
 */
@Component
class InteractiveNotificationCallbackHandler {
  private final Logger log = LoggerFactory.getLogger(InteractiveNotificationCallbackHandler.class);

  private OkHttp3ClientConfiguration okHttp3ClientConfiguration
  private List<InteractiveNotificationService> notificationServices;
  private Environment environment;
  private Map<String, SpinnakerService> spinnakerServices = new HashMap<>();

  @Autowired
  InteractiveNotificationCallbackHandler(
      OkHttp3ClientConfiguration okHttp3ClientConfiguration,
      List<InteractiveNotificationService> notificationServices,
      Environment environment
  ) {
    this.okHttp3ClientConfiguration = okHttp3ClientConfiguration;
    this.notificationServices = notificationServices;
    this.environment = environment;
  }

  // For access from tests only
  InteractiveNotificationCallbackHandler(
      List<InteractiveNotificationService> notificationServices,
      Map<String, SpinnakerService> spinnakerServices,
      Environment environment
  ) {
    this(null, notificationServices, environment);
    this.spinnakerServices = spinnakerServices;
  }

  /**
   * Processes a callback request from the notification service by relaying it to the downstream
   * Spinnaker service that originated the message referenced in the payload.
   *
   * @param source The unique name of the source of the callback (e.g. "slack")
   * @param request The request received from the notification service
   */
  ResponseEntity<String> processCallback(final String source, RequestEntity<String> request) {
    log.debug("Received interactive notification callback request from " + source);

    InteractiveNotificationService notificationService = getNotificationService(source);

    if (notificationService == null) {
      throw new NotFoundException("NotificationService for " + source + " not registered");
    }

    final Notification.InteractiveActionCallback callback =
        notificationService.parseInteractionCallback(request);

    SpinnakerService spinnakerService = getSpinnakerService(callback.getServiceId());

    log.debug("Routing notification callback to originating service " + callback.getServiceId());

    // TODO(lfp): error handling (retries?). I'd like to respond to the message in a thread, but
    //  have been unable to make that work. Troubleshooting with Slack support.
    // TODO(lfp): need to retrieve user's accounts to pass in X-SPINNAKER-ACCOUNTS
    final ResponseBody response = Retrofit2SyncCall.execute(spinnakerService.notificationCallback(callback, callback.getUser()));
    log.debug("Received callback response from downstream Spinnaker service: " + response.string());

    // Allows the notification service implementation to respond to the callback as needed
    Optional<ResponseEntity<String>> outwardResponse =
        notificationService.respondToCallback(request);

    return outwardResponse.orElse(new ResponseEntity(HttpStatus.OK));
  }

  private InteractiveNotificationService getNotificationService(String source) {
    return notificationServices.find { it ->
      it.supportsType(source.toUpperCase())
    }
  }

  private SpinnakerService getSpinnakerService(String serviceId) {
    if (!spinnakerServices.containsKey(serviceId)) {
      String baseUrl = environment.getProperty(serviceId + ".baseUrl");

      if (baseUrl == null) {
        throw new InvalidRequestException(
            "Base URL for service " + serviceId + " not found in the configuration.");
      }

      spinnakerServices.put(
        serviceId,
        new Retrofit.Builder()
          .baseUrl(RetrofitUtils.getBaseUrl(baseUrl))
          .client(okHttp3ClientConfiguration.createForRetrofit2().build())
          .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
          .addConverterFactory(JacksonConverterFactory.create())
          .build()
          .create(SpinnakerService.class)
  );
    }

    return spinnakerServices.get(serviceId);
  }

  interface SpinnakerService {
    @POST("notifications/callback")
    Call<ResponseBody> notificationCallback(
        @Body InteractiveActionCallback callback, @Header("X-SPINNAKER-USER") String user);
  }
}
