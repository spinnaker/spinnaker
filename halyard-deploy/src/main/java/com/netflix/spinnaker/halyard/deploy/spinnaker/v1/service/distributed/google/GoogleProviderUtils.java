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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.google;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Firewall;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Operation;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import lombok.Data;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;

class GoogleProviderUtils {
  // Map from service -> the port & job managing the connection.
  private static ConcurrentHashMap<String, Proxy> proxyMap = new ConcurrentHashMap<>();

  static String defaultServiceAccount(AccountDeploymentDetails<GoogleAccount> details) {
    GoogleAccount account = details.getAccount();
    String project = account.getProject();
    Compute compute = getCompute(details);

    try {
      return compute.projects().get(project).execute().getDefaultServiceAccount();
    } catch (IOException e) {
      throw new HalException(FATAL, "Unable to get default compute service account");
    }
  }

  static URI openSshTunnel(AccountDeploymentDetails<GoogleAccount> details, String instanceName, ServiceSettings service) {
    int port = service.getPort();
    String key = Proxy.buildKey(details.getDeploymentName(), instanceName, port);

    Proxy proxy = proxyMap.getOrDefault(key, new Proxy());
    JobExecutor jobExecutor = DaemonTaskHandler.getTask().getJobExecutor();

    if (proxy.getJobId() == null || !jobExecutor.jobExists(proxy.getJobId())) {
      proxy.setPort(port);
      List<String> command = buildGcloudComputeCommand(details);

      command.add("ssh");
      command.add("--zone=" + service.getLocation());
      command.add(instanceName);
      command.add("--");
      command.add("-N");
      command.add("-L");
      command.add(String.format("%d:localhost:%d", port, port));
      JobRequest request = new JobRequest().setTokenizedCommand(command);

      DaemonTaskHandler.message("Opening port " + port + " against instance " + instanceName);
      proxy.setJobId(jobExecutor.startJob(request));

      // Wait for the proxy to spin up.
      DaemonTaskHandler.safeSleep(TimeUnit.SECONDS.toMillis(5));

      JobStatus status = jobExecutor.updateJob(proxy.jobId);

      // This should be a long-running job.
      if (status.getState() == JobStatus.State.COMPLETED) {
        throw new HalException(Problem.Severity.FATAL,
            "Unable to establish a proxy against account " + instanceName
                + ":\n" + status.getStdOut() + "\n" + status.getStdErr());
      }

      proxyMap.put(key, proxy);
    } else {
      DaemonTaskHandler.message("Reusing existing SSH tunnel");
    }

    try {
      return new URIBuilder()
          .setScheme("http")
          .setHost("localhost")
          .setPort(port)
          .build();
    } catch (URISyntaxException e) {
      throw new RuntimeException("Failed to build URI for SSH connection", e);
    }
  }

  static void waitOnZoneOperation(Compute compute, String project, String zone, Operation operation) throws IOException {
    waitOnOperation(() -> {
      try {
        return compute.zoneOperations().get(project, zone, operation.getName()).execute();
      } catch (IOException e) {
        throw new HalException(FATAL, "Operation failed: " + e);
      }
    });
  }

  static void waitOnGlobalOperation(Compute compute, String project, Operation operation) throws IOException {
    waitOnOperation(() -> {
      try {
        return compute.globalOperations().get(project, operation.getName()).execute();
      } catch (IOException e) {
        throw new HalException(FATAL, "Operation failed: " + e);
      }
    });
  }

  static void waitOnOperation(Supplier<Operation> operationSupplier) {
    Operation operation = operationSupplier.get();
    while (!operation.getStatus().equals("DONE")) {
      if (operation.getError() != null) {
        throw new HalException(FATAL, String.join("\n", operation.getError()
            .getErrors()
            .stream()
            .map(e -> e.getCode() + ": " + e.getMessage()).collect(Collectors.toList())));
      }
      operation = operationSupplier.get();
      DaemonTaskHandler.safeSleep(TimeUnit.SECONDS.toMillis(1));
    }
  }

  static String ensureSpinnakerNetworkExists(AccountDeploymentDetails<GoogleAccount> details) {
    String networkName = "spinnaker-hal";
    String project = details.getAccount().getProject();

    Compute compute = getCompute(details);
    boolean exists = true;
    try {
      compute.networks().get(project, networkName).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        exists = false;
      } else {
        throw new HalException(FATAL, "Google error encountered retrieving network: " + e.getMessage(), e);
      }
    } catch (IOException e) {
      throw new HalException(FATAL, "Failed to check if spinnaker network exists: " + e.getMessage(), e);
    }

    if (!exists) {
      String networkUrl = null;
      Network network = new Network()
          .setAutoCreateSubnetworks(true)
          .setName(networkName)
          .setDescription("Spinnaker network auto-created by Halyard");

      try {
        DaemonTaskHandler.message("Creating a spinnaker network...");
        Operation operation = compute.networks().insert(project, network).execute();
        networkUrl = operation.getTargetLink();
        GoogleProviderUtils.waitOnGlobalOperation(compute, project, operation);
      } catch (IOException e) {
        throw new HalException(FATAL, "Failed to create Spinnaker network: " + e.getMessage(), e);
      }

      Firewall.Allowed allowSsh = new Firewall.Allowed()
          .setPorts(Collections.singletonList("22"))
          .setIPProtocol("tcp");

      Firewall firewallSsh = new Firewall()
          .setNetwork(networkUrl)
          .setAllowed(Collections.singletonList(allowSsh))
          .setName(networkName + "-allow-ssh")
          .setSourceRanges(Collections.singletonList("0.0.0.0/0"));

      Firewall.Allowed allowInternalTcp = new Firewall.Allowed()
          .setPorts(Collections.singletonList("1-65535"))
          .setIPProtocol("tcp");

      Firewall.Allowed allowInternalUdp = new Firewall.Allowed()
          .setPorts(Collections.singletonList("1-65535"))
          .setIPProtocol("udp");

      Firewall.Allowed allowInternalIcmp = new Firewall.Allowed()
          .setIPProtocol("icmp");

      List<Firewall.Allowed> allowInteral = new ArrayList<>();
      allowInteral.add(allowInternalTcp);
      allowInteral.add(allowInternalUdp);
      allowInteral.add(allowInternalIcmp);

      Firewall firewallInternal = new Firewall()
          .setNetwork(networkUrl)
          .setAllowed(allowInteral)
          .setName(networkName + "-allow-internal")
          .setSourceRanges(Collections.singletonList("10.0.0.0/8"));

      try {
        DaemonTaskHandler.message("Adding firewall rules...");
        compute.firewalls().insert(project, firewallSsh).execute();
        compute.firewalls().insert(project, firewallInternal).execute();
      } catch (IOException e) {
        throw new HalException(FATAL, "Failed to create Firewall rule network: " + e.getMessage(), e);
      }
    }

    return String.format("projects/%s/global/networks/%s", project, networkName);
  }

  static Compute getCompute(AccountDeploymentDetails<GoogleAccount> details) {
    ConfigProblemSetBuilder problemSetBuilder = new ConfigProblemSetBuilder(null);
    GoogleNamedAccountCredentials credentials = details.getAccount().getNamedAccountCredentials("", problemSetBuilder);

    if (credentials == null) {
      throw new HalException(problemSetBuilder.build().getProblems());
    }

    return credentials.getCompute();
  }

  static List<String> buildGcloudComputeCommand(AccountDeploymentDetails<GoogleAccount> details) {
    List<String> result = new ArrayList<>();
    result.add("gcloud");
    result.add("compute");
    result.add("--project=" + details.getAccount().getProject());
    return result;
  }

  @Data
  static class Proxy {
    String jobId = "";
    Integer port;

    static String buildKey(String deployment, String instance, int port) {
      return String.format("%d:%s:%s:%d", Thread.currentThread().getId(), deployment, instance, port);
    }
  }
}
