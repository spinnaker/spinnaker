package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateBoot128ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateBoot154ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateBoot667ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateProfileFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GateServiceTest {

  private GateService gateService;
  private GateBoot128ProfileFactory mockBoot128ProfileFactory;
  private GateBoot154ProfileFactory mockBoot154ProfileFactory;
  private GateBoot667ProfileFactory mockBoot667ProfileFactory;
  private ArtifactService mockArtifactService;

  @BeforeEach
  void setUp() {
    gateService = mock(GateService.class, CALLS_REAL_METHODS);
    mockBoot128ProfileFactory = mock(GateBoot128ProfileFactory.class);
    mockBoot154ProfileFactory = mock(GateBoot154ProfileFactory.class);
    mockBoot667ProfileFactory = mock(GateBoot667ProfileFactory.class);
    mockArtifactService = mock(ArtifactService.class);

    gateService.setBoot128ProfileFactory(mockBoot128ProfileFactory);
    gateService.setBoot154ProfileFactory(mockBoot154ProfileFactory);
    gateService.setBoot667ProfileFactory(mockBoot667ProfileFactory);
    when(gateService.getArtifactService()).thenReturn(mockArtifactService);
  }

  @Test
  void testGetGateProfileFactoryVersionLessThan070() {
    when(mockArtifactService.getArtifactVersion("test-deployment", SpinnakerArtifact.GATE))
        .thenReturn("0.6.9");

    GateProfileFactory result = gateService.getGateProfileFactory("test-deployment");
    assertEquals(mockBoot128ProfileFactory, result);
  }

  @Test
  void testGetGateProfileFactoryVersionBetween070And667() {
    when(mockArtifactService.getArtifactVersion("test-deployment", SpinnakerArtifact.GATE))
        .thenReturn("6.66.0");

    GateProfileFactory result = gateService.getGateProfileFactory("test-deployment");
    assertEquals(mockBoot154ProfileFactory, result);
  }

  @Test
  void testGetGateProfileFactoryVersionGreaterThan667() {
    when(mockArtifactService.getArtifactVersion("test-deployment", SpinnakerArtifact.GATE))
        .thenReturn("6.67.1");

    GateProfileFactory result = gateService.getGateProfileFactory("test-deployment");
    assertEquals(mockBoot667ProfileFactory, result);
  }

  @Test
  void testGetGateProfileFactoryInvalidVersionUsesDefault() {
    when(mockArtifactService.getArtifactVersion("test-deployment", SpinnakerArtifact.GATE))
        .thenReturn("invalid-version");

    GateProfileFactory result = gateService.getGateProfileFactory("test-deployment");
    assertEquals(mockBoot667ProfileFactory, result);
  }
}
