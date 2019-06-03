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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.git;

import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.resource.v1.StringReplaceJarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.LocalServiceProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// TODO(lwander): Add monitoring daemon support.
@Component
public class LocalGitServiceProvider extends LocalServiceProvider {
  @Autowired private ArtifactService artifactService;

  @Autowired LocalGitClouddriverService clouddriverService;

  @Autowired LocalGitDeckService deckService;

  @Autowired LocalGitEchoService echoService;

  @Autowired LocalGitFiatService fiatService;

  @Autowired LocalGitFront50Service front50Service;

  @Autowired LocalGitGateService gateService;

  @Autowired LocalGitIgorService igorService;

  @Autowired LocalGitKayentaService kayentaService;

  @Autowired LocalGitOrcaService orcaService;

  @Autowired LocalGitRoscoService roscoService;

  @Autowired LocalGitRedisService redisService;

  @Override
  public String getPrepCommand(DeploymentDetails deploymentDetails, List<String> prepCommands) {
    String servicePrep = String.join("\n", prepCommands);

    TemplatedResource resource = new StringReplaceJarResource("/git/prep.sh");

    Map<String, Object> bindings = new HashMap<>();
    bindings.put("prep-commands", servicePrep);

    return resource.setBindings(bindings).toString();
  }

  @Override
  public String getInstallCommand(
      DeploymentDetails deploymentDetails,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      Map<String, String> installCommands) {
    Map<String, Object> bindings;
    List<SpinnakerService.Type> serviceTypes =
        new ArrayList<>(installCommands.keySet())
            .stream().map(SpinnakerService.Type::fromCanonicalName).collect(Collectors.toList());

    List<String> serviceInstalls =
        serviceTypes.stream()
            .map(t -> installCommands.get(t.getCanonicalName()))
            .collect(Collectors.toList());

    TemplatedResource resource = new StringReplaceJarResource("/git/install.sh");
    bindings = new HashMap<>();
    bindings.put("install-commands", String.join("\n", serviceInstalls));

    return resource.setBindings(bindings).toString();
  }

  @Override
  public RemoteAction clean(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {
    throw new UnsupportedOperationException();
  }

  public List<LocalGitService> getLocalGitServices(List<SpinnakerService.Type> serviceTypes) {
    return getFieldsOfType(LocalGitService.class).stream()
        .filter(s -> s != null && serviceTypes.contains(s.getService().getType()))
        .collect(Collectors.toList());
  }
}
