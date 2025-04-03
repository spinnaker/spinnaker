/*
 * Copyright 2025 OpsMx, Inc.
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
package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateBoot128ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateBoot154ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateSpringSecurity5OAuth2ProfileFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GateServiceTest {

  private GateService gateService;
  private GateBoot128ProfileFactory mockBoot128ProfileFactory;
  private GateBoot154ProfileFactory mockBoot154ProfileFactory;
  private GateSpringSecurity5OAuth2ProfileFactory mockBoot667ProfileFactory;
  private ArtifactService mockArtifactService;

  @BeforeEach
  void setUp() {
    gateService = mock(GateService.class, CALLS_REAL_METHODS);
    mockBoot128ProfileFactory = mock(GateBoot128ProfileFactory.class);
    mockBoot154ProfileFactory = mock(GateBoot154ProfileFactory.class);
    mockBoot667ProfileFactory = mock(GateSpringSecurity5OAuth2ProfileFactory.class);
    mockArtifactService = mock(ArtifactService.class);

    gateService.setBoot128ProfileFactory(mockBoot128ProfileFactory);
    gateService.setBoot154ProfileFactory(mockBoot154ProfileFactory);
    gateService.setSpringSecurity5OAuth2ProfileFactory(mockBoot667ProfileFactory);
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
