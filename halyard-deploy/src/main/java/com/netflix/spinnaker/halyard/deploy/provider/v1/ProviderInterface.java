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
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.OrcaService.Orca;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceInterfaceFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerMonitoringDaemonService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A ProviderInterface is an abstraction for communicating with a specific cloud-provider's installation
 * of Spinnaker.
 */
@Component
public abstract class ProviderInterface<T extends Account> {
  @Autowired
  protected JobExecutor jobExecutor;

  @Autowired
  protected ServiceInterfaceFactory serviceInterfaceFactory;

  @Autowired
  protected String spinnakerOutputPath;

  @Autowired
  protected String spinnakerOutputDependencyPath;

  abstract public ProviderType getProviderType();

  /**
   * @param details are the deployment details for the current deployment.
   * @param artifact is the artifact who's version to fetch.
   * @return the docker image/debian package/etc... for a certain profile.
   */
  abstract protected String componentArtifact(AccountDeploymentDetails<T> details, SpinnakerArtifact artifact);

  abstract public <S> S connectTo(AccountDeploymentDetails<T> details, SpinnakerService<S> service);

  abstract public String connectToCommand(AccountDeploymentDetails<T> details, SpinnakerService service);

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
    DaemonTaskHandler.newStage("Deploying " + service.getArtifact().getName());
    boolean update = serviceExists(details, service);
    Supplier<String> idSupplier;
    if (!update) {
      Map<String, Object> task = upsertLoadBalancerTask(details, service);
      idSupplier = () -> orca.submitTask(task).get("ref");
      DaemonTaskHandler.log("Upserting " + artifactName + " load balancer");
      monitorOrcaTask(idSupplier, orca);
    }

    Map<String, Object> pipeline = deployServerGroupPipeline(details, service, monitoringService, update);
    idSupplier = () -> orca.orchestrate(pipeline).get("ref");
    DaemonTaskHandler.log("Orchestrating " + artifactName + " deployment");
    monitorOrcaTask(idSupplier, orca);
  }

  abstract protected Map<String, Object> upsertLoadBalancerTask(AccountDeploymentDetails<T> details, SpinnakerService service);

  abstract protected Map<String, Object> deployServerGroupPipeline(AccountDeploymentDetails<T> details, SpinnakerService service, SpinnakerMonitoringDaemonService monitoringService, boolean update);

  /**
   * Creates a service only if it isn't running yet. This is useful for dealing with dependent services that can't
   * go down (e.g. redis, consul).
   * @param details are the deployment details for the current deployment.
   * @param service is the service to ensure is running.
   */
  abstract public void ensureServiceIsRunning(AccountDeploymentDetails<T> details, SpinnakerService service);

  abstract public boolean serviceExists(AccountDeploymentDetails<T> details, SpinnakerService service);

  /**
   * Bootstrap the necessary services required to deploy the rest of Spinnaker.
   * @param details are the deployment details for the current deployment.
   * @param services is the full set of Spinnaker services to ultimately deploy.
   */
  abstract public void bootstrapSpinnaker(AccountDeploymentDetails<T> details, SpinnakerEndpoints.Services services);
  abstract public RunningServiceDetails getRunningServiceDetails(AccountDeploymentDetails<T> details, SpinnakerService service);

  protected Map<String, Object> monitorOrcaTask(Supplier<String> task, Orca orca) {
    Map<String, Object> pipeline;
    String status;
    try {
      String id = task.get();
      if (id.startsWith("/")) {
        id = id.substring(1);
      }

      pipeline = orca.getRef(id);
      status = (String) pipeline.get("status");
      while (status.equalsIgnoreCase("running") || status.equalsIgnoreCase("not_started")) {
        try {
          Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException ignored) {
        }
        pipeline = orca.getRef(id);
        status = (String) pipeline.get("status");
      }
    } catch (RetrofitError e) {
      throw new HalException(new ProblemBuilder(Problem.Severity.FATAL, "Failed to monitor task: " + e.getMessage()).build());
    }

    if (status.equalsIgnoreCase("terminal")) {
      throw new HalException(new ProblemBuilder(Problem.Severity.FATAL, "Pipeline failed").build());
    }

    return pipeline;
  }
}
