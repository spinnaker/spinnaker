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

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Firewall;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Operation;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskInterrupted;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.util.SocketUtils;

@Slf4j
class GoogleProviderUtils {
  // Map from service -> the port & job managing the connection.
  private static ConcurrentHashMap<String, Proxy> proxyMap = new ConcurrentHashMap<>();

  static String getNetworkName() {
    return "spinnaker-hal";
  }

  private static String getSshKeyFile() {
    return Paths.get(System.getProperty("user.home"), ".ssh/google_compute_halyard").toString();
  }

  private static String getSshPublicKeyFile() {
    return getSshKeyFile() + ".pub";
  }

  private static int connectRetries = 5;
  private static int openSshRetries = 10;

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

  private static void ensureSshKeysExist() {
    File sshKeyFile = new File(getSshKeyFile());
    if (!sshKeyFile.exists()) {
      if (!sshKeyFile.getParentFile().exists()) {
        sshKeyFile.getParentFile().mkdirs();
      }

      log.info("Generating a new ssh key file...");
      JobExecutor jobExecutor = DaemonTaskHandler.getJobExecutor();
      List<String> command = new ArrayList<>();
      command.add("ssh-keygen");
      command.add("-N"); // no password
      command.add("");
      command.add("-t"); // rsa key
      command.add("rsa");
      command.add("-f"); // path to keyfile
      command.add(getSshKeyFile());
      command.add("-C"); // username sshing into machine
      command.add("ubuntu");

      JobRequest request = new JobRequest().setTokenizedCommand(command);

      JobStatus status;
      try {
        status = jobExecutor.backoffWait(jobExecutor.startJob(request));
      } catch (InterruptedException e) {
        throw new DaemonTaskInterrupted(e);
      }

      if (status.getResult() == JobStatus.Result.FAILURE) {
        throw new HalException(FATAL, "ssh-keygen failed: " + status.getStdErr());
      }

      try {
        File sshPublicKeyFile = new File(getSshPublicKeyFile());
        String sshKeyContents = IOUtils.toString(new FileInputStream(sshPublicKeyFile));

        if (!sshKeyContents.startsWith("ubuntu:")) {
          sshKeyContents = "ubuntu:" + sshKeyContents;
          FileUtils.writeByteArrayToFile(sshPublicKeyFile, sshKeyContents.getBytes());
        }
      } catch (IOException e) {
        throw new HalException(
            FATAL,
            "Cannot reformat ssh key to match google key format expectation: " + e.getMessage(),
            e);
      }

      command = new ArrayList<>();
      command.add("chmod");
      command.add("400");
      command.add(getSshKeyFile());

      request = new JobRequest().setTokenizedCommand(command);
      try {
        status = jobExecutor.backoffWait(jobExecutor.startJob(request));
      } catch (InterruptedException e) {
        throw new DaemonTaskInterrupted(e);
      }

      if (status.getResult() == JobStatus.Result.FAILURE) {
        throw new HalException(FATAL, "chmod failed: " + status.getStdErr() + status.getStdOut());
      }
    }
  }

  public static String getSshPublicKey() {
    ensureSshKeysExist();

    try {
      return IOUtils.toString(new FileInputStream(getSshPublicKeyFile()));
    } catch (IOException e) {
      throw new HalException(FATAL, "Failed to read ssh public key: " + e.getMessage(), e);
    }
  }

  private static Proxy openSshTunnel(String ip, int port, String keyFile)
      throws InterruptedException {
    JobExecutor jobExecutor = DaemonTaskHandler.getJobExecutor();
    List<String> command = new ArrayList<>();

    // Make sure we don't have an entry for this host already (GCP recycles IPs).
    command.add("ssh-keygen");
    command.add("-R");
    command.add(ip);
    JobRequest request = new JobRequest().setTokenizedCommand(command);
    JobStatus status = jobExecutor.backoffWait(jobExecutor.startJob(request));

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      if (status.getStdErr().contains("No such file")) {
        log.info("No ssh known_hosts file exists yet");
      } else {
        throw new HalException(FATAL, "Unable to remove old host entry " + status.getStdErr());
      }
    }

    int localPort = SocketUtils.findAvailableTcpPort();

    command.clear();
    command.add("ssh");
    command.add("ubuntu@" + ip);
    command.add("-o");
    command.add("StrictHostKeyChecking=no");
    command.add("-i");
    command.add(keyFile);
    command.add("-N");
    command.add("-L");
    command.add(String.format("%d:localhost:%d", localPort, port));
    request = new JobRequest().setTokenizedCommand(command);

    String jobId = jobExecutor.startJob(request);

    status = jobExecutor.updateJob(jobId);

    while (status == null) {
      DaemonTaskHandler.safeSleep(TimeUnit.SECONDS.toMillis(1));
      status = jobExecutor.updateJob(jobId);
    }

    return new Proxy().setJobId(jobId).setPort(localPort);
  }

  private static void closeSshTunnel(Proxy proxy) {
    JobExecutor jobExecutor = DaemonTaskHandler.getJobExecutor();
    jobExecutor.cancelJob(proxy.getJobId());
  }

  private static boolean checkIfProxyIsOpen(Proxy proxy) {
    boolean connected = false;
    int tries = 0;
    int port = proxy.getPort();

    while (!connected && tries < connectRetries) {
      tries++;

      try {
        log.info("Attempting to connect to localhost:" + port + "...");
        Socket testSocket = new Socket("localhost", port);
        log.info("Connection opened");

        connected = testSocket.isConnected();
        testSocket.close();
      } catch (IOException e) {
        DaemonTaskHandler.safeSleep(TimeUnit.SECONDS.toMillis(5));
      }
    }

    return connected;
  }

  static URI openSshTunnel(
      AccountDeploymentDetails<GoogleAccount> details, String instanceName, ServiceSettings service)
      throws InterruptedException {
    int port = service.getPort();
    String key = Proxy.buildKey(details.getDeploymentName(), instanceName, port);

    Proxy proxy = proxyMap.getOrDefault(key, new Proxy());
    JobExecutor jobExecutor = DaemonTaskHandler.getJobExecutor();

    if (proxy.getJobId() == null || !jobExecutor.jobExists(proxy.getJobId())) {
      String ip = getInstanceIp(details, instanceName);
      String keyFile = getSshKeyFile();
      log.info("Opening port " + port + " against instance " + instanceName);
      boolean connected = false;
      int tries = 0;

      while (!connected && tries < openSshRetries) {
        tries++;
        proxy = openSshTunnel(ip, port, keyFile);
        connected = checkIfProxyIsOpen(proxy);

        if (!connected) {
          if (!jobExecutor.jobExists(proxy.jobId)
              || jobExecutor.updateJob(proxy.jobId).getState() == JobStatus.State.COMPLETED) {
            log.warn("SSH tunnel closed prematurely");
          }

          log.info(
              "SSH tunnel never opened, retrying in case the instance hasn't started yet... ("
                  + tries
                  + "/"
                  + openSshRetries
                  + ")");
          closeSshTunnel(proxy);
          DaemonTaskHandler.safeSleep(TimeUnit.SECONDS.toMillis(10));
        }
      }

      if (!connected) {
        JobStatus status = jobExecutor.updateJob(proxy.getJobId());
        throw new HalException(
            FATAL, "Unable to connect to instance " + instanceName + ": " + status.getStdErr());
      }

      proxyMap.put(key, proxy);
    }

    try {
      return new URIBuilder()
          .setScheme("http")
          .setHost("localhost")
          .setPort(proxy.getPort())
          .build();
    } catch (URISyntaxException e) {
      throw new RuntimeException("Failed to build URI for SSH connection", e);
    }
  }

  static void waitOnZoneOperation(Compute compute, String project, String zone, Operation operation)
      throws IOException {
    waitOnOperation(
        () -> {
          try {
            return compute.zoneOperations().get(project, zone, operation.getName()).execute();
          } catch (IOException e) {
            throw new HalException(FATAL, "Operation failed: " + e);
          }
        });
  }

  static void waitOnGlobalOperation(Compute compute, String project, Operation operation)
      throws IOException {
    waitOnOperation(
        () -> {
          try {
            return compute.globalOperations().get(project, operation.getName()).execute();
          } catch (IOException e) {
            throw new HalException(FATAL, "Operation failed: " + e);
          }
        });
  }

  private static void waitOnOperation(Supplier<Operation> operationSupplier) {
    Operation operation = operationSupplier.get();
    while (!operation.getStatus().equals("DONE")) {
      if (operation.getError() != null) {
        throw new HalException(
            FATAL,
            String.join(
                "\n",
                operation.getError().getErrors().stream()
                    .map(e -> e.getCode() + ": " + e.getMessage())
                    .collect(Collectors.toList())));
      }
      operation = operationSupplier.get();
      DaemonTaskHandler.safeSleep(TimeUnit.SECONDS.toMillis(1));
    }
  }

  static String ensureSpinnakerNetworkExists(AccountDeploymentDetails<GoogleAccount> details) {
    String networkName = getNetworkName();
    String project = details.getAccount().getProject();

    Compute compute = getCompute(details);
    boolean exists = true;
    try {
      compute.networks().get(project, networkName).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        exists = false;
      } else {
        throw new HalException(
            FATAL, "Google error encountered retrieving network: " + e.getMessage(), e);
      }
    } catch (IOException e) {
      throw new HalException(
          FATAL, "Failed to check if spinnaker network exists: " + e.getMessage(), e);
    }

    if (!exists) {
      String networkUrl;
      Network network =
          new Network()
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

      Firewall.Allowed allowSsh =
          new Firewall.Allowed().setPorts(Collections.singletonList("22")).setIPProtocol("tcp");

      Firewall firewallSsh =
          new Firewall()
              .setNetwork(networkUrl)
              .setAllowed(Collections.singletonList(allowSsh))
              .setName(networkName + "-allow-ssh")
              .setSourceRanges(Collections.singletonList("0.0.0.0/0"));

      Firewall.Allowed allowInternalTcp =
          new Firewall.Allowed()
              .setPorts(Collections.singletonList("1-65535"))
              .setIPProtocol("tcp");

      Firewall.Allowed allowInternalUdp =
          new Firewall.Allowed()
              .setPorts(Collections.singletonList("1-65535"))
              .setIPProtocol("udp");

      Firewall.Allowed allowInternalIcmp = new Firewall.Allowed().setIPProtocol("icmp");

      List<Firewall.Allowed> allowInteral = new ArrayList<>();
      allowInteral.add(allowInternalTcp);
      allowInteral.add(allowInternalUdp);
      allowInteral.add(allowInternalIcmp);

      Firewall firewallInternal =
          new Firewall()
              .setNetwork(networkUrl)
              .setAllowed(allowInteral)
              .setName(networkName + "-allow-internal")
              .setSourceRanges(Collections.singletonList("10.0.0.0/8"));

      try {
        DaemonTaskHandler.message("Adding firewall rules...");
        compute.firewalls().insert(project, firewallSsh).execute();
        compute.firewalls().insert(project, firewallInternal).execute();
      } catch (IOException e) {
        throw new HalException(
            FATAL, "Failed to create Firewall rule network: " + e.getMessage(), e);
      }
    }

    return String.format("projects/%s/global/networks/%s", project, networkName);
  }

  public static void resize(
      AccountDeploymentDetails<GoogleAccount> details,
      String zone,
      String managedInstaceGroupName,
      int targetSize) {
    Compute compute = getCompute(details);
    try {
      compute
          .instanceGroupManagers()
          .resize(details.getAccount().getProject(), zone, managedInstaceGroupName, targetSize);
    } catch (IOException e) {
      throw new HalException(
          FATAL,
          "Unable to resize instance group manager "
              + managedInstaceGroupName
              + ": "
              + e.getMessage(),
          e);
    }
  }

  static Compute getCompute(AccountDeploymentDetails<GoogleAccount> details) {
    ConfigProblemSetBuilder problemSetBuilder = new ConfigProblemSetBuilder(null);
    GoogleNamedAccountCredentials credentials =
        details.getAccount().getNamedAccountCredentials("", null, problemSetBuilder);

    if (credentials == null) {
      throw new HalException(problemSetBuilder.build().getProblems());
    }

    return credentials.getCompute();
  }

  static String getInstanceIp(
      AccountDeploymentDetails<GoogleAccount> details, String instanceName) {
    Compute compute = getCompute(details);
    Instance instance = null;
    try {
      instance =
          compute
              .instances()
              .get(details.getAccount().getProject(), "us-central1-f", instanceName)
              .execute();
    } catch (IOException e) {
      throw new HalException(FATAL, "Unable to get instance " + instanceName);
    }

    return instance.getNetworkInterfaces().stream()
        .map(
            i ->
                i.getAccessConfigs().stream()
                    .map(AccessConfig::getNatIP)
                    .filter(ip -> !StringUtils.isEmpty(ip))
                    .findFirst())
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst()
        .orElseThrow(() -> new HalException(FATAL, "No public IP associated with" + instanceName));
  }

  @Data
  static class Proxy {
    String jobId = "";
    Integer port;

    static String buildKey(String deployment, String instance, int port) {
      return String.format(
          "%d:%s:%s:%d", Thread.currentThread().getId(), deployment, instance, port);
    }
  }
}
