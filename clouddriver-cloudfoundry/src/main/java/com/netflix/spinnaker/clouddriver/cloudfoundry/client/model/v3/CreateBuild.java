package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public class CreateBuild {
  @JsonProperty("package")
  private PackageId packageId;

  public CreateBuild(String packageId) {
    this.packageId = new PackageId(packageId);
  }

  @RequiredArgsConstructor
  @Getter
  private class PackageId {
    private final String guid;
  }
}
