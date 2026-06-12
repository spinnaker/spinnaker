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

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
public class MicrosoftTeamsService {
  private final OkHttp3ClientConfiguration okHttp3ClientConfiguration;

  public MicrosoftTeamsService(OkHttp3ClientConfiguration okHttp3ClientConfiguration) {
    this.okHttp3ClientConfiguration = okHttp3ClientConfiguration;
  }

  public ResponseBody sendMessage(String webhookUrl, MicrosoftTeamsMessage message) {
    // The Retrofit instance needs to be created for each message to be sent as
    // the incoming webhook base URL and path may be different for each Teams channel.
    // The full webhook URL is passed via @Url, which overrides the base URL entirely,
    // so the base URL here only needs to be a valid scheme://host[:port]/ placeholder.
    // Deriving it via resolve("/") strips any path and query string (e.g. Teams
    // Workflow URLs carrying ?api-version=...&sig=...) and always ends in "/", which
    // Retrofit's builder requires.
    MicrosoftTeamsClient microsoftTeamsClient =
        new Retrofit.Builder()
            .baseUrl(HttpUrl.get(webhookUrl).resolve("/").toString())
            .client(okHttp3ClientConfiguration.createForRetrofit2().build())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(MicrosoftTeamsClient.class);

    return Retrofit2SyncCall.execute(microsoftTeamsClient.sendMessage(webhookUrl, message));
  }
}
