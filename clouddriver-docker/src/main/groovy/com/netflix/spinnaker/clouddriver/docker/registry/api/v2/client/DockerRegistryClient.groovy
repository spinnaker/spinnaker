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
import retrofit.Callback
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.GsonConverter
import retrofit.http.GET
import retrofit.http.Header
import retrofit.http.Headers
import retrofit.http.Path

class DockerRegistryClient {
  private DockerBearerTokenService tokenService

  public String address
  private DockerRegistryService registryService
  private GsonConverter converter

  DockerRegistryClient(String address, String email, String username, String password) {
    this.tokenService = new DockerBearerTokenService(username, password)
    this.registryService = new RestAdapter.Builder().setEndpoint(address).setLogLevel(RestAdapter.LogLevel.NONE).build().create(DockerRegistryService)
    this.converter = new GsonConverter(new GsonBuilder().create())
    this.address = address
  }

  interface DockerRegistryService {
    @GET("/v2/{repository}/tags/list")
    @Headers([
      "User-Agent: Spinnaker-Clouddriver",
      "Docker-Distribution-API-Version: registry/2.0"
    ]) // TODO(lwander) get clouddriver version #
    Response getTags(@Path(value="repository", encode=false) String repository, @Header("Authorization") String token)

    @GET("/v2/{repository}/tags/list")
    @Headers([
      "User-Agent: Spinnaker-Clouddriver",
      "Docker-Distribution-API-Version: registry/2.0"
    ]) // TODO(lwander) get clouddriver version #
    Response getTags(@Path(value="repository", encode=false) String repository)

    @GET("/v2/")
    @Headers([
      "User-Agent: Spinnaker-Clouddriver",
      "Docker-Distribution-API-Version: registry/2.0"
    ]) // TODO(lwander) get clouddriver version #
    Response checkVersion()
  }

  /*
   * Implements token request flow described here https://docs.docker.com/registry/spec/auth/token/
   * The tokenService also caches tokens for us, so it will attempt to use an old token before retrying.
   */
  public DockerRegistryTags getTags(String repository) {
    DockerBearerToken dockerToken = tokenService.getToken(repository)
    String token
    if (dockerToken) {
      token = "Bearer ${dockerToken.bearer_token ?: dockerToken.token}"
    }

    Response response
    try {
      if (token) {
        response = registryService.getTags(repository, token)
      } else {
        response = registryService.getTags(repository)
      }
    } catch (RetrofitError error) {
      if (error.response.status == 401) {
        dockerToken = tokenService.getToken(repository, error.response.headers)
        token = "Bearer ${dockerToken.bearer_token ?: dockerToken.token}"
        response = registryService.getTags(repository, token)
      } else {
        throw error
      }
    }

    return (DockerRegistryTags) converter.fromBody(response.body, DockerRegistryTags)
  }

  public Boolean isV2() {
    try {
      registryService.checkVersion()
    } catch (RetrofitError error) {
      if (error.response.status != 401) {
        return false
      }
    }
    return true
  }
}
