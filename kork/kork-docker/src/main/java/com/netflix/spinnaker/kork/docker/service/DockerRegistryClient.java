/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.kork.docker.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.docker.exceptions.DockerRegistryAuthenticationException;
import com.netflix.spinnaker.kork.docker.exceptions.DockerRegistryOperationException;
import com.netflix.spinnaker.kork.docker.model.DockerBearerToken;
import com.netflix.spinnaker.kork.docker.model.DockerManifest;
import com.netflix.spinnaker.kork.docker.model.DockerRegistryCatalog;
import com.netflix.spinnaker.kork.docker.model.DockerRegistryTags;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * TODO: Properties in this class are duplicated in HelmOciDockerArtifactAccount and
 * DockerRegistryNamedAccountCredentials. Future refactoring needed to reduce duplication.
 */
@Slf4j
@Getter
public class DockerRegistryClient {

  private final String address;
  private String email;
  private String username;
  private String password;
  private String passwordCommand;
  private File passwordFile;
  private final File dockerconfigFile;
  private long clientTimeoutMillis;
  private final int paginateSize;
  private final String catalogFile;
  private final String repositoriesRegex;
  private final boolean insecureRegistry;
  private final DockerOkClientProvider okClientProvider;
  private final ServiceClientProvider serviceClientProvider;

  private DockerBearerTokenService tokenService;
  private final RegistryService registryService;
  private static ObjectMapper objectMapper;

  static final String USER_AGENT = DockerUserAgent.getUserAgent();

  public static class Builder {

    private String address;
    private String email;
    private String username;
    private String password;
    private String passwordCommand;
    private java.io.File passwordFile;
    private java.io.File dockerconfigFile;
    private long clientTimeoutMillis;
    private int paginateSize;
    private String catalogFile;
    private String repositoriesRegex;
    private boolean insecureRegistry;
    private DockerOkClientProvider okClientProvider;
    private ServiceClientProvider serviceClientProvider;

    public Builder address(String address) {
      this.address = address;
      return this;
    }

    public Builder email(String email) {
      this.email = email;
      return this;
    }

    public Builder username(String username) {
      this.username = username;
      return this;
    }

    public Builder password(String password) {
      this.password = password;
      return this;
    }

    public Builder passwordCommand(String passwordCommand) {
      this.passwordCommand = passwordCommand;
      return this;
    }

    public Builder passwordFile(java.io.File passwordFile) {
      this.passwordFile = passwordFile;
      return this;
    }

    public Builder dockerconfigFile(java.io.File dockerconfigFile) {
      this.dockerconfigFile = dockerconfigFile;
      return this;
    }

    public Builder clientTimeoutMillis(long clientTimeoutMillis) {
      this.clientTimeoutMillis = clientTimeoutMillis;
      return this;
    }

    public Builder paginateSize(int paginateSize) {
      this.paginateSize = paginateSize;
      return this;
    }

    public Builder catalogFile(String catalogFile) {
      this.catalogFile = catalogFile;
      return this;
    }

    public Builder repositoriesRegex(String repositoriesRegex) {
      this.repositoriesRegex = repositoriesRegex;
      return this;
    }

    public Builder insecureRegistry(boolean insecureRegistry) {
      this.insecureRegistry = insecureRegistry;
      return this;
    }

    public Builder okClientProvider(DockerOkClientProvider okClientProvider) {
      this.okClientProvider = okClientProvider;
      return this;
    }

    public Builder serviceClientProvider(ServiceClientProvider serviceClientProvider) {
      this.serviceClientProvider = serviceClientProvider;
      return this;
    }

    public DockerRegistryClient build() {
      // Enforce exclusivity
      int count = 0;
      if (password != null) count++;
      if (passwordFile != null) count++;
      if (passwordCommand != null) count++;
      if (dockerconfigFile != null) count++;
      if (count > 1) {
        throw new IllegalArgumentException(
            "Error, at most one of \"password\", \"passwordFile\", \"passwordCommand\" or \"dockerconfigFile\" can be specified");
      }

      // Call the appropriate constructor as in Groovy
      if (password != null || passwordCommand != null) {
        return new DockerRegistryClient(
            address,
            email,
            username,
            password,
            passwordCommand,
            clientTimeoutMillis,
            paginateSize,
            catalogFile,
            repositoriesRegex,
            insecureRegistry,
            okClientProvider,
            serviceClientProvider);
      } else if (passwordFile != null) {
        return new DockerRegistryClient(
            address,
            email,
            username,
            passwordFile,
            clientTimeoutMillis,
            paginateSize,
            catalogFile,
            repositoriesRegex,
            insecureRegistry,
            okClientProvider,
            serviceClientProvider);
      } else {
        return new DockerRegistryClient(
            address,
            clientTimeoutMillis,
            paginateSize,
            catalogFile,
            repositoriesRegex,
            insecureRegistry,
            okClientProvider,
            serviceClientProvider);
      }
    }
  }

  private static ObjectMapper getObjectMapper() {
    if (objectMapper == null) {
      objectMapper = new ObjectMapper();
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    return objectMapper;
  }

  // Main constructor: minimal required fields
  public DockerRegistryClient(
      String address,
      long clientTimeoutMillis,
      int paginateSize,
      String catalogFile,
      String repositoriesRegex,
      boolean insecureRegistry,
      DockerOkClientProvider okClientProvider,
      ServiceClientProvider serviceClientProvider) {
    this.paginateSize = paginateSize;
    this.tokenService = new DockerBearerTokenService(serviceClientProvider);
    this.registryService =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(address))
            .client(okClientProvider.provide(address, clientTimeoutMillis, insecureRegistry))
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(RegistryService.class);
    this.address = address;
    this.catalogFile = catalogFile;
    this.repositoriesRegex = repositoriesRegex;
    // Initialize other fields to null or sensible defaults if needed
    this.email = null;
    this.username = null;
    this.password = null;
    this.passwordCommand = null;
    this.passwordFile = null;
    this.dockerconfigFile = null;
    this.insecureRegistry = insecureRegistry;
    this.okClientProvider = okClientProvider;
    this.serviceClientProvider = serviceClientProvider;
  }

  // Constructor for password or passwordCommand
  public DockerRegistryClient(
      String address,
      String email,
      String username,
      String password,
      String passwordCommand,
      long clientTimeoutMillis,
      int paginateSize,
      String catalogFile,
      String repositoriesRegex,
      boolean insecureRegistry,
      DockerOkClientProvider okClientProvider,
      ServiceClientProvider serviceClientProvider) {
    this(
        address,
        clientTimeoutMillis,
        paginateSize,
        catalogFile,
        repositoriesRegex,
        insecureRegistry,
        okClientProvider,
        serviceClientProvider);
    this.tokenService =
        new DockerBearerTokenService(username, password, passwordCommand, serviceClientProvider);
    this.email = email;
    this.username = username;
    this.password = password;
    this.passwordCommand = passwordCommand;
  }

  // Constructor for passwordFile
  public DockerRegistryClient(
      String address,
      String email,
      String username,
      java.io.File passwordFile,
      long clientTimeoutMillis,
      int paginateSize,
      String catalogFile,
      String repositoriesRegex,
      boolean insecureRegistry,
      DockerOkClientProvider okClientProvider,
      ServiceClientProvider serviceClientProvider) {
    this(
        address,
        clientTimeoutMillis,
        paginateSize,
        catalogFile,
        repositoriesRegex,
        insecureRegistry,
        okClientProvider,
        serviceClientProvider);
    this.tokenService = new DockerBearerTokenService(username, passwordFile, serviceClientProvider);
    this.email = email;
    this.username = username;
    this.passwordFile = passwordFile;
  }

  // Constructor for direct injection of services (for testing or advanced usage)
  public DockerRegistryClient(
      String address,
      int paginateSize,
      String catalogFile,
      String repositoriesRegex,
      RegistryService dockerRegistryService,
      DockerBearerTokenService dockerBearerTokenService) {
    this.paginateSize = paginateSize;
    this.address = address;
    this.catalogFile = catalogFile;
    this.repositoriesRegex = repositoriesRegex;
    this.tokenService = dockerBearerTokenService;
    this.registryService = dockerRegistryService;
    // Set other fields to null or sensible defaults
    this.email = null;
    this.username = null;
    this.password = null;
    this.passwordCommand = null;
    this.passwordFile = null;
    this.dockerconfigFile = null;
    this.insecureRegistry = false;
    this.okClientProvider = null;
    this.serviceClientProvider = null;
  }

  public String getBasicAuth() {
    return tokenService.getBasicAuth();
  }

  public String getDigest(String name, String tag) {
    Response<ResponseBody> response = getManifest(name, tag);
    if (!response.isSuccessful()) {
      throw new DockerRegistryOperationException("Failed to fetch manifest: " + response.message());
    }
    return response.headers().get("Docker-Content-Digest");
  }

  public String getConfigDigest(String name, String tag) {
    Response<ResponseBody> response = getSchemaV2Manifest(name, tag);
    DockerManifest dockerManifest =
        (DockerManifest) convertResponseBody(response.body(), DockerManifest.class);
    return dockerManifest.getConfig().getDigest();
  }

  public Map getDigestContent(String name, String digest) {
    Response<ResponseBody> response =
        request(
            // withoutToken: unauthenticated call
            () ->
                Retrofit2SyncCall.executeCall(
                    registryService.getDigestContent(
                        name, digest, tokenService.getBasicAuthHeader(), USER_AGENT)),
            // withToken: authenticated call with token
            (token) ->
                Retrofit2SyncCall.executeCall(
                    registryService.getDigestContent(name, digest, token, USER_AGENT)),
            name);

    return (Map) convertResponseBody(response.body(), Map.class);
  }

  private Response<ResponseBody> getManifest(String name, String tag) {
    return request(
        // withoutToken: unauthenticated call
        () ->
            Retrofit2SyncCall.executeCall(
                registryService.getManifest(
                    name, tag, tokenService.getBasicAuthHeader(), USER_AGENT)),
        // withToken: authenticated call with token
        (token) ->
            Retrofit2SyncCall.executeCall(
                registryService.getManifest(name, tag, token, USER_AGENT)),
        name);
  }

  private Response<ResponseBody> downloadLayer(String repository, String digest) {
    String path = "v2/" + repository + "/blobs/" + digest;
    return request(
        // withoutToken: unauthenticated call
        () ->
            Retrofit2SyncCall.executeCall(
                registryService.downloadBlob(path, tokenService.getBasicAuthHeader(), USER_AGENT)),
        // withToken: authenticated call with token
        (token) ->
            Retrofit2SyncCall.executeCall(registryService.downloadBlob(path, token, USER_AGENT)),
        repository);
  }

  private Response<ResponseBody> getSchemaV2Manifest(String name, String tag) {
    return request(
        // withoutToken: unauthenticated call
        () ->
            Retrofit2SyncCall.executeCall(
                registryService.getSchemaV2Manifest(
                    name, tag, tokenService.getBasicAuthHeader(), USER_AGENT)),
        // withToken: authenticated call with token
        (token) ->
            Retrofit2SyncCall.executeCall(
                registryService.getSchemaV2Manifest(name, tag, token, USER_AGENT)),
        name);
  }

  public Response<ResponseBody> request(
      Supplier<Response<ResponseBody>> withoutToken,
      Function<String, Response<ResponseBody>> withToken,
      String target) {
    try {
      DockerBearerToken dockerToken = tokenService.getToken(target);
      String token = null;
      if (dockerToken != null) {
        token =
            "Bearer "
                + (dockerToken.getBearerToken() != null
                    ? dockerToken.getBearerToken()
                    : dockerToken.getToken() != null
                        ? dockerToken.getToken()
                        : dockerToken.getAccessToken());
      }

      Response<ResponseBody> response;
      try {
        if (token != null) {
          response = withToken.apply(token);
        } else {
          response = withoutToken.get();
        }
      } catch (SpinnakerHttpException error) {
        int status = error.getResponseCode();
        if (status == 400 || status == 401) {
          List<String> authenticateHeader = null;
          for (Map.Entry<String, List<String>> header : error.getHeaders().entrySet()) {
            if ("www-authenticate".equalsIgnoreCase(header.getKey())) {
              authenticateHeader = header.getValue();
            }
          }
          if (authenticateHeader == null || authenticateHeader.isEmpty()) {
            log.warn(
                "Registry {} returned status {} for request '{}' without a WWW-Authenticate header",
                address,
                status,
                target);
            tokenService.clearToken(target);
            throw error;
          }

          String bearerPrefix = "bearer ";
          String basicPrefix = "basic ";
          for (String headerValue : authenticateHeader) {
            if (headerValue.length() >= bearerPrefix.length()
                && bearerPrefix.equalsIgnoreCase(headerValue.substring(0, bearerPrefix.length()))) {
              // Bearer auth: get new token and retry
              dockerToken =
                  tokenService.getToken(target, headerValue.substring(bearerPrefix.length()));
              token =
                  "Bearer "
                      + (dockerToken.getBearerToken() != null
                          ? dockerToken.getBearerToken()
                          : dockerToken.getToken() != null
                              ? dockerToken.getToken()
                              : dockerToken.getAccessToken());
              return withToken.apply(token);
            } else if (headerValue.length() >= basicPrefix.length()
                && basicPrefix.equalsIgnoreCase(headerValue.substring(0, basicPrefix.length()))) {
              // Basic auth: no retry
              tokenService.clearToken(target);
              throw error;
            }
          }
          tokenService.clearToken(target);
          throw new DockerRegistryAuthenticationException(
              "Docker registry must support 'Bearer' or 'Basic' authentication.");
        } else {
          throw error;
        }
      }

      return response;
    } catch (DockerRegistryAuthenticationException e) {
      log.error(
          "Error authenticating with registry {}, for request '{}': {}",
          address,
          target,
          e.getMessage());
      throw e;
    }
  }

  private static String parseLink(String headerValue) {
    String[] links = headerValue.split(";");
    boolean hasNext = false;
    String path = null;
    for (String tok : links) {
      String trimmed = tok.trim();
      if (trimmed.replace(" ", "").equalsIgnoreCase("rel=\"next\"")) {
        hasNext = true;
      }
      if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
        path = trimmed;
      }
    }
    if (!hasNext) return null;
    String link = (path != null) ? path.substring(1, path.length() - 1) : null;
    if (link == null) return null;
    try {
      java.net.URL url = new java.net.URL(link);
      link = url.getFile().substring(1);
    } catch (Exception ignored) {
      // Relative path, do nothing
    }
    return link.startsWith("/") ? link.replaceFirst("/", "") : link;
  }

  private static String findNextLink(okhttp3.Headers headers) {
    if (headers == null) return null;
    Map<String, List<String>> caseInsensitiveHeaders = new HashMap<>();
    for (String name : headers.names()) {
      caseInsensitiveHeaders
          .computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>())
          .addAll(headers.values(name));
    }
    List<String> headerValues = caseInsensitiveHeaders.get("link");
    if (headerValues == null || headerValues.isEmpty()) {
      return null;
    } else if (headerValues.size() > 1) {
      throw new DockerRegistryOperationException(
          "Ambiguous number of Link headers provided, the following paths were identified: "
              + headerValues);
    }
    return parseLink(headerValues.get(0));
  }

  private static Pair<String, Map<String, String>> parseForQueryParams(String nextPath) {
    String nextPathNew = nextPath;
    String queryParamsString = null;
    Map<String, String> queryParams = new HashMap<>();
    int idx = nextPath.indexOf('?');
    if (idx != -1) {
      nextPathNew = nextPath.substring(0, idx);
      queryParamsString = nextPath.substring(idx + 1);
    }
    if (queryParamsString != null) {
      String[] params = queryParamsString.split("&");
      for (String param : params) {
        String[] kv = param.split("=", 2);
        if (kv.length == 2) {
          queryParams.put(kv[0], kv[1]);
        } else if (kv.length == 1) {
          queryParams.put(kv[0], null);
        }
      }
    }
    return new Pair<>(nextPathNew, queryParams);
  }

  // Helper Pair class
  public static class Pair<K, V> {
    public final K first;
    public final V second;

    public Pair(K first, V second) {
      this.first = first;
      this.second = second;
    }
  }

  public DockerRegistryCatalog getCatalog() {
    return getCatalog(null, null);
  }

  public DockerRegistryCatalog getCatalog(String path, Map<String, String> queryParams) {
    if (catalogFile != null && !catalogFile.isEmpty()) {
      log.info("Using catalog list at {}", catalogFile);
      try {
        String userDefinedCatalog = new String(Files.readAllBytes(new File(catalogFile).toPath()));
        return new Gson().fromJson(userDefinedCatalog, DockerRegistryCatalog.class);
      } catch (Exception e) {
        throw new DockerRegistryOperationException(
            "Unable to read catalog file " + catalogFile + ": " + e.getMessage(), e);
      }
    }
    if (queryParams == null) queryParams = new HashMap<>();
    queryParams.putIfAbsent("n", Integer.toString(paginateSize));
    Response<ResponseBody> response;
    try {
      Map<String, String> finalQueryParams = queryParams;
      response =
          request(
              () ->
                  path != null
                      ? Retrofit2SyncCall.executeCall(
                          registryService.get(
                              path,
                              tokenService.getBasicAuthHeader(),
                              USER_AGENT,
                              finalQueryParams))
                      : Retrofit2SyncCall.executeCall(
                          registryService.getCatalog(
                              tokenService.getBasicAuthHeader(), USER_AGENT, finalQueryParams)),
              token ->
                  path != null
                      ? Retrofit2SyncCall.executeCall(
                          registryService.get(path, token, USER_AGENT, finalQueryParams))
                      : Retrofit2SyncCall.executeCall(
                          registryService.getCatalog(token, USER_AGENT, finalQueryParams)),
              "_catalog");
    } catch (Exception e) {
      log.warn("Error encountered during catalog of {}", path, e);
      return new DockerRegistryCatalog();
    }
    String nextPath = findNextLink(response != null ? response.headers() : null);
    DockerRegistryCatalog catalog =
        (DockerRegistryCatalog) convertResponseBody(response.body(), DockerRegistryCatalog.class);
    if (repositoriesRegex != null && !repositoriesRegex.isEmpty()) {
      catalog.getRepositories().removeIf(repo -> !repo.matches(repositoriesRegex));
    }
    if (nextPath != null) {
      Pair<String, Map<String, String>> next = parseForQueryParams(nextPath);
      DockerRegistryCatalog nextCatalog = getCatalog(next.first, next.second);
      catalog.getRepositories().addAll(nextCatalog.getRepositories());
    }
    return catalog;
  }

  public DockerRegistryTags getTags(String repository) {
    return getTags(repository, null, new HashMap<>());
  }

  public DockerRegistryTags getTags(
      String repository, String path, Map<String, String> queryParams) {
    if (queryParams == null) queryParams = new HashMap<>();
    queryParams.putIfAbsent("n", Integer.toString(paginateSize));
    Map<String, String> finalQueryParams = queryParams;
    Response<ResponseBody> response =
        request(
            () ->
                path != null
                    ? Retrofit2SyncCall.executeCall(
                        registryService.get(
                            path, tokenService.getBasicAuthHeader(), USER_AGENT, finalQueryParams))
                    : Retrofit2SyncCall.executeCall(
                        registryService.getTags(
                            repository,
                            tokenService.getBasicAuthHeader(),
                            USER_AGENT,
                            finalQueryParams)),
            token ->
                path != null
                    ? Retrofit2SyncCall.executeCall(
                        registryService.get(path, token, USER_AGENT, finalQueryParams))
                    : Retrofit2SyncCall.executeCall(
                        registryService.getTags(repository, token, USER_AGENT, finalQueryParams)),
            repository);
    String nextPath = findNextLink(response != null ? response.headers() : null);
    DockerRegistryTags tags =
        (DockerRegistryTags) convertResponseBody(response.body(), DockerRegistryTags.class);
    if (nextPath != null) {
      Pair<String, Map<String, String>> next = parseForQueryParams(nextPath);
      DockerRegistryTags nextTags = getTags(repository, next.first, next.second);
      tags.getTags().addAll(nextTags.getTags());
    }
    return tags;
  }

  public void checkV2Availability() {
    try {
      doCheckV2Availability(null);
    } catch (SpinnakerServerException error) {
      if (tokenService.getBasicAuthHeader() == null
          && error instanceof SpinnakerHttpException
          && ((SpinnakerHttpException) error).getResponseCode() == 401) {
        return;
      }
      Response<ResponseBody> response = doCheckV2Availability(tokenService.getBasicAuthHeader());
      if (response.body() == null) {
        log.error("checkV2Availability", error);
        throw error;
      }
    }
  }

  private Map tagDateCache = new HashMap<>();

  public Instant getCreationDate(String name, String tag) {
    String key = name + ":" + tag;
    // Only cache non-latest tags
    if (tagDateCache.containsKey(key) && !"latest".equals(tag)) {
      return (Instant) tagDateCache.get(key);
    }
    try {
      Response<ResponseBody> manifestResponse = getManifest(name, tag);
      Map<?, ?> manifest = (Map<?, ?>) convertResponseBody(manifestResponse.body(), Map.class);

      // Extract the history array and v1Compatibility JSON
      Object historyObj = manifest.get("history");
      if (!(historyObj instanceof java.util.List) || ((java.util.List<?>) historyObj).isEmpty()) {
        throw new DockerRegistryOperationException(
            "Manifest history is missing or empty for " + key);
      }
      Object v1CompatibilityObj = ((java.util.List<?>) historyObj).get(0);
      if (!(v1CompatibilityObj instanceof Map)) {
        v1CompatibilityObj = new Gson().fromJson(v1CompatibilityObj.toString(), Map.class);
      }
      String v1CompatibilityJson =
          ((Map<?, ?>) v1CompatibilityObj).get("v1Compatibility").toString();

      // Parse v1Compatibility JSON for 'created' field
      Map<?, ?> v1CompatibilityMap = new Gson().fromJson(v1CompatibilityJson, Map.class);
      String created = (String) v1CompatibilityMap.get("created");
      Instant dateCreated = Instant.parse(created);

      // Cache non-latest tags
      if (!"latest".equals(tag)) {
        tagDateCache.put(key, dateCreated);
      }
      return dateCreated;
    } catch (Exception e) {
      throw new DockerRegistryOperationException("Failed to fetch creation date for " + key, e);
    }
  }

  private Response<ResponseBody> doCheckV2Availability(String basicAuthHeader) {
    return request(
        () ->
            Retrofit2SyncCall.executeCall(
                registryService.checkVersion(basicAuthHeader, USER_AGENT)),
        token -> Retrofit2SyncCall.executeCall(registryService.checkVersion(token, USER_AGENT)),
        "v2 version check");
  }

  private Object convertResponseBody(ResponseBody body, Class<?> expectedType) {
    if (body == null) {
      throw new DockerRegistryOperationException("Response body is null");
    }
    try {
      return getObjectMapper().readValue(body.string(), expectedType);
    } catch (Exception e) {
      throw new DockerRegistryOperationException("Error parsing response body", e);
    }
  }

  public ResponseBody downloadBlob(String repository, String version) {
    Response<ResponseBody> manifestResponse = getManifest(repository, version);

    DockerManifest manifest =
        (DockerManifest) convertResponseBody(manifestResponse.body(), DockerManifest.class);

    // Ensure there is at least one layer
    if (manifest.getLayers() == null || manifest.getLayers().isEmpty()) {
      throw new RuntimeException("No layers found in manifest");
    }
    // Use the digest of the first layer (usually the artifact, e.g., Helm chart)
    String digest = manifest.getLayers().get(0).getDigest();

    return downloadLayer(repository, digest).body();
  }
}
