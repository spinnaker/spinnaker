/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.echo.pubsub.google;

import com.netflix.spinnaker.echo.services.IgorService;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.parsing.ArtifactExtractor;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class GoogleCloudBuildArtifactExtractor implements ArtifactExtractor {
  private final String account;
  private final IgorService igorService;
  private final RetrySupport retrySupport;

  @Component
  @ConditionalOnProperty("gcb.enabled")
  @RequiredArgsConstructor
  public static class Factory {
    private final IgorService igorService;
    private final RetrySupport retrySupport;

    public GoogleCloudBuildArtifactExtractor create(String account) {
      return new GoogleCloudBuildArtifactExtractor(account, igorService, retrySupport);
    }
  }

  @Override
  public List<Artifact> getArtifacts(String messagePayload) {
    RequestBody build = RequestBody.create(messagePayload, MediaType.parse("application/json"));
    try {
      return retrySupport.retry(
          () ->
              AuthenticatedRequest.allowAnonymous(
                  () ->
                      Retrofit2SyncCall.execute(
                          igorService.extractGoogleCloudBuildArtifacts(account, build))),
          5,
          2000,
          false);
    } catch (Exception e) {
      log.error("Failed to fetch artifacts for build: {}", e);
      return Collections.emptyList();
    }
  }
}
