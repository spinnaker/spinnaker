package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.OrganizationService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Organization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Page;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Space;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationsTest {
  private Organizations organizations;

  {
    OrganizationService organizationService = mock(OrganizationService.class);
    organizations = new Organizations(organizationService);
    when(organizationService.all(anyInt(), any())).thenReturn(generateOrganizationPage());
    when(organizationService.getSpaceByName(anyString(), anyInt(), any())).thenReturn(generateSpacePage());
  }

  @Test
  void findSpaceByRegionSucceedsWhenSpaceExistsInOrg() {
    CloudFoundryOrganization expectedOrganization = CloudFoundryOrganization.builder().id("org-guid").name("org").build();
    CloudFoundrySpace expectedSpace = CloudFoundrySpace.builder().id("space-guid").name("space").organization(expectedOrganization).build();

    Optional<CloudFoundrySpace> result = organizations.findSpaceByRegion("org > space");

    assertThat(result).isEqualTo(Optional.of(expectedSpace));
  }

  @Test
  void findSpaceByRegionThrowsExceptionForOrgSpaceNameCaseMismatch() {
    try {
      organizations.findSpaceByRegion("org > sPaCe");
      failBecauseExceptionWasNotThrown(CloudFoundryApiException.class);
    } catch(Throwable t) {
      assertThat(t).isInstanceOf(CloudFoundryApiException.class);
    }
  }

  private Page<Organization> generateOrganizationPage() {
    Organization organization = new Organization().setName("org");
    Resource.Metadata metadata = new Resource.Metadata().setGuid("org-guid");
    Resource<Organization> resource = new Resource<>();
    resource.setEntity(organization);
    resource.setMetadata(metadata);
    Page<Organization> page = new Page<>();
    page.setTotalResults(1);
    page.setTotalPages(1);
    page.setResources(Collections.singletonList(resource));
    return page;
  }

  private Page<Space> generateSpacePage() {
    Space space = new Space().setName("space");
    Resource.Metadata metadata = new Resource.Metadata().setGuid("space-guid");
    Resource<Space> resource = new Resource<>();
    resource.setEntity(space);
    resource.setMetadata(metadata);
    Page<Space> page = new Page<>();
    page.setTotalResults(1);
    page.setTotalPages(1);
    page.setResources(Collections.singletonList(resource));
    return page;
  }
}
