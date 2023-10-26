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

import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import io.cloudevents.CloudEvent;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.Response;

@Slf4j
@Component
public class CDEventsSenderService {
  private Client retrofitClient;
  private RestAdapter.LogLevel retrofitLogLevel;

  public CDEventsSenderService(Client retrofitClient, RestAdapter.LogLevel retrofitLogLevel) {
    this.retrofitClient = retrofitClient;
    this.retrofitLogLevel = retrofitLogLevel;
  }

  public Response sendCDEvent(CloudEvent cdEvent, String eventsBrokerUrl) {
    CDEventsHTTPMessageConverter converterFactory = CDEventsHTTPMessageConverter.create();
    RequestInterceptor authInterceptor =
        new RequestInterceptor() {
          @Override
          public void intercept(RequestInterceptor.RequestFacade request) {
            request.addHeader("Ce-Id", cdEvent.getId());
            request.addHeader("Ce-Specversion", cdEvent.getSpecVersion().V1.toString());
            request.addHeader("Ce-Source", cdEvent.getSource().toString());
            request.addHeader("Ce-Type", cdEvent.getType());
            request.addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
          }
        };

    CDEventsSenderClient cdEventsSenderClient =
        new RestAdapter.Builder()
            .setConverter(converterFactory)
            .setClient(retrofitClient)
            .setEndpoint(getEndpointUrl(eventsBrokerUrl))
            .setRequestInterceptor(authInterceptor)
            .setLogLevel(retrofitLogLevel)
            .setLog(new Slf4jRetrofitLogger(CDEventsSenderClient.class))
            .build()
            .create(CDEventsSenderClient.class);
    String jsonEvent = converterFactory.convertCDEventToJson(cdEvent);
    log.info("Sending CDEvent Json {} ", jsonEvent);
    return cdEventsSenderClient.sendCDEvent(jsonEvent, getRelativePath(eventsBrokerUrl));
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
}
