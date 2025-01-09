/*
    Copyright (C) 2023 Nordix Foundation.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
*/

package com.netflix.spinnaker.echo.cdevents;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import io.cloudevents.CloudEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import retrofit2.Response;
import retrofit2.Retrofit;

@Slf4j
@Component
public class CDEventsSenderService {
  private OkHttp3ClientConfiguration okHttpClientConfig;

  public CDEventsSenderService(OkHttp3ClientConfiguration okHttpClientConfig) {
    this.okHttpClientConfig = okHttpClientConfig;
  }

  public Response<ResponseBody> sendCDEvent(CloudEvent cdEvent, String eventsBrokerUrl) {
    CDEventsConverterFactory converterFactory = CDEventsConverterFactory.create();

    RequestInterceptor authInterceptor = new RequestInterceptor(cdEvent);

    CDEventsSenderClient cdEventsSenderClient =
        new Retrofit.Builder()
            .baseUrl(getEndpointUrl(eventsBrokerUrl))
            .client(okHttpClientConfig.createForRetrofit2().addInterceptor(authInterceptor).build())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(converterFactory)
            .build()
            .create(CDEventsSenderClient.class);
    String jsonEvent = converterFactory.convertCDEventToJson(cdEvent);
    log.info("Sending CDEvent Json {} ", jsonEvent);
    return Retrofit2SyncCall.execute(
        cdEventsSenderClient.sendCDEvent(jsonEvent, getRelativePath(eventsBrokerUrl)));
  }

  private String getEndpointUrl(String webhookUrl) {
    try {
      URL url = new URL(webhookUrl);
      String endPointURL =
          url.getPort() != -1
              ? url.getProtocol() + "://" + url.getHost() + ":" + url.getPort()
              : url.getProtocol() + "://" + url.getHost();
      log.info("Endpoint Url to send CDEvent {} ", endPointURL);
      return endPointURL;
    } catch (MalformedURLException e) {
      throw new InvalidRequestException("Unable to determine CloudEvents broker address.", e);
    }
  }

  private String getRelativePath(String webhookUrl) {
    String relativePath = "";

    try {
      URL url = new URL(webhookUrl);
      relativePath = url.getPath();

      if (StringUtils.isEmpty(relativePath)) {
        throw new MalformedURLException();
      }
    } catch (MalformedURLException e) {
      throw new InvalidRequestException(
          "Unable to determine URL from CloudEvents broker address.", e);
    }

    // Remove slash from beginning of path as the client will prefix the string with a slash
    if (relativePath.charAt(0) == '/') {
      relativePath = relativePath.substring(1);
    }

    return relativePath;
  }

  private static class RequestInterceptor implements Interceptor {

    private CloudEvent cdEvent;

    public RequestInterceptor(CloudEvent cdEvent) {
      this.cdEvent = cdEvent;
    }

    public okhttp3.Response intercept(Chain chain) throws IOException {
      Request request =
          chain
              .request()
              .newBuilder()
              .addHeader("Ce-Id", cdEvent.getId())
              .addHeader("Ce-Specversion", cdEvent.getSpecVersion().V1.toString())
              .addHeader("Ce-Source", cdEvent.getSource().toString())
              .addHeader("Ce-Type", cdEvent.getType())
              .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .build();
      return chain.proceed(request);
    }
  }
}
