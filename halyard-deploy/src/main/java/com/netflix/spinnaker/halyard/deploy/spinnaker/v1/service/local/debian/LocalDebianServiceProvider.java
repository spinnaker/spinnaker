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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.debian;

import com.netflix.spinnaker.halyard.config.config.v1.ArtifactSourcesConfig;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.resource.v1.StringReplaceJarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.LocalServiceProvider;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LocalDebianServiceProvider extends LocalServiceProvider {
  @Autowired private ArtifactSourcesConfig artifactSourcesConfig;

  @Autowired private ArtifactService artifactService;

  @Autowired LocalDebianClouddriverService clouddriverService;

  @Autowired LocalDebianDeckService deckService;

  @Autowired LocalDebianEchoService echoService;

  @Autowired LocalDebianFiatService fiatService;

  @Autowired LocalDebianFront50Service front50Service;

  @Autowired LocalDebianGateService gateService;

  @Autowired LocalDebianIgorService igorService;

  @Autowired LocalDebianKayentaService kayentaService;

  @Autowired LocalDebianMonitoringDaemonService monitoringDaemonService;

  @Autowired LocalDebianOrcaService orcaService;

  @Autowired LocalDebianRedisService redisService;

  @Autowired LocalDebianRoscoService roscoService;

  @Override
  public String getInstallCommand(
      DeploymentDetails deploymentDetails,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      Map<String, String> installCommands) {
    Map<String, Object> bindings = new HashMap<>();
    List<SpinnakerService.Type> serviceTypes =
        new ArrayList<>(installCommands.keySet())
            .stream().map(SpinnakerService.Type::fromCanonicalName).collect(Collectors.toList());
    List<String> upstartNames =
        getLocalServices(serviceTypes).stream()
            .filter(i -> resolvedConfiguration.getServiceSettings(i.getService()).getEnabled())
            .map(i -> ((LocalDebianService) i).getUpstartServiceName())
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

    resource = new StringReplaceJarResource("/debian/install.sh");
    bindings = new HashMap<>();
    bindings.put("prepare-environment", "true");
    bindings.put("install-redis", "true");
    bindings.put(
        "debian-repository",
        artifactSourcesConfig.mergeWithBomSources(artifactSources).getDebianRepository());
    bindings.put("install-commands", String.join("\n", serviceInstalls));
    bindings.put("service-action", "restart");
    bindings.put("upstart-init", upstartInit);

    return resource.setBindings(bindings).toString();
  }

  @Override
  public RemoteAction clean(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {
    String uninstallArtifacts =
        String.join(
            "\n",
            getServices().stream()
                .filter(s -> s != null && runtimeSettings.getServiceSettings(s).getEnabled())
                .map(s -> ((LocalDebianService) s).uninstallArtifactCommand())
                .collect(Collectors.toList()));

    Map<String, Object> bindings = new HashMap<>();
    TemplatedResource resource = new StringReplaceJarResource("/debian/uninstall.sh");
    bindings.put("uninstall-artifacts", uninstallArtifacts);

    return new RemoteAction()
        .setScript(resource.setBindings(bindings).toString())
        .setAutoRun(true)
        .setScriptDescription(
            "This script apt-get purges all spinnaker components & deletes their config");
  }
}
