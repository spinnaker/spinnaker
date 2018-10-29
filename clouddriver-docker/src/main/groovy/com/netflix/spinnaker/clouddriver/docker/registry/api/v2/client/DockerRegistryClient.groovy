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

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.DockerUserAgent
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerToken
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerTokenService
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.exception.DockerRegistryAuthenticationException
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.exception.DockerRegistryOperationException
import groovy.util.logging.Slf4j
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.GsonConverter
import retrofit.http.GET
import retrofit.http.Header
import retrofit.http.Headers
import retrofit.http.Path
import retrofit.http.Query

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
    boolean insecureRegistry
    DockerOkClientProvider okClientProvider

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

    Builder insecureRegistry(boolean insecureRegistry) {
      this.insecureRegistry = insecureRegistry
      return this
    }

    Builder okClientProvider(DockerOkClientProvider okClientProvider) {
      this.okClientProvider = okClientProvider
      return this
    }

    DockerRegistryClient build() {

      if (password && passwordFile || password && passwordCommand || passwordFile && passwordCommand) {
        throw new IllegalArgumentException('Error, at most one of "password", "passwordFile", "passwordCommand" or "dockerconfigFile" can be specified')
      }
      if (password || passwordCommand) {
        return new DockerRegistryClient(address, email, username, password, passwordCommand, clientTimeoutMillis, paginateSize, catalogFile, insecureRegistry, okClientProvider)
      } else if (passwordFile) {
        return new DockerRegistryClient(address, email, username, passwordFile, clientTimeoutMillis, paginateSize, catalogFile, insecureRegistry, okClientProvider)
      } else {
        return new DockerRegistryClient(address, clientTimeoutMillis, paginateSize, catalogFile, insecureRegistry, okClientProvider)
      }
    }

  }

  private static final Logger LOG = LoggerFactory.getLogger(DockerRegistryClient)

  DockerBearerTokenService tokenService

  String address
  String email
  DockerRegistryService registryService
  GsonConverter converter
  String catalogFile

  final static String userAgent = DockerUserAgent.getUserAgent()
  final int paginateSize

  String getBasicAuth() {
    return tokenService?.basicAuth
  }

  DockerRegistryClient(String address,
                       long clientTimeoutMillis,
                       int paginateSize,
                       String catalogFile,
                       boolean insecureRegistry,
                       DockerOkClientProvider okClientProvider) {

    this.paginateSize = paginateSize
    this.tokenService = new DockerBearerTokenService()

    this.registryService = new RestAdapter.Builder()
      .setEndpoint(address)
      .setClient(okClientProvider.provide(address, clientTimeoutMillis, insecureRegistry))
      .setLogLevel(RestAdapter.LogLevel.NONE)
      .build()
      .create(DockerRegistryService)
    this.converter = new GsonConverter(new GsonBuilder().create())
    this.address = address
    this.catalogFile = catalogFile
  }

  DockerRegistryClient(String address,
                       String email,
                       String username,
                       String password,
                       String passwordCommand,
                       long clientTimeoutMillis,
                       int paginateSize,
                       String catalogFile,
                       boolean insecureRegistry,
                       DockerOkClientProvider okClientProvider) {
    this(address, clientTimeoutMillis, paginateSize, catalogFile, insecureRegistry, okClientProvider)
    this.tokenService = new DockerBearerTokenService(username, password, passwordCommand)
    this.email = email
  }

  DockerRegistryClient(String address,
                       String email,
                       String username,
                       File passwordFile,
                       long clientTimeoutMillis,
                       int paginateSize,
                       String catalogFile,
                       boolean insecureRegistry,
                       DockerOkClientProvider okClientProvider) {
    this(address, clientTimeoutMillis, paginateSize, catalogFile, insecureRegistry, okClientProvider)
    this.tokenService = new DockerBearerTokenService(username, passwordFile)
    this.email = email
  }

  interface DockerRegistryService {
    @GET("/v2/{repository}/tags/list")
    @Headers([
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Response getTags(@Path(value="repository", encode=false) String repository, @Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/v2/{name}/manifests/{reference}")
    @Headers([
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Response getManifest(@Path(value="name", encode=false) String name, @Path(value="reference", encode=false) String reference, @Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/v2/_catalog")
    @Headers([
        "Docker-Distribution-API-Version: registry/2.0"
    ])
    Response getCatalog(@Query(value="n") int paginateSize, @Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/{path}")
    @Headers([
        "Docker-Distribution-API-Version: registry/2.0"
    ])
    Response get(@Path(value="path", encode=false) String path, @Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/v2/")
    @Headers([
      "User-Agent: Spinnaker-Clouddriver",
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Response checkVersion(@Header("Authorization") String token, @Header("User-Agent") String agent)
  }

  public String getDigest(String name, String tag) {
    def response = getManifest(name, tag)
    def headers = response.headers
    def digest = headers?.find {
      it.name == "Docker-Content-Digest"
    }
    return digest?.value
  }

  private Map tagDateCache = [:]

  public Instant getCreationDate(String name, String tag) {
    String key = "${name}:${tag}"
    if(tagDateCache.containsKey(key) && tag !='latest'){
      return tagDateCache[key]
    }
    Map manifest = converter.fromBody(getManifest(name, tag).body, Map)
    Instant dateCreated = Instant.parse(new Gson().fromJson(manifest.history[0].v1Compatibility, Map).created)
    tagDateCache[key] = dateCreated
    dateCreated
  }

  private getManifest(String name, String tag) {
    request({
      registryService.getManifest(name, tag, tokenService.basicAuthHeader, userAgent)
    }, { token ->
      registryService.getManifest(name, tag, token, userAgent)
    }, name)
  }

  private static String parseLink(retrofit.client.Header header) {
    if (!header.name.equalsIgnoreCase("link")) {
      return null
    }

    def links = header.value.split(";").collect { it.trim() }

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

  private static String findNextLink(List<retrofit.client.Header> headers) {
    if (!headers) {
      return null
    }

    def paths = headers.collect { header ->
      parseLink(header)
    }.findAll { it }

    // We are at the end of the pagination.
    if (!paths || paths.size() == 0) {
      return null
    } else if (paths.size() > 1) {
      throw new DockerRegistryOperationException("Ambiguous number of Link headers provided, the following paths were identified: $paths")
    }

    return paths[0]
  }

  /*
   * This method will get all repositories available on this registry. It may fail, as some registries
   * don't want you to download their whole catalog (it's potentially a lot of data).
   */
  public DockerRegistryCatalog getCatalog(String path = null) {
    if (catalogFile) {
      log.info("Using catalog list at $catalogFile")
      try {
        String userDefinedCatalog = new File(catalogFile).getText()
        return (DockerRegistryCatalog) new Gson().fromJson(userDefinedCatalog, DockerRegistryCatalog.class)
      } catch (Exception e) {
        throw new DockerRegistryOperationException("Unable to read catalog file $catalogFile:" + e.getMessage())
      }
    }

    def response
    try {
      response = request({
        path ? registryService.get(path, tokenService.basicAuthHeader, userAgent) :
          registryService.getCatalog(paginateSize, tokenService.basicAuthHeader, userAgent)
      }, { token ->
        path ? registryService.get(path, token, userAgent) :
          registryService.getCatalog(paginateSize, token, userAgent)
      }, "_catalog")
    } catch (Exception e) {
      log.warn("Error encountered during catalog of $path" + e.getMessage())
      return new DockerRegistryCatalog(repositories: [])
    }

    def nextPath = findNextLink(response?.headers)
    def catalog = (DockerRegistryCatalog) converter.fromBody(response.body, DockerRegistryCatalog)

    if (nextPath) {
      def nextCatalog = getCatalog(nextPath)
      catalog.repositories.addAll(nextCatalog.repositories)
    }

    return catalog
  }

  public DockerRegistryTags getTags(String repository, String path = null) {
    def response = request({
      path ? registryService.get(path, tokenService.basicAuthHeader, userAgent) :
        registryService.getTags(repository, tokenService.basicAuthHeader, userAgent)
    }, { token ->
      path ? registryService.get(path, token, userAgent) :
        registryService.getTags(repository, token, userAgent)
    }, repository)

    def nextPath = findNextLink(response?.headers)
    def tags = (DockerRegistryTags) converter.fromBody(response.body, DockerRegistryTags)

    if (nextPath) {
      def nextTags = getTags(repository, nextPath)
      tags.tags.addAll(nextTags.tags)
    }

    return tags
  }

  /*
   * This method will hit the /v2/ endpoint of the configured docker registry. If it this endpoint is up,
   * it will return silently. Otherwise, an exception is thrown detailing why the endpoint isn't available.
   */
  public void checkV2Availability() {
    try {
      doCheckV2Availability()
    } catch (RetrofitError error) {
      // If no credentials are supplied, and we got a 401, the best[1] we can do is assume the registry is OK.
      // [1] https://docs.docker.com/registry/spec/api/#/api-version-check
      if (!tokenService.basicAuthHeader && error.response?.status == 401) {
        return
      }
      Response response = doCheckV2Availability(tokenService.basicAuthHeader)
      if (!response){
        LOG.error "checkV2Availability", error
        throw error
      }
    }
    // Placate the linter (otherwise it expects to return the result of `request()`)
    null
  }

  private Response doCheckV2Availability(String basicAuthHeader = null) {
    request({
      registryService.checkVersion(basicAuthHeader, userAgent)
    }, { token ->
      registryService.checkVersion(token, userAgent)
    }, "v2 version check")
  }

  /*
   * Implements token request flow described here https://docs.docker.com/registry/spec/auth/token/
   * The tokenService also caches tokens for us, so it will attempt to use an old token before retrying.
   */
  public Response request(Closure<Response> withoutToken, Closure<Response> withToken, String target) {
    try {
      DockerBearerToken dockerToken = tokenService.getToken(target)
      String token
      if (dockerToken) {
        token = "Bearer ${(dockerToken.bearer_token ?: dockerToken.token) ?: dockerToken.access_token}"
      }

      Response response
      try {
        if (token) {
          response = withToken(token)
        } else {
          response = withoutToken()
        }
      } catch (RetrofitError error) {
        def status = error.response?.status
        // note, this is a workaround for registries that should be returning
        // 401 when a token expires
        if ([400, 401].contains(status)) {
          String authenticateHeader = null

          error.response.headers.forEach { header ->
            if (header.name.equalsIgnoreCase("www-authenticate")) {
              authenticateHeader = header.value
            }
          }

          if (!authenticateHeader) {
            log.warn "Registry $address returned status $status for request '$target' without a WWW-Authenticate header"
            tokenService.clearToken(target)
            throw error
          }

          String bearerPrefix = "bearer "
          String basicPrefix = "basic "
          if (bearerPrefix.equalsIgnoreCase(authenticateHeader.substring(0, bearerPrefix.length()))) {
            // If we got a 401 and the request requires bearer auth, get a new token and try again
            dockerToken = tokenService.getToken(target, authenticateHeader.substring(bearerPrefix.length()))
            token = "Bearer ${(dockerToken.bearer_token ?: dockerToken.token) ?: dockerToken.access_token}"
            response = withToken(token)
          } else if (basicPrefix.equalsIgnoreCase(authenticateHeader.substring(0, basicPrefix.length()))) {
            // If we got a 401 and the request requires basic auth, there's no point in trying again
            tokenService.clearToken(target)
            throw error
          } else {
            tokenService.clearToken(target)
            throw new DockerRegistryAuthenticationException("Docker registry must support 'Bearer' or 'Basic' authentication.")
          }
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
