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
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

@NonnullByDefault
public abstract class SimpleHttpArtifactCredentials<T extends UserInputValidatedArtifactAccount>
    extends BaseHttpArtifactCredentials<T> {
  protected SimpleHttpArtifactCredentials(OkHttpClient okHttpClient, T account) {
    super(okHttpClient, account);
  }

  protected HttpUrl getDownloadUrl(Artifact artifact) throws IOException {
    return parseUrl(artifact.getReference());
  }

  public final InputStream download(Artifact artifact) throws IOException {
    try {
      return fetchUrl(getDownloadUrl(artifact)).byteStream();
    } catch (IOException e) {
      throw new FailedDownloadException(
          "Unable to download the contents of artifact " + artifact + ": " + e.getMessage(), e);
    }
  }
}
