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

package com.netflix.spinnaker.clouddriver.artifacts.config;

import com.netflix.spinnaker.clouddriver.artifacts.exceptions.FailedDownloadException;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;

import java.io.IOException;
import java.io.InputStream;

public abstract class SimpleHttpArtifactCredentials<T extends ArtifactAccount> extends BaseHttpArtifactCredentials<T> {
  protected SimpleHttpArtifactCredentials(OkHttpClient okHttpClient, T account) {
    super(okHttpClient, account);
  }

  protected HttpUrl getDownloadUrl(Artifact artifact) throws IOException {
    HttpUrl url = HttpUrl.parse(artifact.getReference());
    if (url == null) {
      throw new IllegalArgumentException("Malformed content URL in reference: " + artifact.getReference() + ". Read more here https://www.spinnaker.io/reference/artifacts/types/");
    }
    return url;
  }

  public final InputStream download(Artifact artifact) throws IOException {
    HttpUrl downloadUrl = getDownloadUrl(artifact);
    try {
      return fetchUrl(downloadUrl).byteStream();
    } catch (IOException e) {
      throw new FailedDownloadException("Unable to download the contents of artifact " + artifact + ": " + e.getMessage(), e);
    }
  }
}
