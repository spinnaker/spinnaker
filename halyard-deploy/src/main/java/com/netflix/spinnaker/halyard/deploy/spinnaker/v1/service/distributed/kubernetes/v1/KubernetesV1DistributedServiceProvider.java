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
 *
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v1;

import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.KubernetesSharedServiceSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesV1DistributedServiceProvider extends DistributedServiceProvider<KubernetesAccount> {
  @Autowired
  KubernetesV1ClouddriverBootstrapService clouddriverBootstrapService;

  @Autowired
  KubernetesV1ClouddriverService clouddriverService;

  @Autowired
  KubernetesV1DeckService deckService;

  @Autowired
  KubernetesV1EchoService echoService;

  @Autowired
  KubernetesV1FiatService fiatService;

  @Autowired
  KubernetesV1Front50Service front50Service;

  @Autowired
  KubernetesV1GateService gateService;

  @Autowired
  KubernetesV1IgorService igorService;

  @Autowired
  KubernetesV1KayentaService kayentaService;

  @Autowired
  KubernetesV1MonitoringDaemonService monitoringDaemonService;

  @Autowired
  KubernetesV1OrcaBootstrapService orcaBootstrapService;

  @Autowired
  KubernetesV1OrcaService orcaService;

  @Autowired
  KubernetesV1RedisBootstrapService redisBootstrapService;

  @Autowired
  KubernetesV1RedisService redisService;

  @Autowired
  KubernetesV1RoscoService roscoService;

  // For serialization
  public KubernetesV1DistributedServiceProvider() {}

  @Override
  public RemoteAction clean(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerRuntimeSettings runtimeSettings) {
    KubernetesSharedServiceSettings kubernetesSharedServiceSettings = new KubernetesSharedServiceSettings(details.getDeploymentConfiguration());
    KubernetesV1ProviderUtils.kubectlDeleteNamespaceCommand(DaemonTaskHandler.getJobExecutor(), details, kubernetesSharedServiceSettings.getDeployLocation());
    return new RemoteAction();
  }
}
