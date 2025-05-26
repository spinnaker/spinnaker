/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.kayenta.atlas.backends;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.atlas.model.AtlasStorage;
import com.netflix.kayenta.atlas.service.AtlasStorageRemoteService;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit.converter.JacksonConverter;

@Slf4j
@Builder
public class AtlasStorageUpdater {
  @Getter private final AtlasStorageDatabase atlasStorageDatabase = new AtlasStorageDatabase();

  @NotNull private String uri;

  // If we have retrieved backends.json at least once, we will keep using it forever
  // even if we fail later.  It doesn't really change much over time, so this
  // is likely safe enough.
  @Builder.Default private boolean succeededAtLeastOnce = false;

  boolean run(
      RetrofitClientFactory retrofitClientFactory,
      ObjectMapper objectMapper,
      OkHttpClient okHttpClient) {
    RemoteService remoteService = new RemoteService();
    remoteService.setBaseUrl(uri);
    AtlasStorageRemoteService atlasStorageRemoteService =
        retrofitClientFactory.createClient(
            AtlasStorageRemoteService.class,
            new JacksonConverter(objectMapper),
            remoteService,
            okHttpClient);
    try {
      Map<String, Map<String, AtlasStorage>> atlasStorageMap =
          AuthenticatedRequest.allowAnonymous(atlasStorageRemoteService::fetch);
      atlasStorageDatabase.update(atlasStorageMap);
      succeededAtLeastOnce = true;
    } catch (SpinnakerHttpException e) {
      log.warn(e.getResponseCode() + " error while fetching atlas backends from " + uri, e);
    } catch (SpinnakerServerException e) {
      log.warn("Error while fetching atlas backends from " + uri, e);
    }

    return succeededAtLeastOnce;
  }
}
