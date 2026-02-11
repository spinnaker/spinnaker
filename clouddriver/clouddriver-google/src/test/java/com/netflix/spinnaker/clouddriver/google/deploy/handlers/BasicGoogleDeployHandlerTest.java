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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.model.*;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry;
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException;
import com.netflix.spinnaker.clouddriver.google.deploy.ops.GoogleUserDataProvider;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoHealingPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleDistributionPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck;
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
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.config.GoogleConfiguration;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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
    MockitoAnnotations.initMocks(this);
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
        .when(() -> GCEUtil.buildShieldedVmConfig(mockDescription))
        .thenReturn(shieldedVmConfig);

    basicGoogleDeployHandler.addShieldedVmConfigToInstanceProperties(
        mockDescription, instanceProperties, bootImage);
    assertEquals(shieldedVmConfig, instanceProperties.getShieldedInstanceConfig());
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
  void buildAutoHealingPolicyFromInput_whenMaxUnavailableIsSet_updatesPolicy() {
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
    assertEquals(5, ((FixedOrPercent) result.get(0).get("maxUnavailable")).getFixed());
    assertEquals(10, ((FixedOrPercent) result.get(0).get("maxUnavailable")).getPercent());
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
    mockPolicy.setTargetShape("ANY_SHAPE");

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
    assertThat(instanceGroupManager.getDistributionPolicy().getTargetShape())
        .isEqualTo("ANY_SHAPE");
  }

  @Test
  void testSetInstanceFlexibilityPolicyToInstanceGroup_mapsValidSelectionsOnly() {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();

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

    com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy flexPolicy =
        new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy();
    flexPolicy.setInstanceSelections(selections);
    description.setInstanceFlexibilityPolicy(flexPolicy);

    basicGoogleDeployHandler.setInstanceFlexibilityPolicyToInstanceGroup(
        description, instanceGroupManager);

    assertNotNull(instanceGroupManager.getInstanceFlexibilityPolicy());
    assertEquals(1, instanceGroupManager.getInstanceFlexibilityPolicy().getInstanceSelections().size());
    assertTrue(
        instanceGroupManager.getInstanceFlexibilityPolicy().getInstanceSelections().containsKey(
            "preferred"));
    assertFalse(
        instanceGroupManager.getInstanceFlexibilityPolicy().getInstanceSelections().containsKey(
            "malformed"));
    assertEquals(
        Integer.valueOf(1),
        instanceGroupManager
            .getInstanceFlexibilityPolicy()
            .getInstanceSelections()
            .get("preferred")
            .getRank());
    assertEquals(
        List.of("n2-standard-8"),
        instanceGroupManager
            .getInstanceFlexibilityPolicy()
            .getInstanceSelections()
            .get("preferred")
            .getMachineTypes());
  }

  private GoogleLoadBalancerView mockLoadBalancer(GoogleLoadBalancerType loadBalancerType) {
    GoogleLoadBalancerView mockLB = new GoogleLoadBalancerView() {};

    mockLB.setLoadBalancerType(loadBalancerType);
    return mockLB;
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
}
