package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.SpaceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class SpacesTest {
  private SpaceService spaceService = mock(SpaceService.class);
  private Organizations orgs = mock(Organizations.class);
  private Spaces spaces = new Spaces(spaceService, orgs);
  private String spaceId = "space-guid";
  private CloudFoundrySpace cloudFoundrySpace =
      CloudFoundrySpace.builder().name("space").id(spaceId).build();

  @Test
  void getServiceInstanceByNameAndSpaceShouldReturnServiceInstance() {
    String serviceInstanceName = "service-instance";
    String serviceInstanceId = "service-instance-guid";
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance.setName(serviceInstanceName);
    when(spaceService.getServiceInstancesById(any(), any(), any()))
        .thenReturn(Page.singleton(serviceInstance, serviceInstanceId));

    CloudFoundryServiceInstance actual =
        spaces.getServiceInstanceByNameAndSpace(serviceInstanceName, cloudFoundrySpace);

    assertThat(actual.getName()).isEqualTo(serviceInstanceName);
    assertThat(actual.getId()).isEqualTo(serviceInstanceId);
    verify(spaceService)
        .getServiceInstancesById(
            eq(spaceId), any(), eq(Collections.singletonList("name:" + serviceInstanceName)));
  }

  @Test
  void getServiceInstanceByNameAndSpaceShouldReturnNullWhenSpaceHasNoServiceInstances() {
    String serviceInstanceName1 = "service-instance";
    when(spaceService.getServiceInstancesById(any(), any(), any()))
        .thenReturn(new Page<ServiceInstance>().setTotalResults(0).setTotalPages(1));

    CloudFoundryServiceInstance actual =
        spaces.getServiceInstanceByNameAndSpace(serviceInstanceName1, cloudFoundrySpace);

    assertThat(actual).isNull();
  }
}
