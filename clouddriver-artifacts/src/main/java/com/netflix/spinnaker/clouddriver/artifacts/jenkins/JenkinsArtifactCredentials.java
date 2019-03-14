/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.jenkins;


import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.config.SimpleHttpArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@Slf4j
public class JenkinsArtifactCredentials extends SimpleHttpArtifactCredentials<JenkinsArtifactAccount> implements ArtifactCredentials {
  public static final String TYPE = "jenkins/file";

  @Getter
  private final String name;

  @Getter
  private final List<String> types = Collections.singletonList(TYPE);

  private final JenkinsArtifactAccount jenkinsArtifactAccount;

  JenkinsArtifactCredentials(JenkinsArtifactAccount account, OkHttpClient okHttpClient) {
    super(okHttpClient, account);
    this.jenkinsArtifactAccount = account;
    this.name = account.getName();
  }

  @Override
  protected HttpUrl getDownloadUrl(Artifact artifact) {
    String formattedJenkinsAddress = jenkinsArtifactAccount.getAddress().endsWith("/") ? jenkinsArtifactAccount.getAddress() : jenkinsArtifactAccount.getAddress() + "/";
    String formattedReference = artifact.getReference().startsWith("/") ? artifact.getReference() : "/" + artifact.getReference();
    String buildUrl = formattedJenkinsAddress + "job/" + artifact.getName() + "/" + artifact.getVersion() + "/artifact" + formattedReference;
    HttpUrl url = HttpUrl.parse(buildUrl);
    if (url == null) {
      throw new IllegalArgumentException("Malformed content URL in reference: " + buildUrl + ". Read more here https://www.spinnaker.io/reference/artifacts/types/");
    }
    return url;
  }

}
