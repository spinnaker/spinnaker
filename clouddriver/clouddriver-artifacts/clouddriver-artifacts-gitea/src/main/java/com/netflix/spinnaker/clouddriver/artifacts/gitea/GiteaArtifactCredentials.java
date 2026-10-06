/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.artifacts.gitea;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.config.SimpleHttpArtifactCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Getter;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * Downloads a single file from a Gitea server using the raw file API, i.e. {@code
 * https://gitea.example.com/api/v1/repos/{owner}/{repo}/raw/{filepath}}.
 *
 * <p>Authentication is inherited from {@link
 * com.netflix.spinnaker.clouddriver.artifacts.config.BaseHttpArtifactCredentials}: a configured
 * token is sent as {@code Authorization: token <token>}, which is the header Gitea expects for
 * personal access tokens.
 */
@NonnullByDefault
public class GiteaArtifactCredentials extends SimpleHttpArtifactCredentials<GiteaArtifactAccount>
    implements ArtifactCredentials {
  public static final String CREDENTIALS_TYPE = "artifacts-gitea";
  @Getter private final String name;
  @Getter private final ImmutableList<String> types = ImmutableList.of("gitea/file");

  GiteaArtifactCredentials(GiteaArtifactAccount account, OkHttpClient okHttpClient) {
    super(okHttpClient, account);
    this.name = account.getName();
  }

  @Override
  protected HttpUrl getDownloadUrl(Artifact artifact) {
    HttpUrl url = parseUrl(artifact.getReference());
    String version = Strings.nullToEmpty(artifact.getVersion());
    if (version.isEmpty()) {
      // Without a ref, Gitea serves the file from the repository's default branch.
      return url;
    }
    return url.newBuilder().setQueryParameter("ref", version).build();
  }

  @Override
  public String getType() {
    return CREDENTIALS_TYPE;
  }
}
