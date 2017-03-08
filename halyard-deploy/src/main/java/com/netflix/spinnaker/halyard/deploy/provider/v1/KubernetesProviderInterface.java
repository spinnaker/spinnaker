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

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesImageDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesConfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.LookupService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus.State;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints.Service;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.endpoint.EndpointType;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

  @Value("${deploy.kubernetes.registry:gcr.io/kubernetes-spinnaker}")
  private String REGISTRY;

  @Value("${deploy.kubernetes.config.dir:/opt/spinnaker/config}")
  private String CONFIG_MOUNT;

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
  protected String componentArtifact(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerArtifact artifact) {
    switch (artifact) {
      case REDIS:
        return "gcr.io/kubernetes-spinnaker/redis-cluster:v2";
      default:
        String version = details.getGenerateResult().getArtifactVersions().get(artifact);

        // TODO(lwander/jtk54) we need a published store of validated spinnaker images
        // KubernetesImageDescription image = new KubernetesImageDescription(artifact.getName(), version, REGISTRY);
        KubernetesImageDescription image = new KubernetesImageDescription(artifact.getName(), "latest", "quay.io/spinnaker");
        return KubernetesUtil.getImageId(image);
    }
  }

  @Override
  public Object connectTo(AccountDeploymentDetails<KubernetesAccount> details, EndpointType endpointType) {
    KubernetesAccount account = details.getAccount();
    DaemonTaskHandler.newStage("Connecting to the Kubernetes cluster in account \"" + details.getAccount() + "\"");
    Proxy proxy = proxyMap.getOrDefault(details.getDeploymentName(), new Proxy());
    if (proxy.jobId == null || proxy.jobId.isEmpty()) {
      List<String> command = kubectlAccountCommand(account);
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
        throw new HalException(new ConfigProblemBuilder(Severity.FATAL,
            "Unable to establish a proxy against account " + account.getName()
            + ":\n" + status.getStdOut() + "\n" + status.getStdErr()).build());
      }

      String connectionMessage = status.getStdOut();
      Pattern portPattern = Pattern.compile(":(\\d+)");
      Matcher matcher = portPattern.matcher(connectionMessage);
      if (matcher.find()) {
        proxy.setPort(Integer.valueOf(matcher.group(1)));
        proxyMap.put(details.getDeploymentName(), proxy);
        DaemonTaskHandler.log("Connected to kubernetes cluster for account " + account.getName() + " on port " + proxy.getPort());
      } else {
        throw new HalException(new ConfigProblemBuilder(Severity.FATAL,
            "Could not parse connection information from:\n" + connectionMessage).build());
      }
    }

    Service service = endpointType.getService(details.getEndpoints());

    String endpoint = "http://localhost:" + proxy.getPort() + "/api/v1/proxy/namespaces/"
        + getNamespaceFromAddress(service.getAddress()) + "/services/"
        + getServiceFromAddress(service.getAddress()) + ":" + service.getPort() + "/";

    log.info("Connecting to " + service.getAddress() + " on port " + proxy.getPort());
    return serviceFactory.createService(endpoint, endpointType);
  }

  private void createNamespace(KubernetesClient client, String namespace) {
    if (client.namespaces().withName(namespace).get() == null) {
      client.namespaces().create(new NamespaceBuilder()
          .withNewMetadata()
          .withName(namespace)
          .endMetadata()
          .build());
    }
  }

  public void deployService(AccountDeploymentDetails<KubernetesAccount> details,
                            Service service,
                            String image,
                            List<Pair<VolumeMount, Volume>> volumes,
                            Map<String, String> env) {
    String namespace = getNamespaceFromAddress(service.getAddress());
    String serviceName = getServiceFromAddress(service.getAddress());
    String replicaSetName = serviceName + "-v000";
    int port = service.getPort();

    KubernetesClient client = getClient(details.getAccount());
    createNamespace(client, namespace);

    Map<String, String> serviceSelector = new HashMap<>();
    serviceSelector.put("load-balancer-" + serviceName, "true");

    Map<String, String> replicaSetSelector = new HashMap<>();
    replicaSetSelector.put("server-group", replicaSetName);

    Map<String, String> podLabels = new HashMap<>();
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
        .withPorts(new ServicePortBuilder().withPort(port).build())
        .endSpec();

    if (client.services().inNamespace(namespace).withName(serviceName).get() != null) {
      client.services().inNamespace(namespace).withName(serviceName).delete();
    }

    client.services().inNamespace(namespace).create(serviceBuilder.build());

    List<EnvVar> envVars = env.entrySet().stream().map(e -> {
      EnvVarBuilder envVarBuilder = new EnvVarBuilder();
      return envVarBuilder.withName(e.getKey()).withValue(e.getValue()).build();
    }).collect(Collectors.toList());

    ProbeBuilder probeBuilder = new ProbeBuilder();

    if (service.getHttpHealth() != null) {
      probeBuilder = probeBuilder
          .withNewHttpGet()
          .withNewPort(port)
          .withPath(service.getHttpHealth())
          .endHttpGet();
    } else {
      probeBuilder = probeBuilder
          .withNewTcpSocket()
          .withNewPort()
          .withIntVal(port)
          .endPort()
          .endTcpSocket();
    }

    ContainerBuilder containerBuilder = new ContainerBuilder();

    containerBuilder = containerBuilder
        .withName(serviceName)
        .withImage(image)
        .withPorts(new ContainerPortBuilder().withContainerPort(port).build())
        .withVolumeMounts(volumes.stream().map(Pair::getLeft).collect(Collectors.toList()))
        .withEnv(envVars)
        .withReadinessProbe(probeBuilder.build());

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
        .withVolumes(volumes.stream().map(Pair::getRight).collect(Collectors.toList()))
        .endSpec()
        .endTemplate()
        .endSpec();

    if (client.extensions().replicaSets().inNamespace(namespace).withName(replicaSetName).get() != null) {
      client.extensions().replicaSets().inNamespace(namespace).withName(replicaSetName).delete();
    }

    client.extensions().replicaSets().inNamespace(namespace).create(replicaSetBuilder.build());
    DaemonTaskHandler.log("Deployed service " + serviceName);
  }

  public Map<String, String> specializeEnv(SpinnakerArtifact artifact) {
    Map<String, String> env = new HashMap<>();
    switch (artifact) {
      case REDIS:
        env.put("MASTER", "true");
        break;
      default:
        break;
    }
    return env;
  }

  @Override
  public void deployService(AccountDeploymentDetails<KubernetesAccount> details, Service service) {
    SpinnakerArtifact artifact = service.getArtifact();
    String namespace = getNamespaceFromAddress(service.getAddress());
    List<Pair<VolumeMount, Volume>> volumes = stageProfileDependencies(details, namespace, artifact.getName());
    volumes.add(stageProfile(details, namespace, artifact));
    Map<String, String> env = specializeEnv(artifact);
    deployService(details, service, componentArtifact(details, artifact), volumes, env);
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

  private List<Pair<VolumeMount, Volume>> stageProfileDependencies(AccountDeploymentDetails<KubernetesAccount> details, String namespace, String profileName) {
    List<String> requiredFiles = details
        .getGenerateResult()
        .getProfileRequirements()
        .get(profileName);
    List<Pair<VolumeMount, Volume>> result = new ArrayList<>();

    if (requiredFiles == null) {
      return result;
    }

    Map<String, List<String>> groupByDir = new HashMap<>();
    requiredFiles.forEach(s -> {
      File f = new File(s);
      List<String> files = groupByDir.getOrDefault(f.getParent(), new ArrayList<>());
      files.add(s);
      groupByDir.put(f.getParent(), files);
    });

    groupByDir.entrySet().forEach(e -> {
      String dirName = e.getKey();
      String secretName = String.join("-", "hal", profileName, dirName
          .replace(File.separator, "-")
          .replace(".", "-")
      );
      upsertSecret(details, e.getValue(), secretName, namespace);

      result.add(buildVolumePair(secretName, dirName));
    });

    return result;
  }

  private Pair<VolumeMount, Volume> stageProfile(AccountDeploymentDetails<KubernetesAccount> details, String namespace, SpinnakerArtifact artifact) {
    File outputPath = new File(spinnakerOutputPath);
    File[] profiles = outputPath.listFiles();
    String secretName = componentSecret(artifact.getName());
    upsertSecret(details, artifact.profilePaths(profiles), secretName, namespace);
    return buildVolumePair(secretName, CONFIG_MOUNT);
  }

  private Pair<VolumeMount, Volume> buildVolumePair(String secretName, String dirName) {
    VolumeMountBuilder volumeMountBuilder = new VolumeMountBuilder();
    volumeMountBuilder.withName(secretName)
        .withMountPath(dirName);

    VolumeBuilder volumeBuilder = new VolumeBuilder()
        .withNewSecret()
        .withSecretName(secretName)
        .endSecret()
        .withName(secretName);

    return new ImmutablePair<>(volumeMountBuilder.build(), volumeBuilder.build());
  }

  private void upsertSecret(AccountDeploymentDetails<KubernetesAccount> details, List<String> files, String secretName, String namespace) {
    KubernetesClient client = getClient(details.getAccount());
    createNamespace(client, namespace);

    if (client.secrets().inNamespace(namespace).withName(secretName).get() != null) {
      client.secrets().inNamespace(namespace).withName(secretName).delete();
    }

    Map<String, String> secretContents = new HashMap<>();

    files.forEach(s -> {
      try {
        File f = new File(s);
        String name = f.getName();
        String data = new String(Base64.getEncoder().encode(IOUtils.toString(new FileInputStream(f)).getBytes()));
        secretContents.putIfAbsent(name, data);
      } catch (IOException e) {
        throw new HalException(
            new ConfigProblemBuilder(Severity.ERROR, "Unable to read contents of \"" + s + "\": " + e).build()
        );
      }
    });

    SecretBuilder secretBuilder = new SecretBuilder();
    secretBuilder = secretBuilder.withNewMetadata()
        .withName(secretName)
        .withNamespace(namespace)
        .endMetadata()
        .withData(secretContents);

    log.info("Staging secret " + secretName + " in namespace " + namespace + " with contents " + files);

    client.secrets().inNamespace(namespace).create(secretBuilder.build());
  }

  private KubernetesClient getClient(KubernetesAccount account) {
    Config config = KubernetesConfigParser.parse(account.getKubeconfigFile(),
        account.getContext(),
        account.getCluster(),
        account.getUser(),
        account.getNamespaces(), 
        false);

    return new DefaultKubernetesClient(config);
  }
}
