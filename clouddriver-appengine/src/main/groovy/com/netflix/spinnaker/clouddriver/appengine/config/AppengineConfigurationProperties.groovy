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

package com.netflix.spinnaker.clouddriver.appengine.config

import com.netflix.spinnaker.clouddriver.appengine.AppengineJobExecutor
import com.netflix.spinnaker.clouddriver.googlecommon.config.GoogleCommonManagedAccount
import com.squareup.okhttp.OkHttpClient
import groovy.json.JsonSlurper
import groovy.transform.ToString
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.client.Response
import retrofit.http.GET
import retrofit.http.Headers
import retrofit.mime.TypedByteArray

class AppengineConfigurationProperties {
  @ToString(includeNames = true)
  static class ManagedAccount extends GoogleCommonManagedAccount {
    static final String metadataUrl = "http://metadata.google.internal/computeMetadata/v1"

    String serviceAccountEmail
    String localRepositoryDirectory
    String gitHttpsUsername
    String gitHttpsPassword
    String githubOAuthAccessToken
    String sshPrivateKeyFilePath
    String sshPrivateKeyPassphrase
    String sshKnownHostsFilePath
    boolean sshTrustUnknownHosts
    GcloudReleaseTrack gcloudReleaseTrack
    List<String> services
    List<String> versions
    List<String> omitServices
    List<String> omitVersions

    void initialize(AppengineJobExecutor jobExecutor) {
      if (this.jsonPath) {
        jobExecutor.runCommand(["gcloud", "auth", "activate-service-account", "--key-file", this.jsonPath])

        def accountJson = new JsonSlurper().parse(new File(this.jsonPath))
        this.project = this.project ?: accountJson["project_id"]
        this.serviceAccountEmail = this.serviceAccountEmail ?: accountJson["client_email"]
      } else {
        def metadataService = createMetadataService()

        try {
          this.project = this.project ?: responseToString(metadataService.getProject())
          this.serviceAccountEmail = responseToString(metadataService.getApplicationDefaultServiceAccountEmail())
        } catch (e) {
          throw new RuntimeException("Could not find application default credentials for App Engine.", e)
        }
      }
    }

    static MetadataService createMetadataService() {
      RestAdapter restAdapter = new RestAdapter.Builder()
        .setEndpoint(metadataUrl)
        .setClient(new OkClient(new OkHttpClient(retryOnConnectionFailure: true)))
        .build()

      return restAdapter.create(MetadataService.class)
    }

    static interface MetadataService {
      @Headers("Metadata-Flavor: Google")
      @GET("/project/project-id")
      Response getProject()

      @Headers("Metadata-Flavor: Google")
      @GET("/instance/service-accounts/default/email")
      Response getApplicationDefaultServiceAccountEmail()
    }

    static String responseToString(Response response) {
      new String(((TypedByteArray) response.body).bytes)
    }

    static enum GcloudReleaseTrack {
      ALPHA,
      BETA,
      STABLE,
    }
  }

  List<ManagedAccount> accounts = []
}
