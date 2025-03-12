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
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService.ResolvedConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.SidecarService;
import java.util.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public abstract class VaultClientService extends SpinnakerService<VaultClientService.Vault>
    implements SidecarService {
  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.VAULT;
  }

  @Override
  public Type getType() {
    return Type.VAULT_CLIENT;
  }

  @Override
  public Class<Vault> getEndpointClass() {
    return Vault.class;
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    return new ArrayList<>();
  }

  public interface Vault {}

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class Settings extends ServiceSettings {
    Integer port = 8200;
    Boolean enabled = true;
    Boolean safeToUpdate = true;
    Boolean monitored = false;
    Boolean sidecar = true;
    Boolean skipLifeCycleManagement = false;
    Map<String, String> env = new HashMap<>();

    public Settings() {}
  }

  public List<Profile> getSidecarProfiles(
      ResolvedConfiguration resolvedConfiguration, SpinnakerService service) {
    return Collections.emptyList();
  }

  @Override
  protected Optional<String> customProfileOutputPath(String profileName) {
    return Optional.empty();
  }
}
