/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;


import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.AwsCredentialsProfileFactoryBuilder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.ClouddriverProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.squareup.okhttp.Response;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Path;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
abstract public class ClouddriverService extends SpringService<ClouddriverService.Clouddriver> {
  public static final String REDIS_KEY_SPACE = "com.netflix.spinnaker.clouddriver*";

  @Autowired
  ClouddriverProfileFactory clouddriverProfileFactory;

  protected ClouddriverProfileFactory getClouddriverProfileFactory() {
    return clouddriverProfileFactory;
  }

  @Autowired
  AwsCredentialsProfileFactoryBuilder awsCredentialsProfileFactoryBuilder;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.CLOUDDRIVER;
  }

  @Override
  public Type getType() {
    return Type.CLOUDDRIVER;
  }

  @Override
  public Class<Clouddriver> getEndpointClass() {
    return Clouddriver.class;
  }

  @Override
  public List<Profile> getProfiles(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> profiles = super.getProfiles(deploymentConfiguration, endpoints);
    String filename = "clouddriver.yml";

    String path = Paths.get(getConfigOutputPath(), filename).toString();
    Profile profile = getClouddriverProfileFactory().getProfile(filename, path, deploymentConfiguration, endpoints);

    profiles.add(profile);
    return profiles;
  }

  protected Optional<Profile> generateAwsProfile(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints, String spinnakerHome) {
    String name = "aws/clouddriver-credentials" + spinnakerHome.replace("/", "_");
    AwsProvider awsProvider = deploymentConfiguration.getProviders().getAws();
    if (awsProvider.isEnabled()
        && !StringUtils.isEmpty(awsProvider.getAccessKeyId())
        && !StringUtils.isEmpty(awsProvider.getSecretAccessKey())) {
      String outputFile = awsCredentialsProfileFactoryBuilder.getOutputFile(spinnakerHome);
      return Optional.of(awsCredentialsProfileFactoryBuilder
          .setArtifact(SpinnakerArtifact.CLOUDDRIVER)
          .setAccessKeyId(awsProvider.getAccessKeyId())
          .setSecretAccessKey(awsProvider.getSecretAccessKey())
          .build()
          .getProfile(name, outputFile, deploymentConfiguration, endpoints));
    } else {
      return Optional.empty();
    }
  }

  public interface Clouddriver {
    @POST("/{provider}/ops")
    Response providerOp(@Path("provider") String provider, @Body List<Map<String, Object>> payload);

    @GET("/resolvedEnv")
    Map<String, String> resolvedEnv();

    @GET("/health")
    SpringHealth health();
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class Settings extends SpringServiceSettings {
    Integer port = 7002;
    // Address is how the service is looked up.
    String address = "localhost";
    // Host is what's bound to by the service.
    String host = "0.0.0.0";
    String scheme = "http";
    String healthEndpoint = "/health";
    Boolean enabled = true;
    Boolean safeToUpdate = true;
    Boolean monitored = true;
    Boolean sidecar = false;
    Integer targetSize = 1;
    Boolean skipLifeCycleManagement = false;
    Map<String, String> env = new HashMap<>();

    public Settings() {}

    public Settings(List<String> profiles) {
      setProfiles(profiles);
    }
  }
}
