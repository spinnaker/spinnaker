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
 */

package com.netflix.spinnaker.halyard.deploy.provider.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider.ProviderType;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.OrcaService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.OrcaService.Orca;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceInterfaceFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerMonitoringDaemonService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A ProviderInterface is an abstraction for communicating with a specific cloud-provider's installation
 * of Spinnaker.
 */
@Component
public abstract class ProviderInterface<T extends Account> {
  @Value("${deploy.maxRemainingServerGroups:2}")
  protected Integer MAX_REMAINING_SERVER_GROUPS;

  @Autowired
  protected JobExecutor jobExecutor;

  @Autowired
  protected ServiceInterfaceFactory serviceInterfaceFactory;

  @Autowired
  protected String spinnakerOutputPath;

  @Autowired
  protected String spinnakerOutputDependencyPath;

  @Autowired
  private OrcaRunner orcaRunner;

  abstract public ProviderType getProviderType();

  /**
   * @param details are the deployment details for the current deployment.
   * @param artifact is the artifact who's version to fetch.
   * @return the docker image/debian package/etc... for a certain profile.
   */
  abstract protected String componentArtifact(AccountDeploymentDetails<T> details, SpinnakerArtifact artifact);

  abstract public <S> S connectTo(AccountDeploymentDetails<T> details, SpinnakerService<S> service);
  abstract public String connectToCommand(AccountDeploymentDetails<T> details, SpinnakerService service);

  abstract protected Map<String, Object> upsertLoadBalancerStage(AccountDeploymentDetails<T> details, SpinnakerService service);
  abstract protected Map<String, Object> deployServerGroupPipeline(AccountDeploymentDetails<T> details, SpinnakerService service, SpinnakerMonitoringDaemonService monitoringService, boolean update);

  /**
   * Creates a service only if it isn't running yet. This is useful for dealing with dependent services that can't
   * go down (e.g. redis, consul).
   * @param details are the deployment details for the current deployment.
   * @param service is the service to ensure is running.
   */
  abstract public void ensureServiceIsRunning(AccountDeploymentDetails<T> details, SpinnakerService service);
  abstract public boolean serviceExists(AccountDeploymentDetails<T> details, SpinnakerService service);
  abstract public void deleteServerGroup(AccountDeploymentDetails<T> details, SpinnakerService service, String serverGroupName);

  /**
   * Bootstrap the necessary services required to deploy the rest of Spinnaker.
   * @param details are the deployment details for the current deployment.
   * @param services is the full set of Spinnaker services to ultimately deploy.
   */
  abstract public void bootstrapSpinnaker(AccountDeploymentDetails<T> details, SpinnakerEndpoints.Services services);
  abstract public RunningServiceDetails getRunningServiceDetails(AccountDeploymentDetails<T> details, SpinnakerService service);

  abstract protected String getServerGroupFromInstanceId(AccountDeploymentDetails<T> details, SpinnakerService service, String instanceId);

  /**
   * Deploy a service using Orca's orchestration engine.
   * @param details are the deployment details for the current deployment.
   * @param orca is the instance of orca used to orchestrate the deployment.
   * @param endpoints are the endpoints spinnaker is conforming to.
   * @param name is the service being deployed.
   */
  public void deployService(AccountDeploymentDetails<T> details, Orca orca, SpinnakerEndpoints endpoints, String name) {
    SpinnakerService service = endpoints.getService(name);
    SpinnakerMonitoringDaemonService monitoringService = endpoints.getServices().getMonitoringDaemon();
    String artifactName = service.getArtifact().getName();
    boolean update = serviceExists(details, service);
    Supplier<String> idSupplier;
    if (!update) {
      Map<String, Object> task = upsertLoadBalancerStage(details, service);
      DaemonTaskHandler.newStage("Upserting " + artifactName + " load balancer");
      DaemonTaskHandler.log("Submitting upsert task of " + artifactName + " load balancer");
      idSupplier = () -> orca.submitTask(task).get("ref");
      orcaRunner.monitorTask(idSupplier, orca);
    }

    Map<String, Object> pipeline = deployServerGroupPipeline(details, service, monitoringService, update);
    DaemonTaskHandler.newStage("Orchestrating " + artifactName + " deployment");
    DaemonTaskHandler.log("Submitting deploy task of " + artifactName + " server group");
    idSupplier = () -> orca.orchestrate(pipeline).get("ref");
    orcaRunner.monitorPipeline(idSupplier, orca);
  }

  public void reapOrcaServerGroups(AccountDeploymentDetails<T> details, OrcaService orcaService) {
    Orca orca = connectTo(details, orcaService);
    Map<String, Orca.ActiveExecutions> executions = orca.getActiveExecutions();

    Map<String, Integer> executionsByServerGroup = new HashMap<>();

    // Record the total number of executions in each pool of orcas.
    executions.forEach((s, e) -> {
      String instanceName = s.split("@")[1];
      String serverGroupName = getServerGroupFromInstanceId(details, orcaService, instanceName);
      int count = executionsByServerGroup.getOrDefault(serverGroupName, 0);
      count += e.getCount();
      executionsByServerGroup.put(serverGroupName, count);
    });

    // Omit the last deployed orcas from being deleted, since they are kept around for rollbacks.
    List<String> allOrcas = new ArrayList<>(executionsByServerGroup.keySet());
    allOrcas.sort(String::compareTo);

    int orcaCount = allOrcas.size();
    if (orcaCount <= MAX_REMAINING_SERVER_GROUPS) {
      return;
    }

    allOrcas = allOrcas.subList(0, orcaCount - MAX_REMAINING_SERVER_GROUPS);
    for (String orcaName : allOrcas) {
      // TODO(lwander) consult clouddriver to ensure this orca isn't enabled
      if (executionsByServerGroup.get(orcaName) == 0) {
        DaemonTaskHandler.log("Reaping old orca instance " + orcaName);
        deleteServerGroup(details, orcaService, orcaName);
      }
    }
  }

}
