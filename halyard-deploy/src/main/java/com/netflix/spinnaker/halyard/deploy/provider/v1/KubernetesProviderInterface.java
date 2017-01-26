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

import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.config.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.config.spinnaker.v1.SpinnakerEndpoints.Service;
import com.netflix.spinnaker.halyard.deploy.component.v1.ComponentType;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobStatus.Result;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobStatus.State;
import com.netflix.spinnaker.halyard.deploy.resource.v1.JarResource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class KubernetesProviderInterface extends ProviderInterface<KubernetesAccount> {
  @Value("${deploy.kubernetes.minPollSeconds:1}")
  private int MIN_POLL_INTERVAL_SECONDS;

  @Value("${deploy.kubernetes.maxPollSeconds:16}")
  private int MAX_POLL_INTERVAL_SECONDS;

  @Value("${deploy.kubernetes.pollTimeout:10}")
  private int TIMEOUT_MINUTES;
  
  private static final String CLOUDRIVER_CONFIG_PATH = "/kubernetes/raw/hal-clouddriver.yml";

  // Map from deployment name -> the port & job managing the connection.
  private ConcurrentHashMap<String, Proxy> proxyMap = new ConcurrentHashMap<>();

  @Data
  private static class Proxy {
    String jobId;
    Integer port;
  }

  @Override
  public Object connectTo(DeploymentDetails<KubernetesAccount> details, ComponentType componentType) {
    Proxy proxy = proxyMap.getOrDefault(details.getDeploymentName(), new Proxy());

    if (proxy.jobId == null || proxy.jobId.isEmpty()) {
      List<String> command = kubectlAccountCommand(details.getAccount());
      command.add("proxy");
      command.add("--port=0"); // select a random port
      JobRequest request = new JobRequest().setTokenizedCommand(command);

      proxy.jobId = jobExecutor.startJob(request);

      // Wait for the proxy to spin up.
      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      } catch (InterruptedException ignored) {
      }

      JobStatus status = jobExecutor.updateJob(proxy.jobId);

      // This should be a long-running job.
      if (status.getState() == State.COMPLETED) {
        throw new HalconfigException(new ProblemBuilder(Severity.FATAL,
            "Unable to establish a proxy against account " + details.getAccount().getName()
            + ":\n" + status.getStdOut() + "\n" + status.getStdErr()).build());
      }

      String connectionMessage = status.getStdOut();
      Pattern portPattern = Pattern.compile(":(\\d+)");
      Matcher matcher = portPattern.matcher(connectionMessage);
      if (matcher.find()) {
        log.info("Connecting to " + details.getAccount().getName() + " on port " + matcher.group(1));
        proxy.setPort(Integer.valueOf(matcher.group(1)));
      } else {
        throw new HalconfigException(new ProblemBuilder(Severity.FATAL,
            "Could not parse connection information from:\n" + connectionMessage).build());
      }
    }

    Service service = componentType.getService(details.getEndpoints());

    String endpoint = "http://localhost:" + proxy.getPort() + "/api/v1/proxy/namespaces/"
        + getNamespaceFromAddress(service.getAddress()) + "/services/"
        + getServiceFromAddress(service.getAddress()) + ":" + service.getPort() + "/";

    return serviceFactory.createService(endpoint, componentType);
  }

  @Override
  public void bootstrapClouddriver(DeploymentDetails<KubernetesAccount> details) {
    KubernetesAccount account = details.getAccount();
    List<String> command = kubectlAccountCommand(account);

    Service clouddriver = ComponentType.CLOUDDRIVER.getService(details.getEndpoints());

    // kubectl [--flags] create namespace {%namespace%}
    command.add("create");
    command.add("namespace");
    command.add(getNamespaceFromAddress(clouddriver.getAddress()));

    JobRequest request = new JobRequest()
        .setTokenizedCommand(command)
        .setTimeoutMillis(TimeUnit.MINUTES.toMillis(TIMEOUT_MINUTES));

    String jobId = jobExecutor.startJob(request);

    JobStatus jobStatus = jobExecutor.backoffWait(jobId,
        TimeUnit.SECONDS.toMillis(MIN_POLL_INTERVAL_SECONDS),
        TimeUnit.SECONDS.toMillis(MAX_POLL_INTERVAL_SECONDS));

    if (jobStatus.getResult() == Result.FAILURE && !jobStatus.getStdErr().contains("already exists")) {
      throw new HalconfigException(new
          ProblemBuilder(Severity.FATAL, "Unable to bootstrap clouddriver:\n" + jobStatus.getStdErr()).build());
    }

    command = kubectlAccountCommand(account);
    // kubectl [--flags] create -f -
    // reads a resource definition from stdin
    command.add("create");
    command.add("-f");
    command.add("-");

    request = new JobRequest()
        .setTokenizedCommand(command)
        .setTimeoutMillis(TimeUnit.MINUTES.toMillis(TIMEOUT_MINUTES));

    Map<String, String> bindings = new HashMap<>();
    bindings.put("namespace", getNamespaceFromAddress(clouddriver.getAddress()));
    bindings.put("service-name", getServiceFromAddress(clouddriver.getAddress()));
    bindings.put("component-name", "spin-clouddriver-v000");
    bindings.put("component-type", ComponentType.CLOUDDRIVER.name());
    bindings.put("port", Integer.toString(clouddriver.getPort()));

    TemplatedResource bootstrap = new JarResource(CLOUDRIVER_CONFIG_PATH).setBindings(bindings);

    InputStream cloudddriverConfig = new ByteArrayInputStream(bootstrap.toString().getBytes());

    jobId = jobExecutor.startJob(request, System.getenv(), cloudddriverConfig);

    jobStatus = jobExecutor.backoffWait(jobId,
        TimeUnit.SECONDS.toMillis(MIN_POLL_INTERVAL_SECONDS),
        TimeUnit.SECONDS.toMillis(MAX_POLL_INTERVAL_SECONDS));

    if (jobStatus.getResult() == Result.FAILURE) {
      throw new HalconfigException(new
          ProblemBuilder(Severity.FATAL, "Unable to bootstrap clouddriver:\n" + jobStatus.getStdErr()).build());
    }
  }

  private List<String> kubectlAccountCommand(KubernetesAccount account) {
    List<String> command = new ArrayList<>();
    command.add("kubectl");

    String context = account.getContext();
    if (context != null && !context.isEmpty()) {
      command.add("--context");
      command.add(context);
    }

    String cluster = account.getCluster();
    if (cluster != null && !cluster.isEmpty()) {
      command.add("--cluster");
      command.add(cluster);
    }

    String user = account.getUser();
    if (user != null && !user.isEmpty()) {
      command.add("--user");
      command.add(user);
    }

    String kubeconfig = account.getKubeconfigFile();
    if (kubeconfig != null && !kubeconfig.isEmpty()) {
      command.add("--kubeconfig");
      command.add(kubeconfig);
    }
    return command;
  }

  private String getServiceFromAddress(String address) {
    return parseAddressEntry(address, 0);
  }

  private String getNamespaceFromAddress(String address) {
    return parseAddressEntry(address, 1);
  }

  private String parseAddressEntry(String address, int index) {
    if (index < 0 || index > 1) {
      throw new IllegalArgumentException("Index must be in the range [0, 1]");
    }

    String[] split = address.split("\\.");
    if (split.length != 2) {
      throw new IllegalArgumentException("Address \"" + address + "\" is formatted incorrectly. It should be <service>.<namespace>");
    }

    return split[index];

  }
}
