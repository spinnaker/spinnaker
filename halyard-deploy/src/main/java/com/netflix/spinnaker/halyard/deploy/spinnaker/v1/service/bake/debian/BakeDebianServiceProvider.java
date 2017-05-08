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

import com.netflix.spinnaker.halyard.config.config.v1.ArtifactSources;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.resource.v1.JarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.bake.BakeServiceProvider;
import io.fabric8.utils.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class BakeDebianServiceProvider extends BakeServiceProvider {
  @Autowired
  private ArtifactSources artifactSources;

  @Autowired
  BakeDebianClouddriverService clouddriverService;

  @Autowired
  BakeDebianConsulClientService consulClientService;

  @Autowired
  BakeDebianConsulServerService consulServerService;

  @Autowired
  BakeDebianDeckService deckService;

  @Autowired
  BakeDebianEchoService echoService;

  @Autowired
  BakeDebianFiatService fiatService;

  @Autowired
  BakeDebianFront50Service front50Service;

  @Autowired
  BakeDebianGateService gateService;

  @Autowired
  BakeDebianIgorService igorService;

  @Autowired
  BakeDebianMonitoringDaemonService monitoringDaemonService;

  @Autowired
  BakeDebianOrcaService orcaService;

  @Autowired
  BakeDebianRedisService redisService;

  @Autowired
  BakeDebianRoscoService roscoService;

  @Autowired
  BakeDebianVaultClientService vaultClientService;

  @Autowired
  BakeDebianVaultServerService vaultServerService;

  @Autowired
  String startupScriptPath;

  @Override
  public String getInstallCommand(GenerateService.ResolvedConfiguration resolvedConfiguration, Map<String, String> installCommands, String startupCommand) {
    Map<String, String> bindings = new HashMap<>();
    List<String> serviceNames = new ArrayList<>(installCommands.keySet());
    List<String> upstartNames = getPrioritizedBakeableServices(serviceNames)
        .stream()
        .filter(i -> resolvedConfiguration.getServiceSettings(i.getService()).isEnabled())
        .map(i -> ((BakeDebianService) i).getUpstartServiceName())
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    List<String> serviceInstalls = serviceNames.stream()
        .map(installCommands::get)
        .collect(Collectors.toList());

    TemplatedResource resource = new JarResource("/debian/init.sh");
    bindings.put("services", Strings.join(upstartNames, " "));
    String upstartInit = resource.setBindings(bindings).toString();

    resource = new JarResource("/debian/pre-bake.sh");
    bindings = new HashMap<>();
    bindings.put("debian-repository", artifactSources.getDebianRepository());
    bindings.put("install-commands", String.join("\n", serviceInstalls));
    bindings.put("upstart-init", upstartInit);
    bindings.put("startup-file", Paths.get(startupScriptPath, "startup.sh").toString());
    bindings.put("startup-command", startupCommand);

    return resource.setBindings(bindings).toString();
  }

  @Override
  public RemoteAction clean(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {
    throw new HalException(Problem.Severity.FATAL, "Bakeable services do not support being uninstalled.");
  }
}
