/*
 * Copyright 2020 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.echo.microsoftteams;

import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.Response;
import retrofit.converter.JacksonConverter;

@Slf4j
public class MicrosoftTeamsService {
  private Client retrofitClient;
  private RestAdapter.LogLevel retrofitLogLevel;

  public MicrosoftTeamsService(Client retrofitClient, RestAdapter.LogLevel retrofitLogLevel) {
    this.retrofitClient = retrofitClient;
    this.retrofitLogLevel = retrofitLogLevel;
  }

  public Response sendMessage(String webhookUrl, MicrosoftTeamsMessage message) {
    // The RestAdapter instantiation needs to occur for each message to be sent as
    // the incoming webhook base URL and path may be different for each Teams channel
    MicrosoftTeamsClient microsoftTeamsClient =
        new RestAdapter.Builder()
            .setConverter(new JacksonConverter())
            .setClient(retrofitClient)
            .setEndpoint(getEndpointUrl(webhookUrl))
            .setLogLevel(retrofitLogLevel)
            .setLog(new Slf4jRetrofitLogger(MicrosoftTeamsClient.class))
            .build()
            .create(MicrosoftTeamsClient.class);

    return microsoftTeamsClient.sendMessage(getRelativePath(webhookUrl), message);
  }

  private String getEndpointUrl(String webhookUrl) {
    try {
      URL url = new URL(webhookUrl);
      return url.getProtocol() + "://" + url.getHost();
    } catch (MalformedURLException e) {
      throw new InvalidRequestException(
          "Unable to determine base URL from Microsoft Teams webhook URL.", e);
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
          "Unable to determine relative path from Microsoft Teams webhook URL.", e);
    }

    // Remove slash from beginning of path as the client will prefix the string with a slash
    if (relativePath.charAt(0) == '/') {
      relativePath = relativePath.substring(1);
    }

    return relativePath;
  }
}
