package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.OrganizationService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Organization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import retrofit2.Response;
import retrofit2.mock.Calls;

class OrganizationsTest {
  private Organizations organizations;

  {
    OrganizationService organizationService = mock(OrganizationService.class);
    organizations = new Organizations(organizationService);
    when(organizationService.all(any(), any()))
        .thenReturn(Calls.response(Response.success(generateOrganizationPage())));
  }

  @Test
  void findByNameSucceedsWhenOrgExists() {
    CloudFoundryOrganization expectedOrganization =
        CloudFoundryOrganization.builder().id("org-guid").name("org").build();

    Optional<CloudFoundryOrganization> result = organizations.findByName("org");

    assertThat(result).isEqualTo(Optional.of(expectedOrganization));
  }

  private Pagination<Organization> generateOrganizationPage() {
    Organization organization = new Organization();
    organization.setGuid("org-guid");
    organization.setName("org");
    Pagination.Details details = new Pagination.Details();
    details.setTotalPages(1);
    Pagination<Organization> pagination = new Pagination<>();
    pagination.setPagination(details);
    pagination.setResources(List.of(organization));
    return pagination;
  }
}
