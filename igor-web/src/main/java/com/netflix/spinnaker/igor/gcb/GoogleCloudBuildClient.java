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

package com.netflix.spinnaker.igor.gcb;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.cloudbuild.v1.CloudBuild;
import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.Operation;
import com.google.api.services.storage.Storage;
import java.io.IOException;
import java.io.InputStream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Generates authenticated requests to the Google Cloud Build API for a single configured account,
 * delegating to GoogleCloudBuildExecutor to execute these requests.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class GoogleCloudBuildClient {
  private final String projectId;
  private final CloudBuild cloudBuild;
  private final Storage cloudStorage;
  private final GoogleCloudBuildExecutor executor;

  @RequiredArgsConstructor
  public static class Factory {
    private final CloudBuildFactory cloudBuildFactory;
    private final GoogleCloudBuildExecutor executor;
    private final String applicationName;

    public GoogleCloudBuildClient create(GoogleCredential credential, String projectId) {
      CloudBuild cloudBuild = cloudBuildFactory.getCloudBuild(credential, applicationName);
      Storage cloudStorage = cloudBuildFactory.getCloudStorage(credential, applicationName);
      return new GoogleCloudBuildClient(projectId, cloudBuild, cloudStorage, executor);
    }
  }

  public Operation createBuild(Build build) {
    return executor.execute(() -> cloudBuild.projects().builds().create(projectId, build));
  }

  public Build getBuild(String buildId) {
    return executor.execute(() -> cloudBuild.projects().builds().get(projectId, buildId));
  }

  public InputStream fetchStorageObject(String bucket, String object) throws IOException {
    return cloudStorage.objects().get(bucket, object).executeMediaAsInputStream();
  }
}
