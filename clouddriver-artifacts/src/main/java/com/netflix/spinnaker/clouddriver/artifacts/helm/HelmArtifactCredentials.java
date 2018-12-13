/*
 * Copyright 2018 Mirantis, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.helm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Request.Builder;
import com.squareup.okhttp.Response;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Data
public class HelmArtifactCredentials implements ArtifactCredentials {
  private final String name;
  private final List<String> types = Arrays.asList("helm/chart");

  @JsonIgnore
  private final IndexParser indexParser;

  @JsonIgnore
  OkHttpClient okHttpClient;

  @JsonIgnore
  private final Builder requestBuilder;

  @JsonIgnore
  ObjectMapper objectMapper;

  @Override
  public boolean handlesType(String type) {
    return type.equals("helm/chart");
  }

  public HelmArtifactCredentials(HelmArtifactAccount account, OkHttpClient okHttpClient) {
    this.name = account.getName();
    this.okHttpClient = okHttpClient;
    this.indexParser = new IndexParser(account.getRepository());
    Builder builder = new Builder();
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
      log.info("Loaded credentials for helm artifact account {}", account.getName());
    } else {
      log.info("No credentials included with helm artifact account {}", account.getName());
    }
    requestBuilder = builder;
  }

  private String credentialsFromFile(String filename) {
    try {
      String credentials = FileUtils.readFileToString(new File(filename));
      return credentials.trim();
    } catch (IOException e) {
      log.error("Could not read helm credentials file {}", filename, e);
      return null;
    }
  }

  public InputStream download(Artifact artifact) throws IOException {
    InputStream index = downloadIndex();

    List<String> urls = indexParser.findUrls(index, artifact.getName(), artifact.getVersion());
    Response downloadResponse;
    for (String url : urls) {
      try {
        Request downloadRequest = requestBuilder
          .url(url)
          .build();
        downloadResponse = okHttpClient.newCall(downloadRequest).execute();
        return downloadResponse.body().byteStream();
      } catch (IllegalArgumentException e) {
        log.warn("Invalid url: ", url);
      }
    }
    throw new FailedDownloadException("Unable to download the contents of artifact");
  }

  public List<String> getArtifactNames() {
    InputStream index;
    List<String> names;
    try {
      index = downloadIndex();
      names = indexParser.findNames(index);
    } catch (IOException e) {
      throw new NotFoundException("Failed to download chart names for '" + name + "' account");
    }
    return names;
  }

  public List<String> getArtifactVersions(String artifactName) {
    InputStream index;
    List<String> versions;
    try {
      index = downloadIndex();
      versions = indexParser.findVersions(index, artifactName);
    } catch (IOException e) {
      throw new NotFoundException("Failed to download chart versions for '" + name + "' account");
    }
    return versions;
  }

  private InputStream downloadIndex() throws IOException {
    Request indexDownloadRequest = requestBuilder
      .url(indexParser.indexPath())
      .build();
    Response indexDownloadResponse;
    try {
      indexDownloadResponse = okHttpClient.newCall(indexDownloadRequest).execute();
    } catch (IOException e) {
      throw new FailedDownloadException("Failed to download index.yaml file in '" + indexParser.getRepository() + "' repository");
    }
    return indexDownloadResponse.body().byteStream();
  }

  public class FailedDownloadException extends IOException {
    public FailedDownloadException(String message) {
      super(message);
    }
  }
}
