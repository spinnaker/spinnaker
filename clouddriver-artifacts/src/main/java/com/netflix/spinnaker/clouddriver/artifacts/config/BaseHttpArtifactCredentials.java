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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.squareup.okhttp.*;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseHttpArtifactCredentials<T extends ArtifactAccount> {
  @JsonIgnore private final Headers headers;

  @JsonIgnore private final OkHttpClient okHttpClient;

  protected BaseHttpArtifactCredentials(OkHttpClient okHttpClient, T account) {
    this.okHttpClient = okHttpClient;
    this.headers = getHeaders(account);
  }

  private Optional<String> getAuthHeader(ArtifactAccount account) {
    Optional<String> authHeader = Optional.empty();

    if (account instanceof TokenAuth) {
      TokenAuth tokenAuth = (TokenAuth) account;
      authHeader = tokenAuth.getTokenAuthHeader();
    }

    if (!authHeader.isPresent() && account instanceof BasicAuth) {
      BasicAuth basicAuth = (BasicAuth) account;
      authHeader = basicAuth.getBasicAuthHeader();
    }
    return authHeader;
  }

  protected Headers getHeaders(T account) {
    Headers.Builder headers = new Headers.Builder();
    Optional<String> authHeader = getAuthHeader(account);
    if (authHeader.isPresent()) {
      headers.set("Authorization", authHeader.get());
      log.info("Loaded credentials for artifact account {}", account.getName());
    } else {
      log.info("No credentials included for artifact account {}", account.getName());
    }
    return headers.build();
  }

  protected HttpUrl parseUrl(String stringUrl) {
    HttpUrl httpUrl = HttpUrl.parse(stringUrl);
    if (httpUrl == null) {
      throw new IllegalArgumentException("Malformed URL: " + stringUrl);
    }
    return httpUrl;
  }

  protected ResponseBody fetchUrl(String url) throws IOException {
    return fetchUrl(parseUrl(url));
  }

  protected ResponseBody fetchUrl(HttpUrl url) throws IOException {
    Request request = new Request.Builder().headers(headers).url(url).build();

    Response downloadResponse = okHttpClient.newCall(request).execute();
    return downloadResponse.body();
  }
}
