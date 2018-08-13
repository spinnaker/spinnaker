/*
 * Copyright 2018 Joel Wilsson
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

package com.netflix.spinnaker.clouddriver.artifacts.http;

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
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Data
public class HttpArtifactCredentials implements ArtifactCredentials {
  private final String name;
  private final List<String> types = Arrays.asList("http/file");

  @JsonIgnore
  private final Builder requestBuilder;

  @JsonIgnore
  OkHttpClient okHttpClient;

  public HttpArtifactCredentials(HttpArtifactAccount account, OkHttpClient okHttpClient) {
    this.name = account.getName();
    this.okHttpClient = okHttpClient;
    Builder builder = new Request.Builder();
    boolean useLogin = !StringUtils.isEmpty(account.getUsername()) && !StringUtils.isEmpty(account.getPassword());
    boolean useUsernamePasswordFile = !StringUtils.isEmpty(account.getUsernamePasswordFile());
    boolean useAuth = useLogin || useUsernamePasswordFile;
    if (useAuth) {
      String authHeader = "";
      if (useUsernamePasswordFile) {
        authHeader = "Basic " + Base64.encodeBase64String((credentialsFromFile(account.getUsernamePasswordFile())).getBytes());
      } else if (useLogin) {
        authHeader = "Basic " + Base64.encodeBase64String((account.getUsername() + ":" + account.getPassword()).getBytes());
      }
      builder.header("Authorization", authHeader);
      log.info("Loaded credentials for http artifact account {}", account.getName());
    } else {
      log.info("No credentials included with http artifact account {}", account.getName());
    }
    requestBuilder = builder;
  }

  private String credentialsFromFile(String filename) {
    try {
      String credentials = FileUtils.readFileToString(new File(filename));
      return credentials.replace("\n", "");
    } catch (IOException e) {
      log.error("Could not read http credentials file {}", filename, e);
      return null;
    }
  }

  public InputStream download(Artifact artifact) throws IOException {
    Request downloadRequest = requestBuilder
      .url(artifact.getReference())
      .build();

    Response downloadResponse = okHttpClient.newCall(downloadRequest).execute();
    return downloadResponse.body().byteStream();
  }

  @Override
  public boolean handlesType(String type) {
    return type.equals("http/file");
  }
}
