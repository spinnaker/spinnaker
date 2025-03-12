/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.DockerUserAgent
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerToken
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerTokenService
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.exception.DockerRegistryAuthenticationException
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.exception.DockerRegistryOperationException
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import groovy.util.logging.Slf4j
import okhttp3.ResponseBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.Response
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

import java.time.Instant

@Slf4j
class DockerRegistryClient {

  static class Builder {
    String address
    String email
    String username
    String password
    String passwordCommand
    File passwordFile
    File dockerconfigFile
    long clientTimeoutMillis
    int paginateSize
    String catalogFile
    String repositoriesRegex
    boolean insecureRegistry
    DockerOkClientProvider okClientProvider
    ServiceClientProvider serviceClientProvider

    Builder address(String address) {
      this.address = address
      return this
    }

    Builder email(String email) {
      this.email = email
      return this
    }

    Builder username(String username) {
      this.username = username
      return this
    }

    Builder password(String password) {
      this.password = password
      return this
    }

    Builder passwordCommand(String passwordCommand) {
      this.passwordCommand = passwordCommand
      return this
    }

    Builder passwordFile(File passwordFile) {
      this.passwordFile = passwordFile
      return this
    }

    Builder dockerconfigFile(File dockerconfigFile) {
      this.dockerconfigFile = dockerconfigFile
      return this
    }

    Builder clientTimeoutMillis(long clientTimeoutMillis) {
      this.clientTimeoutMillis = clientTimeoutMillis
      return this
    }

    Builder paginateSize(int paginateSize) {
      this.paginateSize = paginateSize
      return this
    }

    Builder catalogFile(String catalogFile) {
      this.catalogFile = catalogFile
      return this
    }

    Builder repositoriesRegex(String regex) {
      this.repositoriesRegex = regex
      return this
    }


    Builder insecureRegistry(boolean insecureRegistry) {
      this.insecureRegistry = insecureRegistry
      return this
    }

    Builder okClientProvider(DockerOkClientProvider okClientProvider) {
      this.okClientProvider = okClientProvider
      return this
    }

    Builder serviceClientProvider(ServiceClientProvider serviceClientProvider) {
      this.serviceClientProvider = serviceClientProvider
      return this
    }

    DockerRegistryClient build() {

      if (password && passwordFile || password && passwordCommand || passwordFile && passwordCommand) {
        throw new IllegalArgumentException('Error, at most one of "password", "passwordFile", "passwordCommand" or "dockerconfigFile" can be specified')
      }
      if (password || passwordCommand) {
        return new DockerRegistryClient(address, email, username, password, passwordCommand, clientTimeoutMillis, paginateSize, catalogFile, repositoriesRegex, insecureRegistry, okClientProvider, serviceClientProvider)
      } else if (passwordFile) {
        return new DockerRegistryClient(address, email, username, passwordFile, clientTimeoutMillis, paginateSize, catalogFile, repositoriesRegex,  insecureRegistry, okClientProvider, serviceClientProvider)
      } else {
        return new DockerRegistryClient(address, clientTimeoutMillis, paginateSize, catalogFile, repositoriesRegex, insecureRegistry, okClientProvider, serviceClientProvider)
      }
    }

  }

  private static final Logger LOG = LoggerFactory.getLogger(DockerRegistryClient)

  DockerBearerTokenService tokenService

  String address
  String email
  DockerRegistryService registryService
  String catalogFile
  String repositoriesRegex

  final static String userAgent = DockerUserAgent.getUserAgent()
  final int paginateSize

  String getBasicAuth() {
    return tokenService?.basicAuth
  }

  DockerRegistryClient(String address,
                       long clientTimeoutMillis,
                       int paginateSize,
                       String catalogFile,
                       String repositoriesRegex,
                       boolean insecureRegistry,
                       DockerOkClientProvider okClientProvider,
                       ServiceClientProvider serviceClientProvider) {

    this.paginateSize = paginateSize
    this.tokenService = new DockerBearerTokenService(serviceClientProvider)
    this.registryService = new Retrofit.Builder()
      .baseUrl(address)
      .client(okClientProvider.provide(address, clientTimeoutMillis, insecureRegistry))
      .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
      .addConverterFactory(JacksonConverterFactory.create())
      .build()
      .create(DockerRegistryService);
    this.address = address
    this.catalogFile = catalogFile
    this.repositoriesRegex = repositoriesRegex
  }

  DockerRegistryClient(String address,
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
    this(address, clientTimeoutMillis, paginateSize, catalogFile, repositoriesRegex, insecureRegistry, okClientProvider, serviceClientProvider)
    this.tokenService = new DockerBearerTokenService(username, password, passwordCommand, serviceClientProvider)
    this.email = email
  }

  DockerRegistryClient(String address,
                       int paginateSize,
                       String catalogFile,
                       String repositoriesRegex,
                       DockerRegistryService dockerRegistryService,
                       DockerBearerTokenService dockerBearerTokenService) {
    this.paginateSize = paginateSize
    this.address = address
    this.catalogFile = catalogFile
    this.repositoriesRegex = repositoriesRegex
    this.tokenService = dockerBearerTokenService
    this.registryService = dockerRegistryService;
  }

  DockerRegistryClient(String address,
                       String email,
                       String username,
                       File passwordFile,
                       long clientTimeoutMillis,
                       int paginateSize,
                       String catalogFile,
                       String repositoriesRegex,
                       boolean insecureRegistry,
                       DockerOkClientProvider okClientProvider,
                       ServiceClientProvider serviceClientProvider) {
    this(address, clientTimeoutMillis, paginateSize, catalogFile, repositoriesRegex, insecureRegistry, okClientProvider, serviceClientProvider)
    this.tokenService = new DockerBearerTokenService(username, passwordFile, serviceClientProvider)
    this.email = email
  }

  interface DockerRegistryService {
    @GET("/v2/{repository}/tags/list")
    @Headers([
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Call<ResponseBody> getTags(@Path(value="repository", encoded=true) String repository, @Header("Authorization") String token, @Header("User-Agent") String agent, @QueryMap(encoded=true) Map<String, String> queryParams)


    @GET("/v2/{name}/manifests/{reference}")
    @Headers([
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Call<ResponseBody> getManifest(@Path(value="name", encoded=true) String name, @Path(value="reference", encoded=true) String reference, @Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/v2/{name}/manifests/{reference}")
    @Headers([
      "Docker-Distribution-API-Version: registry/2.0",
      "Accept: application/vnd.docker.distribution.manifest.v2+json"
    ])
    Call<ResponseBody> getSchemaV2Manifest(@Path(value="name", encoded=true) String name, @Path(value="reference", encoded=true) String reference, @Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/v2/_catalog")
    @Headers([
        "Docker-Distribution-API-Version: registry/2.0"
    ])
    Call<ResponseBody> getCatalog(@Header("Authorization") String token, @Header("User-Agent") String agent, @QueryMap(encoded=true) Map<String, String> queryParams)


    @GET("/{path}")
    @Headers([
        "Docker-Distribution-API-Version: registry/2.0"
    ])
    Call<ResponseBody> get(@Path(value="path", encoded=true) String path, @Header("Authorization") String token, @Header("User-Agent") String agent, @QueryMap(encoded=true) Map<String, String> queryParams)


    @GET("/v2/")
    @Headers([
      "User-Agent: Spinnaker-Clouddriver",
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Call<ResponseBody> checkVersion(@Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/v2/{repository}/blobs/{digest}")
    @Headers([
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Call<ResponseBody> getDigestContent(@Path(value="repository", encoded=true) String repository, @Path(value="digest", encoded=true) String digest, @Header("Authorization") String token, @Header("User-Agent") String agent)
  }

  public String getDigest(String name, String tag) {
    def response = getManifest(name, tag)
    def headers = response.headers
    def digest = headers?.find {
      it.name == "Docker-Content-Digest"
    }
    return digest?.value
  }

  public String getConfigDigest(String name, String tag) {
    def response = getSchemaV2Manifest(name, tag)
    def manifestMap = convertResponseBody(response.body(), Map)
    return manifestMap?.config?.digest
  }

  public Map getDigestContent(String name, String digest) {
    def response =   request({
        Retrofit2SyncCall.executeCall(registryService.getDigestContent(name, digest, tokenService.basicAuthHeader, userAgent))
    }, { token ->
      Retrofit2SyncCall.executeCall(registryService.getDigestContent(name, digest, token, userAgent))
    }, name)

    return convertResponseBody(response.body(), Map)
  }

  static def convertResponseBody(ResponseBody responseBody, Class aClass) {
    if (responseBody == null) {
      throw new DockerRegistryOperationException("ResponseBody cannot be null")
    }
    try {
      def objectMapper = new ObjectMapper()
      def jsonString = responseBody.string()
      return objectMapper.readValue(jsonString, aClass)
    } catch (Exception e) {
      throw new DockerRegistryOperationException("Failed to parse ResponseBody : ${e.message}", e)
    }
  }

  private Map tagDateCache = [:]

  public Instant getCreationDate(String name, String tag) {
    String key = "${name}:${tag}"
    if(tagDateCache.containsKey(key) && tag !='latest'){
      return tagDateCache[key]
    }
    Map manifest = convertResponseBody(getManifest(name, tag).body(), Map)
    Instant dateCreated = Instant.parse(new Gson().fromJson(manifest.history[0].v1Compatibility, Map).created)
    tagDateCache[key] = dateCreated
    dateCreated
  }

  private getManifest(String name, String tag) {
    request({
      Retrofit2SyncCall.executeCall(registryService.getManifest(name, tag, tokenService.basicAuthHeader, userAgent))
    }, { token ->
      Retrofit2SyncCall.executeCall(registryService.getManifest(name, tag, token, userAgent))
    }, name)
  }

  private getSchemaV2Manifest(String name, String tag) {
    request({
      Retrofit2SyncCall.executeCall(registryService.getSchemaV2Manifest(name, tag, tokenService.basicAuthHeader, userAgent))
    }, { token ->
      Retrofit2SyncCall.executeCall(registryService.getSchemaV2Manifest(name, tag, token, userAgent))
    }, name)
  }


  private static String parseLink(String headerValue) {

    def links = headerValue.split(";").collect { it.trim() }

    if (!(links.findAll { String tok ->
      tok.replace(" ", "").equalsIgnoreCase("rel=\"next\"")
    })) {
      return null
    }

    def path = links.find { String tok ->
      tok && tok.getAt(0) == "<" && tok.getAt(tok.length() - 1) == ">"
    }

    def link = path?.substring(1, path.length() - 1)

    try {
      def url = new URL(link)
      link = url.getFile().substring(1)
    } catch (Exception e) {
      // In the case where the link isn't a valid URL, we were passed just the
      // relative path[1]
      // [1] https://tools.ietf.org/html/rfc3986#section-5
    }

    return link.startsWith('/') ? link.replaceFirst('/', '') : link
  }

  private static String findNextLink(okhttp3.Headers headers) {
    if (!headers) {
      return null
    }

    def caseInsensitiveHeaders = [:].withDefault { [] }
    headers.names().each { name ->
      caseInsensitiveHeaders[name.toLowerCase()] += headers.values(name)
    }

    def headerValues = caseInsensitiveHeaders["link"]

    // We are at the end of the pagination.
    if (!headerValues || headerValues.size() == 0) {
      return null
    } else if (headerValues.size() > 1) {
      throw new DockerRegistryOperationException("Ambiguous number of Link headers provided, the following paths were identified: $headerValues")
    }

    return parseLink(headerValues[0] as String)
  }

  /*
   * This method will get all repositories available on this registry. It may fail, as some registries
   * don't want you to download their whole catalog (it's potentially a lot of data).
   */
  public DockerRegistryCatalog getCatalog(String path = null, Map<String, String> queryParams = [:]) {
    if (catalogFile) {
      log.info("Using catalog list at $catalogFile")
      try {
        String userDefinedCatalog = new File(catalogFile).getText()
        return (DockerRegistryCatalog) new Gson().fromJson(userDefinedCatalog, DockerRegistryCatalog.class)
      } catch (Exception e) {
        throw new DockerRegistryOperationException("Unable to read catalog file $catalogFile: " + e.getMessage(), e)
      }
    }

    queryParams.computeIfAbsent("n", { paginateSize.toString() })
    def response
    try {
      response = request({
        path ? Retrofit2SyncCall.executeCall(registryService.get(path, tokenService.basicAuthHeader, userAgent, queryParams)) :
          Retrofit2SyncCall.executeCall(registryService.getCatalog(tokenService.basicAuthHeader, userAgent, queryParams))
      }, { token ->
        path ? Retrofit2SyncCall.executeCall(registryService.get(path, token, userAgent, queryParams)) :
          Retrofit2SyncCall.executeCall(registryService.getCatalog(token, userAgent, queryParams))
      }, "_catalog")
    } catch (Exception e) {
      log.warn("Error encountered during catalog of $path", e)
      return new DockerRegistryCatalog(repositories: [])
    }

    def nextPath = findNextLink(response?.headers())
    def catalog = convertResponseBody(response.body(), DockerRegistryCatalog)

    if(repositoriesRegex) {
      catalog.repositories = catalog.repositories.findAll { it ==~ repositoriesRegex }
    }
    if (nextPath) {
      def nextPathNew
      (nextPathNew, queryParams) = parseForQueryParams(nextPath)
      def nextCatalog = getCatalog(nextPathNew, queryParams)
      catalog.repositories.addAll(nextCatalog.repositories)
    }

    return catalog
  }

  public DockerRegistryTags getTags(String repository, String path = null, Map<String, String> queryParams = [:]) {
    queryParams.computeIfAbsent("n", { paginateSize.toString() })
    def response = request({
      path ? Retrofit2SyncCall.executeCall(registryService.get(path, tokenService.basicAuthHeader, userAgent, queryParams)) :
        Retrofit2SyncCall.executeCall(registryService.getTags(repository, tokenService.basicAuthHeader, userAgent, queryParams))
    }, { token ->
      path ? Retrofit2SyncCall.executeCall(registryService.get(path, token, userAgent, queryParams)) :
        Retrofit2SyncCall.executeCall(registryService.getTags(repository, token, userAgent, queryParams))
    }, repository)

    def nextPath = findNextLink(response?.headers())
    def tags = convertResponseBody(response.body(), DockerRegistryTags)

    if (nextPath) {
      def nextPathNew
      (nextPathNew, queryParams) = parseForQueryParams(nextPath)
      def nextTags = getTags(repository, nextPathNew, queryParams)
      tags.tags.addAll(nextTags.tags)
    }

    return tags
  }

  /**
   * This method takes a string that might contain a query string and splits it into the path and the query parameters.
   * @param nextPath the string that might contain a query string
   * @return a tuple containing the path (without query string) and a map of query parameters
   */
  static Tuple2<String, Map<String, String>> parseForQueryParams(String nextPath) {
    def nextPathNew
    def queryParamsString
    Map<String, String> queryParams = [:]
    if (nextPath.contains("?")) {
      (nextPathNew, queryParamsString) = nextPath.split("\\?", 2)
    } else {
      nextPathNew = nextPath
    }
    if (queryParamsString) {
      queryParams = queryParamsString.split("&").collectEntries { param ->
        def (key, value) = param.split("=")
        [key, value]
      }
    }
    [nextPathNew, queryParams]
  }

  /*
   * This method will hit the /v2/ endpoint of the configured docker registry. If it this endpoint is up,
   * it will return silently. Otherwise, an exception is thrown detailing why the endpoint isn't available.
   */
  public void checkV2Availability() {
    try {
      doCheckV2Availability()
    } catch (SpinnakerServerException error) {
      // If no credentials are supplied, and we got a 401, the best[1] we can do is assume the registry is OK.
      // [1] https://docs.docker.com/registry/spec/api/#/api-version-check
      if (!tokenService.basicAuthHeader && error instanceof SpinnakerHttpException && ((SpinnakerHttpException)error).getResponseCode() == 401) {
        return
      }
      def response = doCheckV2Availability(tokenService.basicAuthHeader)
      if (!response.body()){
        LOG.error "checkV2Availability", error
        throw error
      }
    }
    // Placate the linter (otherwise it expects to return the result of `request()`)
    null
  }

  private Response<ResponseBody> doCheckV2Availability(String basicAuthHeader = null) {
    request({
      Retrofit2SyncCall.executeCall(registryService.checkVersion(basicAuthHeader, userAgent))
    }, { token ->
      Retrofit2SyncCall.executeCall(registryService.checkVersion(token, userAgent))
    }, "v2 version check")
  }

  /*
   * Implements token request flow described here https://docs.docker.com/registry/spec/auth/token/
   * The tokenService also caches tokens for us, so it will attempt to use an old token before retrying.
   */
  public Response<ResponseBody> request(Closure<Response<ResponseBody>> withoutToken, Closure<Response<ResponseBody>> withToken, String target) {
    try {
      DockerBearerToken dockerToken = tokenService.getToken(target)
      String token
      if (dockerToken) {
        token = "Bearer ${(dockerToken.bearer_token ?: dockerToken.token) ?: dockerToken.access_token}"
      }

      Response<ResponseBody> response
      try {
        if (token) {
          response = withToken(token)
        } else {
          response = withoutToken()
        }
      } catch (SpinnakerHttpException error) {
        def status = error.getResponseCode()
        // note, this is a workaround for registries that should be returning
        // 401 when a token expires
        if ([400, 401].contains(status)) {
          List<String> authenticateHeader = null

          error.headers.entrySet().forEach { header ->
            if (header.key.equalsIgnoreCase("www-authenticate")) {
              authenticateHeader = header.value
            }
          }

          if (!authenticateHeader || authenticateHeader.isEmpty()) {
            log.warn "Registry $address returned status $status for request '$target' without a WWW-Authenticate header"
            tokenService.clearToken(target)
            throw error
          }

          String bearerPrefix = "bearer "
          String basicPrefix = "basic "
          for (String headerValue in authenticateHeader) {
            if (bearerPrefix.equalsIgnoreCase(headerValue.substring(0, bearerPrefix.length()))) {
              // If we got a 401 and the request requires bearer auth, get a new token and try again
              dockerToken = tokenService.getToken(target, headerValue.substring(bearerPrefix.length()))
              token = "Bearer ${(dockerToken.bearer_token ?: dockerToken.token) ?: dockerToken.access_token}"
              return withToken(token)
            } else if (basicPrefix.equalsIgnoreCase(headerValue.substring(0, basicPrefix.length()))) {
              // If we got a 401 and the request requires basic auth, there's no point in trying again
              tokenService.clearToken(target)
              throw error
            }
          }

          tokenService.clearToken(target)
          throw new DockerRegistryAuthenticationException("Docker registry must support 'Bearer' or 'Basic' authentication.")
        } else {
          throw error
        }
      }

      return response
    } catch (DockerRegistryAuthenticationException e) {
      log.error "Error authenticating with registry $address, for request '$target': ${e.getMessage()}"
      throw e
    }
  }
}
