/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.echo.notification.DryRunNotificationAgent;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.echo.services.Front50Service;
import com.squareup.okhttp.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import static retrofit.Endpoints.newFixedEndpoint;

@Configuration
@ConditionalOnProperty("dryRun.enabled:false")
public class DryRunConfig {

  @Bean
  Endpoint dryRunEndpoint(@Value("dryRun.url") String url) {
    return newFixedEndpoint(url);
  }

  @Bean DryRunNotificationAgent dryRunNotificationAgent(
    Front50Service front50,
    OkHttpClient okHttpClient,
    RestAdapter.LogLevel retrofitLogLevel,
    Endpoint dryRunEndpoint)
  {
    OrcaService orca = new RestAdapter.Builder()
      .setEndpoint(dryRunEndpoint)
      .setClient(new OkClient(okHttpClient))
      .setLogLevel(retrofitLogLevel)
      .build()
      .create(OrcaService.class);
    return new DryRunNotificationAgent(front50, orca);
  }
}
