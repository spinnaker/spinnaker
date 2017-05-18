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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.RedisService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedLogCollector;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Component
@Data
public class KubernetesRedisService extends RedisService implements KubernetesDistributedService<Jedis> {
  @Delegate
  @Autowired
  KubernetesDistributedServiceDelegate distributedServiceDelegate;

  @Delegate(excludes = HasServiceSettings.class)
  public DistributedLogCollector getLogCollector() {
    return getLogCollectorFactory().build(this);
  }

  @Override
  public Jedis connectToPrimaryService(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerRuntimeSettings runtimeSettings) {
    ServiceSettings settings = runtimeSettings.getServiceSettings(this);
    List<String> command = Arrays.stream(connectCommand(details, runtimeSettings).split(" ")).collect(Collectors.toList());
    JobRequest request = new JobRequest().setTokenizedCommand(command);
    String jobId = getJobExecutor().startJob(request);
    // Wait for the proxy to spin up.

    DaemonTaskHandler.safeSleep(TimeUnit.SECONDS.toMillis(5));

    JobStatus status = getJobExecutor().updateJob(jobId);

    // This should be a long-running job.
    if (status.getState() == JobStatus.State.COMPLETED) {
      throw new HalException(Problem.Severity.FATAL,
          "Unable to establish a proxy against Redis:\n" + status.getStdOut()
              + "\n" + status.getStdErr());
    }

    return new Jedis("localhost", settings.getPort());
  }

  @Override
  public Settings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    Settings settings = new Settings();
    String location = "spinnaker";
    settings.setAddress(buildAddress(location))
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setLocation(location)
        .setEnabled(true);
    return settings;
  }

  public String getArtifactId(String deploymentName) {
    return "gcr.io/kubernetes-spinnaker/redis-cluster:v2";
  }

  final DeployPriority deployPriority = new DeployPriority(5);
  final boolean requiredToBootstrap = false;
}
