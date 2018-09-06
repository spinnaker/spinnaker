package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import lombok.Data;

@Data
public class Domain {
  private String name;
  private String owningOrganizationGuid;
}
