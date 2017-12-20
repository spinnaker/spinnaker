/*
 * Copyright 2017 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.github;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Request.Builder;
import com.squareup.okhttp.Response;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Data
public class GitHubArtifactCredentials implements ArtifactCredentials {
  private final String name;

  @JsonIgnore
  private final Builder requestBuilder;

  @JsonIgnore
  OkHttpClient okHttpClient;

  @JsonIgnore
  ObjectMapper objectMapper;

  public GitHubArtifactCredentials(GitHubArtifactAccount account, OkHttpClient okHttpClient, ObjectMapper objectMapper) {
    this.name = account.getName();
    this.okHttpClient = okHttpClient;
    this.objectMapper = objectMapper;
    Builder builder = new Request.Builder();
    boolean useLogin = !StringUtils.isEmpty(account.getUsername()) && !StringUtils.isEmpty(account.getPassword());
    boolean useUsernamePasswordFile = !StringUtils.isEmpty(account.getUsernamePasswordFile());
    boolean useToken = !StringUtils.isEmpty(account.getToken());
    boolean useTokenFile = !StringUtils.isEmpty(account.getTokenFile());
    boolean useAuth = useLogin || useToken || useUsernamePasswordFile || useTokenFile;
    if (useAuth) {
      String authHeader = "";
      if (useTokenFile) {
        authHeader = "token " + credentialsFromFile(account.getTokenFile());
      } else if (useUsernamePasswordFile) {
        authHeader = "Basic " + Base64.encodeBase64String((credentialsFromFile(account.getUsernamePasswordFile())).getBytes());
      }  else if (useToken) {
        authHeader = "token " + account.getToken();
      } else if (useLogin) {
        authHeader = "Basic " + Base64.encodeBase64String((account.getUsername() + ":" + account.getPassword()).getBytes());
      }
      builder.header("Authorization", authHeader);
      log.info("Loaded credentials for GitHub Artifact Account {}", account.getName());
    } else {
      log.info("No credentials included with GitHub Artifact Account {}", account.getName());
    }
    requestBuilder = builder;
  }

  private String credentialsFromFile(String filename) {
    try {
      String credentials = FileUtils.readFileToString(new File(filename));
      return credentials.replace("\n", "");
    } catch (IOException e) {
      log.error("Could not read GitHub credentials file {}", filename);
      return null;
    }
  }

  public InputStream download(Artifact artifact) throws IOException {
    HttpUrl.Builder metadataUrlBuilder = HttpUrl.parse(artifact.getReference()).newBuilder();
    metadataUrlBuilder.addQueryParameter("ref", artifact.getVersion());
    Request metadataRequest = requestBuilder
      .url(metadataUrlBuilder.build().toString())
      .build();
    Response metadataResponse = okHttpClient.newCall(metadataRequest).execute();
    String body = metadataResponse.body().string();
    ContentMetadata metadata = objectMapper.readValue(body, ContentMetadata.class);
    Request downloadRequest = requestBuilder
      .url(metadata.getDownloadUrl())
      .build();
    Response downloadResponse = okHttpClient.newCall(downloadRequest).execute();
    return downloadResponse.body().byteStream();
  }

  @Override
  public boolean handlesType(String type) {
    return type.equals("github/file");
  }

  @Data
  public static class ContentMetadata {
    @JsonProperty("download_url")
    private String downloadUrl;
  }
}
