/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.appengine.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.clouddriver.appengine.AppengineJobExecutor;
import com.netflix.spinnaker.clouddriver.googlecommon.config.GoogleCommonManagedAccount;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import okhttp3.OkHttpClient;
import org.springframework.util.StringUtils;
import retrofit.RestAdapter;
import retrofit.client.Response;
import retrofit.converter.JacksonConverter;
import retrofit.http.GET;
import retrofit.http.Headers;
import retrofit.mime.TypedByteArray;

@Data
public class AppengineConfigurationProperties {
  private List<ManagedAccount> accounts = new ArrayList<>();
  private String gcloudPath;

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class ManagedAccount extends GoogleCommonManagedAccount {
    public static final String metadataUrl = "http://metadata.google.internal/computeMetadata/v1";

    private String serviceAccountEmail;
    @EqualsAndHashCode.Exclude private String computedServiceAccountEmail;
    private String localRepositoryDirectory = "/var/tmp/clouddriver";
    private String gitHttpsUsername;
    private String gitHttpsPassword;
    private String githubOAuthAccessToken;
    private String sshPrivateKeyFilePath;
    private String sshPrivateKeyPassphrase;
    private String sshKnownHostsFilePath;
    private boolean sshTrustUnknownHosts;
    private GcloudReleaseTrack gcloudReleaseTrack;
    private List<String> services;
    private List<String> versions;
    private List<String> omitServices;
    private List<String> omitVersions;
    private Long cachingIntervalSeconds;

    public void initialize(AppengineJobExecutor jobExecutor, String gcloudPath) {
      if (!StringUtils.isEmpty(getJsonPath())) {
        jobExecutor.runCommand(
            List.of(gcloudPath, "auth", "activate-service-account", "--key-file", getJsonPath()));
        ObjectMapper mapper = new ObjectMapper();
        try {
          JsonNode node = mapper.readTree(new File(getJsonPath()));
          if (StringUtils.isEmpty(getProject())) {
            setProject(node.get("project_id").asText());
          }
          if (StringUtils.isEmpty(serviceAccountEmail)) {
            this.computedServiceAccountEmail = node.get("client_email").asText();
          } else {
            this.computedServiceAccountEmail = serviceAccountEmail;
          }

        } catch (Exception e) {
          throw new RuntimeException("Could not find read JSON configuration file.", e);
        }
      } else {
        MetadataService metadataService = createMetadataService();

        try {
          if (StringUtils.isEmpty(getProject())) {
            setProject(responseToString(metadataService.getProject()));
          }
          this.computedServiceAccountEmail =
              responseToString(metadataService.getApplicationDefaultServiceAccountEmail());
        } catch (Exception e) {
          throw new RuntimeException(
              "Could not find application default credentials for App Engine.", e);
        }
      }
    }

    static MetadataService createMetadataService() {
      OkHttpClient okHttpClient = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
      RestAdapter restAdapter =
          new RestAdapter.Builder()
              .setEndpoint(metadataUrl)
              .setConverter(new JacksonConverter())
              .setClient(new Ok3Client(okHttpClient))
              .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
              .build();
      return restAdapter.create(MetadataService.class);
    }

    interface MetadataService {
      @Headers("Metadata-Flavor: Google")
      @GET("/project/project-id")
      Response getProject();

      @Headers("Metadata-Flavor: Google")
      @GET("/instance/service-accounts/default/email")
      Response getApplicationDefaultServiceAccountEmail();
    }

    static String responseToString(Response response) {
      return new String(((TypedByteArray) response.getBody()).getBytes());
    }

    public enum GcloudReleaseTrack {
      ALPHA,
      BETA,
      STABLE,
    }
  }
}
