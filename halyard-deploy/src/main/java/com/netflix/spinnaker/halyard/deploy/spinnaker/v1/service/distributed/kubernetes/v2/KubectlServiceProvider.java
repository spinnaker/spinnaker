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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2;

import com.netflix.spinnaker.halyard.config.model.v1.ha.HaServices;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.KubernetesSharedServiceSettings;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class KubectlServiceProvider extends SpinnakerServiceProvider<AccountDeploymentDetails<KubernetesAccount>> {
  @Autowired
  @Qualifier("kubernetesV2ClouddriverService")
  KubernetesV2ClouddriverService clouddriverService;

  @Autowired
  KubernetesV2ClouddriverCachingService clouddriverCachingService;

  @Autowired
  KubernetesV2ClouddriverRoService clouddriverRoService;

  @Autowired
  KubernetesV2ClouddriverRwService clouddriverRwService;

  @Autowired
  KubernetesV2DeckService deckService;

  @Autowired
  @Qualifier("kubernetesV2EchoService")
  KubernetesV2EchoService echoService;

  @Autowired
  KubernetesV2EchoSchedulerService echoSchedulerService;

  @Autowired
  KubernetesV2EchoSlaveService echoSlaveService;

  @Autowired
  KubernetesV2FiatService fiatService;

  @Autowired
  KubernetesV2Front50Service front50Service;

  @Autowired
  KubernetesV2GateService gateService;

  @Autowired
  KubernetesV2IgorService igorService;

  @Autowired
  KubernetesV2KayentaService kayentaService;

  @Autowired
  KubernetesV2MonitoringDaemonService monitoringDaemonService;

  @Autowired
  KubernetesV2OrcaService orcaService;

  @Autowired
  KubernetesV2RedisService redisService;

  @Autowired
  KubernetesV2RoscoService roscoService;

  @Override
  public RemoteAction clean(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerRuntimeSettings runtimeSettings) {
    DaemonTaskHandler.newStage("Invoking kubectl");
    DaemonTaskHandler.message("Deleting all 'svc,deploy,secret' resources with label 'app=spin'...");
    KubernetesSharedServiceSettings kubernetesSharedServiceSettings = new KubernetesSharedServiceSettings(details.getDeploymentConfiguration());
    KubernetesV2Utils.deleteSpinnaker(details.getAccount(), kubernetesSharedServiceSettings.getDeployLocation());
    return new RemoteAction();
  }

  public List<KubernetesV2Service> getServicesByPriority(List<SpinnakerService.Type> serviceTypes) {
    List<KubernetesV2Service> result = getFieldsOfType(KubernetesV2Service.class).stream()
        .filter(d -> serviceTypes.contains(d.getService().getType()))
        .sorted((d1, d2) -> d2.getDeployPriority().compareTo(d1.getDeployPriority()))
        .collect(Collectors.toList());

    return result;
  }

  public KubernetesV2Service getService(SpinnakerService.Type type) {
    return getService(type, Object.class);
  }

  public <S> KubernetesV2Service getService(SpinnakerService.Type type, Class<S> clazz) {
    Field serviceField = getField(type.getCanonicalName() + "service");
    if (serviceField == null) {
      return null;
    }

    serviceField.setAccessible(true);
    try {
      return (KubernetesV2Service) serviceField.get(this);
    } catch (IllegalAccessException e) {
      throw new HalException(Problem.Severity.FATAL, "Can't access service field for " + type + ": " + e.getMessage());
    } finally {
      serviceField.setAccessible(false);
    }
  }}
