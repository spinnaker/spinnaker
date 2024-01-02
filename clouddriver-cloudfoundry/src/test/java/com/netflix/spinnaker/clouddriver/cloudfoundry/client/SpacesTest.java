package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.SpaceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Page;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Space;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import retrofit2.Response;
import retrofit2.mock.Calls;

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
        .thenReturn(
            Calls.response(Response.success(Page.singleton(serviceInstance, serviceInstanceId))));

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
        .thenReturn(
            Calls.response(
                Response.success(new Page<ServiceInstance>().setTotalResults(0).setTotalPages(1))));

    CloudFoundryServiceInstance actual =
        spaces.getServiceInstanceByNameAndSpace(serviceInstanceName1, cloudFoundrySpace);

    assertThat(actual).isNull();
  }

  @Test
  void findSpaceByRegionSucceedsWhenSpaceExistsInOrg() {
    CloudFoundryOrganization expectedOrganization =
        CloudFoundryOrganization.builder().id("org-guid").name("org").build();

    Space space = new Space();
    space.setName("space");
    space.setGuid("space-guid");

    CloudFoundrySpace expectedSpace =
        CloudFoundrySpace.builder()
            .id("space-guid")
            .name("space")
            .organization(expectedOrganization)
            .build();

    when(spaceService.all(any(), any(), any()))
        .thenReturn(Calls.response(Response.success(generateSpacePage())));
    when(orgs.findByName(anyString())).thenReturn(Optional.of(expectedOrganization));
    Optional<CloudFoundrySpace> result = spaces.findSpaceByRegion("org > space");

    assertThat(result).isEqualTo(Optional.of(expectedSpace));
  }

  @Test
  void findSpaceByRegionThrowsExceptionForOrgSpaceNameCaseMismatch() {
    try {
      spaces.findSpaceByRegion("org > sPaCe");
      failBecauseExceptionWasNotThrown(CloudFoundryApiException.class);
    } catch (Throwable t) {
      assertThat(t).isInstanceOf(CloudFoundryApiException.class);
    }
  }

  private Pagination<Space> generateSpacePage() {
    Space space = new Space();
    space.setGuid("space-guid");
    space.setName("space");
    Pagination.Details details = new Pagination.Details();
    details.setTotalPages(1);
    Pagination<Space> pagination = new Pagination<>();
    pagination.setPagination(details);
    pagination.setResources(List.of(space));
    return pagination;
  }
}
