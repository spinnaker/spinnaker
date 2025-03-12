/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.AwsCredentialsProfileFactoryBuilder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.KayentaProfileFactory;
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
public abstract class KayentaService extends SpringService<KayentaService.Kayenta> {

  @Autowired KayentaProfileFactory kayentaProfileFactory;

  @Autowired AwsCredentialsProfileFactoryBuilder awsCredentialsProfileFactoryBuilder;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.KAYENTA;
  }

  @Override
  public Type getType() {
    return Type.KAYENTA;
  }

  @Override
  public Class<Kayenta> getEndpointClass() {
    return Kayenta.class;
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> profiles = super.getProfiles(deploymentConfiguration, endpoints);
    String filename = "kayenta.yml";

    String path = Paths.get(getConfigOutputPath(), filename).toString();
    Profile profile =
        kayentaProfileFactory.getProfile(filename, path, deploymentConfiguration, endpoints);

    profiles.add(profile);
    return profiles;
  }

  protected Optional<Profile> generateAwsProfile(
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints,
      String spinnakerHome) {
    String name = "aws/kayenta-credentials" + spinnakerHome.replace("/", "_");
    Canary canary = deploymentConfiguration.getCanary();

    if (canary.isEnabled()) {
      AwsCanaryServiceIntegration awsCanaryServiceIntegration =
          (AwsCanaryServiceIntegration)
              getServiceIntegrationByClass(canary, AwsCanaryServiceIntegration.class);

      // TODO(lwander/duftler): Seems like this approach leaves us open to potential collision
      // between kayenta aws
      // accounts, and front50 and clouddriver configuration.
      if (awsCanaryServiceIntegration.isS3Enabled()) {
        Optional<AwsCanaryAccount> optionalAwsCanaryAccount =
            awsCanaryServiceIntegration.getAccounts().stream()
                .filter(
                    a ->
                        !StringUtils.isEmpty(a.getAccessKeyId())
                            && !StringUtils.isEmpty(a.getSecretAccessKey()))
                .findFirst();

        if (optionalAwsCanaryAccount.isPresent()) {
          AwsCanaryAccount awsCanaryAccount = optionalAwsCanaryAccount.get();
          String outputFile = awsCredentialsProfileFactoryBuilder.getOutputFile(spinnakerHome);

          awsCredentialsProfileFactoryBuilder.setProfileName(
              StringUtils.isNotBlank(awsCanaryAccount.getProfileName())
                  ? awsCanaryAccount.getProfileName()
                  : "default");

          return Optional.of(
              awsCredentialsProfileFactoryBuilder
                  .setArtifact(SpinnakerArtifact.KAYENTA)
                  .setAccessKeyId(awsCanaryAccount.getAccessKeyId())
                  .setSecretAccessKey(awsCanaryAccount.getSecretAccessKey())
                  .build()
                  .getProfile(name, outputFile, deploymentConfiguration, endpoints));
        }
      }
    }

    return Optional.empty();
  }

  public interface Kayenta {
    @GET("/resolvedEnv")
    Map<String, String> resolvedEnv();

    @GET("/health")
    SpringHealth health();
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class Settings extends SpringServiceSettings {
    Integer port = 8090;
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

  private static AbstractCanaryServiceIntegration getServiceIntegrationByClass(
      Canary canary, Class<? extends AbstractCanaryServiceIntegration> serviceIntegrationClass) {
    return canary.getServiceIntegrations().stream()
        .filter(s -> serviceIntegrationClass.isAssignableFrom(s.getClass()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Canary service integration of type "
                        + serviceIntegrationClass.getSimpleName()
                        + " not found."));
  }
}
