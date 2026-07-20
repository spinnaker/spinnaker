/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationErrors;
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry;
import com.netflix.spinnaker.clouddriver.google.deploy.converters.BasicGoogleDeployAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException;
import com.netflix.spinnaker.clouddriver.google.deploy.ops.GoogleUserDataProvider;
import com.netflix.spinnaker.clouddriver.google.deploy.validators.BasicGoogleDeployDescriptionValidator;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoHealingPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleDistributionPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck;
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleNetwork;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.model.GoogleSubnet;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalHttpLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerView;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancingPolicy;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleNetworkLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleSslLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleNetworkProvider;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSubnetProvider;
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.google.test.CapturingComputeTransport;
import com.netflix.spinnaker.clouddriver.google.test.CapturingComputeTransport.CapturedRequest;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.config.GoogleConfiguration;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.CollectionUtils;

@ExtendWith(MockitoExtension.class)
public class BasicGoogleDeployHandlerTest {
  @Mock private GoogleConfigurationProperties googleConfigurationProperties;
  @Mock private GoogleClusterProvider googleClusterProvider;
  @Mock private GoogleConfiguration.DeployDefaults googleDeployDefaults;
  @Mock private GoogleOperationPoller googleOperationPoller;
  @Mock private GoogleUserDataProvider googleUserDataProvider;
  @Mock private GoogleLoadBalancerProvider googleLoadBalancerProvider;
  @Mock private GoogleNetworkProvider googleNetworkProvider;
  @Mock private GoogleSubnetProvider googleSubnetProvider;
  @Mock private Cache cacheView;
  private ObjectMapper objectMapper = new ObjectMapper();
  @Mock private SafeRetry safeRetry;
  @Mock private Registry registry;

  @InjectMocks @Spy private BasicGoogleDeployHandler basicGoogleDeployHandler;

  private BasicGoogleDeployDescription mockDescription;
  private GoogleNamedAccountCredentials mockCredentials;
  private Task mockTask;
  GoogleAutoscalingPolicy mockAutoscalingPolicy;
  private MockedStatic<GCEUtil> mockedGCEUtil;
  private MockedStatic<Utils> mockedUtils;

  @BeforeEach
  void setUp() throws Exception {
    mockDescription = new BasicGoogleDeployDescription();
    mockCredentials = mock(GoogleNamedAccountCredentials.class);
    mockTask = mock(Task.class);
    mockedGCEUtil = mockStatic(GCEUtil.class);
    mockedUtils = mockStatic(Utils.class);
    mockAutoscalingPolicy = mock(GoogleAutoscalingPolicy.class);

    // Manually inject the real ObjectMapper using reflection
    Field objectMapperField = BasicGoogleDeployHandler.class.getDeclaredField("objectMapper");
    objectMapperField.setAccessible(true);
    objectMapperField.set(basicGoogleDeployHandler, objectMapper);
  }

  @AfterEach
  void tearDown() {
    mockedGCEUtil.close();
    mockedUtils.close();
  }

  @Test
  void testGetRegionFromInput_WithNonBlankRegion() {
    mockDescription.setRegion("us-central1");
    assertEquals("us-central1", basicGoogleDeployHandler.getRegionFromInput(mockDescription));
  }

  @Test
  void testGetRegionFromInput_WithBlankRegion() {
    mockDescription.setRegion("");
    mockDescription.setZone("us-central1-a");
    mockDescription.setCredentials(mockCredentials);
    when(mockCredentials.regionFromZone("us-central1-a")).thenReturn("us-central1");

    String result = basicGoogleDeployHandler.getRegionFromInput(mockDescription);
    assertEquals("us-central1", result);
  }

  @Test
  void testGetRegionFromInput_WithNullRegion() {
    mockDescription.setRegion(null);
    mockDescription.setZone("us-central1-a");
    mockDescription.setCredentials(mockCredentials);
    when(mockCredentials.regionFromZone("us-central1-a")).thenReturn("us-central1");

    String result = basicGoogleDeployHandler.getRegionFromInput(mockDescription);
    assertEquals("us-central1", result);
  }

  @Test
  void testGetLocationFromInput_RegionalTrue() {
    String region = "us-central1";
    mockDescription.setRegion(region);
    mockDescription.setRegional(true);
    assertEquals(region, basicGoogleDeployHandler.getLocationFromInput(mockDescription, region));
  }

  @Test
  void testGetLocationFromInput_RegionalFalse() {
    String zone = "us-central1-a";
    mockDescription.setRegional(false);
    mockDescription.setZone(zone);

    assertEquals(zone, basicGoogleDeployHandler.getLocationFromInput(mockDescription, ""));
  }

  @Test
  void testGetLocationFromInput_RegionalOmittedResolvesZonally() {
    String zone = "us-central1-a";
    mockDescription.setRegional(null);
    mockDescription.setZone(zone);

    assertEquals(zone, basicGoogleDeployHandler.getLocationFromInput(mockDescription, ""));
  }

  @Test
  void testGetMachineTypeNameFromInput_WithCustomInstanceType() {
    String instanceType = "custom-4-16384";
    mockDescription.setInstanceType(instanceType);

    assertEquals(
        instanceType,
        basicGoogleDeployHandler.getMachineTypeNameFromInput(
            mockDescription, mockTask, "location"));
  }

  @Test
  void getMachineTypeNameFromInput_NullRegionalConfig() {
    String instanceType = "custom-4-16384";
    mockDescription.setInstanceType(instanceType);

    assertEquals(
        instanceType,
        basicGoogleDeployHandler.getMachineTypeNameFromInput(
            mockDescription, mockTask, "location"));
  }

  @Test
  void getMachineTypeNameFromInput_NullSelectZone() {
    String instanceType = "whatever-4-16384";
    mockDescription.setInstanceType(instanceType);
    mockDescription.setSelectZones(null);
    mockDescription.setCredentials(mockCredentials);
    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryMachineType(
                    eq(instanceType),
                    eq("location"),
                    eq(mockCredentials),
                    eq(mockTask),
                    eq("DEPLOY")))
        .thenReturn("some-machine");
    assertEquals(
        "some-machine",
        basicGoogleDeployHandler.getMachineTypeNameFromInput(
            mockDescription, mockTask, "location"));
  }

  @Test
  void testGetMachineTypeNameFromInput_WithNonCustomInstanceType() {
    String instanceType = "n1-standard-1";
    String location = "us-central1";
    String machineTypeName = "n1-standard-1-machine";

    mockDescription.setInstanceType(instanceType);
    mockDescription.setCredentials(mockCredentials);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryMachineType(
                    eq(instanceType),
                    eq(location),
                    eq(mockCredentials),
                    eq(mockTask),
                    eq("DEPLOY")))
        .thenReturn(machineTypeName);

    assertEquals(
        machineTypeName,
        basicGoogleDeployHandler.getMachineTypeNameFromInput(mockDescription, mockTask, location));
    mockedGCEUtil.verify(
        () -> GCEUtil.queryMachineType(instanceType, location, mockCredentials, mockTask, "DEPLOY"),
        times(1));
  }

  @Test
  void testGetMachineTypeNameFromInput_RegionalNotAvailableInAllZones() {
    String instanceType = "c4-highcpu-2";
    String location = "us-central1";
    mockDescription.setInstanceType(instanceType);
    mockDescription.setRegional(true);
    mockDescription.setCredentials(mockCredentials);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryMachineType(
                    eq(instanceType),
                    eq(location),
                    eq(mockCredentials),
                    eq(mockTask),
                    eq("DEPLOY")))
        .thenThrow(
            new GoogleOperationException(
                "Machine type "
                    + instanceType
                    + " not found in zone us-central1. When using Regional distribution without explicit selection of Zones, the machine type must be available in all zones of the region"));

    assertThrowsExactly(
        GoogleOperationException.class,
        () -> {
          basicGoogleDeployHandler.getMachineTypeNameFromInput(mockDescription, mockTask, location);
        },
        "Machine type "
            + instanceType
            + " not found in zone us-central1. When using Regional distribution without explicit selection of Zones, the machine type must be available in all zones of the region");

    mockedGCEUtil.verify(
        () -> GCEUtil.queryMachineType(instanceType, location, mockCredentials, mockTask, "DEPLOY"),
        times(1));
  }

  @Test
  void testGetMachineTypeNameFromInput_RegionalAvailableInAllZones() {
    String instanceType = "c4-highcpu-2";
    String location = "us-central1";
    String machineTypeName = "c4-highcpu-2-machine";
    mockDescription.setInstanceType(instanceType);
    mockDescription.setRegional(true);
    mockDescription.setCredentials(mockCredentials);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryMachineType(
                    eq(instanceType),
                    eq(location),
                    eq(mockCredentials),
                    eq(mockTask),
                    eq("DEPLOY")))
        .thenReturn(machineTypeName);

    String result =
        basicGoogleDeployHandler.getMachineTypeNameFromInput(mockDescription, mockTask, location);

    assertEquals(machineTypeName, result);
    mockedGCEUtil.verify(
        () -> GCEUtil.queryMachineType(instanceType, location, mockCredentials, mockTask, "DEPLOY"),
        times(1));
  }

  @Test
  void testGetMachineTypeNameFromInput_RegionalNotAvailableInAllSelectedZones() {
    String instanceType = "c4-highcpu-2";
    String location = "us-central1";
    String machineTypeName = "c4-highcpu-2-machine";

    mockDescription.setInstanceType(instanceType);
    mockDescription.setRegional(true);
    mockDescription.setSelectZones(true);
    mockDescription.setCredentials(mockCredentials);
    mockDescription.setDistributionPolicy(
        new GoogleDistributionPolicy(List.of("us-central1-a", "us-central1-f"), "EVEN"));

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryMachineType(
                    eq(instanceType),
                    eq("us-central1-a"),
                    eq(mockCredentials),
                    eq(mockTask),
                    eq("DEPLOY")))
        .thenReturn(machineTypeName);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryMachineType(
                    eq(instanceType),
                    eq("us-central1-f"),
                    eq(mockCredentials),
                    eq(mockTask),
                    eq("DEPLOY")))
        .thenThrow(
            new GoogleOperationException(
                "Machine type "
                    + instanceType
                    + " not found in zone us-central1-f. When using selectZones, the machine type must be available in all selected zones."));

    assertThrowsExactly(
        GoogleOperationException.class,
        () -> {
          basicGoogleDeployHandler.getMachineTypeNameFromInput(mockDescription, mockTask, location);
        },
        "Machine type "
            + instanceType
            + " not found in zone us-central1-f. When using selectZones, the machine type must be available in all selected zones.");

    mockedGCEUtil.verify(
        () ->
            GCEUtil.queryMachineType(
                instanceType, "us-central1-a", mockCredentials, mockTask, "DEPLOY"),
        times(1));
    mockedGCEUtil.verify(
        () ->
            GCEUtil.queryMachineType(
                instanceType, "us-central1-f", mockCredentials, mockTask, "DEPLOY"),
        times(1));
  }

  @Test
  void testGetMachineTypeNameFromInput_NoDistributionPolicySetUseLocation() {

    String instanceType = "c4-highcpu-2";
    String location = "us-central1";
    String machineTypeName = "c4-highcpu-2-machine";

    mockDescription.setInstanceType(instanceType);
    mockDescription.setRegional(true);
    mockDescription.setSelectZones(true);
    mockDescription.setCredentials(mockCredentials);
    mockDescription.setDistributionPolicy(null);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryMachineType(
                    eq(instanceType),
                    eq(location),
                    eq(mockCredentials),
                    eq(mockTask),
                    eq("DEPLOY")))
        .thenReturn(machineTypeName);

    assertThat(
            basicGoogleDeployHandler.getMachineTypeNameFromInput(
                mockDescription, mockTask, location))
        .isEqualTo(machineTypeName);
  }

  @Test
  void testGetMachineTypeNameFromInput_RegionalAvailableInAllSelectedZones() {
    String instanceType = "c4-highcpu-2";
    String location = "us-central1";
    String machineTypeName = "c4-highcpu-2-machine";
    mockDescription.setInstanceType(instanceType);
    mockDescription.setRegional(true);
    mockDescription.setSelectZones(true);
    mockDescription.setCredentials(mockCredentials);
    mockDescription.setDistributionPolicy(
        new GoogleDistributionPolicy(List.of("us-central1-a", "us-central1-b"), "EVEN"));

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryMachineType(
                    eq(instanceType),
                    eq("us-central1-a"),
                    eq(mockCredentials),
                    eq(mockTask),
                    eq("DEPLOY")))
        .thenReturn(machineTypeName);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryMachineType(
                    eq(instanceType),
                    eq("us-central1-b"),
                    eq(mockCredentials),
                    eq(mockTask),
                    eq("DEPLOY")))
        .thenReturn(machineTypeName);

    String result =
        basicGoogleDeployHandler.getMachineTypeNameFromInput(mockDescription, mockTask, location);

    assertEquals(machineTypeName, result);

    mockedGCEUtil.verify(
        () ->
            GCEUtil.queryMachineType(
                instanceType, "us-central1-a", mockCredentials, mockTask, "DEPLOY"),
        times(1));
    mockedGCEUtil.verify(
        () ->
            GCEUtil.queryMachineType(
                instanceType, "us-central1-b", mockCredentials, mockTask, "DEPLOY"),
        times(1));
  }

  @Test
  void testBuildNetworkFromInput_WithNonBlankNetworkName() {
    String networkName = "custom-network";
    GoogleNetwork mockGoogleNetwork = mock(GoogleNetwork.class);

    when(mockGoogleNetwork.getName()).thenReturn(networkName);
    mockDescription.setNetwork(networkName);
    mockDescription.setAccountName("test-account");

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryNetwork(
                    eq("test-account"), eq(networkName), eq(mockTask), eq("DEPLOY"), any()))
        .thenReturn(mockGoogleNetwork);

    GoogleNetwork result =
        basicGoogleDeployHandler.buildNetworkFromInput(mockDescription, mockTask);

    assertEquals(networkName, result.getName());
  }

  @Test
  void testBuildNetworkFromInput_WithBlankNetworkName() {
    String defaultNetworkName = "default";
    GoogleNetwork mockGoogleNetwork = mock(GoogleNetwork.class);

    mockDescription.setNetwork("");
    mockDescription.setAccountName("test-account");

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryNetwork(
                    eq("test-account"), eq(defaultNetworkName), eq(mockTask), eq("DEPLOY"), any()))
        .thenReturn(mockGoogleNetwork);

    GoogleNetwork result =
        basicGoogleDeployHandler.buildNetworkFromInput(mockDescription, mockTask);
    assertEquals(mockGoogleNetwork, result);
  }

  @Test
  void testBuildSubnetFromInput_WithNonBlankSubnetAndNoAutoCreateSubnets() {
    String region = "us-central1";
    String subnetName = "custom-subnet";
    String networkId = "basic-network";

    GoogleNetwork mockNetwork = mock(GoogleNetwork.class);
    GoogleSubnet mockSubnet = mock(GoogleSubnet.class);
    mockDescription.setSubnet(subnetName);
    mockDescription.setAccountName("test-account");
    when(mockNetwork.getId()).thenReturn(networkId);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.querySubnet(
                    eq("test-account"),
                    eq(region),
                    eq(subnetName),
                    eq(mockTask),
                    eq("DEPLOY"),
                    any()))
        .thenReturn(mockSubnet);

    GoogleSubnet result =
        basicGoogleDeployHandler.buildSubnetFromInput(
            mockDescription, mockTask, mockNetwork, region);
    assertEquals(mockSubnet, result);
  }

  @Test
  void testBuildSubnetFromInput_WithNonBlankSubnetAndAutoCreateSubnets() {
    String region = "us-central1";
    String subnetName = "custom-subnet";
    String networkId = "projects/test-network";

    mockDescription.setSubnet(subnetName);
    mockDescription.setAccountName("test-account");
    GoogleNetwork mockNetwork = new GoogleNetwork();
    GoogleSubnet mockSubnet = new GoogleSubnet();
    mockNetwork.setId(networkId);
    mockNetwork.setAutoCreateSubnets(true);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.querySubnet(
                    eq("test-account"),
                    eq(region),
                    eq(subnetName),
                    eq(mockTask),
                    eq("DEPLOY"),
                    any()))
        .thenReturn(mockSubnet);
    mockedGCEUtil
        .when(
            () ->
                GCEUtil.querySubnet(
                    eq("test-account"),
                    eq(region),
                    eq(networkId),
                    eq(mockTask),
                    eq("DEPLOY"),
                    any()))
        .thenReturn(mockSubnet);

    GoogleSubnet result =
        basicGoogleDeployHandler.buildSubnetFromInput(
            mockDescription, mockTask, mockNetwork, region);
    assertEquals(mockSubnet, result);
    mockedGCEUtil.verify(
        () -> GCEUtil.querySubnet(any(), any(), any(), any(), any(), any()), times(2));
  }

  @Test
  void testAutoCreateSubnetsNull() {
    String region = "us-central1";
    String subnetName = "custom-subnet";
    String networkId = "projects/test-network";

    mockDescription.setSubnet(subnetName);
    mockDescription.setAccountName("test-account");
    GoogleNetwork mockNetwork = new GoogleNetwork();
    GoogleSubnet mockSubnet = new GoogleSubnet();
    mockNetwork.setId(networkId);
    mockNetwork.setAutoCreateSubnets(null);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.querySubnet(
                    eq("test-account"),
                    eq(region),
                    eq(subnetName),
                    eq(mockTask),
                    eq("DEPLOY"),
                    any()))
        .thenReturn(mockSubnet);

    assertThat(
            basicGoogleDeployHandler.buildSubnetFromInput(
                mockDescription, mockTask, mockNetwork, region))
        .isEqualTo(mockSubnet);
  }

  @Test
  void testBuildSubnetFromInput_WithBlankSubnetAndNoAutoCreateSubnets() {
    String region = "us-central1";

    mockDescription.setSubnet("");
    GoogleNetwork mockNetwork = new GoogleNetwork();

    GoogleSubnet result =
        basicGoogleDeployHandler.buildSubnetFromInput(
            mockDescription, mockTask, mockNetwork, region);
    assertNull(result);
    mockedGCEUtil.verifyNoInteractions();
  }

  @Test
  void testGetLoadBalancerToUpdateFromInput_WithNullLLB() {
    mockDescription.setLoadBalancers(null);

    BasicGoogleDeployHandler.LoadBalancerInfo result =
        basicGoogleDeployHandler.getLoadBalancerToUpdateFromInput(mockDescription, mockTask);

    assertNotNull(result);
    assertTrue(result.internalLoadBalancers.isEmpty());
    assertTrue(result.internalHttpLoadBalancers.isEmpty());
    assertTrue(result.sslLoadBalancers.isEmpty());
    assertTrue(result.tcpLoadBalancers.isEmpty());
    assertTrue(result.targetPools.isEmpty());

    mockedGCEUtil.verifyNoInteractions();
  }

  @Test
  void testGetLoadBalancerToUpdateFromInput_WithEmptyLoadBalancers() {
    mockDescription.setLoadBalancers(Collections.emptyList());

    BasicGoogleDeployHandler.LoadBalancerInfo result =
        basicGoogleDeployHandler.getLoadBalancerToUpdateFromInput(mockDescription, mockTask);

    assertNotNull(result);
    assertTrue(result.internalLoadBalancers.isEmpty());
    assertTrue(result.internalHttpLoadBalancers.isEmpty());
    assertTrue(result.sslLoadBalancers.isEmpty());
    assertTrue(result.tcpLoadBalancers.isEmpty());
    assertTrue(result.targetPools.isEmpty());

    mockedGCEUtil.verifyNoInteractions();
  }

  @Test
  void testGetLoadBalancerToUpdateFromInput_WithNonEmptyLoadBalancers_TrafficDisabled() {
    List<String> loadBalancerNames = Arrays.asList("lb1", "lb2");
    mockDescription.setLoadBalancers(loadBalancerNames);
    mockDescription.setDisableTraffic(true);

    List<GoogleLoadBalancerView> foundLoadBalancers =
        Arrays.asList(
            mockLoadBalancer(GoogleLoadBalancerType.INTERNAL),
            mockLoadBalancer(GoogleLoadBalancerType.INTERNAL_MANAGED),
            mockLoadBalancer(GoogleLoadBalancerType.SSL),
            mockLoadBalancer(GoogleLoadBalancerType.TCP),
            mockLoadBalancer(GoogleLoadBalancerType.NETWORK));

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryAllLoadBalancers(
                    any(), eq(loadBalancerNames), eq(mockTask), eq("DEPLOY")))
        .thenReturn(foundLoadBalancers);

    BasicGoogleDeployHandler.LoadBalancerInfo result =
        basicGoogleDeployHandler.getLoadBalancerToUpdateFromInput(mockDescription, mockTask);

    assertNotNull(result);
    assertEquals(1, result.internalLoadBalancers.size());
    assertEquals(1, result.internalHttpLoadBalancers.size());
    assertEquals(1, result.sslLoadBalancers.size());
    assertEquals(1, result.tcpLoadBalancers.size());
    assertTrue(result.targetPools.isEmpty());

    mockedGCEUtil.verify(
        () ->
            GCEUtil.queryAllLoadBalancers(any(), eq(loadBalancerNames), eq(mockTask), eq("DEPLOY")),
        times(1));
  }

  @Test
  void testGetLoadBalancerToUpdateFromInput_WithNonEmptyLoadBalancers_TrafficEnabled() {
    List<String> loadBalancerNames = Arrays.asList("lb1", "lb2");
    mockDescription.setLoadBalancers(loadBalancerNames);
    mockDescription.setDisableTraffic(false);

    GoogleNetworkLoadBalancer nlb = new GoogleNetworkLoadBalancer();
    nlb.setTargetPool("target-pool");
    GoogleLoadBalancerView lbv = nlb.getView();

    List<GoogleLoadBalancerView> foundLoadBalancers =
        Arrays.asList(
            mockLoadBalancer(GoogleLoadBalancerType.INTERNAL),
            mockLoadBalancer(GoogleLoadBalancerType.INTERNAL_MANAGED),
            mockLoadBalancer(GoogleLoadBalancerType.SSL),
            mockLoadBalancer(GoogleLoadBalancerType.TCP),
            lbv);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryAllLoadBalancers(
                    any(), eq(loadBalancerNames), eq(mockTask), eq("DEPLOY")))
        .thenReturn(foundLoadBalancers);

    BasicGoogleDeployHandler.LoadBalancerInfo result =
        basicGoogleDeployHandler.getLoadBalancerToUpdateFromInput(mockDescription, mockTask);

    assertNotNull(result);
    assertEquals(1, result.internalLoadBalancers.size());
    assertEquals(1, result.internalHttpLoadBalancers.size());
    assertEquals(1, result.sslLoadBalancers.size());
    assertEquals(1, result.tcpLoadBalancers.size());
    assertEquals(1, result.targetPools.size());
    assertEquals("target-pool", result.targetPools.get(0));

    mockedGCEUtil.verify(
        () ->
            GCEUtil.queryAllLoadBalancers(any(), eq(loadBalancerNames), eq(mockTask), eq("DEPLOY")),
        times(1));
  }

  @Test
  void testGetLoadBalancerToUpdateFromInput_WithNonEmptyLoadBalancers_TrafficEnabled_Null() {
    List<String> loadBalancerNames = Arrays.asList("lb1", "lb2");
    mockDescription.setLoadBalancers(loadBalancerNames);
    mockDescription.setDisableTraffic(null);

    GoogleNetworkLoadBalancer nlb = new GoogleNetworkLoadBalancer();
    nlb.setTargetPool("target-pool");
    GoogleLoadBalancerView lbv = nlb.getView();

    List<GoogleLoadBalancerView> foundLoadBalancers =
        Arrays.asList(
            mockLoadBalancer(GoogleLoadBalancerType.INTERNAL),
            mockLoadBalancer(GoogleLoadBalancerType.INTERNAL_MANAGED),
            mockLoadBalancer(GoogleLoadBalancerType.SSL),
            mockLoadBalancer(GoogleLoadBalancerType.TCP),
            lbv);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryAllLoadBalancers(
                    any(), eq(loadBalancerNames), eq(mockTask), eq("DEPLOY")))
        .thenReturn(foundLoadBalancers);

    BasicGoogleDeployHandler.LoadBalancerInfo result =
        basicGoogleDeployHandler.getLoadBalancerToUpdateFromInput(mockDescription, mockTask);

    assertNotNull(result);
    assertEquals(1, result.internalLoadBalancers.size());
    assertEquals(1, result.internalHttpLoadBalancers.size());
    assertEquals(1, result.sslLoadBalancers.size());
    assertEquals(1, result.tcpLoadBalancers.size());
    assertEquals(1, result.targetPools.size());
    assertEquals("target-pool", result.targetPools.get(0));

    mockedGCEUtil.verify(
        () ->
            GCEUtil.queryAllLoadBalancers(any(), eq(loadBalancerNames), eq(mockTask), eq("DEPLOY")),
        times(1));
  }

  @Test
  void testBuildBootImage() {
    when(googleConfigurationProperties.getBaseImageProjects())
        .thenReturn(Arrays.asList("base-project-1", "base-project-2"));
    Image mockedImage = mock(Image.class);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.getBootImage(
                    eq(mockDescription),
                    eq(mockTask),
                    eq("DEPLOY"),
                    any(),
                    eq(Arrays.asList("base-project-1", "base-project-2")),
                    eq(safeRetry),
                    any()))
        .thenReturn(mockedImage);

    Image result = basicGoogleDeployHandler.buildBootImage(mockDescription, mockTask);

    mockedGCEUtil.verify(
        () ->
            GCEUtil.getBootImage(
                eq(mockDescription),
                eq(mockTask),
                eq("DEPLOY"),
                any(),
                eq(Arrays.asList("base-project-1", "base-project-2")),
                eq(safeRetry),
                any()));

    assertEquals(mockedImage, result);
  }

  @Test
  void testBuildAttachedDisks() {
    when(googleConfigurationProperties.getBaseImageProjects())
        .thenReturn(Arrays.asList("base-project-1", "base-project-2"));
    List<AttachedDisk> attachedDisksMock =
        Arrays.asList(mock(AttachedDisk.class), mock(AttachedDisk.class));
    Image bootImageMock = mock(Image.class);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.buildAttachedDisks(
                    eq(mockDescription),
                    eq(null),
                    eq(false),
                    eq(googleDeployDefaults),
                    eq(mockTask),
                    eq("DEPLOY"),
                    any(),
                    eq(Arrays.asList("base-project-1", "base-project-2")),
                    eq(bootImageMock),
                    eq(safeRetry),
                    any()))
        .thenReturn(attachedDisksMock);

    List<AttachedDisk> result =
        basicGoogleDeployHandler.buildAttachedDisks(mockDescription, mockTask, bootImageMock);

    // Step 8: Verify that the static method was called with correct arguments
    mockedGCEUtil.verify(
        () ->
            GCEUtil.buildAttachedDisks(
                eq(mockDescription),
                eq(null),
                eq(false),
                eq(googleDeployDefaults),
                eq(mockTask),
                eq("DEPLOY"),
                any(),
                eq(Arrays.asList("base-project-1", "base-project-2")),
                eq(bootImageMock),
                eq(safeRetry),
                any()));

    assertEquals(attachedDisksMock, result);
  }

  @Test
  void testBuildNetworkInterface_AssociatePublicIpAddress() {
    GoogleNetwork networkMock = mock(GoogleNetwork.class);
    GoogleSubnet subnetMock = mock(GoogleSubnet.class);
    NetworkInterface networkInterfaceMock = mock(NetworkInterface.class);

    mockDescription.setAssociatePublicIpAddress(null);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.buildNetworkInterface(
                    eq(networkMock),
                    eq(subnetMock),
                    eq(true),
                    eq("External NAT"),
                    eq("ONE_TO_ONE_NAT")))
        .thenReturn(networkInterfaceMock);

    NetworkInterface result =
        basicGoogleDeployHandler.buildNetworkInterface(mockDescription, networkMock, subnetMock);

    mockedGCEUtil.verify(
        () ->
            GCEUtil.buildNetworkInterface(
                eq(networkMock),
                eq(subnetMock),
                eq(true),
                eq("External NAT"),
                eq("ONE_TO_ONE_NAT")));

    assertEquals(networkInterfaceMock, result);
  }

  @Test
  void testBuildNetworkInterface_NoAssociatePublicIpAddress() {
    GoogleNetwork networkMock = mock(GoogleNetwork.class);
    GoogleSubnet subnetMock = mock(GoogleSubnet.class);
    NetworkInterface networkInterfaceMock = mock(NetworkInterface.class);

    mockDescription.setAssociatePublicIpAddress(false);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.buildNetworkInterface(
                    eq(networkMock),
                    eq(subnetMock),
                    eq(false),
                    eq("External NAT"),
                    eq("ONE_TO_ONE_NAT")))
        .thenReturn(networkInterfaceMock);

    NetworkInterface result =
        basicGoogleDeployHandler.buildNetworkInterface(mockDescription, networkMock, subnetMock);

    mockedGCEUtil.verify(
        () ->
            GCEUtil.buildNetworkInterface(
                eq(networkMock),
                eq(subnetMock),
                eq(false),
                eq("External NAT"),
                eq("ONE_TO_ONE_NAT")));

    assertEquals(networkInterfaceMock, result);
  }

  @Test
  void testHasBackedServiceFromInput_WithBackendServiceInMetadata() {
    BasicGoogleDeployHandler.LoadBalancerInfo loadBalancerInfoMock =
        new BasicGoogleDeployHandler.LoadBalancerInfo();

    mockDescription.setInstanceMetadata(Map.of("backend-service-names", "some-backend-service"));

    assertTrue(
        basicGoogleDeployHandler.hasBackedServiceFromInput(mockDescription, loadBalancerInfoMock));
  }

  @Test
  void testHasBackedServiceFromInput_WithNullData() {
    BasicGoogleDeployHandler.LoadBalancerInfo loadBalancerInfoMock =
        new BasicGoogleDeployHandler.LoadBalancerInfo();

    mockDescription.setInstanceMetadata(null);

    assertFalse(
        basicGoogleDeployHandler.hasBackedServiceFromInput(mockDescription, loadBalancerInfoMock));
  }

  @Test
  void testHasBackedServiceFromInput_WithSslLoadBalancers() {
    BasicGoogleDeployHandler.LoadBalancerInfo loadBalancerInfoMock =
        mock(BasicGoogleDeployHandler.LoadBalancerInfo.class);
    mockDescription.setInstanceMetadata(Collections.emptyMap());

    List<GoogleLoadBalancerView> sslLoadBalancers =
        Arrays.asList(new GoogleLoadBalancerView() {}, new GoogleLoadBalancerView() {});
    when(loadBalancerInfoMock.getSslLoadBalancers()).thenReturn(sslLoadBalancers);

    boolean result =
        basicGoogleDeployHandler.hasBackedServiceFromInput(mockDescription, loadBalancerInfoMock);
    assertTrue(result);
  }

  @Test
  void testHasBackedServiceFromInput_WithoutBackedService() {
    BasicGoogleDeployHandler.LoadBalancerInfo loadBalancerInfoMock =
        mock(BasicGoogleDeployHandler.LoadBalancerInfo.class);

    mockDescription.setInstanceMetadata(Collections.emptyMap());
    when(loadBalancerInfoMock.getSslLoadBalancers()).thenReturn(Collections.emptyList());
    when(loadBalancerInfoMock.getTcpLoadBalancers()).thenReturn(Collections.emptyList());

    boolean result =
        basicGoogleDeployHandler.hasBackedServiceFromInput(mockDescription, loadBalancerInfoMock);
    assertFalse(result);
  }

  @Test
  void testHasBackedServiceFromInput_WithTcpLoadBalancers() {
    BasicGoogleDeployHandler.LoadBalancerInfo loadBalancerInfoMock =
        mock(BasicGoogleDeployHandler.LoadBalancerInfo.class);

    mockDescription.setInstanceMetadata(Collections.emptyMap());

    List<GoogleLoadBalancerView> tcpLoadBalancers = Arrays.asList(new GoogleLoadBalancerView() {});
    when(loadBalancerInfoMock.getSslLoadBalancers()).thenReturn(Collections.emptyList());
    when(loadBalancerInfoMock.getTcpLoadBalancers()).thenReturn(tcpLoadBalancers);

    boolean result =
        basicGoogleDeployHandler.hasBackedServiceFromInput(mockDescription, loadBalancerInfoMock);
    assertTrue(result);
  }

  @Test
  void testBuildLoadBalancerPolicyFromInput_PolicyInDescription() throws Exception {
    GoogleHttpLoadBalancingPolicy policyMock = new GoogleHttpLoadBalancingPolicy();
    policyMock.setBalancingMode(GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION);
    mockDescription.setLoadBalancingPolicy(policyMock);
    mockDescription.setInstanceMetadata(Collections.emptyMap());

    assertEquals(
        policyMock, basicGoogleDeployHandler.buildLoadBalancerPolicyFromInput(mockDescription));
  }

  @Test
  void testBuildLoadBalancerPolicyFromInput_PolicyInMetadata() throws Exception {

    mockDescription.setLoadBalancingPolicy(null);
    mockDescription.setInstanceMetadata(
        Map.of(
            "load-balancing-policy",
            "{\"balancingMode\": \"UTILIZATION\", \"maxUtilization\": 0.75}"));

    GoogleHttpLoadBalancingPolicy result =
        basicGoogleDeployHandler.buildLoadBalancerPolicyFromInput(mockDescription);

    // Verify the JSON was parsed correctly
    assertNotNull(result);
    assertEquals(GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION, result.getBalancingMode());
    assertEquals(0.75f, result.getMaxUtilization());
  }

  @Test
  void testBuildLoadBalancerPolicyFromInput_DefaultPolicy() throws Exception {
    mockDescription.setLoadBalancingPolicy(null);
    mockDescription.setInstanceMetadata(Collections.emptyMap());

    GoogleHttpLoadBalancingPolicy result =
        basicGoogleDeployHandler.buildLoadBalancerPolicyFromInput(mockDescription);

    assertEquals(GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION, result.getBalancingMode());
    assertEquals(0.80f, result.getMaxUtilization());
    assertEquals(1.0f, result.getCapacityScaler());
    assertNotNull(result.getNamedPorts());
    assertEquals(1, result.getNamedPorts().size());
    assertEquals(
        GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME,
        result.getNamedPorts().get(0).getName());
    assertEquals(
        GoogleHttpLoadBalancingPolicy.getHTTP_DEFAULT_PORT(),
        result.getNamedPorts().get(0).getPort());
  }

  @Test
  void testGetBackendServiceToUpdate_NoBackendService() {
    BasicGoogleDeployHandler.LoadBalancerInfo lbInfoMock =
        new BasicGoogleDeployHandler.LoadBalancerInfo();
    doReturn(false)
        .when(basicGoogleDeployHandler)
        .hasBackedServiceFromInput(mockDescription, lbInfoMock);

    List<BackendService> result =
        basicGoogleDeployHandler.getBackendServiceToUpdate(
            mockDescription, "serverGroupName", lbInfoMock, null, "region");
    assertTrue(result.isEmpty());
  }

  @Test
  void testGetBackendServiceToUpdate_WithBackendService() throws Exception {
    HashMap<String, String> instanceMetadata = new HashMap<>();
    instanceMetadata.put("backend-service-names", "backend-service-1,backend-service2");
    mockDescription.setInstanceMetadata(instanceMetadata);
    mockDescription.setCredentials(mock(GoogleNamedAccountCredentials.class));
    mockDescription.setRegional(true);

    GoogleBackendService backendServiceMock = new GoogleBackendService();
    backendServiceMock.setName("backend-service-ssl");

    GoogleSslLoadBalancer googleSslLB = new GoogleSslLoadBalancer();
    googleSslLB.setBackendService(backendServiceMock);

    List<GoogleLoadBalancerView> sslLB = new ArrayList<>();
    sslLB.add(googleSslLB.getView());

    BasicGoogleDeployHandler.LoadBalancerInfo lbInfoMock =
        new BasicGoogleDeployHandler.LoadBalancerInfo();
    lbInfoMock.setSslLoadBalancers(sslLB);

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.resolveHttpLoadBalancerNamesMetadata(anyList(), any(), anyString(), any()))
        .thenReturn(Arrays.asList("lb-1", "lb-2"));
    doReturn(mock(BackendService.class))
        .when(basicGoogleDeployHandler)
        .getBackendServiceFromProvider(any(), anyString());
    mockedGCEUtil
        .when(() -> GCEUtil.updateMetadataWithLoadBalancingPolicy(any(), any(), any()))
        .then(Answers.RETURNS_SMART_NULLS);
    mockedGCEUtil
        .when(() -> GCEUtil.backendFromLoadBalancingPolicy(any()))
        .thenReturn(new Backend());
    doReturn(true)
        .when(basicGoogleDeployHandler)
        .hasBackedServiceFromInput(mockDescription, lbInfoMock);

    List<BackendService> result =
        basicGoogleDeployHandler.getBackendServiceToUpdate(
            mockDescription,
            "serverGroupName",
            lbInfoMock,
            new GoogleHttpLoadBalancingPolicy(),
            "region");
    assertNotNull(result);
    assertEquals(3, result.size());
  }

  @Test
  void testGetBackendServiceToUpdate_WithNullMetadataAndLoadBalancerInitializesMetadata()
      throws Exception {
    mockDescription.setInstanceMetadata(null);
    mockDescription.setCredentials(mockCredentials);
    mockDescription.setRegional(true);

    GoogleBackendService backendServiceMock = new GoogleBackendService();
    backendServiceMock.setName("backend-service-ssl");

    GoogleSslLoadBalancer googleSslLB = new GoogleSslLoadBalancer();
    googleSslLB.setName("ssl-lb");
    googleSslLB.setBackendService(backendServiceMock);

    BasicGoogleDeployHandler.LoadBalancerInfo lbInfo =
        new BasicGoogleDeployHandler.LoadBalancerInfo();
    lbInfo.setSslLoadBalancers(List.of(googleSslLB.getView()));

    mockedGCEUtil
        .when(
            () ->
                GCEUtil.resolveHttpLoadBalancerNamesMetadata(anyList(), any(), anyString(), any()))
        .thenReturn(List.of("http-lb"));
    doReturn(new BackendService())
        .when(basicGoogleDeployHandler)
        .getBackendServiceFromProvider(any(), anyString());
    mockedGCEUtil
        .when(() -> GCEUtil.updateMetadataWithLoadBalancingPolicy(any(), any(), any()))
        .then(Answers.RETURNS_SMART_NULLS);
    mockedGCEUtil
        .when(() -> GCEUtil.backendFromLoadBalancingPolicy(any()))
        .thenReturn(new Backend());
    when(mockCredentials.getProject()).thenReturn("test-project");

    List<BackendService> result =
        basicGoogleDeployHandler.getBackendServiceToUpdate(
            mockDescription,
            "serverGroupName",
            lbInfo,
            new GoogleHttpLoadBalancingPolicy(),
            "us-central1");

    assertThat(result).hasSize(1);
    assertThat(mockDescription.getInstanceMetadata())
        .containsEntry("global-load-balancer-names", "ssl-lb,http-lb");
  }

  @Test
  void testGetRegionBackendServicesToUpdateWithNoLoadBalancers() {
    List<BackendService> result =
        basicGoogleDeployHandler.getRegionBackendServicesToUpdate(
            mockDescription,
            "server-group-name",
            new BasicGoogleDeployHandler.LoadBalancerInfo(),
            new GoogleHttpLoadBalancingPolicy(),
            "region");

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetRegionBackendServicesToUpdateWithInternalLoadBalancers() throws IOException {
    GoogleHttpLoadBalancingPolicy policyMock = mock(GoogleHttpLoadBalancingPolicy.class);
    BasicGoogleDeployHandler.LoadBalancerInfo lbInfoMock =
        mock(BasicGoogleDeployHandler.LoadBalancerInfo.class);
    GoogleBackendService backendServiceMock = mock(GoogleBackendService.class);
    when(backendServiceMock.getName()).thenReturn("backend-service-internal");

    List<GoogleLoadBalancerView> internalLB = new ArrayList<>();
    GoogleInternalLoadBalancer googleInternalLB = new GoogleInternalLoadBalancer();
    googleInternalLB.setBackendService(backendServiceMock);
    googleInternalLB.setName("internal-load-balancer");
    internalLB.add(googleInternalLB.getView());
    when(lbInfoMock.getInternalLoadBalancers()).thenReturn(internalLB);

    List<GoogleLoadBalancerView> internaHttplLB = new ArrayList<>();
    GoogleInternalHttpLoadBalancer googleInternalHttpLB = new GoogleInternalHttpLoadBalancer();
    googleInternalHttpLB.setName("internal-http-load-balancer");
    internaHttplLB.add(googleInternalHttpLB.getView());
    when(lbInfoMock.getInternalHttpLoadBalancers()).thenReturn(internaHttplLB);

    Map<String, String> instanceMetadata = new HashMap<>();
    instanceMetadata.put("load-balancer-names", "load-balancer-1,load-balancer-2");
    instanceMetadata.put("region-backend-service-names", "us-central1-backend");
    mockDescription.setInstanceMetadata(instanceMetadata);
    mockDescription.setCredentials(mockCredentials);
    mockDescription.setZone("us-central1-a");

    String region = "us-central1";
    doReturn(mock(BackendService.class))
        .when(basicGoogleDeployHandler)
        .getRegionBackendServiceFromProvider(any(), any(), any());

    mockedGCEUtil
        .when(() -> GCEUtil.buildZonalServerGroupUrl(any(), any(), any()))
        .thenReturn("zonal-server-group-url");
    GoogleBackendService googleBackendService = new GoogleBackendService();
    googleBackendService.setName("google-backend-service");
    mockedUtils
        .when(() -> Utils.getBackendServicesFromInternalHttpLoadBalancerView(any()))
        .thenReturn(List.of(googleBackendService));

    List<BackendService> result =
        basicGoogleDeployHandler.getRegionBackendServicesToUpdate(
            mockDescription, "server-group-name", lbInfoMock, policyMock, region);
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(
        "load-balancer-1,load-balancer-2,internal-load-balancer,internal-http-load-balancer",
        instanceMetadata.get("load-balancer-names"));
  }

  @Test
  void testGetRegionBackendServicesToUpdateWithNullMetadataAndInternalLoadBalancers()
      throws IOException {
    GoogleHttpLoadBalancingPolicy policy = new GoogleHttpLoadBalancingPolicy();
    BasicGoogleDeployHandler.LoadBalancerInfo lbInfo =
        mock(BasicGoogleDeployHandler.LoadBalancerInfo.class);
    GoogleBackendService backendService = new GoogleBackendService();
    backendService.setName("backend-service-internal");

    GoogleInternalLoadBalancer googleInternalLB = new GoogleInternalLoadBalancer();
    googleInternalLB.setBackendService(backendService);
    googleInternalLB.setName("internal-load-balancer");
    when(lbInfo.getInternalLoadBalancers()).thenReturn(List.of(googleInternalLB.getView()));
    when(lbInfo.getInternalHttpLoadBalancers()).thenReturn(Collections.emptyList());
    mockDescription.setInstanceMetadata(null);
    mockDescription.setCredentials(mockCredentials);
    mockDescription.setRegional(true);
    when(mockCredentials.getProject()).thenReturn("test-project");
    doReturn(new BackendService())
        .when(basicGoogleDeployHandler)
        .getRegionBackendServiceFromProvider(any(), any(), any());
    mockedGCEUtil
        .when(() -> GCEUtil.buildRegionalServerGroupUrl(any(), any(), any()))
        .thenReturn("regional-server-group-url");

    List<BackendService> result =
        basicGoogleDeployHandler.getRegionBackendServicesToUpdate(
            mockDescription, "server-group-name", lbInfo, policy, "us-central1");

    assertThat(result).hasSize(1);
    assertThat(mockDescription.getInstanceMetadata())
        .containsEntry("load-balancer-names", "internal-load-balancer");
  }

  @Test
  void testAddUserDataToInstanceMetadata_WithEmptyMetadata() {
    String serverGroupName = "test-server-group";
    String instanceTemplateName = "test-template";
    Map<String, String> userDataMap = new HashMap<>();
    userDataMap.put("key1", "value1");

    mockDescription.setInstanceMetadata(new HashMap<>());
    doReturn(userDataMap)
        .when(basicGoogleDeployHandler)
        .getUserData(mockDescription, serverGroupName, instanceTemplateName, mockTask);

    basicGoogleDeployHandler.addUserDataToInstanceMetadata(
        mockDescription, serverGroupName, instanceTemplateName, mockTask);

    verify(basicGoogleDeployHandler)
        .getUserData(mockDescription, serverGroupName, instanceTemplateName, mockTask);

    assertEquals(1, mockDescription.getInstanceMetadata().size());
    assertEquals("value1", mockDescription.getInstanceMetadata().get("key1"));
  }

  @Test
  void testAddUserDataToInstanceMetadata_WithNonEmptyMetadata() {
    String serverGroupName = "test-server-group";
    String instanceTemplateName = "test-template";
    Map<String, String> existingMetadata = new HashMap<>();
    existingMetadata.put("existingKey", "existingValue");
    Map<String, String> userDataMap = new HashMap<>();
    userDataMap.put("key1", "value1");
    mockDescription.setInstanceMetadata(existingMetadata);
    doReturn(userDataMap)
        .when(basicGoogleDeployHandler)
        .getUserData(mockDescription, serverGroupName, instanceTemplateName, mockTask);

    basicGoogleDeployHandler.addUserDataToInstanceMetadata(
        mockDescription, serverGroupName, instanceTemplateName, mockTask);

    verify(basicGoogleDeployHandler)
        .getUserData(mockDescription, serverGroupName, instanceTemplateName, mockTask);

    assertEquals(2, mockDescription.getInstanceMetadata().size());
    assertEquals("existingValue", mockDescription.getInstanceMetadata().get("existingKey"));
    assertEquals("value1", mockDescription.getInstanceMetadata().get("key1"));
  }

  @Test
  void testGetUserData_WithCustomUserData() {
    String serverGroupName = "test-server-group";
    String instanceTemplateName = "test-template";
    String customUserData = "custom-data";
    mockDescription.setUserData(customUserData);

    Map<String, String> mockUserData = Map.of("key", "value");

    when(googleUserDataProvider.getUserData(
            serverGroupName,
            instanceTemplateName,
            mockDescription,
            mockDescription.getCredentials(),
            customUserData))
        .thenReturn(mockUserData);

    Map<String, String> result =
        basicGoogleDeployHandler.getUserData(
            mockDescription, serverGroupName, instanceTemplateName, mockTask);

    verify(googleUserDataProvider)
        .getUserData(
            serverGroupName,
            instanceTemplateName,
            mockDescription,
            mockDescription.getCredentials(),
            customUserData);
    verify(mockTask).updateStatus("DEPLOY", "Resolved user data.");
    assertEquals(mockUserData, result);
  }

  @Test
  void testGetUserData_WithEmptyCustomUserData() {
    String serverGroupName = "test-server-group";
    String instanceTemplateName = "test-template";
    String emptyUserData = "";
    mockDescription.setUserData(null);

    Map<String, String> mockUserData = Map.of("key", "value");

    when(googleUserDataProvider.getUserData(
            serverGroupName,
            instanceTemplateName,
            mockDescription,
            mockDescription.getCredentials(),
            emptyUserData))
        .thenReturn(mockUserData);

    Map<String, String> result =
        basicGoogleDeployHandler.getUserData(
            mockDescription, serverGroupName, instanceTemplateName, mockTask);

    verify(googleUserDataProvider)
        .getUserData(
            serverGroupName,
            instanceTemplateName,
            mockDescription,
            mockDescription.getCredentials(),
            emptyUserData);
    verify(mockTask).updateStatus("DEPLOY", "Resolved user data.");
    assertEquals(mockUserData, result);
  }

  @Test
  void testAddSelectZonesToInstanceMetadata_RegionalAndSelectZonesTrue() {
    mockDescription.setRegional(true);
    mockDescription.setSelectZones(true);

    mockDescription.setInstanceMetadata(new HashMap<>());

    basicGoogleDeployHandler.addSelectZonesToInstanceMetadata(mockDescription);

    assertTrue(mockDescription.getInstanceMetadata().containsKey("select-zones"));
    assertEquals("true", mockDescription.getInstanceMetadata().get("select-zones"));
  }

  @Test
  void testAddSelectZonesToInstanceMetadata_NonRegional() {
    mockDescription.setRegional(false);
    basicGoogleDeployHandler.addSelectZonesToInstanceMetadata(mockDescription);
    assertNull(mockDescription.getInstanceMetadata());
  }

  @Test
  void testAddSelectZonesToInstanceMetadata_ZonesWasntSet() {
    mockDescription.setRegional(true);
    basicGoogleDeployHandler.addSelectZonesToInstanceMetadata(mockDescription);
    assertNull(mockDescription.getInstanceMetadata());
  }

  @Test
  void testAddSelectZonesToInstanceMetadata_SelectZonesFalse() {
    mockDescription.setRegional(true);
    mockDescription.setSelectZones(false);

    basicGoogleDeployHandler.addSelectZonesToInstanceMetadata(mockDescription);
    assertNull(mockDescription.getInstanceMetadata());
  }

  @Test
  void addSelectZonesToInstanceMetadata_regionalFalse_removesStaleMarkerFromCopy() {
    Map<String, String> sourceMetadata =
        new HashMap<>(Map.of("select-zones", "true", "unrelated-key", "unrelated-value"));
    mockDescription.setRegional(true);
    mockDescription.setSelectZones(false);
    mockDescription.setInstanceMetadata(sourceMetadata);

    basicGoogleDeployHandler.addSelectZonesToInstanceMetadata(mockDescription);

    assertThat(mockDescription.getInstanceMetadata())
        .containsOnly(Map.entry("unrelated-key", "unrelated-value"));
    assertThat(mockDescription.getInstanceMetadata()).isNotSameAs(sourceMetadata);
    assertThat(sourceMetadata)
        .containsEntry("select-zones", "true")
        .containsEntry("unrelated-key", "unrelated-value");
  }

  @Test
  void addSelectZonesToInstanceMetadata_nonRegional_removesStaleMarkerAndPreservesMetadata() {
    Map<String, String> sourceMetadata =
        new HashMap<>(Map.of("select-zones", "true", "unrelated-key", "unrelated-value"));
    mockDescription.setRegional(false);
    mockDescription.setSelectZones(true);
    mockDescription.setInstanceMetadata(sourceMetadata);

    basicGoogleDeployHandler.addSelectZonesToInstanceMetadata(mockDescription);

    assertThat(mockDescription.getInstanceMetadata())
        .containsOnly(Map.entry("unrelated-key", "unrelated-value"));
    assertThat(mockDescription.getInstanceMetadata()).isNotSameAs(sourceMetadata);
  }

  @Test
  void instanceTemplateInsertBody_explicitFalseOmitsInheritedSelectZonesMetadata()
      throws Exception {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setRegional(true);
    description.setSelectZones(false);
    description.setInstanceMetadata(
        new HashMap<>(Map.of("select-zones", "true", "unrelated-key", "unrelated-value")));

    basicGoogleDeployHandler.addSelectZonesToInstanceMetadata(description);
    mockedGCEUtil
        .when(() -> GCEUtil.buildMetadataFromMap(description.getInstanceMetadata()))
        .thenCallRealMethod();
    Metadata metadata = basicGoogleDeployHandler.buildMetadataFromInstanceMetadata(description);
    InstanceTemplate template =
        basicGoogleDeployHandler.buildInstanceTemplate(
            "example-template", new InstanceProperties().setMetadata(metadata));

    CapturingComputeTransport transport = new CapturingComputeTransport();
    Compute compute =
        new Compute(
            transport, GsonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
    compute.instanceTemplates().insert("test-project", template).execute();

    CapturedRequest insertRequest =
        transport.findPostTo("/projects/test-project/global/instanceTemplates").orElseThrow();
    JsonNode metadataItems =
        objectMapper
            .readTree(insertRequest.body())
            .path("properties")
            .path("metadata")
            .path("items");

    assertThat(metadataItems.findValuesAsText("key")).containsExactly("unrelated-key");
    assertThat(metadataItems.findValuesAsText("value")).containsExactly("unrelated-value");
    assertThat(insertRequest.body()).doesNotContain("select-zones");
  }

  @Test
  void addZonesNoMetadataSet() {
    mockDescription.setRegional(true);
    mockDescription.setSelectZones(true);

    basicGoogleDeployHandler.addSelectZonesToInstanceMetadata(mockDescription);
    assertThat(mockDescription.getInstanceMetadata().get("select-zones")).isEqualTo("true");
  }

  @Test
  void testBuildMetadataFromInstanceMetadata() {
    Map<String, String> mockInstanceMetadata = new HashMap<>();
    mockInstanceMetadata.put("key1", "value1");
    mockInstanceMetadata.put("key2", "value2");

    Metadata mockMetadata = new Metadata();
    mockMetadata.setItems(new ArrayList<>());
    mockDescription.setInstanceMetadata(mockInstanceMetadata);
    mockedGCEUtil
        .when(() -> GCEUtil.buildMetadataFromMap(mockInstanceMetadata))
        .thenReturn(mockMetadata);

    assertEquals(
        mockMetadata, basicGoogleDeployHandler.buildMetadataFromInstanceMetadata(mockDescription));
  }

  @Test
  void testBuildTagsFromInput() {
    List<String> inputTags = new ArrayList<>();
    inputTags.add("tag1");
    inputTags.add("tag2");

    Tags mockTags = new Tags();
    mockTags.setItems(inputTags);
    mockDescription.setTags(inputTags);
    mockedGCEUtil.when(() -> GCEUtil.buildTagsFromList(inputTags)).thenReturn(mockTags);

    assertEquals(mockTags, basicGoogleDeployHandler.buildTagsFromInput(mockDescription));
  }

  @Test
  void testBuildServiceAccountFromInput_AuthScopesPresent_ServiceAccountEmailBlank() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setAuthScopes(List.of("scope1", "scope2"));
    description.setServiceAccountEmail("");

    ServiceAccount account = new ServiceAccount();
    account.setEmail("default");
    account.setScopes(List.of("scope1", "scope2"));
    List<ServiceAccount> mockServiceAccounts = List.of(account);
    mockedGCEUtil
        .when(() -> GCEUtil.buildServiceAccount(any(), any()))
        .thenReturn(mockServiceAccounts);

    List<ServiceAccount> result =
        basicGoogleDeployHandler.buildServiceAccountFromInput(description);

    assertEquals("default", description.getServiceAccountEmail());
    assertNotNull(result);
    assertEquals(mockServiceAccounts, result);
    mockedGCEUtil.verify(
        () -> GCEUtil.buildServiceAccount("default", List.of("scope1", "scope2")), times(1));
  }

  @Test
  void testBuildServiceAccountFromInput_AuthScopesEmpty() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setAuthScopes(List.of());
    description.setServiceAccountEmail("custom-email");

    ServiceAccount account = new ServiceAccount();
    account.setEmail("custom-email");
    account.setScopes(List.of());
    List<ServiceAccount> mockServiceAccounts = List.of(account);
    mockedGCEUtil
        .when(() -> GCEUtil.buildServiceAccount(any(), any()))
        .thenReturn(mockServiceAccounts);

    List<ServiceAccount> result =
        basicGoogleDeployHandler.buildServiceAccountFromInput(description);

    assertEquals("custom-email", description.getServiceAccountEmail());
    assertNotNull(result);
    assertEquals(mockServiceAccounts, result);
    mockedGCEUtil.verify(() -> GCEUtil.buildServiceAccount("custom-email", List.of()), times(1));
  }

  @Test
  void testBuildServiceAccountFromInput_AuthScopesNull() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setAuthScopes(null);
    description.setServiceAccountEmail("custom-email");

    mockedGCEUtil.when(() -> GCEUtil.buildServiceAccount(any(), any())).thenReturn(List.of());

    List<ServiceAccount> result =
        basicGoogleDeployHandler.buildServiceAccountFromInput(description);

    assertEquals("custom-email", description.getServiceAccountEmail());
    assertThat(result).isEmpty();
    mockedGCEUtil.verify(() -> GCEUtil.buildServiceAccount("custom-email", null), times(1));
  }

  @Test
  void testBuildSchedulingFromInput() {
    mockedGCEUtil
        .when(() -> GCEUtil.buildScheduling(mockDescription))
        .thenReturn(mock(Scheduling.class));

    basicGoogleDeployHandler.buildSchedulingFromInput(mockDescription);

    mockedGCEUtil.verify(() -> GCEUtil.buildScheduling(mockDescription), times(1));
  }

  @Test
  void testBuildLabelsFromInput_ExistingLabels() {
    Map<String, String> existingLabels = new HashMap<>();
    existingLabels.put("key1", "value1");
    mockDescription.setLabels(existingLabels);

    Map<String, String> labels =
        basicGoogleDeployHandler.buildLabelsFromInput(
            mockDescription, "my-server-group", "us-central1");

    assertEquals(3, labels.size());
    assertEquals("us-central1", labels.get("spinnaker-region"));
    assertEquals("my-server-group", labels.get("spinnaker-server-group"));
    assertEquals("value1", labels.get("key1"));
  }

  @Test
  void testBuildLabelsFromInput_NullLabels() {
    mockDescription.setLabels(null);

    Map<String, String> labels =
        basicGoogleDeployHandler.buildLabelsFromInput(
            mockDescription, "my-server-group", "us-central1");

    assertEquals(2, labels.size());
    assertEquals("us-central1", labels.get("spinnaker-region"));
    assertEquals("my-server-group", labels.get("spinnaker-server-group"));
    assertThat(mockDescription.getLabels()).isSameAs(labels);
  }

  @Test
  void validateAcceleratorConfig_throwsExceptionForInvalidConfig() {
    mockDescription.setAcceleratorConfigs(List.of(new AcceleratorConfig()));
    mockDescription.setRegional(false);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              basicGoogleDeployHandler.validateAcceleratorConfig(mockDescription);
            });

    assertEquals(
        "Accelerators are only supported with regional server groups if the zones are specified by the user.",
        exception.getMessage());
  }

  @Test
  void validateAcceleratorConfig_noExceptionForValidConfig() {
    mockDescription.setAcceleratorConfigs(List.of());
    assertDoesNotThrow(() -> basicGoogleDeployHandler.validateAcceleratorConfig(mockDescription));
  }

  @Test
  void validateAcceleratorConfig_noExceptionForNullConfig() {
    mockDescription.setAcceleratorConfigs(null);
    assertDoesNotThrow(() -> basicGoogleDeployHandler.validateAcceleratorConfig(mockDescription));
  }

  @Test
  void validateAcceleratorConfig_validRegionalWithZones() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setAcceleratorConfigs(List.of(new AcceleratorConfig()));
    description.setRegional(true);
    description.setSelectZones(false);

    assertDoesNotThrow(() -> basicGoogleDeployHandler.validateAcceleratorConfig(description));
  }

  @Test
  void buildInstancePropertiesFromInput_validInputs_success() {
    String machineTypeName = "n1-standard-1";
    List<AttachedDisk> attachedDisks = List.of(mock(AttachedDisk.class));
    NetworkInterface networkInterface = mock(NetworkInterface.class);
    Metadata metadata = mock(Metadata.class);
    Tags tags = new Tags();
    List<ServiceAccount> serviceAccounts = List.of(mock(ServiceAccount.class));
    Scheduling scheduling = mock(Scheduling.class);
    Map<String, String> labels = Map.of("key1", "value1");
    mockDescription.setAcceleratorConfigs(List.of(new AcceleratorConfig()));
    mockDescription.setCanIpForward(true);
    mockDescription.setResourceManagerTags(Map.of("resource-tag-key", "resource-tag-value"));

    InstanceProperties result =
        basicGoogleDeployHandler.buildInstancePropertiesFromInput(
            mockDescription,
            machineTypeName,
            attachedDisks,
            networkInterface,
            metadata,
            tags,
            serviceAccounts,
            scheduling,
            labels);

    assertEquals(machineTypeName, result.getMachineType());
    assertEquals(attachedDisks, result.getDisks());
    assertFalse(result.getGuestAccelerators().isEmpty());
    assertEquals(1, result.getNetworkInterfaces().size());
    assertEquals(networkInterface, result.getNetworkInterfaces().get(0));
    assertTrue(result.getCanIpForward());
    assertEquals(metadata, result.getMetadata());
    assertEquals(tags, result.getTags());
    assertEquals(labels, result.getLabels());
    assertEquals(scheduling, result.getScheduling());
    assertEquals(serviceAccounts, result.getServiceAccounts());
    assertEquals(mockDescription.getResourceManagerTags(), result.getResourceManagerTags());
  }

  @Test
  void buildInstancePropertiesFromInput_noAcceleratorConfigs_emptyGuestAccelerators() {
    String machineTypeName = "n1-standard-1";
    List<AttachedDisk> attachedDisks = List.of(mock(AttachedDisk.class));
    NetworkInterface networkInterface = mock(NetworkInterface.class);
    Metadata metadata = new Metadata();
    Tags tags = new Tags();
    List<ServiceAccount> serviceAccounts = List.of(new ServiceAccount());
    Scheduling scheduling = new Scheduling();
    Map<String, String> labels = Map.of("key1", "value1");

    mockDescription.setAcceleratorConfigs(Collections.emptyList());
    mockDescription.setCanIpForward(false);
    mockDescription.setResourceManagerTags(Collections.emptyMap());

    InstanceProperties result =
        basicGoogleDeployHandler.buildInstancePropertiesFromInput(
            mockDescription,
            machineTypeName,
            attachedDisks,
            networkInterface,
            metadata,
            tags,
            serviceAccounts,
            scheduling,
            labels);

    assertEquals(machineTypeName, result.getMachineType());
    assertEquals(attachedDisks, result.getDisks());
    assertTrue(result.getGuestAccelerators().isEmpty());
    assertEquals(1, result.getNetworkInterfaces().size());
    assertEquals(networkInterface, result.getNetworkInterfaces().get(0));
    assertFalse(result.getCanIpForward());
    assertEquals(metadata, result.getMetadata());
    assertEquals(tags, result.getTags());
    assertEquals(labels, result.getLabels());
    assertEquals(scheduling, result.getScheduling());
    assertEquals(serviceAccounts, result.getServiceAccounts());
    assertTrue(result.getResourceManagerTags().isEmpty());
  }

  @Test
  void buildInstancePropertiesFromInput_nullAcceleratorConfigs_emptyGuestAccelerators() {
    String machineTypeName = "n1-standard-1";
    List<AttachedDisk> attachedDisks = List.of(mock(AttachedDisk.class));
    NetworkInterface networkInterface = mock(NetworkInterface.class);
    Metadata metadata = mock(Metadata.class);
    Tags tags = mock(Tags.class);
    List<ServiceAccount> serviceAccounts = List.of(mock(ServiceAccount.class));
    Scheduling scheduling = mock(Scheduling.class);
    Map<String, String> labels = Map.of("key1", "value1");

    mockDescription.setAcceleratorConfigs(null);
    mockDescription.setCanIpForward(false);
    mockDescription.setResourceManagerTags(Collections.emptyMap());

    InstanceProperties result =
        basicGoogleDeployHandler.buildInstancePropertiesFromInput(
            mockDescription,
            machineTypeName,
            attachedDisks,
            networkInterface,
            metadata,
            tags,
            serviceAccounts,
            scheduling,
            labels);

    assertEquals(machineTypeName, result.getMachineType());
    assertEquals(attachedDisks, result.getDisks());
    assertTrue(result.getGuestAccelerators().isEmpty());
    assertEquals(1, result.getNetworkInterfaces().size());
    assertEquals(networkInterface, result.getNetworkInterfaces().get(0));
    assertFalse(result.getCanIpForward());
    assertEquals(metadata, result.getMetadata());
    assertEquals(tags, result.getTags());
    assertEquals(labels, result.getLabels());
    assertEquals(scheduling, result.getScheduling());
    assertEquals(serviceAccounts, result.getServiceAccounts());
    assertTrue(result.getResourceManagerTags().isEmpty());
  }

  @Test
  void addShieldedVmConfigToInstanceProperties_shieldedVmCompatible_configAdded() {
    InstanceProperties instanceProperties = new InstanceProperties();
    Image bootImage = new Image();
    ShieldedInstanceConfig shieldedVmConfig = new ShieldedInstanceConfig();

    mockedGCEUtil.when(() -> GCEUtil.isShieldedVmCompatible(bootImage)).thenReturn(true);
    mockedGCEUtil
        .when(() -> GCEUtil.buildShieldedInstanceConfig(mockDescription))
        .thenReturn(shieldedVmConfig);

    basicGoogleDeployHandler.addShieldedVmConfigToInstanceProperties(
        mockDescription, instanceProperties, bootImage);
    assertEquals(shieldedVmConfig, instanceProperties.getShieldedInstanceConfig());
  }

  @Test
  void instanceTemplateInsertBody_usesShieldedInstanceConfigAndOmitsPartnerMetadata()
      throws Exception {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setEnableSecureBoot(true);
    description.setEnableVtpm(true);
    description.setEnableIntegrityMonitoring(false);
    description.setPartnerMetadata(Map.of("partner-key", "partner-value"));
    Image bootImage = new Image();
    InstanceProperties instanceProperties =
        new InstanceProperties().setMachineType("e2-standard-2");

    mockedGCEUtil.when(() -> GCEUtil.isShieldedVmCompatible(bootImage)).thenReturn(true);
    mockedGCEUtil.when(() -> GCEUtil.buildShieldedInstanceConfig(description)).thenCallRealMethod();
    basicGoogleDeployHandler.addShieldedVmConfigToInstanceProperties(
        description, instanceProperties, bootImage);

    InstanceTemplate template =
        basicGoogleDeployHandler.buildInstanceTemplate("example-template", instanceProperties);

    CapturingComputeTransport transport = new CapturingComputeTransport();
    Compute compute =
        new Compute(
            transport, GsonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
    compute.instanceTemplates().insert("test-project", template).execute();

    CapturedRequest insertRequest =
        transport.findPostTo("/projects/test-project/global/instanceTemplates").orElseThrow();
    JsonNode capturedBody = objectMapper.readTree(insertRequest.body());
    JsonNode properties = capturedBody.path("properties");
    JsonNode shieldedConfig = properties.path("shieldedInstanceConfig");

    assertThat(shieldedConfig.has("enableSecureBoot")).isTrue();
    assertThat(shieldedConfig.path("enableSecureBoot").asBoolean()).isTrue();
    assertThat(shieldedConfig.has("enableVtpm")).isTrue();
    assertThat(shieldedConfig.path("enableVtpm").asBoolean()).isTrue();
    assertThat(shieldedConfig.has("enableIntegrityMonitoring")).isTrue();
    assertThat(shieldedConfig.path("enableIntegrityMonitoring").asBoolean()).isFalse();
    assertThat(properties.has("shieldedVmConfig")).isFalse();
    assertThat(properties.has("partnerMetadata")).isFalse();
    assertThat(insertRequest.body()).doesNotContain("partnerMetadata");
    assertThat(insertRequest.body()).doesNotContain("shieldedVmConfig");
  }

  @Test
  void addShieldedVmConfigToInstanceProperties_notShieldedVmCompatible_noConfigAdded() {
    InstanceProperties instanceProperties = new InstanceProperties();
    Image bootImage = new Image();

    mockedGCEUtil.when(() -> GCEUtil.isShieldedVmCompatible(bootImage)).thenReturn(false);

    basicGoogleDeployHandler.addShieldedVmConfigToInstanceProperties(
        mockDescription, instanceProperties, bootImage);
    assertNull(instanceProperties.getShieldedInstanceConfig());
  }

  @Test
  void addMinCpuPlatformToInstanceProperties_minCpuPlatformIsNotBlank_setMinCpuPlatform() {
    InstanceProperties instanceProperties = new InstanceProperties();
    String minCpuPlatform = "Intel Skylake";
    mockDescription.setMinCpuPlatform(minCpuPlatform);

    basicGoogleDeployHandler.addMinCpuPlatformToInstanceProperties(
        mockDescription, instanceProperties);
    assertEquals(minCpuPlatform, instanceProperties.getMinCpuPlatform());
  }

  @Test
  void addMinCpuPlatformToInstanceProperties_minCpuPlatformIsBlank_doNotSetMinCpuPlatform() {
    InstanceProperties instanceProperties = new InstanceProperties();
    mockDescription.setMinCpuPlatform("");

    basicGoogleDeployHandler.addMinCpuPlatformToInstanceProperties(
        mockDescription, instanceProperties);

    assertNull(instanceProperties.getMinCpuPlatform());
  }

  @Test
  void buildInstanceTemplate_validInputs_returnsInstanceTemplate() {
    String name = "test-instance-template";
    InstanceProperties instanceProperties = new InstanceProperties();

    InstanceTemplate result =
        basicGoogleDeployHandler.buildInstanceTemplate(name, instanceProperties);

    assertNotNull(result);
    assertEquals(name, result.getName());
    assertEquals(instanceProperties, result.getProperties());
  }

  @Test
  void setCapacityFromInput_withValidCapacity_setsTargetSize() {
    BasicGoogleDeployDescription.Capacity capacity = new BasicGoogleDeployDescription.Capacity();
    capacity.setDesired(5);
    mockDescription.setCapacity(capacity);

    basicGoogleDeployHandler.setCapacityFromInput(mockDescription);
    assertThat(mockDescription.getTargetSize()).isEqualTo(5);
  }

  @Test
  void setCapacityFromInput_withNullCapacity_doesNotSetTargetSize() {
    mockDescription.setCapacity(null);
    basicGoogleDeployHandler.setCapacityFromInput(mockDescription);
    assertThat(mockDescription.getTargetSize()).isNull();
  }

  @Test
  void setAutoscalerCapacityFromInput_withValidAutoscalerAndCapacity_updatesAutoscalingPolicy() {
    BasicGoogleDeployDescription.Capacity capacity = new BasicGoogleDeployDescription.Capacity();
    capacity.setMin(2);
    capacity.setMax(10);
    mockDescription.setCapacity(capacity);
    mockDescription.setAutoscalingPolicy(mockAutoscalingPolicy);
    doReturn(true).when(basicGoogleDeployHandler).autoscalerIsSpecified(mockDescription);

    basicGoogleDeployHandler.setAutoscalerCapacityFromInput(mockDescription);

    verify(mockAutoscalingPolicy).setMinNumReplicas(2);
    verify(mockAutoscalingPolicy).setMaxNumReplicas(10);
    mockedGCEUtil.verify(
        () -> GCEUtil.calibrateTargetSizeWithAutoscaler(mockDescription), times(1));
  }

  @Test
  void setAutoscalerCapacityFromInput_withAutoscalerNotSpecified_doesNothing() {
    doReturn(false).when(basicGoogleDeployHandler).autoscalerIsSpecified(mockDescription);

    basicGoogleDeployHandler.setAutoscalerCapacityFromInput(mockDescription);
    mockedGCEUtil.verify(
        () -> GCEUtil.calibrateTargetSizeWithAutoscaler(mockDescription), times(0));
  }

  @Test
  void setAutoscalerCapacityFromInput_withNullCapacity_doesNotUpdateAutoscalingPolicy() {
    mockDescription.setCapacity(null);
    doReturn(true).when(basicGoogleDeployHandler).autoscalerIsSpecified(mockDescription);

    basicGoogleDeployHandler.setAutoscalerCapacityFromInput(mockDescription);

    verify(mockAutoscalingPolicy, never()).setMinNumReplicas(anyInt());
    verify(mockAutoscalingPolicy, never()).setMaxNumReplicas(anyInt());
    mockedGCEUtil.verify(
        () -> GCEUtil.calibrateTargetSizeWithAutoscaler(mockDescription), times(1));
  }

  @Test
  void autoscalerIsSpecified_whenAutoscalingPolicyIsNull_returnsFalse() {
    mockDescription.setAutoscalingPolicy(null);
    assertFalse(
        basicGoogleDeployHandler.autoscalerIsSpecified(mockDescription),
        "Expected false when AutoscalingPolicy is null");
  }

  @Test
  void autoscalerIsSpecified_whenAllUtilizationsAndSchedulesAreNull_returnsFalse() {
    when(mockAutoscalingPolicy.getCpuUtilization()).thenReturn(null);
    when(mockAutoscalingPolicy.getLoadBalancingUtilization()).thenReturn(null);
    when(mockAutoscalingPolicy.getCustomMetricUtilizations()).thenReturn(null);
    when(mockAutoscalingPolicy.getScalingSchedules()).thenReturn(null);
    mockDescription.setAutoscalingPolicy(mockAutoscalingPolicy);

    assertFalse(
        basicGoogleDeployHandler.autoscalerIsSpecified(mockDescription),
        "Expected false when all utilization and schedules are null");
  }

  @Test
  void autoscalerIsSpecified_whenCpuUtilizationIsNotNull_returnsTrue() {
    when(mockAutoscalingPolicy.getCpuUtilization())
        .thenReturn(new GoogleAutoscalingPolicy.CpuUtilization());

    mockDescription.setAutoscalingPolicy(mockAutoscalingPolicy);
    assertTrue(
        basicGoogleDeployHandler.autoscalerIsSpecified(mockDescription),
        "Expected true when CpuUtilization is not null");
  }

  @Test
  void autoscalerIsSpecified_whenLoadBalancingUtilizationIsNotNull_returnsTrue() {
    when(mockAutoscalingPolicy.getLoadBalancingUtilization())
        .thenReturn(new GoogleAutoscalingPolicy.LoadBalancingUtilization());
    mockDescription.setAutoscalingPolicy(mockAutoscalingPolicy);

    assertTrue(
        basicGoogleDeployHandler.autoscalerIsSpecified(mockDescription),
        "Expected true when LoadBalancingUtilization is not null");
  }

  @Test
  void autoscalerIsSpecified_whenCustomMetricUtilizationsIsNotNull_returnsTrue() {
    when(mockAutoscalingPolicy.getCustomMetricUtilizations()).thenReturn(new ArrayList<>());
    mockDescription.setAutoscalingPolicy(mockAutoscalingPolicy);

    assertTrue(
        basicGoogleDeployHandler.autoscalerIsSpecified(mockDescription),
        "Expected true when CustomMetricUtilizations is not null");
  }

  @Test
  void autoscalerIsSpecified_whenScalingSchedulesIsNotNull_returnsTrue() {
    mockDescription.setAutoscalingPolicy(mockAutoscalingPolicy);
    assertTrue(
        basicGoogleDeployHandler.autoscalerIsSpecified(mockDescription),
        "Expected true when ScalingSchedules is not null");
  }

  @Test
  void setCapacityFromSource_whenSourceIsNull_doesNothing() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setSource(null);

    basicGoogleDeployHandler.setCapacityFromSource(description, mockTask);
    verify(mockTask, never()).updateStatus(anyString(), anyString());
    assertNull(description.getTargetSize());
  }

  @Test
  void setCapacityFromSource_whenUseSourceCapacityIsFalse_doesNothing() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    BasicGoogleDeployDescription.Source mockSource = new BasicGoogleDeployDescription.Source();
    description.setSource(mockSource);
    mockSource.setUseSourceCapacity(false);

    basicGoogleDeployHandler.setCapacityFromSource(description, mockTask);
    verify(mockTask, never()).updateStatus(anyString(), anyString());
    assertNull(description.getTargetSize());
  }

  @Test
  void setCapacityFromSource_whenRegionOrServerGroupNameIsBlank_doesNothing() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    BasicGoogleDeployDescription.Source mockSource =
        mock(BasicGoogleDeployDescription.Source.class);
    description.setSource(mockSource);
    when(mockSource.getUseSourceCapacity()).thenReturn(true);
    when(mockSource.getRegion()).thenReturn(StringUtils.EMPTY);

    basicGoogleDeployHandler.setCapacityFromSource(description, mockTask);
    verify(mockTask, never()).updateStatus(anyString(), anyString());
    assertNull(description.getTargetSize());
  }

  @Test
  void setCapacityFromSource_whenUseSourceCapacityIsNull_doesNothing() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    BasicGoogleDeployDescription.Source mockSource =
        mock(BasicGoogleDeployDescription.Source.class);
    description.setSource(mockSource);
    when(mockSource.getUseSourceCapacity()).thenReturn(null);

    basicGoogleDeployHandler.setCapacityFromSource(description, mockTask);
    verify(mockTask, never()).updateStatus(anyString(), anyString());
    assertNull(description.getTargetSize());
  }

  @Test
  void setCapacityFromSource_whenValidSource_updatesDescriptionCapacityAndPolicy() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    BasicGoogleDeployDescription.Source mockSource =
        mock(BasicGoogleDeployDescription.Source.class);
    description.setSource(mockSource);
    description.setAccountName("account-name");
    GoogleServerGroup.View mockServerGroupView = mock(GoogleServerGroup.View.class);
    ServerGroup.Capacity capacity = new ServerGroup.Capacity(1, 5, 3);
    when(mockSource.getUseSourceCapacity()).thenReturn(true);
    when(mockSource.getRegion()).thenReturn("us-central1");
    when(mockSource.getServerGroupName()).thenReturn("test-server-group");
    when(mockServerGroupView.getCapacity()).thenReturn(capacity);
    when(mockServerGroupView.getAutoscalingPolicy()).thenReturn(new GoogleAutoscalingPolicy());

    mockedGCEUtil
        .when(() -> GCEUtil.queryServerGroup(any(), anyString(), anyString(), anyString()))
        .thenReturn(mockServerGroupView);

    basicGoogleDeployHandler.setCapacityFromSource(description, mockTask);

    verify(mockTask).updateStatus(eq("DEPLOY"), contains("Looking up server group"));
    assertEquals(3, description.getTargetSize()); // Assuming target size is set to desired (3)
    assertNotNull(description.getAutoscalingPolicy());
  }

  @Test
  void buildAutoHealingPolicyFromInput_whenNoAutoHealingPolicy_returnsNull() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setAutoHealingPolicy(null);
    List<InstanceGroupManagerAutoHealingPolicy> result =
        basicGoogleDeployHandler.buildAutoHealingPolicyFromInput(description, mockTask);
    assertNull(result);
  }

  @Test
  void buildAutoHealingPolicyFromInput_whenHealthCheckIsValid_returnsPolicy() {
    GoogleAutoHealingPolicy mockAutoHealingPolicy = new GoogleAutoHealingPolicy();
    mockAutoHealingPolicy.setHealthCheck("valid-health-check");
    mockAutoHealingPolicy.setHealthCheckKind(GoogleHealthCheck.HealthCheckKind.healthCheck);
    mockDescription.setCredentials(mockCredentials);
    mockDescription.setAccountName("account-name");
    mockDescription.setAutoHealingPolicy(mockAutoHealingPolicy);

    GoogleHealthCheck mockHealthCheck = new GoogleHealthCheck();
    mockHealthCheck.setSelfLink("health-check-link");
    mockAutoHealingPolicy.setInitialDelaySec(300);
    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryHealthCheck(
                    any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mockHealthCheck);

    List<InstanceGroupManagerAutoHealingPolicy> result =
        basicGoogleDeployHandler.buildAutoHealingPolicyFromInput(mockDescription, mockTask);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("health-check-link", result.get(0).getHealthCheck());
    assertEquals(300, result.get(0).getInitialDelaySec());
  }

  @Test
  void buildAutoHealingPolicyFromInput_whenHealthCheckIsBlank_returnsNull() {
    assertNull(basicGoogleDeployHandler.buildAutoHealingPolicyFromInput(mockDescription, mockTask));
  }

  @Test
  void buildAutoHealingPolicyFromInput_whenMaxUnavailableIsSet_doesNotPropagateUnsupportedField() {
    GoogleAutoHealingPolicy mockAutoHealingPolicy = new GoogleAutoHealingPolicy();
    mockAutoHealingPolicy.setHealthCheck("valid-health-check");
    mockAutoHealingPolicy.setHealthCheckKind(GoogleHealthCheck.HealthCheckKind.healthCheck);
    mockAutoHealingPolicy.setInitialDelaySec(300);
    GoogleAutoHealingPolicy.FixedOrPercent mockMaxUnavailable =
        new GoogleAutoHealingPolicy.FixedOrPercent();
    mockMaxUnavailable.setFixed(5.0);
    mockMaxUnavailable.setPercent(10.0);
    mockAutoHealingPolicy.setMaxUnavailable(mockMaxUnavailable);

    mockDescription.setCredentials(mockCredentials);
    mockDescription.setAccountName("account-name");
    mockDescription.setAutoHealingPolicy(mockAutoHealingPolicy);

    GoogleHealthCheck mockHealthCheck = new GoogleHealthCheck();
    mockHealthCheck.setSelfLink("health-check-link");
    mockedGCEUtil
        .when(
            () ->
                GCEUtil.queryHealthCheck(
                    any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mockHealthCheck);

    List<InstanceGroupManagerAutoHealingPolicy> result =
        basicGoogleDeployHandler.buildAutoHealingPolicyFromInput(mockDescription, mockTask);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNull(result.get(0).get("maxUnavailable"));
  }

  @Test
  void buildInstanceGroupFromInput_whenValidInput_returnsInstanceGroupManager() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setTargetSize(3);
    String serverGroupName = "test-server-group";
    String instanceTemplateUrl = "http://instance-template-url";
    List<String> targetPools = List.of("target-pool-1", "target-pool-2");
    List<InstanceGroupManagerAutoHealingPolicy> autoHealingPolicies =
        List.of(new InstanceGroupManagerAutoHealingPolicy());

    InstanceGroupManager result =
        basicGoogleDeployHandler.buildInstanceGroupFromInput(
            description, serverGroupName, instanceTemplateUrl, targetPools, autoHealingPolicies);

    assertNotNull(result);
    assertEquals(serverGroupName, result.getName());
    assertEquals(serverGroupName, result.getBaseInstanceName());
    assertEquals(instanceTemplateUrl, result.getInstanceTemplate());
    assertEquals(3, result.getTargetSize());
    assertEquals(targetPools, result.getTargetPools());
    assertEquals(autoHealingPolicies, result.getAutoHealingPolicies());
  }

  @Test
  void
      buildInstanceGroupFromInput_whenNullTargetPoolsAndAutoHealingPolicies_returnsInstanceGroupManager() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setTargetSize(2);
    String serverGroupName = "test-server-group";
    String instanceTemplateUrl = "http://instance-template-url";

    InstanceGroupManager result =
        basicGoogleDeployHandler.buildInstanceGroupFromInput(
            description, serverGroupName, instanceTemplateUrl, null, null);

    assertNotNull(result);
    assertEquals(serverGroupName, result.getName());
    assertEquals(serverGroupName, result.getBaseInstanceName());
    assertEquals(instanceTemplateUrl, result.getInstanceTemplate());
    assertEquals(2, result.getTargetSize());
    assertEquals(null, result.getTargetPools());
    assertEquals(null, result.getAutoHealingPolicies());
  }

  @Test
  void
      buildInstanceGroupFromInput_whenEmptyTargetPoolsAndAutoHealingPolicies_returnsInstanceGroupManager() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setTargetSize(5);
    String serverGroupName = "test-server-group";
    String instanceTemplateUrl = "http://instance-template-url";
    List<String> emptyTargetPools = Collections.emptyList();
    List<InstanceGroupManagerAutoHealingPolicy> emptyAutoHealingPolicies = Collections.emptyList();

    InstanceGroupManager result =
        basicGoogleDeployHandler.buildInstanceGroupFromInput(
            description,
            serverGroupName,
            instanceTemplateUrl,
            emptyTargetPools,
            emptyAutoHealingPolicies);

    assertNotNull(result);
    assertEquals(serverGroupName, result.getName());
    assertEquals(serverGroupName, result.getBaseInstanceName());
    assertEquals(instanceTemplateUrl, result.getInstanceTemplate());
    assertEquals(5, result.getTargetSize());
    assertEquals(emptyTargetPools, result.getTargetPools());
    assertEquals(emptyAutoHealingPolicies, result.getAutoHealingPolicies());
  }

  @Test
  void testSetNamedPortsToInstanceGroup_withLoadBalancingPolicyNamedPorts() {
    List<NamedPort> namedPorts = List.of(new NamedPort().setName("http").setPort(80));
    GoogleHttpLoadBalancingPolicy loadBalancingPolicy = new GoogleHttpLoadBalancingPolicy();
    loadBalancingPolicy.setNamedPorts(namedPorts);
    BasicGoogleDeployHandler.LoadBalancerInfo mockLBInfo =
        mock(BasicGoogleDeployHandler.LoadBalancerInfo.class);
    InstanceGroupManager instanceGroupManager = mock(InstanceGroupManager.class);

    BasicGoogleDeployDescription.Source source = new BasicGoogleDeployDescription.Source();
    source.setServerGroupName("server-group");
    source.setRegion("us-central1");
    mockDescription.setSource(source);
    mockDescription.setLoadBalancingPolicy(loadBalancingPolicy);
    doReturn(true)
        .when(basicGoogleDeployHandler)
        .hasBackedServiceFromInput(mockDescription, mockLBInfo);

    basicGoogleDeployHandler.setNamedPortsToInstanceGroup(
        mockDescription, mockLBInfo, instanceGroupManager);
    verify(instanceGroupManager).setNamedPorts(namedPorts);
  }

  @Test
  void testSetNamedPortsToInstanceGroup_withSourceServerGroupNamedPorts() {
    Map<String, Integer> sourceNamedPorts = Map.of("http", 80, "https", 443);
    GoogleServerGroup sourceServerGroup = new GoogleServerGroup();
    sourceServerGroup.setNamedPorts(sourceNamedPorts);

    BasicGoogleDeployDescription.Source source = new BasicGoogleDeployDescription.Source();
    source.setServerGroupName("source-server-group");
    source.setRegion("us-central1");

    BasicGoogleDeployHandler.LoadBalancerInfo mockLBInfo =
        mock(BasicGoogleDeployHandler.LoadBalancerInfo.class);
    InstanceGroupManager instanceGroupManager = mock(InstanceGroupManager.class);

    mockDescription.setSource(source);
    mockDescription.setLoadBalancingPolicy(null);
    when(googleClusterProvider.getServerGroup(any(), anyString(), anyString()))
        .thenReturn(sourceServerGroup.getView());
    doReturn(true)
        .when(basicGoogleDeployHandler)
        .hasBackedServiceFromInput(mockDescription, mockLBInfo);

    basicGoogleDeployHandler.setNamedPortsToInstanceGroup(
        mockDescription, mockLBInfo, instanceGroupManager);
    verify(instanceGroupManager)
        .setNamedPorts(
            argThat(
                list ->
                    new HashSet<>(
                            List.of(
                                new NamedPort().setName("http").setPort(80),
                                new NamedPort().setName("https").setPort(443)))
                        .equals(new HashSet<>(list))));
  }

  @Test
  void testSetNamedPortsToInstanceGroup_withNoNamedPortsOrSourceSetsDefault() {
    BasicGoogleDeployHandler.LoadBalancerInfo mockLBInfo =
        new BasicGoogleDeployHandler.LoadBalancerInfo();
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();

    BasicGoogleDeployDescription.Source source = new BasicGoogleDeployDescription.Source();
    source.setServerGroupName("source-server-group");
    source.setRegion("us-central1");

    mockDescription.setLoadBalancingPolicy(null);
    mockDescription.setSource(source);
    doReturn(true)
        .when(basicGoogleDeployHandler)
        .hasBackedServiceFromInput(mockDescription, mockLBInfo);

    basicGoogleDeployHandler.setNamedPortsToInstanceGroup(
        mockDescription, mockLBInfo, instanceGroupManager);

    assertThat(instanceGroupManager.getNamedPorts().get(0).getName())
        .isEqualTo(GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME);
    assertThat(instanceGroupManager.getNamedPorts().get(0).getPort())
        .isEqualTo(GoogleHttpLoadBalancingPolicy.getHTTP_DEFAULT_PORT());
  }

  @Test
  void testSetNamedPortsToInstanceGroup_withLoadBalancingPolicyListeningPort() {
    BasicGoogleDeployHandler.LoadBalancerInfo mockLBInfo =
        new BasicGoogleDeployHandler.LoadBalancerInfo();
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    GoogleHttpLoadBalancingPolicy loadBalancingPolicy = new GoogleHttpLoadBalancingPolicy();
    loadBalancingPolicy.setListeningPort(8080);
    BasicGoogleDeployDescription.Source source = new BasicGoogleDeployDescription.Source();
    source.setServerGroupName(""); // empty serverGroupName
    mockDescription.setSource(source);
    mockDescription.setLoadBalancingPolicy(loadBalancingPolicy);

    doReturn(true)
        .when(basicGoogleDeployHandler)
        .hasBackedServiceFromInput(mockDescription, mockLBInfo);

    basicGoogleDeployHandler.setNamedPortsToInstanceGroup(
        mockDescription, mockLBInfo, instanceGroupManager);
    assertThat(instanceGroupManager.getNamedPorts().get(0).getName())
        .isEqualTo(GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME);
    assertThat(instanceGroupManager.getNamedPorts().get(0).getPort()).isEqualTo(8080);
  }

  @Test
  void testCreateInstanceGroupManagerFromInput_whenRegional() throws IOException {
    mockDescription.setRegional(true);

    doNothing().when(basicGoogleDeployHandler).setDistributionPolicyToInstanceGroup(any(), any());
    doReturn("")
        .when(basicGoogleDeployHandler)
        .createRegionalInstanceGroupManagerAndWait(any(), any(), any(), anyString(), any(), any());
    doNothing()
        .when(basicGoogleDeployHandler)
        .createRegionalAutoscaler(any(), any(), any(), any(), any());

    basicGoogleDeployHandler.createInstanceGroupManagerFromInput(
        mockDescription,
        new InstanceGroupManager(),
        new BasicGoogleDeployHandler.LoadBalancerInfo(),
        "test-server-group",
        "us-central1",
        mockTask);

    verify(basicGoogleDeployHandler).setDistributionPolicyToInstanceGroup(any(), any());
    verify(basicGoogleDeployHandler)
        .createRegionalInstanceGroupManagerAndWait(any(), any(), any(), any(), any(), any());
    verify(basicGoogleDeployHandler).createRegionalAutoscaler(any(), any(), any(), any(), any());
  }

  @Test
  void testCreateInstanceGroupManagerFromInput_RegionalFlexPolicy_disablesInstanceRedistribution()
      throws IOException {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    mockDescription.setRegional(true);
    mockDescription.setInstanceFlexibilityPolicy(flexPolicy());

    doAnswer(invocation -> "target-link")
        .when(basicGoogleDeployHandler)
        .createRegionalInstanceGroupManagerAndWait(any(), any(), any(), any(), any(), any());
    doNothing()
        .when(basicGoogleDeployHandler)
        .createRegionalAutoscaler(any(), any(), any(), any(), any());

    basicGoogleDeployHandler.createInstanceGroupManagerFromInput(
        mockDescription,
        instanceGroupManager,
        new BasicGoogleDeployHandler.LoadBalancerInfo(),
        "test-server-group",
        "us-central1",
        mockTask);

    assertThat(instanceGroupManager.getInstanceFlexibilityPolicy()).isNotNull();
    assertThat(instanceGroupManager.getUpdatePolicy().getInstanceRedistributionType())
        .isEqualTo("NONE");
  }

  @Test
  void
      testCreateInstanceGroupManagerFromInput_RegionalNonEvenDistribution_disablesInstanceRedistribution()
          throws IOException {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    mockDescription.setRegional(true);
    mockDescription.setDistributionPolicy(new GoogleDistributionPolicy(null, "ANY"));

    doAnswer(invocation -> "target-link")
        .when(basicGoogleDeployHandler)
        .createRegionalInstanceGroupManagerAndWait(any(), any(), any(), any(), any(), any());
    doNothing()
        .when(basicGoogleDeployHandler)
        .createRegionalAutoscaler(any(), any(), any(), any(), any());

    basicGoogleDeployHandler.createInstanceGroupManagerFromInput(
        mockDescription,
        instanceGroupManager,
        new BasicGoogleDeployHandler.LoadBalancerInfo(),
        "test-server-group",
        "us-central1",
        mockTask);

    assertThat(instanceGroupManager.getDistributionPolicy().getTargetShape()).isEqualTo("ANY");
    assertThat(instanceGroupManager.getUpdatePolicy().getInstanceRedistributionType())
        .isEqualTo("NONE");
  }

  @Test
  void
      testCreateInstanceGroupManagerFromInput_RegionalEvenDistribution_doesNotForceInstanceRedistribution()
          throws IOException {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    mockDescription.setRegional(true);
    mockDescription.setDistributionPolicy(new GoogleDistributionPolicy(null, "EVEN"));

    doAnswer(invocation -> "target-link")
        .when(basicGoogleDeployHandler)
        .createRegionalInstanceGroupManagerAndWait(any(), any(), any(), any(), any(), any());
    doNothing()
        .when(basicGoogleDeployHandler)
        .createRegionalAutoscaler(any(), any(), any(), any(), any());

    basicGoogleDeployHandler.createInstanceGroupManagerFromInput(
        mockDescription,
        instanceGroupManager,
        new BasicGoogleDeployHandler.LoadBalancerInfo(),
        "test-server-group",
        "us-central1",
        mockTask);

    assertThat(instanceGroupManager.getDistributionPolicy().getTargetShape()).isEqualTo("EVEN");
    assertThat(instanceGroupManager.getUpdatePolicy()).isNull();
  }

  @Test
  void testCreateInstanceGroupManagerFromInput_RegionalFlexPolicy_sendsRedistributionContract()
      throws IOException {
    CapturingComputeTransport transport = new CapturingComputeTransport();
    Compute compute =
        new Compute(
            transport, GsonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setName("example-server-group")
            .setBaseInstanceName("example-server-group")
            .setInstanceTemplate("global/instanceTemplates/example-template")
            .setTargetSize(2);
    mockDescription.setRegional(true);
    mockDescription.setDisableTraffic(true);
    mockDescription.setCredentials(mockCredentials);
    mockDescription.setDistributionPolicy(new GoogleDistributionPolicy(null, "ANY"));
    mockDescription.setInstanceFlexibilityPolicy(flexPolicy());
    when(mockCredentials.getCompute()).thenReturn(compute);
    when(mockCredentials.getProject()).thenReturn("test-project");
    injectField("registry", new DefaultRegistry());

    basicGoogleDeployHandler.createInstanceGroupManagerFromInput(
        mockDescription,
        instanceGroupManager,
        new BasicGoogleDeployHandler.LoadBalancerInfo(),
        "example-server-group",
        "us-central1",
        mockTask);

    CapturedRequest insertRequest =
        transport
            .findPostTo("/projects/test-project/regions/us-central1/instanceGroupManagers")
            .orElseThrow();
    JsonNode capturedBody = objectMapper.readTree(insertRequest.body());

    assertThat(insertRequest.method()).isEqualTo("POST");
    assertThat(capturedBody.path("distributionPolicy").path("targetShape").asText())
        .isEqualTo("ANY");
    assertThat(
            capturedBody
                .path("instanceFlexibilityPolicy")
                .path("instanceSelections")
                .has("primary"))
        .isTrue();
    assertThat(capturedBody.path("updatePolicy").path("instanceRedistributionType").asText())
        .isEqualTo("NONE");
  }

  @Test
  void testCreateInstanceGroupManagerFromInput_RegionalRanklessFlexSelections_omitsRankFields()
      throws IOException {
    CapturingComputeTransport transport = new CapturingComputeTransport();
    Compute compute =
        new Compute(
            transport, GsonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setName("example-server-group")
            .setBaseInstanceName("example-server-group")
            .setInstanceTemplate("global/instanceTemplates/example-template")
            .setTargetSize(2);
    mockDescription.setRegional(true);
    mockDescription.setDisableTraffic(true);
    mockDescription.setCredentials(mockCredentials);
    mockDescription.setDistributionPolicy(new GoogleDistributionPolicy(null, " balanced "));
    GoogleInstanceFlexibilityPolicy.InstanceSelection preferred =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(null, List.of(" n2-standard-8 "));
    GoogleInstanceFlexibilityPolicy.InstanceSelection fallback =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(null, List.of("e2-standard-8"));
    GoogleInstanceFlexibilityPolicy flexPolicy = new GoogleInstanceFlexibilityPolicy();
    Map<String, GoogleInstanceFlexibilityPolicy.InstanceSelection> selections = new HashMap<>();
    selections.put("preferred", preferred);
    selections.put("fallback", fallback);
    flexPolicy.setInstanceSelections(selections);
    mockDescription.setInstanceFlexibilityPolicy(flexPolicy);
    when(mockCredentials.getCompute()).thenReturn(compute);
    when(mockCredentials.getProject()).thenReturn("test-project");
    injectField("registry", new DefaultRegistry());

    basicGoogleDeployHandler.createInstanceGroupManagerFromInput(
        mockDescription,
        instanceGroupManager,
        new BasicGoogleDeployHandler.LoadBalancerInfo(),
        "example-server-group",
        "us-central1",
        mockTask);

    CapturedRequest insertRequest =
        transport
            .findPostTo("/projects/test-project/regions/us-central1/instanceGroupManagers")
            .orElseThrow();
    JsonNode capturedBody = objectMapper.readTree(insertRequest.body());
    JsonNode outboundSelections =
        capturedBody.path("instanceFlexibilityPolicy").path("instanceSelections");

    assertThat(capturedBody.path("distributionPolicy").path("targetShape").asText())
        .isEqualTo("BALANCED");
    assertThat(outboundSelections.path("preferred").path("machineTypes").get(0).asText())
        .isEqualTo("n2-standard-8");
    assertThat(outboundSelections.path("fallback").path("machineTypes").get(0).asText())
        .isEqualTo("e2-standard-8");
    assertThat(outboundSelections.path("preferred").path("rank").isMissingNode()).isTrue();
    assertThat(outboundSelections.path("fallback").path("rank").isMissingNode()).isTrue();
    assertThat(insertRequest.body()).doesNotContain("\"rank\"");
  }

  @Test
  void testCreateInstanceGroupManagerFromInput_whenNotRegional() throws IOException {
    BasicGoogleDeployHandler.LoadBalancerInfo mockLBInfo =
        new BasicGoogleDeployHandler.LoadBalancerInfo();
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    mockDescription.setRegional(false);

    doReturn("")
        .when(basicGoogleDeployHandler)
        .createInstanceGroupManagerAndWait(any(), any(), any(), any(), any());
    doNothing().when(basicGoogleDeployHandler).createAutoscaler(any(), any(), any(), any());

    basicGoogleDeployHandler.createInstanceGroupManagerFromInput(
        mockDescription,
        instanceGroupManager,
        mockLBInfo,
        "test-server-group",
        "us-central1",
        mockTask);

    verify(basicGoogleDeployHandler, never()).setDistributionPolicyToInstanceGroup(any(), any());
    verify(basicGoogleDeployHandler)
        .createInstanceGroupManagerAndWait(any(), any(), any(), any(), any());
    verify(basicGoogleDeployHandler).createAutoscaler(any(), any(), any(), any());
  }

  @Test
  void testCreateInstanceGroupManagerFromInput_whenRegionalNull() throws IOException {
    BasicGoogleDeployHandler.LoadBalancerInfo mockLBInfo =
        new BasicGoogleDeployHandler.LoadBalancerInfo();
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    mockDescription.setRegional(null);

    doReturn("")
        .when(basicGoogleDeployHandler)
        .createInstanceGroupManagerAndWait(any(), any(), any(), any(), any());
    doNothing().when(basicGoogleDeployHandler).createAutoscaler(any(), any(), any(), any());

    basicGoogleDeployHandler.createInstanceGroupManagerFromInput(
        mockDescription,
        instanceGroupManager,
        mockLBInfo,
        "test-server-group",
        "us-central1",
        mockTask);

    verify(basicGoogleDeployHandler, never()).setDistributionPolicyToInstanceGroup(any(), any());
    verify(basicGoogleDeployHandler)
        .createInstanceGroupManagerAndWait(any(), any(), any(), any(), any());
    verify(basicGoogleDeployHandler).createAutoscaler(any(), any(), any(), any());
  }

  @Test
  void createInstanceGroupManagerAndWait_withOmittedDisableTraffic_doesNotThrow()
      throws IOException {
    CapturingComputeTransport transport = new CapturingComputeTransport();
    Compute compute =
        new Compute(
            transport, GsonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
    mockDescription.setCredentials(mockCredentials);
    mockDescription.setZone("us-central1-a");
    when(mockCredentials.getCompute()).thenReturn(compute);
    when(mockCredentials.getProject()).thenReturn("test-project");
    injectField("registry", new DefaultRegistry());

    assertDoesNotThrow(
        () ->
            basicGoogleDeployHandler.createInstanceGroupManagerAndWait(
                mockDescription,
                new BasicGoogleDeployHandler.LoadBalancerInfo(),
                "example-server-group",
                new InstanceGroupManager(),
                mockTask));
  }

  @Test
  void shouldWaitForInstanceGroupManagerCreation_nullDisableTrafficEnablesBackendWait() {
    BasicGoogleDeployHandler.LoadBalancerInfo loadBalancerInfo =
        new BasicGoogleDeployHandler.LoadBalancerInfo();
    mockDescription.setDisableTraffic(null);
    mockDescription.setInstanceMetadata(Map.of("backend-service-names", "example-backend-service"));

    assertTrue(
        basicGoogleDeployHandler.shouldWaitForInstanceGroupManagerCreation(
            mockDescription, loadBalancerInfo));
  }

  @Test
  void shouldWaitForInstanceGroupManagerCreation_disableTrafficSuppressesLoadBalancerWait() {
    BasicGoogleDeployHandler.LoadBalancerInfo loadBalancerInfo =
        new BasicGoogleDeployHandler.LoadBalancerInfo();
    loadBalancerInfo.getSslLoadBalancers().add(new GoogleLoadBalancerView() {});
    mockDescription.setDisableTraffic(true);

    assertFalse(
        basicGoogleDeployHandler.shouldWaitForInstanceGroupManagerCreation(
            mockDescription, loadBalancerInfo));
  }

  @Test
  void shouldWaitForInstanceGroupManagerCreation_autoscalerForcesWait() {
    BasicGoogleDeployHandler.LoadBalancerInfo loadBalancerInfo =
        new BasicGoogleDeployHandler.LoadBalancerInfo();
    when(mockAutoscalingPolicy.getCpuUtilization())
        .thenReturn(new GoogleAutoscalingPolicy.CpuUtilization());
    mockDescription.setAutoscalingPolicy(mockAutoscalingPolicy);
    mockDescription.setDisableTraffic(true);

    assertTrue(
        basicGoogleDeployHandler.shouldWaitForInstanceGroupManagerCreation(
            mockDescription, loadBalancerInfo));
  }

  @Test
  void testNoZonesSelectedButDistributionPolicySet() {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    GoogleDistributionPolicy policy = new GoogleDistributionPolicy();
    policy.setTargetShape("someShape");
    mockDescription.setDistributionPolicy(policy);

    basicGoogleDeployHandler.setDistributionPolicyToInstanceGroup(
        mockDescription, instanceGroupManager);

    assertThat(instanceGroupManager.getDistributionPolicy()).isNotNull();
  }

  @Test
  void testNoDistributionPolicySet() {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    mockDescription.setDistributionPolicy(null);
    basicGoogleDeployHandler.setDistributionPolicyToInstanceGroup(
        mockDescription, instanceGroupManager);

    assertThat(instanceGroupManager.getDistributionPolicy()).isNull();
  }

  @Test
  void testSetDistributionPolicyWithZones() {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    GoogleDistributionPolicy mockPolicy = new GoogleDistributionPolicy();
    mockPolicy.setZones(List.of("zone-1", "zone-2"));
    mockPolicy.setTargetShape("ANY");

    mockDescription.setDistributionPolicy(mockPolicy);
    mockDescription.setSelectZones(true);
    mockDescription.setCredentials(mockCredentials);
    when(mockCredentials.getProject()).thenReturn("test-project");
    mockedGCEUtil.when(() -> GCEUtil.buildZoneUrl(any(), any())).thenReturn("static-zone");

    basicGoogleDeployHandler.setDistributionPolicyToInstanceGroup(
        mockDescription, instanceGroupManager);
    assertThat(instanceGroupManager.getDistributionPolicy().getZones()).hasSize(2);
    assertThat(instanceGroupManager.getDistributionPolicy().getZones().get(0).getZone())
        .isEqualTo("static-zone");
    assertThat(instanceGroupManager.getDistributionPolicy().getZones().get(1).getZone())
        .isEqualTo("static-zone");
    assertThat(instanceGroupManager.getDistributionPolicy().getTargetShape()).isEqualTo("ANY");
  }

  @Test
  void testSetInstanceFlexibilityPolicyToInstanceGroup_nullPolicy_doesNotModifyInstanceGroup() {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setInstanceFlexibilityPolicy(null);

    basicGoogleDeployHandler.setInstanceFlexibilityPolicyToInstanceGroup(
        description, instanceGroupManager);

    assertNull(instanceGroupManager.getInstanceFlexibilityPolicy());
  }

  @Test
  void testSetInstanceFlexibilityPolicyToInstanceGroup_mapsValidSelectionsOnly() {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setRegional(true);

    com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy.InstanceSelection
        selection =
            new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
                .InstanceSelection();
    selection.setRank(1);
    selection.setMachineTypes(List.of("n2-standard-8"));

    Map<
            String,
            com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
                .InstanceSelection>
        selections = new HashMap<>();
    selections.put("preferred", selection);
    selections.put("malformed", null);
    selections.put(
        "rankless",
        new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
            .InstanceSelection(null, List.of("n2-standard-16")));
    selections.put(
        "missingMachineTypes",
        new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
            .InstanceSelection(2, null));
    selections.put(
        "emptyMachineTypes",
        new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
            .InstanceSelection(3, Collections.emptyList()));

    com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy flexPolicy =
        new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy();
    flexPolicy.setInstanceSelections(selections);
    description.setInstanceFlexibilityPolicy(flexPolicy);

    basicGoogleDeployHandler.setInstanceFlexibilityPolicyToInstanceGroup(
        description, instanceGroupManager);

    assertNotNull(instanceGroupManager.getInstanceFlexibilityPolicy());
    assertEquals(
        2, instanceGroupManager.getInstanceFlexibilityPolicy().getInstanceSelections().size());
    assertTrue(
        instanceGroupManager
            .getInstanceFlexibilityPolicy()
            .getInstanceSelections()
            .containsKey("preferred"));
    assertTrue(
        instanceGroupManager
            .getInstanceFlexibilityPolicy()
            .getInstanceSelections()
            .containsKey("rankless"));
    assertFalse(
        instanceGroupManager
            .getInstanceFlexibilityPolicy()
            .getInstanceSelections()
            .containsKey("malformed"));
    assertEquals(
        Integer.valueOf(1),
        instanceGroupManager
            .getInstanceFlexibilityPolicy()
            .getInstanceSelections()
            .get("preferred")
            .getRank());
    assertNull(
        instanceGroupManager
            .getInstanceFlexibilityPolicy()
            .getInstanceSelections()
            .get("rankless")
            .getRank());
    assertEquals(
        List.of("n2-standard-8"),
        instanceGroupManager
            .getInstanceFlexibilityPolicy()
            .getInstanceSelections()
            .get("preferred")
            .getMachineTypes());
  }

  @Test
  void
      testSetInstanceFlexibilityPolicyToInstanceGroup_allMalformedSelections_doesNotModifyInstanceGroup() {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setRegional(true);

    Map<
            String,
            com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
                .InstanceSelection>
        selections = new HashMap<>();
    selections.put("nullSelection", null);
    selections.put(
        "missingMachineTypes",
        new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
            .InstanceSelection(1, null));
    selections.put(
        "emptyMachineTypes",
        new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
            .InstanceSelection(2, Collections.emptyList()));

    com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy flexPolicy =
        new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy();
    flexPolicy.setInstanceSelections(selections);
    description.setInstanceFlexibilityPolicy(flexPolicy);

    basicGoogleDeployHandler.setInstanceFlexibilityPolicyToInstanceGroup(
        description, instanceGroupManager);

    assertNull(instanceGroupManager.getInstanceFlexibilityPolicy());
  }

  @Test
  void testSetInstanceFlexibilityPolicyToInstanceGroup_nonRegional_doesNotModifyInstanceGroup() {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setRegional(false);
    description.setApplication("myapp");

    com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy.InstanceSelection
        selection =
            new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
                .InstanceSelection();
    selection.setRank(1);
    selection.setMachineTypes(List.of("n2-standard-8"));

    Map<
            String,
            com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
                .InstanceSelection>
        selections = new HashMap<>();
    selections.put("preferred", selection);

    com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy flexPolicy =
        new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy();
    flexPolicy.setInstanceSelections(selections);
    description.setInstanceFlexibilityPolicy(flexPolicy);

    basicGoogleDeployHandler.setInstanceFlexibilityPolicyToInstanceGroup(
        description, instanceGroupManager);

    assertNull(instanceGroupManager.getInstanceFlexibilityPolicy());
  }

  private GoogleLoadBalancerView mockLoadBalancer(GoogleLoadBalancerType loadBalancerType) {
    GoogleLoadBalancerView mockLB = new GoogleLoadBalancerView() {};

    mockLB.setLoadBalancerType(loadBalancerType);
    return mockLB;
  }

  private static GoogleInstanceFlexibilityPolicy flexPolicy() {
    GoogleInstanceFlexibilityPolicy.InstanceSelection selection =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(0, List.of("e2-standard-2"));
    GoogleInstanceFlexibilityPolicy flexPolicy = new GoogleInstanceFlexibilityPolicy();
    flexPolicy.setInstanceSelections(Map.of("primary", selection));
    return flexPolicy;
  }

  private void injectField(String fieldName, Object value) {
    try {
      Field field = BasicGoogleDeployHandler.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(basicGoogleDeployHandler, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testBuildLoadBalancerPolicyFromInput_WithValidInputPolicy() throws Exception {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    GoogleHttpLoadBalancingPolicy inputPolicy = new GoogleHttpLoadBalancingPolicy();
    inputPolicy.setBalancingMode(GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION);
    inputPolicy.setMaxUtilization(0.8f);
    inputPolicy.setCapacityScaler(1.0f);
    description.setLoadBalancingPolicy(inputPolicy);
    description.setInstanceMetadata(new HashMap<>());

    GoogleHttpLoadBalancingPolicy result =
        basicGoogleDeployHandler.buildLoadBalancerPolicyFromInput(description);

    assertNotNull(result);
    assertEquals(GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION, result.getBalancingMode());
    assertEquals(0.8f, result.getMaxUtilization());
    assertEquals(1.0f, result.getCapacityScaler());
  }

  @Test
  void testBuildLoadBalancerPolicyFromInput_WithJsonMetadata() throws Exception {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    Map<String, String> metadata = new HashMap<>();
    metadata.put(
        "load-balancing-policy", "{\"balancingMode\":\"UTILIZATION\",\"maxUtilization\":0.9}");
    description.setInstanceMetadata(metadata);

    GoogleHttpLoadBalancingPolicy result =
        basicGoogleDeployHandler.buildLoadBalancerPolicyFromInput(description);

    assertNotNull(result);
    assertEquals(GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION, result.getBalancingMode());
    assertEquals(0.9f, result.getMaxUtilization());
  }

  @Test
  void testBuildLoadBalancerPolicyFromInput_WithInvalidJson() throws Exception {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    Map<String, String> metadata = new HashMap<>();
    metadata.put("load-balancing-policy", "invalid-json{");
    description.setInstanceMetadata(metadata);

    // Should throw JsonProcessingException for invalid JSON
    assertThrows(
        com.fasterxml.jackson.core.JsonProcessingException.class,
        () -> {
          basicGoogleDeployHandler.buildLoadBalancerPolicyFromInput(description);
        });
  }

  @Test
  void testBuildLoadBalancerPolicyFromInput_FallsBackToDefaultPolicy() throws Exception {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setInstanceMetadata(new HashMap<>());

    GoogleHttpLoadBalancingPolicy result =
        basicGoogleDeployHandler.buildLoadBalancerPolicyFromInput(description);

    assertNotNull(result);
    assertEquals(GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION, result.getBalancingMode());
    assertEquals(0.8f, result.getMaxUtilization());
    assertEquals(1.0f, result.getCapacityScaler());
    assertNotNull(result.getNamedPorts());
    assertEquals(1, result.getNamedPorts().size());
  }

  @Test
  void testBuildLoadBalancerPolicyFromInput_WithNullMetadataFallsBackToDefaultPolicy()
      throws Exception {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setInstanceMetadata(null);

    GoogleHttpLoadBalancingPolicy result =
        basicGoogleDeployHandler.buildLoadBalancerPolicyFromInput(description);

    assertNotNull(result);
    assertEquals(GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION, result.getBalancingMode());
    assertEquals(0.8f, result.getMaxUtilization());
    assertEquals(1.0f, result.getCapacityScaler());
    assertNotNull(description.getInstanceMetadata());
  }

  @Test
  void testDirectEditMinimalDeployPayloadWithOmittedOptionalFieldsAssemblesWithoutNpe()
      throws Exception {
    CapturingComputeTransport transport = new CapturingComputeTransport();
    Compute compute =
        new Compute(
            transport, GsonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
    BasicGoogleDeployDescription description =
        objectMapper.readValue(
            "{"
                + "\"application\":\"example\","
                + "\"stack\":\"flex\","
                + "\"region\":\"us-central1\","
                + "\"regional\":true,"
                + "\"selectZones\":false,"
                + "\"distributionPolicy\":{\"targetShape\":\"ANY\"},"
                + "\"instanceFlexibilityPolicy\":{\"instanceSelections\":{\"primary\":{\"rank\":0,\"machineTypes\":[\"e2-standard-2\"]}}}"
                + "}",
            BasicGoogleDeployDescription.class);
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setName("example-server-group")
            .setBaseInstanceName("example-server-group")
            .setInstanceTemplate("global/instanceTemplates/example-template")
            .setTargetSize(2);
    description.setCredentials(mockCredentials);
    description.setDisableTraffic(true);
    when(mockCredentials.getCompute()).thenReturn(compute);
    when(mockCredentials.getProject()).thenReturn("test-project");
    injectField("registry", new DefaultRegistry());
    mockedGCEUtil.when(() -> GCEUtil.buildServiceAccount(any(), any())).thenReturn(List.of());
    mockedGCEUtil.when(() -> GCEUtil.buildTagsFromList(any())).thenReturn(new Tags());

    GoogleHttpLoadBalancingPolicy policy =
        basicGoogleDeployHandler.buildLoadBalancerPolicyFromInput(description);
    List<ServiceAccount> serviceAccounts =
        basicGoogleDeployHandler.buildServiceAccountFromInput(description);
    Tags tags = basicGoogleDeployHandler.buildTagsFromInput(description);
    Map<String, String> labels =
        basicGoogleDeployHandler.buildLabelsFromInput(
            description, "example-server-group", "us-central1");
    basicGoogleDeployHandler.createInstanceGroupManagerFromInput(
        description,
        instanceGroupManager,
        new BasicGoogleDeployHandler.LoadBalancerInfo(),
        "example-server-group",
        "us-central1",
        mockTask);
    CapturedRequest insertRequest =
        transport
            .findPostTo("/projects/test-project/regions/us-central1/instanceGroupManagers")
            .orElseThrow();
    JsonNode capturedBody = objectMapper.readTree(insertRequest.body());

    assertThat(policy).isNotNull();
    assertThat(serviceAccounts).isEmpty();
    assertThat(tags).isNotNull();
    assertThat(labels).containsKeys("spinnaker-region", "spinnaker-server-group");
    assertThat(description.getInstanceMetadata()).isNotNull();
    assertThat(description.getLabels()).isSameAs(labels);
    assertThat(capturedBody.path("distributionPolicy").path("targetShape").asText())
        .isEqualTo("ANY");
    assertThat(
            capturedBody
                .path("instanceFlexibilityPolicy")
                .path("instanceSelections")
                .has("primary"))
        .isTrue();
    assertThat(capturedBody.path("updatePolicy").path("instanceRedistributionType").asText())
        .isEqualTo("NONE");
  }

  @Test
  void testDirectEditPayload_NullTags_AssemblesThroughHandlerWithoutNpe() throws Exception {
    assertDirectEditInputMutationAssemblesWithoutNpe(description -> description.setTags(null));
  }

  @Test
  void testDirectEditPayload_OmittedDistributionPolicyZones_AssemblesThroughHandlerWithoutNpe()
      throws Exception {
    // targetShape=ANY with no explicit zones must still produce a valid regional distribution
    // policy; selectZones stays false so omitted zones are expected.
    assertDirectEditInputMutationAssemblesWithoutNpe(
        description ->
            description.setDistributionPolicy(new GoogleDistributionPolicy(null, "ANY")));
  }

  @Test
  void testDirectEditPayload_EmptyLoadBalancers_AssemblesThroughHandlerWithoutNpe()
      throws Exception {
    assertDirectEditInputMutationAssemblesWithoutNpe(
        description -> description.setLoadBalancers(new ArrayList<>()));
  }

  @Test
  void testDirectEditPayload_OmittedSource_AssemblesThroughHandlerWithoutNpe() throws Exception {
    assertDirectEditInputMutationAssemblesWithoutNpe(description -> description.setSource(null));
  }

  /**
   * Drives a saved/direct-edit payload through real handler assembly (build helpers plus {@code
   * createInstanceGroupManagerFromInput}) after applying one omitted/null input mutation. Asserts
   * the case does not cause an NPE and that the regional redistribution contract still holds in the
   * serialized outbound request body. Complements the field-level null tests above by covering the
   * optional fields that are omissible through saved/direct-edit payloads (tags,
   * distributionPolicy.zones, loadBalancers, source).
   */
  private void assertDirectEditInputMutationAssemblesWithoutNpe(
      Consumer<BasicGoogleDeployDescription> inputMutation) throws Exception {
    CapturingComputeTransport transport = new CapturingComputeTransport();
    Compute compute =
        new Compute(
            transport, GsonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
    BasicGoogleDeployDescription description = populatedRegionalFlexDescription();
    inputMutation.accept(description);
    description.setCredentials(mockCredentials);
    description.setDisableTraffic(true);
    when(mockCredentials.getCompute()).thenReturn(compute);
    when(mockCredentials.getProject()).thenReturn("test-project");
    injectField("registry", new DefaultRegistry());
    mockedGCEUtil.when(() -> GCEUtil.buildServiceAccount(any(), any())).thenReturn(List.of());
    mockedGCEUtil.when(() -> GCEUtil.buildTagsFromList(any())).thenReturn(new Tags());

    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setName("example-server-group")
            .setBaseInstanceName("example-server-group")
            .setInstanceTemplate("global/instanceTemplates/example-template")
            .setTargetSize(2);

    assertDoesNotThrow(
        () -> {
          basicGoogleDeployHandler.buildLoadBalancerPolicyFromInput(description);
          basicGoogleDeployHandler.buildServiceAccountFromInput(description);
          basicGoogleDeployHandler.buildTagsFromInput(description);
          basicGoogleDeployHandler.buildLabelsFromInput(
              description, "example-server-group", "us-central1");
          basicGoogleDeployHandler.createInstanceGroupManagerFromInput(
              description,
              instanceGroupManager,
              new BasicGoogleDeployHandler.LoadBalancerInfo(),
              "example-server-group",
              "us-central1",
              mockTask);
        });

    CapturedRequest insertRequest =
        transport
            .findPostTo("/projects/test-project/regions/us-central1/instanceGroupManagers")
            .orElseThrow();
    JsonNode capturedBody = objectMapper.readTree(insertRequest.body());
    assertThat(capturedBody.path("distributionPolicy").path("targetShape").asText())
        .isEqualTo("ANY");
    assertThat(capturedBody.path("updatePolicy").path("instanceRedistributionType").asText())
        .isEqualTo("NONE");
  }

  @Test
  void testDirectEditThroughConverterValidatorAndHandler_FullyPopulated_AssemblesWithoutNpe()
      throws Exception {
    assertDirectEditThroughConverterValidatorAndHandler(input -> {});
  }

  @Test
  void testDirectEditThroughConverterValidatorAndHandler_OmittedAuthScopes_AssemblesWithoutNpe()
      throws Exception {
    assertDirectEditThroughConverterValidatorAndHandler(input -> input.remove("authScopes"));
  }

  @Test
  void
      testDirectEditThroughConverterValidatorAndHandler_OmittedInstanceMetadata_AssemblesWithoutNpe()
          throws Exception {
    assertDirectEditThroughConverterValidatorAndHandler(input -> input.remove("instanceMetadata"));
  }

  @Test
  void testDirectEditThroughConverterValidatorAndHandler_OmittedLabels_AssemblesWithoutNpe()
      throws Exception {
    assertDirectEditThroughConverterValidatorAndHandler(input -> input.remove("labels"));
  }

  @Test
  void testDirectEditThroughConverterValidatorAndHandler_NullTags_AssemblesWithoutNpe()
      throws Exception {
    assertDirectEditThroughConverterValidatorAndHandler(input -> input.put("tags", null));
  }

  @Test
  void testDirectEditThroughConverterValidator_OmittedExplicitZonesRejectedBeforeHandlerAssembly() {
    GoogleNamedAccountCredentials credentials =
        new GoogleNamedAccountCredentials.Builder()
            .name("test-account")
            .project("test-project")
            .credentials(mock(GoogleCredentials.class))
            .regionToZonesMap(
                Map.of("us-central1", List.of("us-central1-a", "us-central1-b", "us-central1-c")))
            .build();
    MapBackedCredentialsRepository<GoogleNamedAccountCredentials> credentialsRepository =
        new MapBackedCredentialsRepository<>(
            GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
            new NoopCredentialsLifecycleHandler<>());
    credentialsRepository.save(credentials);

    BasicGoogleDeployAtomicOperationConverter converter =
        new BasicGoogleDeployAtomicOperationConverter();
    converter.setCredentialsRepository(credentialsRepository);
    BasicGoogleDeployDescriptionValidator validator = new BasicGoogleDeployDescriptionValidator();
    validator.setCredentialsRepository(credentialsRepository);
    setPrivateField(validator, "googleDeployDefaults", new GoogleConfiguration.DeployDefaults());

    Map<String, Object> input = directEditInput();
    @SuppressWarnings("unchecked")
    Map<String, Object> distributionPolicy = (Map<String, Object>) input.get("distributionPolicy");
    distributionPolicy.remove("zones");
    BasicGoogleDeployDescription description = converter.convertDescription(input);
    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);

    validator.validate(List.of(), description, errors);

    assertThat(errors.getFieldErrors())
        .anySatisfy(
            error -> {
              assertThat(error.getField()).isEqualTo("distributionPolicy.zones");
              assertThat(error.getCode())
                  .isEqualTo(
                      "basicGoogleDeployDescription.distributionPolicy.zones.requiredWhenSelectZones");
            });
  }

  @Test
  void testDirectEditThroughConverterValidatorAndHandler_EmptyLoadBalancers_AssemblesWithoutNpe()
      throws Exception {
    // The base resolves a real (stubbed) LB; emptying the list must take the early-return path in
    // getLoadBalancerToUpdateFromInput instead of calling queryAllLoadBalancers (asserted in
    // helper).
    assertDirectEditThroughConverterValidatorAndHandler(
        input -> input.put("loadBalancers", new ArrayList<>()), false);
  }

  @Test
  void testDirectEditThroughConverterValidatorAndHandler_OmittedSource_AssemblesWithoutNpe()
      throws Exception {
    // source is only consumed by the higher-level handle() ancestry path, not by
    // createInstanceGroupManagerFromInput; this case proves the converter+validator tolerate a
    // payload whose optional nested `source` object is absent (the base includes it).
    assertDirectEditThroughConverterValidatorAndHandler(input -> input.remove("source"));
  }

  /**
   * Integrated inbound-boundary coverage for the direct-edit deploy path. Unlike the field-level
   * input mutation tests above (which hand-build or directly deserialize the description), this
   * drives the raw direct-edit payload map through the real {@link
   * BasicGoogleDeployAtomicOperationConverter} and {@link BasicGoogleDeployDescriptionValidator}
   * before real {@link BasicGoogleDeployHandler} assembly. It fails if the converter or validator
   * rejects or mis-maps a null/omitted optional field on a regional flex payload, and asserts the
   * serialized outbound {@code regionInstanceGroupManagers.insert} body still carries the GCP
   * regional-MIG redistribution contract: flex or non-EVEN placement implies {@code
   * updatePolicy.instanceRedistributionType=NONE}.
   */
  private void assertDirectEditThroughConverterValidatorAndHandler(
      Consumer<Map<String, Object>> inputMutation) throws Exception {
    assertDirectEditThroughConverterValidatorAndHandler(inputMutation, true);
  }

  private void assertDirectEditThroughConverterValidatorAndHandler(
      Consumer<Map<String, Object>> inputMutation, boolean expectsLoadBalancerLookup)
      throws Exception {
    CapturingComputeTransport transport = new CapturingComputeTransport();
    Compute compute =
        new Compute(
            transport, GsonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);

    // Real credentials so the converter, validator, and handler all resolve the same account. The
    // capturing Compute lets us inspect the outbound insert body without a live GCP call.
    GoogleNamedAccountCredentials credentials =
        new GoogleNamedAccountCredentials.Builder()
            .name("test-account")
            .project("test-project")
            .credentials(mock(GoogleCredentials.class))
            .compute(compute)
            .regionToZonesMap(
                Map.of("us-central1", List.of("us-central1-a", "us-central1-b", "us-central1-c")))
            .build();
    MapBackedCredentialsRepository<GoogleNamedAccountCredentials> credentialsRepository =
        new MapBackedCredentialsRepository<>(
            GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
            new NoopCredentialsLifecycleHandler<>());
    credentialsRepository.save(credentials);

    BasicGoogleDeployAtomicOperationConverter converter =
        new BasicGoogleDeployAtomicOperationConverter();
    converter.setCredentialsRepository(credentialsRepository);

    BasicGoogleDeployDescriptionValidator validator = new BasicGoogleDeployDescriptionValidator();
    validator.setCredentialsRepository(credentialsRepository);
    // googleDeployDefaults is a private @Autowired Groovy field with no generated setter.
    setPrivateField(validator, "googleDeployDefaults", new GoogleConfiguration.DeployDefaults());

    Map<String, Object> input = directEditInput();
    inputMutation.accept(input);

    // Inbound boundary 1: converter must build the description straight from the raw map.
    BasicGoogleDeployDescription description = converter.convertDescription(input);

    // Inbound boundary 2: validator must accept the same payload with no errors.
    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);
    validator.validate(List.of(), description, errors);
    assertThat(errors.hasErrors()).as("validation errors: %s", errors.getAllErrors()).isFalse();

    // Handler assembly boundary: run the same build helpers plus the regional insert so the
    // outbound request body reflects the fully assembled description. This is where a null/omitted
    // optional field or a missing redistribution contract would surface.
    injectField("registry", new DefaultRegistry());
    mockedGCEUtil.when(() -> GCEUtil.buildServiceAccount(any(), any())).thenReturn(List.of());
    mockedGCEUtil.when(() -> GCEUtil.buildTagsFromList(any())).thenReturn(new Tags());

    // Stub the zone/LB collaborators only for the cases that actually reach them, so strict
    // stubbing still flags a genuinely dead stub.
    boolean expectsExplicitZones =
        Boolean.TRUE.equals(description.getSelectZones())
            && description.getDistributionPolicy() != null
            && !CollectionUtils.isEmpty(description.getDistributionPolicy().getZones());
    if (expectsExplicitZones) {
      mockedGCEUtil
          .when(() -> GCEUtil.buildZoneUrl(eq("test-project"), any()))
          .thenReturn(
              "https://www.googleapis.com/compute/v1/projects/test-project/zones/us-central1-a");
    }
    if (expectsLoadBalancerLookup) {
      // Return an empty result so no GoogleLoadBalancerView is cast to a concrete view subtype
      // downstream; the point of this case is that a non-empty list drives the resolver call.
      mockedGCEUtil
          .when(() -> GCEUtil.queryAllLoadBalancers(any(), any(), any(), any()))
          .thenReturn(List.of());
    }

    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setName("example-server-group")
            .setBaseInstanceName("example-server-group")
            .setInstanceTemplate("global/instanceTemplates/example-template")
            .setTargetSize(2);

    // Resolve the LoadBalancerInfo from the raw description.loadBalancers (rather than passing an
    // empty one) so the loadBalancers case genuinely exercises inbound LB resolution.
    BasicGoogleDeployHandler.LoadBalancerInfo lbInfo =
        basicGoogleDeployHandler.getLoadBalancerToUpdateFromInput(description, mockTask);

    assertDoesNotThrow(
        () -> {
          basicGoogleDeployHandler.buildLoadBalancerPolicyFromInput(description);
          basicGoogleDeployHandler.buildServiceAccountFromInput(description);
          basicGoogleDeployHandler.buildTagsFromInput(description);
          basicGoogleDeployHandler.buildLabelsFromInput(
              description, "example-server-group", "us-central1");
          basicGoogleDeployHandler.createInstanceGroupManagerFromInput(
              description,
              instanceGroupManager,
              lbInfo,
              "example-server-group",
              "us-central1",
              mockTask);
        });

    // The LB case must actually flow through GCEUtil.queryAllLoadBalancers (non-empty case) or
    // take the early-return empty path (empty case), not silently no-op. lbInfo is empty either way
    // (the resolver was stubbed to return no load balancers), so we assert on the branch taken.
    assertThat(lbInfo).isNotNull();
    if (expectsLoadBalancerLookup) {
      mockedGCEUtil.verify(
          () ->
              GCEUtil.queryAllLoadBalancers(
                  any(), eq(description.getLoadBalancers()), any(), any()));
    } else {
      mockedGCEUtil.verify(
          () -> GCEUtil.queryAllLoadBalancers(any(), any(), any(), any()), never());
    }

    CapturedRequest insertRequest =
        transport
            .findPostTo("/projects/test-project/regions/us-central1/instanceGroupManagers")
            .orElseThrow();
    JsonNode capturedBody = objectMapper.readTree(insertRequest.body());
    assertThat(capturedBody.path("distributionPolicy").path("targetShape").asText())
        .isEqualTo("ANY");
    JsonNode primarySelection =
        capturedBody.path("instanceFlexibilityPolicy").path("instanceSelections").path("primary");
    assertThat(primarySelection.path("rank").asInt()).isEqualTo(0);
    List<String> outboundMachineTypes = new ArrayList<>();
    primarySelection.path("machineTypes").forEach(node -> outboundMachineTypes.add(node.asText()));
    assertThat(outboundMachineTypes).containsExactly("e2-standard-2");
    assertThat(capturedBody.path("updatePolicy").path("instanceRedistributionType").asText())
        .isEqualTo("NONE");

    // Valid direct-edit paths must materialize the explicit zones in the outbound body, proving
    // setDistributionPolicyToInstanceGroup consumed the direct-edit zones list.
    JsonNode outboundZones = capturedBody.path("distributionPolicy").path("zones");
    if (expectsExplicitZones) {
      assertThat(outboundZones.isArray()).isTrue();
      assertThat(outboundZones).hasSize(1);
      assertThat(outboundZones.get(0).path("zone").asText())
          .isEqualTo(
              "https://www.googleapis.com/compute/v1/projects/test-project/zones/us-central1-a");
    } else {
      assertThat(outboundZones.isMissingNode() || outboundZones.isEmpty()).isTrue();
    }
  }

  /**
   * A valid regional flex direct-edit payload as a raw request map (the shape Spinnaker hands to
   * the converter), populated with the optional fields the input mutations omit/null.
   */
  private Map<String, Object> directEditInput() {
    Map<String, Object> input = new HashMap<>();
    input.put("application", "example");
    input.put("stack", "flex");
    input.put("credentials", "test-account");
    input.put("region", "us-central1");
    input.put("regional", true);
    // selectZones=true requires and consumes the explicit distributionPolicy.zones list during
    // handler assembly.
    input.put("selectZones", true);
    input.put("disableTraffic", true);
    input.put("targetSize", 2);
    input.put("image", "debian-11");
    input.put("instanceType", "e2-standard-2");

    Map<String, Object> disk = new HashMap<>();
    disk.put("type", "pd-ssd");
    disk.put("sizeGb", 10);
    input.put("disks", new ArrayList<>(List.of(disk)));

    Map<String, Object> distributionPolicy = new HashMap<>();
    distributionPolicy.put("targetShape", "ANY");
    distributionPolicy.put("zones", new ArrayList<>(List.of("us-central1-a")));
    input.put("distributionPolicy", distributionPolicy);

    Map<String, Object> primarySelection = new HashMap<>();
    primarySelection.put("rank", 0);
    primarySelection.put("machineTypes", new ArrayList<>(List.of("e2-standard-2")));
    Map<String, Object> instanceSelections = new HashMap<>();
    instanceSelections.put("primary", primarySelection);
    Map<String, Object> instanceFlexibilityPolicy = new HashMap<>();
    instanceFlexibilityPolicy.put("instanceSelections", instanceSelections);
    input.put("instanceFlexibilityPolicy", instanceFlexibilityPolicy);

    input.put("tags", new ArrayList<>(List.of("allow-http")));
    input.put("authScopes", new ArrayList<>(List.of("https://www.googleapis.com/auth/compute")));
    input.put("labels", new HashMap<>(Map.of("team", "spinnaker")));
    input.put("instanceMetadata", new HashMap<>(Map.of("user-key", "user-value")));
    input.put("loadBalancers", new ArrayList<>(List.of("some-lb")));

    Map<String, Object> source = new HashMap<>();
    source.put("region", "us-central1");
    source.put("serverGroupName", "example-server-group");
    source.put("useSourceCapacity", false);
    input.put("source", source);

    return input;
  }

  private static void setPrivateField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private BasicGoogleDeployDescription populatedRegionalFlexDescription() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setApplication("example");
    description.setStack("flex");
    description.setRegion("us-central1");
    description.setRegional(true);
    description.setSelectZones(false);
    description.setDistributionPolicy(new GoogleDistributionPolicy(null, "ANY"));
    description.setInstanceFlexibilityPolicy(flexPolicy());
    description.setTags(new ArrayList<>(List.of("allow-http")));
    description.setAuthScopes(new ArrayList<>(List.of("https://www.googleapis.com/auth/compute")));
    description.setLabels(new HashMap<>(Map.of("team", "spinnaker")));
    description.setInstanceMetadata(new HashMap<>(Map.of("user-key", "user-value")));
    description.setLoadBalancers(new ArrayList<>(List.of("some-lb")));
    return description;
  }
}
