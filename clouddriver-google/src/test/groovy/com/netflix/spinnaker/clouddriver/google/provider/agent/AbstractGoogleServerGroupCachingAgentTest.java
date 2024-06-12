/*
 * Copyright 2019 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SERVER_GROUPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Autoscaler;
import com.google.api.services.compute.model.AutoscalerStatusDetails;
import com.google.api.services.compute.model.AutoscalingPolicy;
import com.google.api.services.compute.model.AutoscalingPolicyCpuUtilization;
import com.google.api.services.compute.model.AutoscalingPolicyCustomMetricUtilization;
import com.google.api.services.compute.model.AutoscalingPolicyLoadBalancingUtilization;
import com.google.api.services.compute.model.AutoscalingPolicyScaleInControl;
import com.google.api.services.compute.model.DistributionPolicy;
import com.google.api.services.compute.model.DistributionPolicyZoneConfiguration;
import com.google.api.services.compute.model.FixedOrPercent;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceGroupManagerActionsSummary;
import com.google.api.services.compute.model.InstanceGroupManagerAutoHealingPolicy;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Metadata.Items;
import com.google.api.services.compute.model.NamedPort;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.api.services.compute.model.StatefulPolicy;
import com.google.api.services.compute.model.StatefulPolicyPreservedState;
import com.google.api.services.compute.model.StatefulPolicyPreservedStateDiskDevice;
import com.google.api.services.compute.model.Tags;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.cats.provider.DefaultProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.google.cache.Keys;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.AutoscalingMode;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.CustomMetricUtilization;
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleInstanceHealth;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import javax.annotation.ParametersAreNonnullByDefault;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractGoogleServerGroupCachingAgentTest {

  private static final String ACCOUNT_NAME = "partypups";
  private static final String PROJECT = "myproject";
  private static final String REGION = "myregion";
  private static final String REGION_URL = "http://compute/regions/" + REGION;
  private static final String ZONE = REGION + "-myzone";
  private static final String ZONE_URL = "http://compute/zones/" + ZONE;

  private ObjectMapper objectMapper;

  @BeforeEach
  void createTestObjects() {
    objectMapper = new ObjectMapper();
  }

  @Test
  void basicServerGroupProperties() {
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setName("myServerGroup")
            .setZone(ZONE_URL)
            .setSelfLink("http://my/fun/link")
            .setNamedPorts(
                ImmutableList.of(
                    new NamedPort().setName("first").setPort(10111),
                    new NamedPort().setName("second").setPort(20222)))
            .setCurrentActions(
                new InstanceGroupManagerActionsSummary().setCreating(2).setDeleting(4))
            .setTargetSize(3)
            .setStatefulPolicy(
                new StatefulPolicy()
                    .setPreservedState(
                        new StatefulPolicyPreservedState()
                            .setDisks(
                                ImmutableMap.of(
                                    "myDisk", new StatefulPolicyPreservedStateDiskDevice()))))
            .setAutoHealingPolicies(
                ImmutableList.of(
                    new InstanceGroupManagerAutoHealingPolicy().setInitialDelaySec(92)));

    Compute compute =
        new StubComputeFactory().setInstanceGroupManagers(instanceGroupManager).create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(compute, ImmutableList.of(instanceGroupManager));

    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());

    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);

    assertThat(serverGroup.getName()).isEqualTo(instanceGroupManager.getName());
    assertThat(serverGroup.getSelfLink()).isEqualTo(instanceGroupManager.getSelfLink());

    assertThat(serverGroup.getCurrentActions()).isEqualTo(instanceGroupManager.getCurrentActions());

    assertThat(serverGroup.getStatefulPolicy()).isEqualTo(instanceGroupManager.getStatefulPolicy());
    assertThat(serverGroup.getAutoHealingPolicy())
        .isEqualTo(instanceGroupManager.getAutoHealingPolicies().get(0));
    assertThat(serverGroup.getLaunchConfig()).containsKeys("createdTime");

    assertThat(serverGroup.getAccount()).isEqualTo(ACCOUNT_NAME);
    assertThat(serverGroup.getRegional()).isFalse();
    assertThat(serverGroup.getRegion()).isEqualTo(REGION);
    assertThat(serverGroup.getZone()).isEqualTo(ZONE);
    assertThat(serverGroup.getZones()).containsExactly(ZONE);

    assertThat(serverGroup.getNamedPorts())
        .containsOnly(entry("first", 10111), entry("second", 20222));
    assertThat(serverGroup.getAsg())
        .contains(entry("minSize", 3), entry("maxSize", 3), entry("desiredCapacity", 3));
  }

  @Test
  void serverGroupPropertiesForZonalServerGroup() {
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setName("myServerGroup")
            .setZone(ZONE_URL)
            // This should be ignored for zonal server groups, but we'll set one to make sure
            .setDistributionPolicy(
                new DistributionPolicy()
                    .setZones(
                        ImmutableList.of(
                            new DistributionPolicyZoneConfiguration()
                                .setZone("http://compute/zones/fakezone1"))));

    Compute compute =
        new StubComputeFactory().setInstanceGroupManagers(instanceGroupManager).create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(compute, ImmutableList.of(instanceGroupManager));

    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());

    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);
    assertThat(serverGroup.getRegional()).isFalse();
    assertThat(serverGroup.getRegion()).isEqualTo(REGION);
    assertThat(serverGroup.getZone()).isEqualTo(ZONE);
    assertThat(serverGroup.getZones()).containsExactly(ZONE);
  }

  @Test
  void serverGroupPropertiesForRegionalServerGroup() {
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setName("myServerGroup")
            .setRegion(REGION_URL)
            .setDistributionPolicy(
                new DistributionPolicy()
                    .setZones(
                        ImmutableList.of(
                            new DistributionPolicyZoneConfiguration()
                                .setZone("http://compute/zones/fakezone1"),
                            new DistributionPolicyZoneConfiguration()
                                .setZone("http://compute/zones/fakezone2"),
                            new DistributionPolicyZoneConfiguration()
                                .setZone("http://compute/zones/fakezone3")))
                    .setTargetShape("ANY"));

    Compute compute =
        new StubComputeFactory().setInstanceGroupManagers(instanceGroupManager).create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(compute, ImmutableList.of(instanceGroupManager));

    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());

    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);
    assertThat(serverGroup.getRegional()).isTrue();
    assertThat(serverGroup.getRegion()).isEqualTo(REGION);
    assertThat(serverGroup.getZone()).isNull();
    assertThat(serverGroup.getZones())
        .containsExactlyInAnyOrder("fakezone1", "fakezone2", "fakezone3");
    assertThat(serverGroup.getDistributionPolicy().getTargetShape()).isEqualTo("ANY");
  }

  @Test
  void serverGroupPropertiesFromInstanceTemplate() {
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setInstanceTemplate("http://compute/global/instanceTemplates/myInstanceTemplate")
            .setZone(ZONE_URL);
    InstanceTemplate instanceTemplate =
        new InstanceTemplate()
            .setName("myInstanceTemplate")
            .setProperties(
                new InstanceProperties()
                    .setDisks(
                        ImmutableList.of(
                            new AttachedDisk()
                                .setBoot(true)
                                .setInitializeParams(
                                    new AttachedDiskInitializeParams()
                                        .setSourceImage("http://compute/global/images/myImage"))))
                    .setServiceAccounts(
                        ImmutableList.of(new ServiceAccount().setEmail("spinnaker@spinnaker.io")))
                    .setMachineType("machineType")
                    .setMinCpuPlatform("minCpuPlatform")
                    .setCanIpForward(true)
                    .setNetworkInterfaces(
                        ImmutableList.of(
                            new NetworkInterface()
                                .setNetwork(
                                    String.format(
                                        "http://compute/network/projects/%s/myNetworkName",
                                        PROJECT))))
                    .setMetadata(
                        new Metadata()
                            .setItems(
                                ImmutableList.of(
                                    new Items().setKey("load-balancer-names").setValue("one,two"),
                                    new Items()
                                        .setKey("global-load-balancer-names")
                                        .setValue("three,four"),
                                    new Items()
                                        .setKey("backend-service-names")
                                        .setValue("five,six"),
                                    new Items()
                                        .setKey("load-balancing-policy")
                                        .setValue("{\"maxUtilization\": 1.3}"))))
                    .setLabels(ImmutableMap.of("label1", "value1", "label2", "value2"))
                    .setTags(new Tags().setItems(ImmutableList.of("tag1", "tag2"))));

    DefaultProviderCache providerCache = inMemoryProviderCache();
    providerCache.putCacheData(
        IMAGES.getNs(),
        new DefaultCacheData(
            Keys.getImageKey(ACCOUNT_NAME, "myImage"),
            ImmutableMap.of(
                "image",
                ImmutableMap.of(
                    "description",
                    "appversion: myapp-1.0.0-12345.h777/999/10111,"
                        + "build_host: spin.nyc.corp,"
                        + "build_info_url: http://jenkins/artifact/12345")),
            ImmutableMap.of()));

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager)
            .setInstanceTemplates(instanceTemplate)
            .create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(compute, ImmutableList.of(instanceGroupManager));

    CacheResult cacheResult = cachingAgent.loadData(providerCache);

    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);

    assertThat(serverGroup.getInstanceTemplateTags())
        .isEqualTo(ImmutableSet.copyOf(instanceTemplate.getProperties().getTags().getItems()));
    assertThat(serverGroup.getInstanceTemplateServiceAccounts())
        .isEqualTo(ImmutableSet.copyOf(instanceTemplate.getProperties().getServiceAccounts()));
    assertThat(serverGroup.getInstanceTemplateLabels())
        .isEqualTo(instanceTemplate.getProperties().getLabels());
    assertThat(serverGroup.getLaunchConfig())
        .contains(
            entry("imageId", "myImage"),
            entry("launchConfigurationName", instanceTemplate.getName()),
            entry("instanceType", instanceTemplate.getProperties().getMachineType()),
            entry("minCpuPlatform", instanceTemplate.getProperties().getMinCpuPlatform()),
            entry("instanceTemplate", instanceTemplate));
    assertThat(serverGroup.getAsg())
        .contains(
            entry("load-balancer-names", ImmutableList.of("one", "two")),
            entry("global-load-balancer-names", ImmutableList.of("three", "four")),
            entry("backend-service-names", ImmutableList.of("five", "six")));
    assertThat(serverGroup.getAsg()).containsKey("load-balancing-policy");
    assertThat(
            ((Map<String, Float>) serverGroup.getAsg().get("load-balancing-policy"))
                .get("maxUtilization"))
        .isEqualTo(1.3f, Offset.offset(.0000001f));
    assertThat(serverGroup.getNetworkName()).isEqualTo("myNetworkName");
    assertThat(serverGroup.getBuildInfo())
        .containsOnly(
            entry("package_name", "myapp"),
            entry("version", "1.0.0"),
            entry("commit", "12345"),
            entry(
                "jenkins",
                ImmutableMap.of("name", "999", "number", "777", "host", "spin.nyc.corp")),
            entry("buildInfoUrl", "http://jenkins/artifact/12345"));
  }

  @Test
  void minimalBuildInfo() {
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setInstanceTemplate("http://compute/global/instanceTemplates/myInstanceTemplate")
            .setZone(ZONE_URL);
    InstanceTemplate instanceTemplate =
        new InstanceTemplate()
            .setName("myInstanceTemplate")
            .setProperties(
                new InstanceProperties()
                    .setDisks(
                        ImmutableList.of(
                            new AttachedDisk()
                                .setBoot(true)
                                .setInitializeParams(
                                    new AttachedDiskInitializeParams()
                                        .setSourceImage("http://compute/global/images/myImage")))));

    DefaultProviderCache providerCache = inMemoryProviderCache();
    providerCache.putCacheData(
        IMAGES.getNs(),
        new DefaultCacheData(
            Keys.getImageKey(ACCOUNT_NAME, "myImage"),
            ImmutableMap.of(
                "image", ImmutableMap.of("description", "appversion: myapp-1.0.0-h123")),
            ImmutableMap.of()));

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager)
            .setInstanceTemplates(instanceTemplate)
            .create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(compute, ImmutableList.of(instanceGroupManager));

    CacheResult cacheResult = cachingAgent.loadData(providerCache);

    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);

    assertThat(serverGroup.getBuildInfo())
        .containsOnly(entry("package_name", "myapp"), entry("version", "1.0.0"));
  }

  @Test
  void serverGroupPropertiesFromInstances() {
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager().setBaseInstanceName("myServerGroup-").setZone(ZONE_URL);
    Instance serverInstance =
        new Instance()
            .setName("myServerGroup-1234")
            .setId(BigInteger.valueOf(10111))
            .setMachineType("http://compute/global/machineTypes/reallyBigComputer")
            .setCpuPlatform("goog86")
            .setZone(ZONE_URL)
            .setNetworkInterfaces(
                ImmutableList.of(
                    new NetworkInterface()
                        .setNetwork(
                            String.format(
                                "http://compute/network/projects/%s/myNetworkName", PROJECT))))
            .setMetadata(
                new Metadata()
                    .setItems(
                        ImmutableList.of(new Items().setKey("itemKey").setValue("itemValue"))))
            .setDisks(ImmutableList.of(new AttachedDisk().setType("myDiskType")))
            .setServiceAccounts(
                ImmutableList.of(new ServiceAccount().setEmail("spinnaker@spinnaker.io")))
            .setSelfLink("http://my/fun/link")
            .setTags(new Tags().setItems(ImmutableList.of("tag1", "tag2")))
            .setLabels(ImmutableMap.of("label1", "value1", "label2", "value2"))
            .setStatus("RUNNING");

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager)
            .setInstances(serverInstance)
            .create();

    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(compute, ImmutableList.of(instanceGroupManager));

    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());

    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);

    assertThat(serverGroup.getInstances()).hasSize(1);
    GoogleInstance cacheInstance = getOnlyElement(serverGroup.getInstances());

    assertThat(cacheInstance.getName()).isEqualTo(serverInstance.getName());
    assertThat(cacheInstance.getAccount()).isEqualTo(PROJECT);
    assertThat(cacheInstance.getGceId()).isEqualTo("10111");
    assertThat(cacheInstance.getInstanceType()).isEqualTo("reallyBigComputer");
    assertThat(cacheInstance.getCpuPlatform()).isEqualTo("goog86");
    assertThat(cacheInstance.getZone()).isEqualTo(ZONE);
    assertThat(cacheInstance.getRegion()).isEqualTo(REGION);
    assertThat(cacheInstance.getNetworkInterfaces())
        .isEqualTo(serverInstance.getNetworkInterfaces());
    assertThat(cacheInstance.getNetworkName()).isEqualTo("myNetworkName");
    assertThat(cacheInstance.getMetadata()).isEqualTo(serverInstance.getMetadata());
    AttachedDisk diskWithCorrectType = new AttachedDisk();
    diskWithCorrectType.putAll(cacheInstance.getDisks().get(0));
    assertThat(ImmutableList.of(diskWithCorrectType)).isEqualTo(serverInstance.getDisks());
    assertThat(cacheInstance.getServiceAccounts()).isEqualTo(serverInstance.getServiceAccounts());
    assertThat(cacheInstance.getSelfLink()).isEqualTo(serverInstance.getSelfLink());
    assertThat(cacheInstance.getTags()).isEqualTo(serverInstance.getTags());
    assertThat(cacheInstance.getLabels()).isEqualTo(serverInstance.getLabels());
    assertThat(cacheInstance.getInstanceHealth())
        .isEqualTo(new GoogleInstanceHealth(GoogleInstanceHealth.Status.RUNNING));
  }

  @Test
  void serverGroupDisksAreSortedProperly() {

    List<String> diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(true).setType("FLAKY").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk2"),
            new AttachedDisk().setBoot(false).setType("FLAKY").setDeviceName("disk3"),
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk4"),
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk5"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk6"));

    // Non-persistent disks are removed, and then the first boot disk is moved to the front.
    // Other boot disks are removed.
    assertThat(diskNames).containsExactly("disk4", "disk2", "disk3", "disk6");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(true).setType("FLAKY").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk2"),
            new AttachedDisk().setBoot(false).setType("FLAKY").setDeviceName("disk3"),
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk4"),
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk5"),
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk6"),
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk7"));

    // Since the first disk is persistent and bootable, we leave the disks untouched.
    assertThat(diskNames)
        .containsExactly("disk0", "disk1", "disk2", "disk3", "disk4", "disk5", "disk6", "disk7");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(true).setType("FLAKY").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk2"),
            new AttachedDisk().setBoot(true).setType("FLAKY").setDeviceName("disk3"),
            new AttachedDisk().setBoot(false).setType("FLAKY").setDeviceName("disk4"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk5"));

    // Since there is no persistent boot disk, we remove all boot disks.
    assertThat(diskNames).containsExactly("disk2", "disk4", "disk5");

    // These are copied from the original test code
    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk0"));
    assertThat(diskNames).containsExactly("disk0");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk1"));
    assertThat(diskNames).containsExactly("disk0", "disk1");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk1"));
    assertThat(diskNames).containsExactly("disk1", "disk0");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk2"));
    assertThat(diskNames).containsExactly("disk0", "disk1", "disk2");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk2"));
    assertThat(diskNames).containsExactly("disk1", "disk0", "disk2");

    // Mix in a SCRATCH disk.
    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(false).setType("SCRATCH").setDeviceName("disk1"));
    assertThat(diskNames).containsExactly("disk0", "disk1");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("SCRATCH").setDeviceName("disk2"));
    assertThat(diskNames).containsExactly("disk0", "disk1", "disk2");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("SCRATCH").setDeviceName("disk2"));
    assertThat(diskNames).containsExactly("disk1", "disk0", "disk2");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("SCRATCH").setDeviceName("disk0"),
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk2"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk3"));
    assertThat(diskNames).containsExactly("disk0", "disk1", "disk2", "disk3");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(true).setType("PERSISTENT").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("SCRATCH").setDeviceName("disk2"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk3"));
    assertThat(diskNames).containsExactly("disk1", "disk0", "disk2", "disk3");

    // Boot disk missing (really shouldn't happen, but want to ensure we don't disturb the results).
    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk0"));
    assertThat(diskNames).containsExactly("disk0");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk1"));
    assertThat(diskNames).containsExactly("disk0", "disk1");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk2"));
    assertThat(diskNames).containsExactly("disk0", "disk1", "disk2");

    // Mix in a SCRATCH disk and Boot disk missing.
    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(false).setType("SCRATCH").setDeviceName("disk1"));
    assertThat(diskNames).containsExactly("disk0", "disk1");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("SCRATCH").setDeviceName("disk2"));
    assertThat(diskNames).containsExactly("disk0", "disk1", "disk2");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("SCRATCH").setDeviceName("disk0"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk2"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk3"));
    assertThat(diskNames).containsExactly("disk0", "disk1", "disk2", "disk3");

    diskNames =
        retrieveCachedDiskNames(
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk0"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk1"),
            new AttachedDisk().setBoot(false).setType("SCRATCH").setDeviceName("disk2"),
            new AttachedDisk().setBoot(false).setType("PERSISTENT").setDeviceName("disk3"));
    assertThat(diskNames).containsExactly("disk0", "disk1", "disk2", "disk3");
  }

  private List<String> retrieveCachedDiskNames(AttachedDisk... inputDisks) {
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setInstanceTemplate("http://compute/global/instanceTemplates/myInstanceTemplate")
            .setZone(ZONE_URL);
    InstanceTemplate instanceTemplate =
        new InstanceTemplate()
            .setName("myInstanceTemplate")
            .setProperties(new InstanceProperties().setDisks(ImmutableList.copyOf(inputDisks)));

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager)
            .setInstanceTemplates(instanceTemplate)
            .create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(compute, ImmutableList.of(instanceGroupManager));

    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());

    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);

    return getDiskNames(serverGroup);
  }

  private static ImmutableList<String> getDiskNames(GoogleServerGroup serverGroup) {
    Map<String, Object> launchConfig = serverGroup.getLaunchConfig();
    Map<String, Object> instanceTemplate =
        (Map<String, Object>) launchConfig.get("instanceTemplate");
    Map<String, Object> properties = (Map<String, Object>) instanceTemplate.get("properties");
    List<Map<String, Object>> disks = (List<Map<String, Object>>) properties.get("disks");
    return disks.stream().map(disk -> (String) disk.get("deviceName")).collect(toImmutableList());
  }

  @Test
  void serverGroupDisabledProperty() {

    Items loadBalancerItem = new Items().setKey("load-balancer-names");

    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setInstanceTemplate("http://compute/global/instanceTemplates/myInstanceTemplate")
            .setZone(ZONE_URL);
    InstanceTemplate instanceTemplate =
        new InstanceTemplate()
            .setName("myInstanceTemplate")
            .setProperties(
                new InstanceProperties()
                    .setMetadata(new Metadata().setItems(ImmutableList.of(loadBalancerItem))));

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager)
            .setInstanceTemplates(instanceTemplate)
            .create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(compute, ImmutableList.of(instanceGroupManager));

    // If there are load balancers and target pools, then we not disabled
    instanceGroupManager.setTargetPools(ImmutableList.of("targetPool1"));
    loadBalancerItem.setValue("loadBalancer1");
    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());
    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);
    assertThat(serverGroup.getDisabled()).isFalse();

    // If there are load balancers and no target pools, then we _are_ disabled.
    instanceGroupManager.setTargetPools(null);
    loadBalancerItem.setValue("loadBalancer1");
    cacheResult = cachingAgent.loadData(inMemoryProviderCache());
    serverGroup = getOnlyServerGroup(cacheResult);
    assertThat(serverGroup.getDisabled()).isTrue();

    // If there are no load balancers, then we are not disabled, regardless of the target pools
    instanceGroupManager.setTargetPools(ImmutableList.of("targetPool1"));
    loadBalancerItem.setValue(null);
    cacheResult = cachingAgent.loadData(inMemoryProviderCache());
    serverGroup = getOnlyServerGroup(cacheResult);
    assertThat(serverGroup.getDisabled()).isFalse();

    instanceGroupManager.setTargetPools(null);
    loadBalancerItem.setValue(null);
    cacheResult = cachingAgent.loadData(inMemoryProviderCache());
    serverGroup = getOnlyServerGroup(cacheResult);
    assertThat(serverGroup.getDisabled()).isFalse();
  }

  @Test
  void serverGroupAutoscalerProperties() {

    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager().setName("myServerGroup").setTargetSize(303).setZone(ZONE_URL);
    Autoscaler autoscaler =
        new Autoscaler()
            .setZone(ZONE_URL)
            .setTarget("myServerGroup")
            .setAutoscalingPolicy(
                new AutoscalingPolicy().setMinNumReplicas(101).setMaxNumReplicas(202))
            .setStatusDetails(
                ImmutableList.of(
                    new AutoscalerStatusDetails().setMessage("message1"),
                    new AutoscalerStatusDetails().setMessage("message2")));

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager)
            .setAutoscalers(autoscaler)
            .create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(
            compute, ImmutableList.of(instanceGroupManager), ImmutableList.of(autoscaler));

    // If there are load balancers and target pools, then we not disabled
    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());
    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);
    assertThat(serverGroup.getAsg())
        .containsOnly(entry("minSize", 101), entry("maxSize", 202), entry("desiredCapacity", 303));
    assertThat(serverGroup.getAutoscalingMessages()).containsExactly("message1", "message2");
  }

  @Test
  void serverGroupAutoscalingPolicy_allFields() {

    AutoscalingPolicy input =
        new AutoscalingPolicy()
            .setCoolDownPeriodSec(123)
            .setCpuUtilization(
                new AutoscalingPolicyCpuUtilization()
                    .setUtilizationTarget(9.87)
                    .setPredictiveMethod("OPTIMIZE_AVAILABILITY"))
            .setLoadBalancingUtilization(
                new AutoscalingPolicyLoadBalancingUtilization().setUtilizationTarget(6.54))
            .setMaxNumReplicas(99)
            .setMinNumReplicas(11)
            .setMode("ON")
            .setCustomMetricUtilizations(
                ImmutableList.of(
                    new AutoscalingPolicyCustomMetricUtilization()
                        .setMetric("myMetric")
                        .setUtilizationTarget(911.23)
                        .setUtilizationTargetType("GAUGE")
                        .setSingleInstanceAssignment(1.0),
                    new AutoscalingPolicyCustomMetricUtilization()))
            .setScaleInControl(
                new AutoscalingPolicyScaleInControl()
                    .setTimeWindowSec(10111)
                    .setMaxScaledInReplicas(new FixedOrPercent().setFixed(123).setPercent(456)));

    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager().setName("myServerGroup").setZone(ZONE_URL);
    Autoscaler autoscaler =
        new Autoscaler().setZone(ZONE_URL).setTarget("myServerGroup").setAutoscalingPolicy(input);

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager)
            .setAutoscalers(autoscaler)
            .create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(
            compute, ImmutableList.of(instanceGroupManager), ImmutableList.of(autoscaler));

    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());
    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);
    GoogleAutoscalingPolicy converted = serverGroup.getAutoscalingPolicy();

    assertThat(converted.getCoolDownPeriodSec()).isEqualTo(input.getCoolDownPeriodSec());
    assertThat(converted.getCpuUtilization().getUtilizationTarget())
        .isEqualTo(input.getCpuUtilization().getUtilizationTarget());
    assertThat(converted.getCpuUtilization().getPredictiveMethod().toString())
        .isEqualTo(input.getCpuUtilization().getPredictiveMethod());
    assertThat(converted.getLoadBalancingUtilization().getUtilizationTarget())
        .isEqualTo(input.getLoadBalancingUtilization().getUtilizationTarget());
    assertThat(converted.getMaxNumReplicas()).isEqualTo(input.getMaxNumReplicas());
    assertThat(converted.getMinNumReplicas()).isEqualTo(input.getMinNumReplicas());
    assertThat(converted.getMode().toString()).isEqualTo(input.getMode());
    assertThat(converted.getScaleInControl().getTimeWindowSec()).isEqualTo(10111);
    assertThat(converted.getScaleInControl().getMaxScaledInReplicas().getFixed()).isEqualTo(123);
    assertThat(converted.getScaleInControl().getMaxScaledInReplicas().getPercent()).isEqualTo(456);

    assertThat(converted.getCustomMetricUtilizations())
        .hasSize(input.getCustomMetricUtilizations().size());
    for (int i = 0; i < converted.getCustomMetricUtilizations().size(); ++i) {
      CustomMetricUtilization convertedCustomMetric =
          converted.getCustomMetricUtilizations().get(i);
      AutoscalingPolicyCustomMetricUtilization inputCustomMetric =
          input.getCustomMetricUtilizations().get(i);
      assertThat(convertedCustomMetric.getMetric()).isEqualTo(inputCustomMetric.getMetric());
      assertThat(convertedCustomMetric.getUtilizationTarget())
          .isEqualTo(inputCustomMetric.getUtilizationTarget());
      assertThat(
              Optional.ofNullable(convertedCustomMetric.getUtilizationTargetType())
                  .map(Object::toString)
                  .orElse(null))
          .isEqualTo(inputCustomMetric.getUtilizationTargetType());
    }
  }

  @Test
  void serverGroupAutoscalingPolicy_onlyUpIsTransformedToOnlyScaleOut() {

    AutoscalingPolicy input = new AutoscalingPolicy().setMode("ONLY_UP");

    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager().setName("myServerGroup").setZone(ZONE_URL);
    Autoscaler autoscaler =
        new Autoscaler().setZone(ZONE_URL).setTarget("myServerGroup").setAutoscalingPolicy(input);

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager)
            .setAutoscalers(autoscaler)
            .create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(
            compute, ImmutableList.of(instanceGroupManager), ImmutableList.of(autoscaler));

    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());
    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);
    GoogleAutoscalingPolicy converted = serverGroup.getAutoscalingPolicy();

    assertThat(converted.getMode()).isEqualTo(AutoscalingMode.ONLY_SCALE_OUT);
  }

  @Test
  void serverGroupAutoscalingPolicy_noFields() {

    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager().setName("myServerGroup").setZone(ZONE_URL);
    Autoscaler autoscaler =
        new Autoscaler()
            .setZone(ZONE_URL)
            .setTarget("myServerGroup")
            .setAutoscalingPolicy(new AutoscalingPolicy());

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager)
            .setAutoscalers(autoscaler)
            .create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(
            compute, ImmutableList.of(instanceGroupManager), ImmutableList.of(autoscaler));

    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());
    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);
    GoogleAutoscalingPolicy converted = serverGroup.getAutoscalingPolicy();

    assertThat(converted.getCoolDownPeriodSec()).isNull();
    assertThat(converted.getCpuUtilization()).isNull();
    assertThat(converted.getCustomMetricUtilizations()).isNull();
    assertThat(converted.getLoadBalancingUtilization()).isNull();
    assertThat(converted.getMaxNumReplicas()).isNull();
    assertThat(converted.getMinNumReplicas()).isNull();
    assertThat(converted.getMode()).isNull();
    assertThat(converted.getScaleInControl()).isNull();
  }

  @Test
  void serverGroupAutoscalingPolicy_unknownPredictiveAutoscalerMethod() {

    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager().setName("myServerGroup").setZone(ZONE_URL);
    Autoscaler autoscaler =
        new Autoscaler()
            .setZone(ZONE_URL)
            .setTarget("myServerGroup")
            .setAutoscalingPolicy(
                new AutoscalingPolicy()
                    .setCpuUtilization(
                        new AutoscalingPolicyCpuUtilization()
                            .setPredictiveMethod("SOME THING THAT DOESN'T REALLY EXIST")));

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager)
            .setAutoscalers(autoscaler)
            .create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(
            compute, ImmutableList.of(instanceGroupManager), ImmutableList.of(autoscaler));

    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());
    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);
    GoogleAutoscalingPolicy converted = serverGroup.getAutoscalingPolicy();

    assertThat(converted.getCpuUtilization().getPredictiveMethod()).isNull();
  }

  @Test
  void serverGroupAutoscalingPolicy_emptyPredictiveAutoscalerMethod() {

    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager().setName("myServerGroup").setZone(ZONE_URL);
    Autoscaler autoscaler =
        new Autoscaler()
            .setZone(ZONE_URL)
            .setTarget("myServerGroup")
            .setAutoscalingPolicy(
                new AutoscalingPolicy().setCpuUtilization(new AutoscalingPolicyCpuUtilization()));

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager)
            .setAutoscalers(autoscaler)
            .create();
    AbstractGoogleServerGroupCachingAgent cachingAgent =
        createCachingAgent(
            compute, ImmutableList.of(instanceGroupManager), ImmutableList.of(autoscaler));

    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());
    GoogleServerGroup serverGroup = getOnlyServerGroup(cacheResult);
    GoogleAutoscalingPolicy converted = serverGroup.getAutoscalingPolicy();

    assertThat(converted.getCpuUtilization().getPredictiveMethod()).isNull();
  }

  public static AbstractGoogleServerGroupCachingAgent createCachingAgent(
      Compute compute, Collection<InstanceGroupManager> instanceGroupManagers) {
    return createCachingAgent(
        compute, instanceGroupManagers, /* autoscalers= */ ImmutableList.of());
  }

  private GoogleServerGroup getOnlyServerGroup(CacheResult cacheResult) {
    Collection<CacheData> serverGroups = cacheResult.getCacheResults().get(SERVER_GROUPS.getNs());
    assertThat(serverGroups).hasSize(1);
    return objectMapper.convertValue(
        getOnlyElement(serverGroups).getAttributes(), GoogleServerGroup.class);
  }

  private static DefaultProviderCache inMemoryProviderCache() {
    return new DefaultProviderCache(new InMemoryCache());
  }

  public static AbstractGoogleServerGroupCachingAgent createCachingAgent(
      Compute compute,
      Collection<InstanceGroupManager> instanceGroupManagers,
      Collection<Autoscaler> autoscalers) {
    GoogleNamedAccountCredentials credentials =
        new GoogleNamedAccountCredentials.Builder()
            .project(PROJECT)
            .name(ACCOUNT_NAME)
            .compute(compute)
            .regionToZonesMap(ImmutableMap.of(REGION, ImmutableList.of(ZONE)))
            .build();
    GoogleComputeApiFactory computeApiFactory =
        new GoogleComputeApiFactory(
            new GoogleOperationPoller(),
            new DefaultRegistry(),
            "user-agent",
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool()));
    return new TestCachingAgent(credentials, computeApiFactory, instanceGroupManagers, autoscalers);
  }

  @ParametersAreNonnullByDefault
  private static class TestCachingAgent extends AbstractGoogleServerGroupCachingAgent {

    private final Collection<InstanceGroupManager> instanceGroupManagers;
    private final Collection<Autoscaler> autoscalers;

    TestCachingAgent(
        GoogleNamedAccountCredentials credentials,
        GoogleComputeApiFactory computeApiFactory,
        Collection<InstanceGroupManager> instanceGroupManagers,
        Collection<Autoscaler> autoscalers) {
      super(credentials, computeApiFactory, new DefaultRegistry(), REGION, new ObjectMapper());
      this.instanceGroupManagers = instanceGroupManagers;
      this.autoscalers = autoscalers;
    }

    @Override
    Collection<InstanceGroupManager> retrieveInstanceGroupManagers() {
      return instanceGroupManagers;
    }

    @Override
    Collection<Autoscaler> retrieveAutoscalers() {
      return autoscalers;
    }

    @Override
    String getBatchContextPrefix() {
      return getClass().getSimpleName();
    }

    @Override
    Collection<String> getOnDemandKeysToEvictForMissingServerGroup(
        ProviderCache providerCache, String serverGroupName) {
      throw new UnsupportedOperationException("#getOnDemandKeysToEvictForMissingServerGroup()");
    }

    @Override
    boolean keyOwnedByThisAgent(Map<String, String> parsedKey) {
      throw new UnsupportedOperationException("#keyOwnedByThisAgent()");
    }

    @Override
    Optional<InstanceGroupManager> retrieveInstanceGroupManager(String name) {
      throw new UnsupportedOperationException("#retrieveInstanceGroupManager()");
    }

    @Override
    Optional<Autoscaler> retrieveAutoscaler(InstanceGroupManager manager) {
      throw new UnsupportedOperationException("#retrieveAutoscaler()");
    }

    @Override
    Collection<Instance> retrieveRelevantInstances(InstanceGroupManager manager) {
      throw new UnsupportedOperationException("#retrieveRelevantInstances()");
    }
  }
}
