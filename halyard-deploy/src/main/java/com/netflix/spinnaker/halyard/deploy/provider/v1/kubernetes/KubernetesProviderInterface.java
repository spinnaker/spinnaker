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

package com.netflix.spinnaker.halyard.deploy.provider.v1.kubernetes;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesImageDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesConfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
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
import com.netflix.spinnaker.halyard.deploy.provider.v1.OperationFactory.ConfigSource;
import com.netflix.spinnaker.halyard.deploy.provider.v1.ProviderInterface;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.OrcaService.Orca;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.RedisService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerPublicService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.halyard.config.model.v1.node.Provider.ProviderType.KUBERNETES;

@Component
@Slf4j
public class KubernetesProviderInterface extends ProviderInterface<KubernetesAccount> {
  @Value("${deploy.kubernetes.minPollSeconds:1}")
  private int MIN_POLL_INTERVAL_SECONDS;

  @Value("${deploy.kubernetes.maxPollSeconds:16}")
  private int MAX_POLL_INTERVAL_SECONDS;

  @Value("${deploy.kubernetes.pollTimeout:10}")
  private int TIMEOUT_MINUTES;

  @Value("${spinnaker.artifacts.docker:gcr.io/spinnaker-marketplace}")
  private String REGISTRY;

  @Value("${deploy.kubernetes.config.dir:/opt/spinnaker/config}")
  private String CONFIG_MOUNT;

  @Autowired
  LookupService lookupService;

  @Autowired
  KubernetesOperationFactory kubernetesOperationFactory;

  // Map from deployment name -> the port & job managing the connection.
  private ConcurrentHashMap<String, Proxy> proxyMap = new ConcurrentHashMap<>();

  @Data
  private static class Proxy {
    String jobId;
    Integer port;
  }

  @Override
  public Provider.ProviderType getProviderType() {
    return KUBERNETES;
  }

  @Override
  protected String componentArtifact(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerArtifact artifact) {
    switch (artifact) {
      case REDIS:
        return "gcr.io/kubernetes-spinnaker/redis-cluster:v2";
      default:
        String version = details.getGenerateResult().getArtifactVersions().get(artifact);

        KubernetesImageDescription image = new KubernetesImageDescription(artifact.getName(), version, REGISTRY);
        return KubernetesUtil.getImageId(image);
    }
  }

  @Override
  public <T> T connectTo(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerService<T> service) {
    KubernetesAccount account = details.getAccount();
    DaemonTaskHandler.newStage("Connecting to the Kubernetes cluster in account \"" + account.getName() + "\"");
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

    String endpoint = "http://localhost:" + proxy.getPort() + "/api/v1/proxy/namespaces/"
        + getNamespaceFromAddress(service.getAddress()) + "/services/"
        + getServiceFromAddress(service.getAddress()) + ":" + service.getPort() + "/";

    log.info("Connecting to " + service.getAddress() + " on port " + proxy.getPort());
    return serviceInterfaceFactory.createService(endpoint, service);
  }

  private List<ConfigSource> configSources(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerService service) {
    String namespace = getNamespaceFromAddress(service.getAddress());
    SpinnakerArtifact artifact = service.getArtifact();
    List<Pair<VolumeMount, Volume>> volumes = new ArrayList<>();
    volumes.add(stageDependencies(details, namespace, artifact.getName()));
    volumes.add(stageProfile(details, namespace, artifact));
    return volumes.stream().map(this::fromVolumePair).collect(Collectors.toList());
  }

  @Override
  public void deployService(AccountDeploymentDetails<KubernetesAccount> details, Orca orca, SpinnakerService service) {
    String artifactName = service.getArtifact().getName();
    DaemonTaskHandler.newStage("Deploying " + service.getArtifact().getName());
    String accountName = details.getAccount().getName();
    SpinnakerArtifact artifact = service.getArtifact();
    List<ConfigSource> configSources = configSources(details, service);
    Map<String, Object> task = kubernetesOperationFactory.createUpsertPipeline(accountName, service);
    Supplier<String> idSupplier = () -> orca.submitTask(task).get("ref");
    DaemonTaskHandler.log("Upserting " + artifactName + " load balancer");
    monitorOrcaTask(idSupplier, orca);

    Map<String, Object> pipeline = kubernetesOperationFactory.createDeployPipeline(accountName, service, componentArtifact(details, artifact), configSources);
    idSupplier = () -> orca.orchestrate(pipeline).get("ref");
    DaemonTaskHandler.log("Orchestrating " + artifactName + " red/black deployment");
    monitorOrcaTask(idSupplier, orca);
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

  private String bootstrapService(AccountDeploymentDetails<KubernetesAccount> details,
      SpinnakerService service,
      boolean recreate,
      String image,
      List<Pair<VolumeMount, Volume>> volumes,
      Map<String, String> env) {
    String namespace = getNamespaceFromAddress(service.getAddress());
    String serviceName = getServiceFromAddress(service.getAddress());
    String replicaSetName = serviceName + "-v000";
    DaemonTaskHandler.log("Deploying service " + serviceName);
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

    boolean serviceExists = false;
    if (client.services().inNamespace(namespace).withName(serviceName).get() != null) {
      if (recreate) {
        client.services().inNamespace(namespace).withName(serviceName).delete();
      } else {
        serviceExists = true;
      }
    }

    if (!serviceExists) {
      client.services().inNamespace(namespace).create(serviceBuilder.build());
    }

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

    boolean serverExists = false;
    if (client.extensions().replicaSets().inNamespace(namespace).withName(replicaSetName).get() != null) {
      if (recreate) {
        client.extensions().replicaSets().inNamespace(namespace).withName(replicaSetName).delete();
      } else {
        serverExists = true;
      }
    }

    if (!serverExists) {
      RunningServiceDetails serviceDetails = getRunningServiceDetails(details, service);
      while (serviceDetails.getHealthy() != 0) {
        try {
          Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException ignored) {
        }
        serviceDetails = getRunningServiceDetails(details, service);
      }

      client.extensions().replicaSets().inNamespace(namespace).create(replicaSetBuilder.build());
    }

    return replicaSetName;
  }

  private String bootstrapService(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerService service, boolean recreate) {
    SpinnakerArtifact artifact = service.getArtifact();
    String namespace = getNamespaceFromAddress(service.getAddress());
    List<Pair<VolumeMount, Volume>> volumes = new ArrayList<>();
    volumes.add(stageDependencies(details, namespace, artifact.getName()));
    volumes.add(stageProfile(details, namespace, artifact));
    Map<String, String> env = service.getEnv();

    if (!service.getProfiles().isEmpty()) {
      env.put(artifact.getName().toUpperCase() + "_OPTS", "-Dspring.profiles.active=" + service.getProfiles().stream().reduce((a, b) -> a + "," + b).get());
    }
    return bootstrapService(details, service, recreate, componentArtifact(details, artifact), volumes, env);
  }

  @Override
  public void ensureRedisIsRunning(AccountDeploymentDetails<KubernetesAccount> details, RedisService redisService) {
    bootstrapService(details, redisService, false);
  }

  @Override
  public void bootstrapSpinnaker(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerEndpoints.Services services) {
    bootstrapService(details, services.getRedisBootstrap(), true);
    bootstrapService(details, services.getClouddriverBootstrap(), true);
    bootstrapService(details, services.getOrcaBootstrap(), true);
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

  static String getServiceFromAddress(String address) {
    return parseAddressEntry(address, 0);
  }

  static String getNamespaceFromAddress(String address) {
    return parseAddressEntry(address, 1);
  }

  static private String parseAddressEntry(String address, int index) {
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

  private String componentDependencies(String name) {
    return "hal-" + name + "-dependencies";
  }

  private Pair<VolumeMount, Volume> stageProfile(AccountDeploymentDetails<KubernetesAccount> details,
      String namespace,
      SpinnakerArtifact artifact) {
    File outputPath = new File(spinnakerOutputPath);
    File[] profiles = outputPath.listFiles();
    String secretName = componentSecret(artifact.getName());
    upsertSecret(details, artifact.profilePaths(profiles), secretName, namespace);
    return buildVolumePair(secretName, CONFIG_MOUNT);
  }

  private Pair<VolumeMount, Volume> stageDependencies(AccountDeploymentDetails<KubernetesAccount> details,
      String namespace,
      String profileName) {
    Set<String> requiredFiles = details
        .getGenerateResult()
        .getProfileRequirements()
        .getOrDefault(profileName, new HashSet<>());
    String secretName = componentDependencies(profileName);
    upsertSecret(details, requiredFiles, secretName, namespace);
    return buildVolumePair(secretName, spinnakerOutputDependencyPath);
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

  private ConfigSource fromVolumePair(Pair<VolumeMount, Volume> volumePair) {
    ConfigSource res = new ConfigSource();
    Volume volume = volumePair.getRight();
    VolumeMount volumeMount = volumePair.getLeft();
    res.setId(volume.getSecret().getSecretName());
    res.setMountPoint(volumeMount.getMountPath());
    return res;
  }

  private void upsertSecret(AccountDeploymentDetails<KubernetesAccount> details, Set<String> files, String secretName, String namespace) {
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

  @Override
  public RunningServiceDetails getRunningServiceDetails(AccountDeploymentDetails<KubernetesAccount> details, SpinnakerService service) {
    RunningServiceDetails res = new RunningServiceDetails();
    if (service instanceof SpinnakerPublicService) {
      res.setPublicService((SpinnakerPublicService) service);
    } else {
      res.setService(service);
    }

    KubernetesClient client = getClient(details.getAccount());
    String name = KubernetesProviderInterface.getServiceFromAddress(service.getAddress());
    String namespace = KubernetesProviderInterface.getNamespaceFromAddress(service.getAddress());

    List<Pod> pods = client.pods().inNamespace(namespace).withLabel("load-balancer-" + name, "true").list().getItems();

    int count = (int) pods
        .stream()
        .filter(p -> p
            .getStatus()
            .getContainerStatuses()
            .stream()
            .allMatch(c -> c.getReady() && c.getState().getRunning() != null && c.getState().getTerminated() == null))
        .count();

    res.setHealthy(count);

    return res;
  }
}
