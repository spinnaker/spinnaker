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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.bake.debian;

import com.netflix.spinnaker.halyard.config.config.v1.ArtifactSourcesConfig;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.resource.v1.StringReplaceJarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.bake.BakeServiceProvider;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BakeDebianServiceProvider extends BakeServiceProvider {
  @Autowired private ArtifactSourcesConfig artifactSourcesConfig;

  @Autowired ArtifactService artifactService;

  @Autowired BakeDebianClouddriverService clouddriverService;

  @Autowired BakeDebianConsulClientService consulClientService;

  @Autowired BakeDebianConsulServerService consulServerService;

  @Autowired BakeDebianDeckService deckService;

  @Autowired BakeDebianEchoService echoService;

  @Autowired BakeDebianFiatService fiatService;

  @Autowired BakeDebianFront50Service front50Service;

  @Autowired BakeDebianGateService gateService;

  @Autowired BakeDebianIgorService igorService;

  @Autowired BakeDebianKayentaService kayentaService;

  @Autowired BakeDebianMonitoringDaemonService monitoringDaemonService;

  @Autowired BakeDebianOrcaService orcaService;

  @Autowired BakeDebianRedisService redisService;

  @Autowired BakeDebianRoscoService roscoService;

  @Autowired BakeDebianVaultClientService vaultClientService;

  @Autowired BakeDebianVaultServerService vaultServerService;

  @Autowired String startupScriptPath;

  @Override
  public String getInstallCommand(
      DeploymentDetails deploymentDetails,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      Map<String, String> installCommands,
      String startupCommand) {
    Map<String, Object> bindings = new HashMap<>();
    List<SpinnakerService.Type> serviceTypes =
        new ArrayList<>(installCommands.keySet())
            .stream().map(SpinnakerService.Type::fromCanonicalName).collect(Collectors.toList());
    List<String> upstartNames =
        getPrioritizedBakeableServices(serviceTypes).stream()
            .filter(i -> resolvedConfiguration.getServiceSettings(i.getService()).getEnabled())
            .map(i -> ((BakeDebianService) i).getUpstartServiceName())
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    List<String> systemdServiceConfigs =
        upstartNames.stream().map(n -> n + ".service").collect(Collectors.toList());
    List<String> serviceInstalls =
        serviceTypes.stream()
            .map(t -> installCommands.get(t.getCanonicalName()))
            .collect(Collectors.toList());

    TemplatedResource resource = new StringReplaceJarResource("/debian/init.sh");
    bindings.put("services", String.join(" ", upstartNames));
    bindings.put("systemd-service-configs", String.join(" ", systemdServiceConfigs));
    String upstartInit = resource.setBindings(bindings).toString();
    BillOfMaterials.ArtifactSources artifactSources =
        artifactService.getArtifactSources(deploymentDetails.getDeploymentName());

    resource = new StringReplaceJarResource("/debian/pre-bake.sh");
    bindings = new HashMap<>();
    bindings.put(
        "debian-repository",
        artifactSourcesConfig.mergeWithBomSources(artifactSources).getDebianRepository());
    bindings.put("install-commands", String.join("\n", serviceInstalls));
    bindings.put("upstart-init", upstartInit);
    bindings.put("startup-file", Paths.get(startupScriptPath, "startup.sh").toString());
    bindings.put("startup-command", startupCommand);

    return resource.setBindings(bindings).toString();
  }

  @Override
  public RemoteAction clean(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {
    throw new HalException(
        Problem.Severity.FATAL, "Bakeable services do not support being uninstalled.");
  }
}
