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
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.ManagedInstance;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.ServiceAccount;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.model.GoogleDiskType;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskInterrupted;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService.ResolvedConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceInterfaceFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.VaultServerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.SidecarService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.VaultConfigMount;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.VaultConfigMountSet;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.VaultConnectionDetails;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.client.utils.URIBuilder;

public interface GoogleDistributedService<T> extends DistributedService<T, GoogleAccount> {
  GoogleVaultServerService getVaultServerService();

  GoogleConsulServerService getConsulServerService();

  ArtifactService getArtifactService();

  ServiceInterfaceFactory getServiceInterfaceFactory();

  String getGoogleImageProject(String deploymentName, SpinnakerArtifact artifact);

  String getStartupScriptPath();

  default String getDefaultInstanceType() {
    return "n1-highmem-2";
  }

  default String getHomeDirectory() {
    return "/home/spinnaker";
  }

  default String buildAddress() {
    return String.format("%s.service.spinnaker.consul", getService().getCanonicalName());
  }

  default String getEnvFile() {
    return "/etc/default/spinnaker";
  }

  default List<String> getScopes() {
    List<String> result = new ArrayList<>();
    result.add("https://www.googleapis.com/auth/devstorage.read_only");
    result.add("https://www.googleapis.com/auth/logging.write");
    result.add("https://www.googleapis.com/auth/monitoring.write");
    result.add("https://www.googleapis.com/auth/servicecontrol");
    result.add("https://www.googleapis.com/auth/service.management.readonly");
    result.add("https://www.googleapis.com/auth/trace.append");

    return result;
  }

  @Override
  default Provider.ProviderType getProviderType() {
    return Provider.ProviderType.GOOGLE;
  }

  @Override
  default void resizeVersion(
      AccountDeploymentDetails<GoogleAccount> details,
      ServiceSettings serviceSettings,
      int version,
      int targetSize) {
    String name = getVersionedName(version);
    String zone = serviceSettings.getLocation();
    GoogleProviderUtils.resize(details, zone, name, targetSize);
  }

  default String getStartupScript() {
    return String.join(
        "\n",
        "#!/usr/bin/env bash",
        "",
        "# AUTO-GENERATED BY HALYARD",
        "",
        getStartupScriptPath() + "startup.sh google");
  }

  default String getArtifactId(String deploymentName) {
    String artifactName = getArtifact().getName();
    String version = getArtifactService().getArtifactVersion(deploymentName, getArtifact());
    return String.format(
        "projects/%s/global/images/%s",
        getGoogleImageProject(deploymentName, getArtifact()),
        String.join("-", "spinnaker", artifactName, version.replace(".", "-").replace(":", "-")));
  }

  default VaultConnectionDetails buildConnectionDetails(
      AccountDeploymentDetails<GoogleAccount> details,
      SpinnakerRuntimeSettings runtimeSettings,
      String secretName) {
    GoogleVaultServerService vaultService = getVaultServerService();
    VaultServerService.Vault vault = vaultService.connectToPrimaryService(details, runtimeSettings);

    ServiceSettings vaultSettings = runtimeSettings.getServiceSettings(vaultService);
    RunningServiceDetails vaultDetails =
        vaultService.getRunningServiceDetails(details, runtimeSettings);
    Integer latestVaultVersion = vaultDetails.getLatestEnabledVersion();
    if (latestVaultVersion == null) {
      throw new IllegalStateException("No vault services have been started yet. This is a bug.");
    }

    List<RunningServiceDetails.Instance> instances =
        vaultDetails.getInstances().get(latestVaultVersion);
    if (instances.isEmpty()) {
      throw new IllegalStateException(
          "Current vault service has no running instances. This is a bug.");
    }

    String instanceId = instances.get(0).getId();
    String address =
        new URIBuilder()
            .setScheme("http")
            .setHost(instanceId)
            .setPort(vaultSettings.getPort())
            .toString();

    String token = vaultService.getToken(details.getDeploymentName(), vault);

    return new VaultConnectionDetails().setAddress(address).setSecret(secretName).setToken(token);
  }

  @Override
  default List<ConfigSource> stageProfiles(
      AccountDeploymentDetails<GoogleAccount> details,
      ResolvedConfiguration resolvedConfiguration) {
    String deploymentName = details.getDeploymentName();
    SpinnakerRuntimeSettings runtimeSettings = resolvedConfiguration.getRuntimeSettings();
    SpinnakerService thisService = getService();
    ServiceSettings thisServiceSettings = resolvedConfiguration.getServiceSettings(thisService);
    Map<String, String> env = new HashMap<>();
    Integer version = getRunningServiceDetails(details, runtimeSettings).getLatestEnabledVersion();
    if (version == null) {
      version = 0;
    } else {
      version++;
    }

    List<ConfigSource> configSources = new ArrayList<>();
    String stagingPath = getSpinnakerStagingPath(deploymentName);
    GoogleVaultServerService vaultService = getVaultServerService();
    VaultServerService.Vault vault = vaultService.connectToPrimaryService(details, runtimeSettings);

    for (SidecarService sidecarService : getSidecars(runtimeSettings)) {
      for (Profile profile :
          sidecarService.getSidecarProfiles(resolvedConfiguration, thisService)) {
        if (profile == null) {
          throw new HalException(
              Problem.Severity.FATAL,
              "Service "
                  + sidecarService.getService().getCanonicalName()
                  + " is required but was not supplied for deployment.");
        }

        String secretName = secretName(profile.getName(), version);
        String mountPoint = Paths.get(profile.getOutputFile()).toString();
        Path stagedFile = Paths.get(profile.getStagedFile(stagingPath));
        VaultConfigMount vaultConfigMount =
            VaultConfigMount.fromLocalFile(stagedFile.toFile(), mountPoint);
        secretName =
            vaultService.writeVaultConfig(deploymentName, vault, secretName, vaultConfigMount);

        configSources.add(new ConfigSource().setId(secretName).setMountPath(mountPoint));
      }
    }

    Map<String, Profile> serviceProfiles =
        resolvedConfiguration.getProfilesForService(thisService.getType());
    Set<String> requiredFiles = new HashSet<>();

    for (Map.Entry<String, Profile> entry : serviceProfiles.entrySet()) {
      Profile profile = entry.getValue();
      requiredFiles.addAll(profile.getRequiredFiles());
      env.putAll(profile.getEnv());

      String mountPoint = profile.getOutputFile();
      String secretName = secretName("profile-" + profile.getName(), version);
      Path stagedFile = Paths.get(profile.getStagedFile(stagingPath));
      VaultConfigMount vaultConfigMount =
          VaultConfigMount.fromLocalFile(stagedFile.toFile(), mountPoint);
      secretName =
          vaultService.writeVaultConfig(deploymentName, vault, secretName, vaultConfigMount);

      configSources.add(new ConfigSource().setId(secretName).setMountPath(mountPoint));
    }

    for (String file : requiredFiles) {
      String mountPoint = Paths.get(file).toString();
      String secretName = secretName("dependencies-" + file, version);
      VaultConfigMount vaultConfigMount =
          VaultConfigMount.fromLocalFile(Paths.get(file).toFile(), mountPoint);
      secretName =
          vaultService.writeVaultConfig(deploymentName, vault, secretName, vaultConfigMount);

      configSources.add(new ConfigSource().setId(secretName).setMountPath(mountPoint));
    }

    env.putAll(thisServiceSettings.getEnv());

    String envSourceFile =
        env.entrySet().stream()
            .reduce(
                "",
                (s, e) -> String.format("%s\n%s=%s", s, e.getKey(), e.getValue()),
                (s1, s2) -> String.join("\n", s1, s2));

    String mountPoint = getEnvFile();
    String secretName = secretName("env", version);
    VaultConfigMount vaultConfigMount = VaultConfigMount.fromString(envSourceFile, mountPoint);
    secretName = vaultService.writeVaultConfig(deploymentName, vault, secretName, vaultConfigMount);

    configSources.add(new ConfigSource().setId(secretName).setMountPath(mountPoint));

    return configSources;
  }

  default String secretName(String detail, int version) {
    return String.join(
        "-",
        "hal",
        getService().getType().getCanonicalName(),
        detail,
        version + "",
        RandomStringUtils.random(5, true, true));
  }

  default List<Metadata.Items> getMetadata(
      AccountDeploymentDetails<GoogleAccount> details,
      SpinnakerRuntimeSettings runtimeSettings,
      List<ConfigSource> configSources,
      Integer version) {
    List<Metadata.Items> metadataItems = new ArrayList<>();
    String deploymentName = details.getDeploymentName();

    Metadata.Items items =
        new Metadata.Items().setKey("startup-script").setValue(getStartupScript());
    metadataItems.add(items);

    items = new Metadata.Items().setKey("ssh-keys").setValue(GoogleProviderUtils.getSshPublicKey());
    metadataItems.add(items);

    if (!configSources.isEmpty()) {
      DaemonTaskHandler.message("Mounting config in vault server");
      GoogleVaultServerService vaultService = getVaultServerService();
      VaultServerService.Vault vault =
          vaultService.connectToPrimaryService(details, runtimeSettings);

      String secretName = secretName("config-mounts", version);
      VaultConfigMountSet mountSet = VaultConfigMountSet.fromConfigSources(configSources);
      secretName =
          vaultService.writeVaultConfigMountSet(deploymentName, vault, secretName, mountSet);

      VaultConnectionDetails connectionDetails =
          buildConnectionDetails(details, runtimeSettings, secretName);

      DaemonTaskHandler.message("Placing vault connection details into instance metadata");
      items = new Metadata.Items().setKey("vault_address").setValue(connectionDetails.getAddress());
      metadataItems.add(items);

      items = new Metadata.Items().setKey("vault_token").setValue(connectionDetails.getToken());
      metadataItems.add(items);

      items = new Metadata.Items().setKey("vault_secret").setValue(connectionDetails.getSecret());
      metadataItems.add(items);
    }

    GoogleConsulServerService consulServerService = getConsulServerService();
    RunningServiceDetails consulServerDetails =
        consulServerService.getRunningServiceDetails(details, runtimeSettings);
    Integer latestConsulVersion = consulServerDetails.getLatestEnabledVersion();
    if (latestConsulVersion != null) {
      List<RunningServiceDetails.Instance> instances =
          consulServerDetails.getInstances().get(latestConsulVersion);
      String instancesValue =
          String.join(
              " ",
              instances.stream()
                  .map(RunningServiceDetails.Instance::getId)
                  .collect(Collectors.toList()));

      items =
          new Metadata.Items()
              .setKey("consul-members") // TODO(lwander) change to consul_members for consistency w/
              // vault
              .setValue(instancesValue);

      DaemonTaskHandler.message("Placing consul connection details into instance metadata");
      metadataItems.add(items);
    }

    return metadataItems;
  }

  @Override
  default void ensureRunning(
      AccountDeploymentDetails<GoogleAccount> details,
      ResolvedConfiguration resolvedConfiguration,
      List<ConfigSource> configSources,
      boolean recreate) {
    DaemonTaskHandler.newStage("Deploying " + getServiceName() + " via GCE API");
    Integer version = 0;
    ServiceSettings settings = resolvedConfiguration.getServiceSettings(getService());
    SpinnakerRuntimeSettings runtimeSettings = resolvedConfiguration.getRuntimeSettings();
    RunningServiceDetails runningServiceDetails =
        getRunningServiceDetails(details, runtimeSettings);

    GoogleAccount account = details.getAccount();
    Compute compute = GoogleProviderUtils.getCompute(details);
    String project = account.getProject();
    String zone = settings.getLocation();

    boolean exists = runningServiceDetails.getInstances().containsKey(version);
    if (!recreate && exists) {
      DaemonTaskHandler.message(
          "Service " + getServiceName() + " is already deployed and not safe to restart");
      return;
    } else if (exists) {
      DaemonTaskHandler.message("Recreating existing " + getServiceName() + "...");
      deleteVersion(details, settings, version);
    }

    InstanceGroupManager manager = new InstanceGroupManager();

    InstanceTemplate template =
        new InstanceTemplate()
            .setName(getServiceName() + "-hal-" + System.currentTimeMillis())
            .setDescription("Halyard-generated instance template for deploying Spinnaker");

    Metadata metadata =
        new Metadata().setItems(getMetadata(details, runtimeSettings, configSources, version));

    AccessConfig accessConfig =
        new AccessConfig().setName("External NAT").setType("ONE_TO_ONE_NAT");

    NetworkInterface networkInterface =
        new NetworkInterface()
            .setNetwork(GoogleProviderUtils.ensureSpinnakerNetworkExists(details))
            .setAccessConfigs(Collections.singletonList(accessConfig));

    ServiceAccount sa =
        new ServiceAccount()
            .setEmail(GoogleProviderUtils.defaultServiceAccount(details))
            .setScopes(getScopes());

    InstanceProperties properties =
        new InstanceProperties()
            .setMachineType(getDefaultInstanceType())
            .setMetadata(metadata)
            .setServiceAccounts(Collections.singletonList(sa))
            .setNetworkInterfaces(Collections.singletonList(networkInterface));

    AttachedDisk disk = new AttachedDisk().setBoot(true).setAutoDelete(true).setType("PERSISTENT");

    AttachedDiskInitializeParams diskParams =
        new AttachedDiskInitializeParams()
            .setDiskSizeGb(20L)
            .setDiskType(GCEUtil.buildDiskTypeUrl(project, zone, GoogleDiskType.PD_SSD))
            .setSourceImage(getArtifactId(details.getDeploymentName()));

    disk.setInitializeParams(diskParams);
    List<AttachedDisk> disks = new ArrayList<>();

    disks.add(disk);
    properties.setDisks(disks);
    template.setProperties(properties);

    String instanceTemplateUrl;
    Operation operation;
    try {
      DaemonTaskHandler.message("Creating an instance template");
      operation = compute.instanceTemplates().insert(project, template).execute();
      instanceTemplateUrl = operation.getTargetLink();
      GoogleProviderUtils.waitOnGlobalOperation(compute, project, operation);
    } catch (IOException e) {
      throw new HalException(
          FATAL,
          "Failed to create instance template for "
              + settings.getArtifactId()
              + ": "
              + e.getMessage(),
          e);
    }

    String migName = getVersionedName(version);
    manager.setInstanceTemplate(instanceTemplateUrl);
    manager.setBaseInstanceName(migName);
    manager.setTargetSize(settings.getTargetSize());
    manager.setName(migName);

    try {
      DaemonTaskHandler.message("Deploying the instance group manager");
      operation =
          compute
              .instanceGroupManagers()
              .insert(project, settings.getLocation(), manager)
              .execute();
      GoogleProviderUtils.waitOnZoneOperation(compute, project, settings.getLocation(), operation);
    } catch (IOException e) {
      throw new HalException(
          FATAL,
          "Failed to create instance group to run artifact "
              + settings.getArtifactId()
              + ": "
              + e.getMessage(),
          e);
    }

    boolean ready = false;
    DaemonTaskHandler.message("Waiting for all instances to become healthy.");
    while (!ready) {
      Integer runningVersion =
          getRunningServiceDetails(details, runtimeSettings).getLatestEnabledVersion();
      ready = version.equals(runningVersion);

      DaemonTaskHandler.safeSleep(TimeUnit.SECONDS.toMillis(2));
    }
  }

  @Override
  default Map<String, Object> getLoadBalancerDescription(
      AccountDeploymentDetails<GoogleAccount> details, SpinnakerRuntimeSettings runtimeSettings) {
    return new HashMap<>();
  }

  @Override
  default Map<String, Object> getServerGroupDescription(
      AccountDeploymentDetails<GoogleAccount> details,
      SpinnakerRuntimeSettings runtimeSettings,
      List<ConfigSource> configSources) {
    GoogleAccount account = details.getAccount();
    RunningServiceDetails runningServiceDetails =
        getRunningServiceDetails(details, runtimeSettings);
    Integer version = runningServiceDetails.getLatestEnabledVersion();
    if (version == null) {
      version = 0;
    } else {
      version++;
    }

    Names name = Names.parseName(getServiceName());
    String app = name.getApp();
    String stack = name.getStack();
    String detail = name.getDetail();
    String network = GoogleProviderUtils.getNetworkName();
    Map<String, String> metadata =
        getMetadata(details, runtimeSettings, configSources, version).stream()
            .reduce(
                new HashMap<String, String>(),
                (h1, item) -> {
                  h1.put(item.getKey(), item.getValue());
                  return h1;
                },
                (h1, h2) -> {
                  h1.putAll(h2);
                  return h1;
                });

    String serviceAccountEmail = GoogleProviderUtils.defaultServiceAccount(details);
    List<String> scopes = getScopes();
    String accountName = account.getName();

    Map<String, Object> deployDescription = new HashMap<>();
    deployDescription.put("application", app);
    deployDescription.put("stack", stack);
    deployDescription.put("freeFormDetails", detail);
    deployDescription.put("network", network);
    deployDescription.put("instanceMetadata", metadata);
    deployDescription.put("serviceAccountEmail", serviceAccountEmail);
    deployDescription.put("authScopes", scopes);
    deployDescription.put("accountName", accountName);
    deployDescription.put("account", accountName);
    return deployDescription;

    /* TODO(lwander): Google's credential class cannot be serialized as-is, making this type of construction impossible
    BasicGoogleDeployDescription deployDescription = new BasicGoogleDeployDescription();
    deployDescription.setApplication(app);
    deployDescription.setStack(stack);
    deployDescription.setFreeFormDetails(detail);

    deployDescription.setNetwork(network);
    deployDescription.setInstanceMetadata(metadata);
    deployDescription.setServiceAccountEmail(serviceAccountEmail);
    deployDescription.setAuthScopes(scopes);
    // Google's credentials constructor prevents us from neatly creating a deploy description with only a name supplied
    String jsonKey = null;
    if (!StringUtils.isEmpty(account.getJsonPath())) {
      try {
        jsonKey = IOUtils.toString(new FileInputStream(account.getJsonPath()));
      } catch (IOException e) {
        throw new RuntimeException("Unvalidated json path found during deployment: " + e.getMessage(), e);
      }
    }

    deployDescription.setCredentials(new GoogleNamedAccountCredentials.Builder()
        .name(account.getName())
        .jsonKey(jsonKey)
        .project(account.getProject())
        .build()
    );

    return new ObjectMapper().convertValue(deployDescription, new TypeReference<Map<String, Object>>() { });
    */
  }

  @Override
  default List<String> getHealthProviders() {
    return Collections.singletonList("Discovery");
  }

  @Override
  default Map<String, List<String>> getAvailabilityZones(ServiceSettings settings) {
    Map<String, List<String>> result = new HashMap<>();
    List<String> zones = Collections.singletonList(settings.getLocation());
    result.put(getRegion(settings), zones);
    return result;
  }

  @Override
  default String getRegion(ServiceSettings settings) {
    String zone = settings.getLocation();
    return zone.substring(0, zone.lastIndexOf("-"));
  }

  @Override
  default RunningServiceDetails getRunningServiceDetails(
      AccountDeploymentDetails<GoogleAccount> details, SpinnakerRuntimeSettings runtimeSettings) {
    ServiceSettings settings = runtimeSettings.getServiceSettings(getService());
    RunningServiceDetails result = new RunningServiceDetails();
    result.setLoadBalancer(
        new RunningServiceDetails.LoadBalancer()
            .setExists(true)); // All GCE load balancing is done via consul
    Compute compute = GoogleProviderUtils.getCompute(details);
    GoogleAccount account = details.getAccount();
    List<InstanceGroupManager> migs;
    try {
      migs =
          compute
              .instanceGroupManagers()
              .list(account.getProject(), settings.getLocation())
              .execute()
              .getItems();
      if (migs == null) {
        migs = Collections.emptyList();
      }
    } catch (IOException e) {
      throw new HalException(FATAL, "Failed to load MIGS: " + e.getMessage(), e);
    }

    boolean consulEnabled =
        getSidecars(runtimeSettings).stream()
            .anyMatch(s -> s.getService().getType().equals(SpinnakerService.Type.CONSUL_CLIENT));

    Set<String> healthyConsulInstances =
        consulEnabled
            ? getConsulServerService()
                .connectToPrimaryService(details, runtimeSettings)
                .serviceHealth(getService().getCanonicalName(), true)
                .stream()
                .map(s -> s != null && s.getNode() != null ? s.getNode().getNodeName() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
            : new HashSet<>();

    String serviceName = getService().getServiceName();

    migs =
        migs.stream()
            .filter(ig -> ig.getName().startsWith(serviceName + "-v"))
            .collect(Collectors.toList());

    Map<Integer, List<RunningServiceDetails.Instance>> instances =
        migs.stream()
            .reduce(
                new HashMap<>(),
                (map, mig) -> {
                  Names names = Names.parseName(mig.getName());
                  Integer version = names.getSequence();
                  List<RunningServiceDetails.Instance> computeInstances;
                  try {
                    List<ManagedInstance> managedInstances =
                        compute
                            .instanceGroupManagers()
                            .listManagedInstances(
                                account.getProject(), settings.getLocation(), mig.getName())
                            .execute()
                            .getManagedInstances();

                    if (managedInstances == null) {
                      managedInstances = new ArrayList<>();
                    }

                    computeInstances =
                        managedInstances.stream()
                            .map(
                                i -> {
                                  String instanceUrl = i.getInstance();
                                  String instanceStatus = i.getInstanceStatus();
                                  boolean running =
                                      instanceStatus != null
                                          && instanceStatus.equalsIgnoreCase("running");
                                  String instanceName =
                                      instanceUrl.substring(
                                          instanceUrl.lastIndexOf('/') + 1, instanceUrl.length());
                                  return new RunningServiceDetails.Instance()
                                      .setId(instanceName)
                                      .setLocation(settings.getLocation())
                                      .setRunning(running)
                                      .setHealthy(
                                          !consulEnabled
                                              || healthyConsulInstances.contains(instanceName));
                                })
                            .collect(Collectors.toList());
                  } catch (IOException e) {
                    throw new HalException(
                        FATAL, "Failed to load target pools for " + serviceName, e);
                  }
                  map.put(version, computeInstances);
                  return map;
                },
                (m1, m2) -> {
                  m1.putAll(m2);
                  return m1;
                });

    result.setInstances(instances);

    return result;
  }

  default <S> URI sshTunnelIntoService(
      AccountDeploymentDetails<GoogleAccount> details,
      SpinnakerRuntimeSettings runtimeSettings,
      SpinnakerService<S> sidecar) {
    ServiceSettings settings = runtimeSettings.getServiceSettings(sidecar);
    RunningServiceDetails runningServiceDetails =
        getRunningServiceDetails(details, runtimeSettings);
    Integer enabledVersion = runningServiceDetails.getLatestEnabledVersion();
    if (enabledVersion == null) {
      throw new HalException(
          FATAL,
          "Cannot connect to "
              + getServiceName()
              + " when no server groups have been deployed yet");
    }

    List<RunningServiceDetails.Instance> instances =
        runningServiceDetails.getInstances().get(enabledVersion);

    if (instances == null || instances.isEmpty()) {
      throw new HalException(
          FATAL,
          "Cannot connect to " + getServiceName() + " when no instances have been deployed yet");
    }

    try {
      return GoogleProviderUtils.openSshTunnel(details, instances.get(0).getId(), settings);
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }
  }

  @Override
  default <S> S connectToInstance(
      AccountDeploymentDetails<GoogleAccount> details,
      SpinnakerRuntimeSettings runtimeSettings,
      SpinnakerService<S> sidecar,
      String instanceId) {
    try {
      return getServiceInterfaceFactory()
          .createService(
              GoogleProviderUtils.openSshTunnel(
                      details, instanceId, runtimeSettings.getServiceSettings(sidecar))
                  .toString(),
              sidecar);
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }
  }

  @Override
  default <S> S connectToService(
      AccountDeploymentDetails<GoogleAccount> details,
      SpinnakerRuntimeSettings runtimeSettings,
      SpinnakerService<S> sidecar) {
    return getServiceInterfaceFactory()
        .createService(sshTunnelIntoService(details, runtimeSettings, sidecar).toString(), sidecar);
  }

  @Override
  default String connectCommand(
      AccountDeploymentDetails<GoogleAccount> details, SpinnakerRuntimeSettings runtimeSettings) {
    RunningServiceDetails runningServiceDetails =
        getRunningServiceDetails(details, runtimeSettings);
    Integer version = runningServiceDetails.getLatestEnabledVersion();
    if (version == null) {
      throw new HalException(FATAL, "No version of " + getServiceName() + " to connect to.");
    }

    List<RunningServiceDetails.Instance> instances =
        runningServiceDetails.getInstances().get(version);
    if (instances.isEmpty()) {
      throw new HalException(
          FATAL,
          "Version " + version + " of " + getServiceName() + " has no instances to connect to");
    }

    RunningServiceDetails.Instance instance = instances.get(0);
    String instanceName = instance.getId();
    String zone = instance.getLocation();
    ServiceSettings settings = runtimeSettings.getServiceSettings(getService());
    int port = settings.getPort();

    return String.format(
        "gcloud compute ssh %s --zone %s -- -L %d:localhost:%d -N", instanceName, zone, port, port);
  }

  @Override
  default void deleteVersion(
      AccountDeploymentDetails<GoogleAccount> details, ServiceSettings settings, Integer version) {
    String migName = getVersionedName(version);
    String zone = settings.getLocation();
    String project = details.getAccount().getProject();
    Compute compute = GoogleProviderUtils.getCompute(details);
    InstanceGroupManager mig;
    try {
      mig = compute.instanceGroupManagers().get(project, zone, migName).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        return;
      } else {
        throw new HalException(FATAL, "Failed to load mig " + migName + " in " + zone, e);
      }
    } catch (IOException e) {
      throw new HalException(FATAL, "Failed to load mig " + migName + " in " + zone, e);
    }

    try {
      GoogleProviderUtils.waitOnZoneOperation(
          compute,
          project,
          zone,
          compute.instanceGroupManagers().delete(project, zone, migName).execute());
    } catch (IOException e) {
      throw new HalException(FATAL, "Failed to delete mig " + migName + " in " + zone, e);
    }

    String instanceTemplateName = mig.getInstanceTemplate();
    instanceTemplateName =
        instanceTemplateName.substring(instanceTemplateName.lastIndexOf('/') + 1);
    try {
      GoogleProviderUtils.waitOnGlobalOperation(
          compute,
          project,
          compute.instanceTemplates().delete(project, instanceTemplateName).execute());
    } catch (IOException e) {
      throw new HalException(
          FATAL, "Failed to delete template " + instanceTemplateName + " in " + zone, e);
    }
  }
}
