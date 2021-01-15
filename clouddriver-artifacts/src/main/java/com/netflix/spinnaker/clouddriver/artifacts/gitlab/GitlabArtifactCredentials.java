/*
 * Copyright 2018 Armory
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

package com.netflix.spinnaker.clouddriver.artifacts.gitlab;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.config.SimpleHttpArtifactCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@NonnullByDefault
@Slf4j
public class GitlabArtifactCredentials extends SimpleHttpArtifactCredentials<GitlabArtifactAccount>
    implements ArtifactCredentials {
  public static final String CREDENTIALS_TYPE = "artifacts-gitlab";
  @Getter private final String name;
  @Getter private final ImmutableList<String> types = ImmutableList.of("gitlab/file");

  GitlabArtifactCredentials(GitlabArtifactAccount account, OkHttpClient okHttpClient) {
    super(okHttpClient, account);
    this.name = account.getName();
  }

  @Override
  protected Headers getHeaders(GitlabArtifactAccount account) {
    Headers.Builder headers = new Headers.Builder();
    Optional<String> token = account.getTokenAsString();
    if (token.isPresent()) {
      headers.set("Private-Token", token.get());
      log.info("Loaded credentials for GitLab Artifact Account {}", account.getName());
    } else {
      log.info("No credentials included with GitLab Artifact Account {}", account.getName());
    }
    return headers.build();
  }

  @Override
  protected HttpUrl getDownloadUrl(Artifact artifact) {
    String version = Strings.nullToEmpty(artifact.getVersion());
    if (version.isEmpty()) {
      log.info("No version specified for artifact {}, using 'master'.", version);
      version = "master";
    }
    return parseUrl(artifact.getReference()).newBuilder().addQueryParameter("ref", version).build();
  }

  @Override
  public String getType() {
    return CREDENTIALS_TYPE;
  }
}
