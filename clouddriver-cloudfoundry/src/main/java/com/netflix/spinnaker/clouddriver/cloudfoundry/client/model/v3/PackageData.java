package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.Data;

@Data
public class PackageData {
  private PackageChecksum checksum;
}