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

import com.google.api.services.cloudbuild.v1.CloudBuild;
import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.CancelBuildRequest;
import com.google.api.services.cloudbuild.v1.model.ListBuildTriggersResponse;
import com.google.api.services.cloudbuild.v1.model.Operation;
import com.google.api.services.cloudbuild.v1.model.RepoSource;
import com.google.api.services.storage.Storage;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Generates authenticated requests to the Google Cloud Build API for a single configured account,
 * delegating to GoogleCloudBuildExecutor to execute these requests.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class GoogleCloudBuildClient {
  private final String projectId;
  private final CloudBuild cloudBuild;
  private final Storage cloudStorage;
  private final GoogleCloudBuildExecutor executor;

  @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
  static class Factory {
    private final CloudBuildFactory cloudBuildFactory;
    private final GoogleCloudBuildExecutor executor;
    private final String applicationName;

    GoogleCloudBuildClient create(GoogleCredentials credentials, String projectId) {
      CloudBuild cloudBuild = cloudBuildFactory.getCloudBuild(credentials, applicationName);
      Storage cloudStorage = cloudBuildFactory.getCloudStorage(credentials, applicationName);
      return new GoogleCloudBuildClient(projectId, cloudBuild, cloudStorage, executor);
    }
  }

  Operation createBuild(Build build) {
    if (build.getOptions() != null && build.getOptions().getPool() != null) {
      String[] parts = build.getOptions().getPool().getName().split("/");
      String parent = "projects/" + parts[1] + "/locations/" + parts[3];
      return executor.execute(
          () -> cloudBuild.projects().locations().builds().create(parent, build));
    }
    return executor.execute(() -> cloudBuild.projects().builds().create(projectId, build));
  }

  Build getBuild(String buildId) {
    return executor.execute(() -> cloudBuild.projects().builds().get(projectId, buildId));
  }

  Build stopBuild(String buildId) {
    return executor.execute(
        () -> cloudBuild.projects().builds().cancel(projectId, buildId, new CancelBuildRequest()));
  }

  ListBuildTriggersResponse listTriggers() {
    return executor.execute(() -> cloudBuild.projects().triggers().list(projectId));
  }

  Operation runTrigger(String triggerId, RepoSource repoSource) {
    return executor.execute(
        () -> cloudBuild.projects().triggers().run(projectId, triggerId, repoSource));
  }

  InputStream fetchStorageObject(String bucket, String object, @Nullable Long version)
      throws IOException {
    Storage.Objects.Get getRequest = cloudStorage.objects().get(bucket, object);
    if (version != null) {
      getRequest.setGeneration(version);
    }
    return getRequest.executeMediaAsInputStream();
  }
}
