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

import com.google.gson.GsonBuilder
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerToken
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerTokenService
import com.squareup.okhttp.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.OkClient
import retrofit.client.Response
import retrofit.converter.GsonConverter
import retrofit.http.GET
import retrofit.http.HEAD
import retrofit.http.Header
import retrofit.http.Headers
import retrofit.http.Path

import java.util.concurrent.TimeUnit

class DockerRegistryClient {
  private DockerBearerTokenService tokenService

  public String address
  private DockerRegistryService registryService
  private GsonConverter converter
  private String basicAuth

  @Autowired
  String dockerApplicationName

  @Value('${dockerRegistry.client.timeout:60000}')
  int clientTimeout

  public getBasicAuth() {
    return basicAuth
  }

  DockerRegistryClient(String address, String email, String username, String password) {
    this.tokenService = new DockerBearerTokenService(username, password)
    this.basicAuth = this.tokenService.basicAuth
    OkHttpClient client = new OkHttpClient()
    client.setReadTimeout(clientTimeout, TimeUnit.MILLISECONDS)
    this.registryService = new RestAdapter.Builder()
      .setEndpoint(address)
      .setClient(new OkClient(client))
      .setLogLevel(RestAdapter.LogLevel.NONE)
      .build()
      .create(DockerRegistryService)
    this.converter = new GsonConverter(new GsonBuilder().create())
    this.address = address
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
    Response getCatalog(@Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/v2/")
    @Headers([
      "User-Agent: Spinnaker-Clouddriver",
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Response checkVersion(@Header("Authorization") String token, @Header("User-Agent") String agent)
  }

  public DockerRegistryTags getTags(String repository) {
    def response = request({
      registryService.getTags(repository, tokenService.basicAuthHeader, dockerApplicationName)
    }, { token ->
      registryService.getTags(repository, token, dockerApplicationName)
    }, repository)

    (DockerRegistryTags) converter.fromBody(response.body, DockerRegistryTags)
  }

  public String getDigest(String name, String tag) {
    def response = request({
      registryService.getManifest(name, tag, tokenService.basicAuthHeader, dockerApplicationName)
    }, { token ->
      registryService.getManifest(name, tag, token, dockerApplicationName)
    }, name)

    def headers = response.headers

    def digest = headers?.find {
      it.name == "Docker-Content-Digest"
    }

    return digest?.value
  }

  /*
   * This method will get all repositories available on this registry. It may fail, as some registries
   * don't want you to download their whole catalog (it's potentially a lot of data).
   */
  public DockerRegistryCatalog getCatalog() {
    def response = request({
      registryService.getCatalog(tokenService.basicAuthHeader, dockerApplicationName)
    }, { token ->
      registryService.getCatalog(token, dockerApplicationName)
    }, "_catalog")

    return (DockerRegistryCatalog) converter.fromBody(response.body, DockerRegistryCatalog)
  }

  /*
   * Implements token request flow described here https://docs.docker.com/registry/spec/auth/token/
   * The tokenService also caches tokens for us, so it will attempt to use an old token before retrying.
   */
  public Response request(Closure<Response> withoutToken, Closure<Response> withToken, String target) {
    DockerBearerToken dockerToken = tokenService.getToken(target)
    String token
    if (dockerToken) {
      token = "Bearer ${dockerToken.bearer_token ?: dockerToken.token}"
    }

    Response response
    try {
      if (token) {
        response = withToken(token)
      } else {
        response = withoutToken()
      }
    } catch (RetrofitError error) {
      if (error.response?.status == 401) {
        dockerToken = tokenService.getToken(target, error.response.headers)
        token = "Bearer ${dockerToken.bearer_token ?: dockerToken.token}"
        response = withToken(token)
      } else {
        throw error
      }
    }

    return response
  }

  /*
   * This method will hit the /v2/ endpoint of the configured docker registry. If it this endpoint is up,
   * it will return silently. Otherwise, an exception is thrown detailing why the endpoint isn't available.
   */
  public void checkV2Availability() {
    request({
      registryService.checkVersion(tokenService.basicAuthHeader, dockerApplicationName)
    }, { token ->
      registryService.checkVersion(token, dockerApplicationName)
    }, "v2 version check")

    // Placate the linter (otherwise it expects to return the result of `request()`)
    null
  }
}
