package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.SpaceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.SpaceSummary;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.SummaryServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import io.vavr.collection.HashSet;
import org.junit.jupiter.api.Test;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

class SpacesTest {
  private SpaceService spaceService = mock(SpaceService.class);
  private Organizations orgs = mock(Organizations.class);
  private Spaces spaces = new Spaces(spaceService, orgs);
  private String spaceId = "space-guid";
  private CloudFoundrySpace cloudFoundrySpace = CloudFoundrySpace.builder()
    .name("space")
    .id(spaceId)
    .build();

  @Test
  void getServiceInstanceByNameAndSpaceShouldReturnServiceInstance() {
    String serviceInstanceName1 = "service-instance-1";
    SummaryServiceInstance summaryServiceInstance1 = new SummaryServiceInstance()
      .setGuid("service-instance-1-guid")
      .setName(serviceInstanceName1);
    SummaryServiceInstance summaryServiceInstance2 = new SummaryServiceInstance()
      .setGuid("service-instance-2-guid")
      .setName("service-instance-2");
    SpaceSummary spaceSummary = new SpaceSummary()
      .setServices(HashSet.of(summaryServiceInstance1, summaryServiceInstance2).toJavaSet());
    when(spaceService.getSpaceSummaryById(any())).thenReturn(spaceSummary);

    SummaryServiceInstance serviceInstance = spaces.getSummaryServiceInstanceByNameAndSpace(serviceInstanceName1, cloudFoundrySpace);

    assertThat(serviceInstance).isEqualToComparingFieldByFieldRecursively(summaryServiceInstance1);
    verify(spaceService).getSpaceSummaryById(spaceId);
  }

  @Test
  void getSummaryServiceInstanceByNameAndSpaceShouldReturnNullWhenSpaceHasNoServiceInstances() {
    String serviceInstanceName1 = "service-instance-1";
    SpaceSummary spaceSummary = new SpaceSummary()
      .setServices(null);
    when(spaceService.getSpaceSummaryById(any())).thenReturn(spaceSummary);

    SummaryServiceInstance serviceInstance = spaces.getSummaryServiceInstanceByNameAndSpace(serviceInstanceName1, cloudFoundrySpace);

    assertThat(serviceInstance).isNull();
  }

  @Test
  void getSummaryServiceInstanceByNameAndSpaceShouldReturnNullWhenSpaceHasNoMatchingServiceInstances() {
    String serviceInstanceName1 = "service-instance-1";
    SummaryServiceInstance summaryServiceInstance2 = new SummaryServiceInstance()
      .setGuid("service-instance-2-guid")
      .setName("service-instance-2");
    SpaceSummary spaceSummary = new SpaceSummary()
      .setServices(singleton(summaryServiceInstance2));
    when(spaceService.getSpaceSummaryById(any())).thenReturn(spaceSummary);

    SummaryServiceInstance serviceInstance = spaces.getSummaryServiceInstanceByNameAndSpace(serviceInstanceName1, cloudFoundrySpace);

    assertThat(serviceInstance).isNull();
  }
}
