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
import java.util.List;
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
    requireRawFileUrl(url);
    String version = Strings.nullToEmpty(artifact.getVersion());
    if (version.isEmpty()) {
      // Without a ref, Gitea serves the file from the repository's default branch.
      return url;
    }
    return url.newBuilder().setQueryParameter("ref", version).build();
  }

  /**
   * The account token is sent with every request, so only allow URLs that address a file through
   * the raw file API ({@code <prefix>/api/v1/repos/{owner}/{repo}/raw/{path}}) rather than any
   * other endpoint on the host. {@link HttpUrl} has already normalized {@code .} and {@code ..}
   * segments (including percent-encoded ones), so a reference cannot climb out of that shape.
   */
  private static void requireRawFileUrl(HttpUrl url) {
    List<String> segments = url.pathSegments();
    // api, v1, repos, {owner}, {repo}, raw, then at least one non-empty path segment
    for (int i = 0; i + 6 < segments.size(); i++) {
      if (segments.get(i).equals("api")
          && segments.get(i + 1).equals("v1")
          && segments.get(i + 2).equals("repos")
          && !segments.get(i + 3).isEmpty()
          && !segments.get(i + 4).isEmpty()
          && segments.get(i + 5).equals("raw")
          && !segments.get(segments.size() - 1).isEmpty()) {
        return;
      }
    }
    throw new IllegalArgumentException(
        "Gitea artifact references must use the raw file API, i.e. "
            + "https://<host>/api/v1/repos/{owner}/{repo}/raw/{path}: "
            + url);
  }

  @Override
  public String getType() {
    return CREDENTIALS_TYPE;
  }
}
