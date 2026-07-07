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
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
public abstract class BaseHttpArtifactCredentials<T extends UserInputValidatedArtifactAccount> {
  @JsonIgnore private final OkHttpClient okHttpClient;
  @Getter @VisibleForTesting @JsonIgnore private final T account;

  protected BaseHttpArtifactCredentials(OkHttpClient okHttpClient, T account) {
    this.account = account;
    // Disable automatic redirects to prevent SSRF via unvalidated redirect chains.
    // We manually follow redirects, validating each Location header when restrictions are set.
    this.okHttpClient =
        okHttpClient.newBuilder().followRedirects(false).followSslRedirects(false).build();
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
      throw new IllegalArgumentException(
          "Malformed URL (check artifact references): "
              + stringUrl
              + ". Read more here https://www.spinnaker.io/reference/artifacts/types/");
    }
    if (account.getUrlRestrictions() != null) {
      account.getUrlRestrictions().validateURI(httpUrl);
    }
    return httpUrl;
  }

  protected ResponseBody fetchUrl(String url) throws IOException {
    return fetchUrl(parseUrl(url));
  }

  protected ResponseBody fetchUrl(HttpUrl url) throws IOException {
    HttpUrl currentUrl = url;
    int redirectCount = 0;
    int maxRedirects = 10; // Match OkHttp's default redirect limit
    HttpUrl originalHost = url;

    while (redirectCount < maxRedirects) {
      // Only send auth headers to the original host; strip them on cross-host redirects
      // to prevent credential leakage to attacker-controlled redirect targets
      Headers headers =
          currentUrl.host().equals(originalHost.host())
              ? getHeaders(account)
              : new Headers.Builder().build();
      Request request = new Request.Builder().headers(headers).url(currentUrl).build();
      Response response = okHttpClient.newCall(request).execute();

      // Handle redirects manually with validation at each hop
      if (response.isRedirect()) {
        String location = response.header("Location");
        response.body().close();

        if (location == null || location.trim().isEmpty()) {
          throw new IOException(
              String.format(
                  "Received redirect (%d) from %s with no Location header",
                  response.code(), currentUrl));
        }

        // Resolve relative redirects against the current URL
        HttpUrl redirectUrl = currentUrl.resolve(location);
        if (redirectUrl == null) {
          throw new IOException(
              String.format("Invalid redirect Location header: %s from %s", location, currentUrl));
        }

        // Re-validate the redirect target against URL restrictions when configured.
        // This prevents SSRF attacks where an attacker controls an allowed external host
        // that redirects to internal endpoints (e.g., cloud metadata servers).
        if (account.getUrlRestrictions() != null) {
          account.getUrlRestrictions().validateURI(redirectUrl);
        }

        currentUrl = redirectUrl;
        redirectCount++;
        continue;
      }

      // Non-redirect response
      if (!response.isSuccessful()) {
        response.body().close();
        throw new IOException(
            String.format("Received %d status code from %s", response.code(), currentUrl.host()));
      }

      return response.body();
    }

    throw new IOException(
        String.format("Too many redirects (>%d) following %s", maxRedirects, url));
  }
}
