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

import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesImageDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesConfigParser;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.config.services.v1.LookupService;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints.Service;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.SpinnakerProfile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.endpoint.EndpointType;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobStatus.Result;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobStatus.State;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class KubernetesProviderInterface extends ProviderInterface<KubernetesAccount> {
  @Value("${deploy.kubernetes.minPollSeconds:1}")
  private int MIN_POLL_INTERVAL_SECONDS;

  @Value("${deploy.kubernetes.maxPollSeconds:16}")
  private int MAX_POLL_INTERVAL_SECONDS;

  @Value("${deploy.kubernetes.pollTimeout:10}")
  private int TIMEOUT_MINUTES;

  @Value("${deploy.kubernetes.registry:gcr.io/spinnaker-marketplace}")
  private String REGISTRY;

  @Autowired
  LookupService lookupService;

  private static final String CLOUDRIVER_CONFIG_PATH = "/kubernetes/raw/hal-clouddriver.yml";

  // Map from deployment name -> the port & job managing the connection.
  private ConcurrentHashMap<String, Proxy> proxyMap = new ConcurrentHashMap<>();

  @Data
  private static class Proxy {
    String jobId;
    Integer port;
  }

  @Override
  protected String componentArtifact(DeploymentDetails<KubernetesAccount> details, SpinnakerArtifact artifact) {
    NodeFilter filter = new NodeFilter().withAnyHalconfigFile().setDeployment(details.getDeploymentName());
    String version = artifactService.getArtifactVersion(filter, artifact);

    KubernetesImageDescription image = new KubernetesImageDescription(artifact.name(), version, REGISTRY);
    return KubernetesUtil.getImageId(image);
  }

  @Override
  public Object connectTo(DeploymentDetails<KubernetesAccount> details, EndpointType endpointType) {
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

    Service service = endpointType.getService(details.getEndpoints());

    String endpoint = "http://localhost:" + proxy.getPort() + "/api/v1/proxy/namespaces/"
        + getNamespaceFromAddress(service.getAddress()) + "/services/"
        + getServiceFromAddress(service.getAddress()) + ":" + service.getPort() + "/";

    return serviceFactory.createService(endpoint, endpointType);
  }

  @Override
  public void bootstrapClouddriver(DeploymentDetails<KubernetesAccount> details) {
    KubernetesAccount account = details.getAccount();

    Service clouddriver = EndpointType.CLOUDDRIVER.getService(details.getEndpoints());
    String namespace = getNamespaceFromAddress(clouddriver.getAddress());
    String serviceName = getServiceFromAddress(clouddriver.getAddress());
    String replicaSetName = "spin-clouddriver-v000";
    String credsSecret = "hal-creds-config";
    String clouddriverSecret = componentSecret(EndpointType.CLOUDDRIVER.getName());
    int clouddriverPort = clouddriver.getPort();

    KubernetesClient client = getClient(account);
    if (client.namespaces().withName(namespace).get() == null) {
      client.namespaces().create(new NamespaceBuilder()
          .withNewMetadata()
          .withName(namespace)
          .endMetadata()
          .build());
    }

    Map<String, String> serviceSelector = new HashMap<>();
    serviceSelector.put("load-balancer-" + serviceName, "true");

    Map<String, String> replicaSetSelector = new HashMap<>();
    replicaSetSelector.put("server-group", replicaSetName);

    Map<String, String> podLabels = new HashMap();
    podLabels.putAll(replicaSetSelector);
    podLabels.putAll(serviceSelector);

    ServiceBuilder serviceBuilder = new ServiceBuilder();
    serviceBuilder = serviceBuilder
        .withNewMetadata()
        .withName(serviceName)
        .withNamespace(namespace)
        .endMetadata()
        .withNewSpec()
        .withSelector(serviceSelector)
        .withPorts(new ServicePortBuilder().withPort(clouddriverPort).build())
        .endSpec();

    client.services().inNamespace(namespace).create(serviceBuilder.build());

    ContainerBuilder containerBuilder = new ContainerBuilder();

    containerBuilder = containerBuilder
        .withName(SpinnakerArtifact.CLOUDDRIVER.name())
        .withImage(componentArtifact(details, SpinnakerArtifact.CLOUDDRIVER))
        .withPorts(new ContainerPortBuilder().withContainerPort(clouddriverPort).build());

    ReplicaSetBuilder replicaSetBuilder = new ReplicaSetBuilder();

    replicaSetBuilder = replicaSetBuilder
        .withNewMetadata()
        .withName(replicaSetName)
        .withNamespace(namespace)
        .endMetadata()
        .withNewSpec()
        .withReplicas(1)
        .withNewSelector()
        .withMatchLabels(replicaSetSelector)
        .endSelector()
        .withNewTemplate()
        .withNewMetadata()
        .withLabels(podLabels)
        .endMetadata()
        .withNewSpec()
        .withContainers(containerBuilder.build())
        .endSpec()
        .endTemplate()
        .endSpec();

    client.extensions().replicaSets().inNamespace(namespace).create(replicaSetBuilder.build());
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

  private String componentSecret(String name) {
    return "hal-" + name + "-config";
  }

  private void stageCredentials(DeploymentDetails<KubernetesAccount> details, String secretName, String namespace) {
    NodeFilter accountFilter = new NodeFilter().withAnyHalconfigFile()
        .setDeployment(details.getDeploymentName())
        .withAnyProvider()
        .withAnyAccount();

    List<Node> accounts = lookupService.getMatchingNodesOfType(accountFilter, Account.class);

    List<String> files = accounts.stream()
        .map(a -> a.localFiles().stream().map(f -> {
          f.setAccessible(true);
          try {
            return (String) f.get(a);
          } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get local files for account " + a.getNodeName(), e);
          }
        }).collect(Collectors.toList()))
        .reduce(new ArrayList<>(), (a, b) -> {
          a.addAll(b);
          return a;
        })
        .stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    createSecret(details, files, secretName, namespace);
  }

  private void stageConfig(DeploymentDetails<KubernetesAccount> details, String namespace) {
    File outputPath = new File(spinnakerOutputPath);
    File[] profiles = outputPath.listFiles();
    spinnakerProfiles.forEach(s -> {
      String name = componentSecret(s.getProfileName());
      createSecret(details, s.getArtifact().profilePaths(profiles), name, namespace);
    });
  }

  private void createSecret(DeploymentDetails<KubernetesAccount> details, List<String> files, String secretName, String namespace) {
    List<String> command = kubectlAccountCommand(details.getAccount());

    command.add("create");
    command.add("secret");
    command.add("generic");
    command.add(secretName);
    command.add("--namespace=" + namespace);

    files.forEach(f -> command.add("from-file=" + f));

    JobRequest request = new JobRequest()
        .setTokenizedCommand(command)
        .setTimeoutMillis(TimeUnit.MINUTES.toMillis(TIMEOUT_MINUTES));

    String jobId = jobExecutor.startJob(request);

    JobStatus jobStatus = jobExecutor.backoffWait(jobId,
        TimeUnit.SECONDS.toMillis(MIN_POLL_INTERVAL_SECONDS),
        TimeUnit.SECONDS.toMillis(MAX_POLL_INTERVAL_SECONDS));

    if (jobStatus.getResult() == Result.FAILURE) {
      throw new HalconfigException(new ProblemBuilder(Severity.FATAL,
          "Unable to create secret " + secretName + ":\n"
              + jobStatus.getStdOut() + "\n"
              + jobStatus.getStdErr()).build());
    }
  }

  private KubernetesClient getClient(KubernetesAccount account) {
    Config config = KubernetesConfigParser.parse(account.getKubeconfigFile(),
        account.getContext(),
        account.getCluster(),
        account.getUser(),
        account.getNamespaces());

    return new DefaultKubernetesClient(config);
  }
}
