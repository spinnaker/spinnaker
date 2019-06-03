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
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.S3PersistentStore;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.AwsCredentialsProfileFactoryBuilder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Front50ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.http.GET;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public abstract class Front50Service extends SpringService<Front50Service.Front50> {
  @Autowired Front50ProfileFactory front50ProfileFactory;

  @Autowired AwsCredentialsProfileFactoryBuilder awsCredentialsProfileFactoryBuilder;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.FRONT50;
  }

  @Override
  public Type getType() {
    return Type.FRONT50;
  }

  @Override
  public Class<Front50> getEndpointClass() {
    return Front50.class;
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> profiles = super.getProfiles(deploymentConfiguration, endpoints);
    String filename = "front50.yml";

    String path = Paths.get(getConfigOutputPath(), filename).toString();
    Profile profile =
        front50ProfileFactory.getProfile(filename, path, deploymentConfiguration, endpoints);

    profiles.add(profile);
    return profiles;
  }

  protected Optional<Profile> generateAwsProfile(
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints,
      String spinnakerHome) {
    String name = "aws/front50-credentials" + spinnakerHome.replace("/", "_");
    PersistentStore.PersistentStoreType type =
        deploymentConfiguration.getPersistentStorage().getPersistentStoreType();
    S3PersistentStore store = deploymentConfiguration.getPersistentStorage().getS3();
    if (type == PersistentStore.PersistentStoreType.S3
        && !StringUtils.isEmpty(store.getAccessKeyId())
        && !StringUtils.isEmpty(store.getSecretAccessKey())) {
      String outputFile = awsCredentialsProfileFactoryBuilder.getOutputFile(spinnakerHome);
      return Optional.of(
          awsCredentialsProfileFactoryBuilder
              .setArtifact(SpinnakerArtifact.FRONT50)
              .setAccessKeyId(store.getAccessKeyId())
              .setSecretAccessKey(store.getSecretAccessKey())
              .build()
              .getProfile(name, outputFile, deploymentConfiguration, endpoints));
    } else {
      return Optional.empty();
    }
  }

  public interface Front50 {
    @GET("/resolvedEnv")
    Map<String, String> resolvedEnv();

    @GET("/health")
    SpringHealth health();
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class Settings extends SpringServiceSettings {
    Integer port = 8080;
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
  }
}
