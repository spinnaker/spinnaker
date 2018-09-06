package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import lombok.Data;

@Data
public class Organization {
  private String name;
  private String status;
}
